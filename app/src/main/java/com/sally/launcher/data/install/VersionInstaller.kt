package com.sally.launcher.data.install

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import com.sally.launcher.data.model.MinecraftVersion
import com.sally.launcher.data.download.ResilientDownloader
import com.sally.launcher.data.download.TransferProgress
import com.sally.launcher.data.storage.LauncherStorage
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.util.Locale
import java.util.concurrent.Callable
import java.util.concurrent.CancellationException
import java.util.concurrent.ExecutionException
import java.util.concurrent.ExecutorCompletionService
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit

/**
 * Downloads official Minecraft version data, the client JAR, common Java libraries,
 * the asset index and content-addressed asset objects into app-private storage.
 * Native classifiers are intentionally not selected: desktop natives are not Android-ready.
 *
 * Progress is reported in bytes ([onBytes]): the total is computed once the asset index is known,
 * so the progress bar reflects how much data has moved, not how many files have finished.
 */
class VersionInstaller(context: Context) {
    private val root = LauncherStorage.minecraftRoot(context)
    private val versionsDir = File(root, "versions")
    private val librariesDir = File(root, "libraries")
    private val assetsDir = File(root, "assets")

    data class Result(
        val versionId: String,
        val metadataFile: File,
        val clientJar: File,
        val libraryFiles: Int,
        val assetFiles: Int,
        val downloadedBytes: Long,
    )

    private data class DownloadTask(
        val url: String,
        val destination: File,
        val sha1: String?,
        val expectedSize: Long,
        val label: String,
    )

    fun installVersion(
        version: MinecraftVersion,
        onProgress: (String) -> Unit,
        onBytes: (done: Long, total: Long) -> Unit = { _, _ -> },
        forceRedownload: Boolean = false,
    ): Result {
        requireHttps(version.url)
        val id = safeId(version.id)
        val versionDir = File(versionsDir, id)
        ensureDirectory(versionDir)

        val metadataFile = File(versionDir, "$id.json")
        val metadataPart = File(versionDir, "$id.json.part")
        onProgress("Downloading version metadata…")
        downloadTo(version.url, metadataPart, null, -1L, status = onProgress) { _, _ -> }
        replacePart(metadataPart, metadataFile)

        val metadata = try {
            JSONObject(metadataFile.readText(Charsets.UTF_8))
        } catch (e: Exception) {
            metadataFile.delete()
            throw IOException("Downloaded version metadata is invalid", e)
        }

        val downloads = metadata.optJSONObject("downloads")
            ?: throw IOException("Version metadata has no downloads section")
        val client = downloads.optJSONObject("client")
            ?: throw IOException("This version has no downloadable client JAR")
        val clientUrl = client.optString("url")
        requireHttps(clientUrl)
        val clientSha1 = validSha1(client.optString("sha1"))
        val clientSize = client.optLong("size", -1L)
        val clientJar = File(versionDir, "$id.jar")
        val clientNeeded = forceRedownload || !isValidFile(clientJar, clientSha1, clientSize)

        // The asset index is small, so it is fetched first. That makes every byte that will move
        // known before the progress bar starts, instead of growing the total stage by stage.
        val libraryTasks = buildLibraryTasks(metadata.optJSONArray("libraries"))
        val assetIndex = loadAssetIndex(metadata, onProgress, forceRedownload)
        val assetTasks = buildAssetTasks(assetIndex)
        val allTasks = deduplicateTasks(libraryTasks + assetTasks)
        val pending = if (forceRedownload) allTasks else allTasks.filterNot { isValidFile(it.destination, it.sha1, it.expectedSize) }

        val plannedBytes = (if (clientNeeded) clientSize.coerceAtLeast(0L) else 0L) +
            pending.sumOf { it.expectedSize.coerceAtLeast(0L) }
        val progress = TransferProgress(plannedBytes, onBytes)
        var downloadedBytes = 0L

        if (clientNeeded) {
            onProgress("Downloading Minecraft $id client…")
            val part = File(versionDir, "$id.jar.part")
            if (forceRedownload) part.delete()
            val key = clientJar.path
            progress.update(key, partialBytes(part))
            val transferred = try {
                downloadTo(clientUrl, part, clientSha1, clientSize, status = onProgress) { current, _ ->
                    progress.update(key, current)
                }
            } catch (error: Exception) {
                progress.update(key, partialBytes(part))
                throw error
            }
            downloadedBytes += transferred
            replacePart(part, clientJar)
            progress.finish(key, clientJar.length())
        }

        if (pending.isEmpty()) {
            onProgress("All libraries and assets are already present and verified")
        } else {
            onProgress("Downloading libraries and assets (up to $MAX_PARALLEL_DOWNLOADS at once)…")
            downloadedBytes += downloadTasks(pending, onProgress, progress, forceRedownload)
        }

        onProgress("Install files ready: ${libraryTasks.size} library artifacts, ${assetTasks.size} assets")
        return Result(id, metadataFile, clientJar, libraryTasks.size, assetTasks.size, downloadedBytes)
    }

    private fun buildLibraryTasks(libraries: JSONArray?): List<DownloadTask> {
        if (libraries == null) return emptyList()
        val tasks = mutableListOf<DownloadTask>()
        for (i in 0 until libraries.length()) {
            val library = libraries.optJSONObject(i) ?: continue
            if (!isAllowedOnAndroidLinux(library)) continue
            val downloads = library.optJSONObject("downloads")
            val artifact = downloads?.optJSONObject("artifact")
            if (artifact != null) {
                val path = artifact.optString("path")
                val safePath = safeRelativePath(path) ?: continue
                val url = artifact.optString("url")
                if (!url.startsWith("https://", ignoreCase = true)) continue
                tasks += DownloadTask(
                    url = url,
                    destination = File(librariesDir, safePath),
                    sha1 = validSha1(artifact.optString("sha1")),
                    expectedSize = artifact.optLong("size", -1L),
                    label = library.optString("name", path),
                )
                continue
            }

            // Older version metadata may omit downloads.artifact and rely on Maven coordinates.
            if (downloads == null) {
                val coordinate = library.optString("name")
                val parts = coordinate.split(':')
                if (parts.size !in 3..4) continue
                if (parts.any { it.isBlank() || it == "." || it == ".." || it.contains('/') || it.contains('\\') }) continue
                val groupPath = parts[0].replace('.', '/')
                val artifactName = parts[1]
                val artifactVersion = parts[2]
                val classifier = parts.getOrNull(3)?.let { "-$it" }.orEmpty()
                val fileName = "$artifactName-$artifactVersion$classifier.jar"
                val relative = safeRelativePath("$groupPath/$artifactName/$artifactVersion/$fileName") ?: continue
                val baseUrl = library.optString("url", "https://libraries.minecraft.net/").let {
                    if (it.endsWith('/')) it else "$it/"
                }
                if (!baseUrl.startsWith("https://", ignoreCase = true)) continue
                tasks += DownloadTask(
                    url = baseUrl + relative,
                    destination = File(librariesDir, relative),
                    sha1 = null,
                    expectedSize = -1L,
                    label = coordinate,
                )
            }
        }
        return tasks
    }

    /** Minecraft's rules model desktop OSes. On Android we only fetch generic/Linux Java artifacts;
     * native classifier files are intentionally excluded and need an Android-specific runtime port.
     */
    private fun isAllowedOnAndroidLinux(library: JSONObject): Boolean {
        val rules = library.optJSONArray("rules") ?: return true
        var allowed = false
        val arch = System.getProperty("os.arch").orEmpty().lowercase(Locale.ROOT)
        for (i in 0 until rules.length()) {
            val rule = rules.optJSONObject(i) ?: continue
            val os = rule.optJSONObject("os")
            var matches = true
            if (os != null) {
                val name = os.optString("name")
                if (name.isNotBlank() && name != "linux") matches = false
                val requiredArch = os.optString("arch")
                if (requiredArch.isNotBlank() && !archMatches(requiredArch, arch)) matches = false
                val versionPattern = os.optString("version")
                if (versionPattern.isNotBlank() && !Regex(versionPattern).containsMatchIn(System.getProperty("os.version").orEmpty())) matches = false
            }
            if (matches) allowed = rule.optString("action") == "allow"
        }
        return allowed
    }

    private fun archMatches(required: String, actual: String): Boolean {
        val normalizedRequired = when (required.lowercase(Locale.ROOT)) {
            "x86_64", "amd64" -> "x86_64"
            "x86", "i386", "i486", "i586", "i686" -> "x86"
            "aarch64", "arm64" -> "aarch64"
            else -> required.lowercase(Locale.ROOT)
        }
        val normalizedActual = when {
            actual in setOf("amd64", "x86_64") -> "x86_64"
            actual in setOf("x86", "i386", "i486", "i586", "i686") -> "x86"
            actual in setOf("aarch64", "arm64", "arm64-v8a") -> "aarch64"
            else -> actual
        }
        return normalizedRequired == normalizedActual
    }

    /** Downloads (or reuses) the asset index and returns it parsed; null when metadata has no index. */
    private fun loadAssetIndex(metadata: JSONObject, onProgress: (String) -> Unit, forceRedownload: Boolean): JSONObject? {
        val assetIndex = metadata.optJSONObject("assetIndex") ?: return null
        val assetIndexId = safeId(assetIndex.optString("id").ifBlank { metadata.optString("assets", "legacy") })
        val indexUrl = assetIndex.optString("url")
        requireHttps(indexUrl)
        val indexSha1 = validSha1(assetIndex.optString("sha1"))
        val indexSize = assetIndex.optLong("size", -1L)
        ensureDirectory(File(assetsDir, "indexes"))
        val indexFile = File(File(assetsDir, "indexes"), "$assetIndexId.json")
        if (forceRedownload || !isValidFile(indexFile, indexSha1, indexSize)) {
            onProgress("Downloading asset index $assetIndexId…")
            val part = File(indexFile.parentFile, "${indexFile.name}.part")
            if (forceRedownload) part.delete()
            downloadTo(indexUrl, part, indexSha1, indexSize, status = onProgress) { _, _ -> }
            replacePart(part, indexFile)
        }
        return try {
            JSONObject(indexFile.readText(Charsets.UTF_8))
        } catch (e: Exception) {
            indexFile.delete()
            throw IOException("Asset index is invalid", e)
        }
    }

    private fun buildAssetTasks(index: JSONObject?): List<DownloadTask> {
        val objects = index?.optJSONObject("objects") ?: return emptyList()
        val tasks = mutableListOf<DownloadTask>()
        val keys = objects.keys()
        while (keys.hasNext()) {
            val logicalName = keys.next()
            val item = objects.optJSONObject(logicalName) ?: continue
            val hash = validSha1(item.optString("hash")) ?: continue
            val relative = "${hash.substring(0, 2)}/$hash"
            val destination = File(File(assetsDir, "objects"), relative)
            tasks += DownloadTask(
                url = "https://resources.download.minecraft.net/$relative",
                destination = destination,
                sha1 = hash,
                expectedSize = item.optLong("size", -1L),
                label = logicalName,
            )
        }
        return tasks
    }

    private fun deduplicateTasks(tasks: List<DownloadTask>): List<DownloadTask> {
        val byPath = linkedMapOf<String, DownloadTask>()
        for (task in tasks) {
            val canonical = task.destination.canonicalPath
            val previous = byPath[canonical]
            if (previous != null && previous.sha1 != null && task.sha1 != null && !previous.sha1.equals(task.sha1, true)) {
                throw IOException("Conflicting checksums for ${task.destination.name}")
            }
            if (previous == null) byPath[canonical] = task
        }
        return byPath.values.toList()
    }

    private fun downloadTasks(
        tasks: List<DownloadTask>,
        onProgress: (String) -> Unit,
        progress: TransferProgress,
        forceRedownload: Boolean,
    ): Long {
        ensureDirectory(librariesDir)
        ensureDirectory(File(assetsDir, "objects"))
        val pool = Executors.newFixedThreadPool(MAX_PARALLEL_DOWNLOADS)
        val completion = ExecutorCompletionService<Long>(pool)
        var completed = 0
        var totalBytes = 0L
        var firstFailure: IOException? = null
        val futures = mutableListOf<Future<Long>>()
        try {
            tasks.forEach { task ->
                futures += completion.submit(Callable {
                    val parent = task.destination.parentFile ?: throw IOException("Invalid destination")
                    ensureDirectory(parent)
                    val part = File(parent, "${task.destination.name}.part")
                    if (forceRedownload) part.delete()
                    val key = task.destination.path
                    progress.update(key, partialBytes(part))
                    try {
                        val bytes = downloadTo(task.url, part, task.sha1, task.expectedSize, status = onProgress) { current, _ ->
                            progress.update(key, current)
                        }
                        replacePart(part, task.destination)
                        progress.finish(key, task.destination.length())
                        bytes
                    } catch (error: Exception) {
                        // Keep the bytes that are really on disk so a Retry resumes from the right place.
                        progress.update(key, partialBytes(part))
                        throw error
                    }
                })
            }
            while (completed < tasks.size) {
                try {
                    totalBytes += completion.take().get()
                } catch (e: ExecutionException) {
                    val cause = e.cause ?: e
                    if (firstFailure == null) {
                        firstFailure = IOException("A dependency download failed: ${cause.message ?: cause.javaClass.simpleName}", cause)
                        onProgress("A dependency failed after automatic retries. Stopping other downloads; partial files are kept for Retry.")
                        futures.forEach { future -> if (!future.isDone) future.cancel(true) }
                        pool.shutdownNow()
                    }
                } catch (_: CancellationException) {
                    // Expected after a sibling download fails; the original failure is reported below.
                } finally {
                    completed++
                }
            }
        } finally {
            pool.shutdownNow()
            try {
                // Avoid a manual Retry racing with workers that were interrupted during a network read.
                pool.awaitTermination(35, TimeUnit.SECONDS)
            } catch (interrupted: InterruptedException) {
                Thread.currentThread().interrupt()
            }
        }
        firstFailure?.let { throw it }
        return totalBytes
    }

    private fun downloadTo(
        urlString: String,
        destination: File,
        expectedSha1: String?,
        expectedSize: Long,
        status: (String) -> Unit = {},
        progress: (Long, Long) -> Unit,
    ): Long = ResilientDownloader.download(
        urlString = urlString,
        destination = destination,
        expectedSize = expectedSize,
        expectedDigest = expectedSha1,
        digestAlgorithm = if (expectedSha1 != null) "SHA-1" else null,
        maximumBytes = MAX_DOWNLOAD_FILE_BYTES,
        onProgress = progress,
        onStatus = status,
    )

    private fun partialBytes(file: File): Long = if (file.isFile) file.length() else 0L

    private fun isValidFile(file: File, expectedSha1: String?, expectedSize: Long): Boolean {
        if (!file.isFile || file.length() <= 0L) return false
        if (expectedSha1 != null) return runCatching { sha1(file).equals(expectedSha1, ignoreCase = true) }.getOrDefault(false)
        return expectedSize <= 0L || file.length() == expectedSize
    }

    private fun sha1(file: File): String {
        val digest = MessageDigest.getInstance("SHA-1")
        file.inputStream().buffered().use { input ->
            val buffer = ByteArray(32 * 1024)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun validSha1(value: String?): String? = value?.takeIf { it.matches(Regex("[a-fA-F0-9]{40}")) }

    private fun safeRelativePath(value: String): String? {
        if (value.isBlank() || value.startsWith('/') || value.startsWith('\\')) return null
        val normalized = value.replace('\\', '/')
        if (normalized.split('/').any { it.isBlank() || it == "." || it == ".." }) return null
        return normalized
    }

    private fun safeId(value: String): String {
        val safe = value.replace(Regex("[^A-Za-z0-9._-]"), "_")
        require(safe.isNotBlank() && safe != "." && safe != "..") { "Invalid Minecraft identifier" }
        return safe
    }

    private fun requireHttps(value: String) {
        if (!value.startsWith("https://", ignoreCase = true)) throw IOException("Rejected non-HTTPS download URL")
    }

    private fun ensureDirectory(directory: File) {
        if (!directory.exists() && !directory.mkdirs()) throw IOException("Cannot create directory: ${directory.name}")
        if (!directory.isDirectory) throw IOException("Not a directory: ${directory.name}")
    }

    private fun replacePart(part: File, destination: File) {
        if (!part.renameTo(destination)) {
            part.copyTo(destination, overwrite = true)
            if (!part.delete()) part.deleteOnExit()
        }
    }

    private companion object {
        const val MAX_PARALLEL_DOWNLOADS = 6
        const val MAX_DOWNLOAD_FILE_BYTES = 1L * 1024L * 1024L * 1024L
    }
}
