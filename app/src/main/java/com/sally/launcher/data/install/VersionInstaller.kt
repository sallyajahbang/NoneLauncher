package com.sally.launcher.data.install

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import com.sally.launcher.data.model.MinecraftVersion
import com.sally.launcher.data.storage.LauncherStorage
import java.io.BufferedInputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.Locale
import java.util.concurrent.Callable
import java.util.concurrent.ExecutionException
import java.util.concurrent.ExecutorCompletionService
import java.util.concurrent.Executors

/**
 * Downloads official Minecraft version data, the client JAR, common Java libraries,
 * the asset index and content-addressed asset objects into app-private storage.
 * Native classifiers are intentionally not selected: desktop natives are not Android-ready.
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

    fun installVersion(version: MinecraftVersion, onProgress: (String) -> Unit): Result {
        requireHttps(version.url)
        val id = safeId(version.id)
        val versionDir = File(versionsDir, id)
        ensureDirectory(versionDir)

        val metadataFile = File(versionDir, "$id.json")
        val metadataPart = File(versionDir, "$id.json.part")
        onProgress("Downloading version metadata…")
        downloadTo(version.url, metadataPart, null, -1L) { _, _ -> }
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
        var downloadedBytes = 0L
        if (!isValidFile(clientJar, clientSha1, clientSize)) {
            onProgress("Downloading Minecraft $id client…")
            val part = File(versionDir, "$id.jar.part")
            downloadedBytes += downloadTo(clientUrl, part, clientSha1, clientSize) { current, total ->
                onProgress(formatProgress("Minecraft $id", current, total))
            }
            replacePart(part, clientJar)
        }

        val libraryTasks = buildLibraryTasks(metadata.optJSONArray("libraries"))
        val assetTasks = buildAssetTasks(metadata, onProgress)
        val allTasks = deduplicateTasks(libraryTasks + assetTasks)
        val pending = allTasks.filterNot { isValidFile(it.destination, it.sha1, it.expectedSize) }

        if (pending.isEmpty()) {
            onProgress("All libraries and assets are already present and verified")
        } else {
            onProgress("Downloading ${pending.size} dependency files (up to 4 at once)…")
            downloadedBytes += downloadTasks(pending, onProgress)
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

    private fun buildAssetTasks(metadata: JSONObject, onProgress: (String) -> Unit): List<DownloadTask> {
        val assetIndex = metadata.optJSONObject("assetIndex") ?: return emptyList()
        val assetIndexId = safeId(assetIndex.optString("id").ifBlank { metadata.optString("assets", "legacy") })
        val indexUrl = assetIndex.optString("url")
        requireHttps(indexUrl)
        val indexSha1 = validSha1(assetIndex.optString("sha1"))
        val indexSize = assetIndex.optLong("size", -1L)
        ensureDirectory(File(assetsDir, "indexes"))
        val indexFile = File(File(assetsDir, "indexes"), "$assetIndexId.json")
        if (!isValidFile(indexFile, indexSha1, indexSize)) {
            onProgress("Downloading asset index $assetIndexId…")
            val part = File(indexFile.parentFile, "${indexFile.name}.part")
            downloadTo(indexUrl, part, indexSha1, indexSize) { _, _ -> }
            replacePart(part, indexFile)
        }

        val index = try {
            JSONObject(indexFile.readText(Charsets.UTF_8))
        } catch (e: Exception) {
            indexFile.delete()
            throw IOException("Asset index is invalid", e)
        }
        val objects = index.optJSONObject("objects") ?: return emptyList()
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

    private fun downloadTasks(tasks: List<DownloadTask>, onProgress: (String) -> Unit): Long {
        ensureDirectory(librariesDir)
        ensureDirectory(File(assetsDir, "objects"))
        val pool = Executors.newFixedThreadPool(4)
        val completion = ExecutorCompletionService<Long>(pool)
        var completed = 0
        var totalBytes = 0L
        try {
            tasks.forEach { task ->
                completion.submit(Callable {
                    ensureDirectory(task.destination.parentFile ?: throw IOException("Invalid destination"))
                    val part = File(task.destination.parentFile, "${task.destination.name}.part")
                    val bytes = downloadTo(task.url, part, task.sha1, task.expectedSize) { _, _ -> }
                    replacePart(part, task.destination)
                    bytes
                })
            }
            while (completed < tasks.size) {
                try {
                    totalBytes += completion.take().get()
                    completed++
                    if (completed % 10 == 0 || completed == tasks.size) {
                        onProgress("Downloading libraries/assets: $completed / ${tasks.size} files")
                    }
                } catch (e: ExecutionException) {
                    val cause = e.cause ?: e
                    throw IOException("A dependency download failed: ${cause.message ?: cause.javaClass.simpleName}", cause)
                }
            }
        } finally {
            pool.shutdownNow()
        }
        return totalBytes
    }

    private fun downloadTo(
        urlString: String,
        destination: File,
        expectedSha1: String?,
        expectedSize: Long,
        progress: (Long, Long) -> Unit,
    ): Long {
        requireHttps(urlString)
        var connection: HttpURLConnection? = null
        try {
            connection = (URL(urlString).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 15_000
                readTimeout = 45_000
                instanceFollowRedirects = true
                setRequestProperty("User-Agent", "SallyLauncher/0.3.0 (Android)")
            }
            val code = connection.responseCode
            if (code !in 200..299) throw IOException("Download failed with HTTP $code")
            val total = connection.contentLengthLong
            var current = 0L
            BufferedInputStream(connection.inputStream).use { input ->
                FileOutputStream(destination).use { output ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE * 4)
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        output.write(buffer, 0, count)
                        current += count
                        progress(current, total)
                    }
                    output.fd.sync()
                }
            }
            if (expectedSize > 0 && current != expectedSize) {
                destination.delete()
                throw IOException("File size mismatch for ${destination.name}")
            }
            if (expectedSha1 != null && !sha1(destination).equals(expectedSha1, ignoreCase = true)) {
                destination.delete()
                throw IOException("SHA-1 verification failed for ${destination.name}; file rejected")
            }
            return current
        } catch (e: Exception) {
            destination.delete()
            if (e is IOException) throw e
            throw IOException("Download failed: ${e.message ?: e.javaClass.simpleName}", e)
        } finally {
            connection?.disconnect()
        }
    }

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

    private fun formatProgress(label: String, current: Long, total: Long): String =
        if (total > 0L) "$label: ${current / 1024} / ${total / 1024} KB" else "$label: ${current / 1024} KB"
}
