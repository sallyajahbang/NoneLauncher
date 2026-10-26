package com.sally.launcher.data.runtime

import android.content.Context
import android.os.Build
import android.os.StatFs
import android.system.Os
import com.sally.launcher.data.model.MinecraftVersion
import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.xz.XZCompressorInputStream
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.Locale

/**
 * Downloads and safely installs an Android-specific OpenJDK runtime distribution.
 * This stage prepares runtime files only. Starting a JVM inside Android still needs the
 * launch/JNI bridge and native integration; bin/java is not launched as a desktop process.
 */
class RuntimeManager(context: Context) {
    private val appContext = context.applicationContext
    private val minecraftRoot = File(appContext.filesDir, "minecraft")
    private val runtimesRoot = File(minecraftRoot, "runtimes")

    data class RuntimeInfo(
        val majorVersion: Int,
        val architecture: String,
        val home: File,
    ) {
        fun displayName(): String = "Java $majorVersion · $architecture"
    }

    private data class RuntimeAsset(
        val majorVersion: Int,
        val architecture: String,
        val url: String,
        val sha256: String,
        val size: Long,
    )

    /**
     * Resolve the Java requirement from Mojang's own version JSON and install the closest
     * supported runtime at or above that required major version.
     */
    fun installForVersion(version: MinecraftVersion, onProgress: (String) -> Unit): RuntimeInfo {
        onProgress("Checking Java requirement for Minecraft ${version.id}…")
        val requiredMajor = resolveRequiredJavaMajor(version)
        val architecture = currentRuntimeArchitecture()
        val chosenMajor = supportedJavaMajors(architecture).firstOrNull { it >= requiredMajor }
            ?: throw IOException(
                "Minecraft ${version.id} requires Java $requiredMajor, but no supported runtime is published for Android $architecture."
            )
        if (chosenMajor != requiredMajor) {
            onProgress("Minecraft requires Java $requiredMajor; using compatible Java $chosenMajor…")
        }
        return installRuntime(chosenMajor, onProgress)
    }

    fun supportedJavaMajors(): List<Int> = supportedJavaMajors(currentRuntimeArchitecture())

    fun installedRuntimes(): List<RuntimeInfo> {
        if (!runtimesRoot.isDirectory) return emptyList()
        return runtimesRoot.listFiles()
            .orEmpty()
            .filter { it.isDirectory && it.name.matches(Regex("java-[0-9]+")) }
            .mapNotNull { directory ->
                val major = directory.name.removePrefix("java-").toIntOrNull() ?: return@mapNotNull null
                readAndValidateRuntime(directory, major)
            }
            .sortedBy { it.majorVersion }
    }

    private fun resolveRequiredJavaMajor(version: MinecraftVersion): Int {
        val versionId = safeId(version.id)
        val localMetadata = File(File(minecraftRoot, "versions/$versionId"), "$versionId.json")
        val metadataText = if (localMetadata.isFile && localMetadata.length() > 0L) {
            localMetadata.readText(Charsets.UTF_8)
        } else {
            val metadataUrl = runCatching { URL(version.url) }.getOrNull()
                ?: throw IOException("Minecraft version metadata URL is invalid")
            val metadataHost = metadataUrl.host.lowercase(Locale.ROOT)
            if (metadataUrl.protocol != "https" ||
                (metadataHost != "mojang.com" && !metadataHost.endsWith(".mojang.com"))
            ) {
                throw IOException("Rejected non-Mojang Minecraft version metadata URL")
            }
            readHttpsText(metadataUrl.toString(), "Minecraft version metadata")
        }
        val metadata = try {
            JSONObject(metadataText)
        } catch (e: Exception) {
            throw IOException("Minecraft version metadata is invalid", e)
        }
        val metadataId = metadata.optString("id")
        if (metadataId.isNotBlank() && metadataId != version.id) {
            throw IOException("Version metadata mismatch: expected ${version.id}, received $metadataId")
        }
        val declaredMajor = metadata.optJSONObject("javaVersion")?.optInt("majorVersion", 0) ?: 0
        // Older Mojang version metadata may omit javaVersion; Java 8 is the legacy baseline.
        return declaredMajor.takeIf { it in 1..99 } ?: 8
    }

    private fun installRuntime(major: Int, onProgress: (String) -> Unit): RuntimeInfo {
        val architecture = currentRuntimeArchitecture()
        if (major !in supportedJavaMajors(architecture)) {
            throw IOException("Java $major is not available for Android $architecture from the configured runtime source")
        }
        readAndValidateRuntime(File(runtimesRoot, "java-$major"), major)?.let { installed ->
            onProgress("Java $major is already installed and verified")
            return installed
        }

        ensureDirectory(minecraftRoot)
        ensureDirectory(runtimesRoot)
        ensureSufficientStorage(runtimesRoot)

        onProgress("Getting the official runtime checksum for Java $major ($architecture)…")
        val asset = resolveRuntimeAsset(major, architecture)
        val archive = File(runtimesRoot, ".jre$major-$architecture.tar.xz")
        val part = File(runtimesRoot, ".jre$major-$architecture.tar.xz.part")
        val staging = File(runtimesRoot, ".java-$major.installing")
        val destination = File(runtimesRoot, "java-$major")
        val backup = File(runtimesRoot, ".java-$major.previous")

        try {
            if (!isValidArchive(archive, asset)) {
                archive.delete()
                part.delete()
                onProgress("Downloading Java $major runtime…")
                downloadArchive(asset, part, onProgress)
                if (!part.renameTo(archive)) {
                    part.copyTo(archive, overwrite = true)
                    if (!part.delete()) part.deleteOnExit()
                }
            }

            staging.deleteRecursively()
            ensureDirectory(staging)
            onProgress("Unpacking Java $major runtime safely…")
            extractTarXz(archive, staging, onProgress)
            val stagedInfo = readAndValidateRuntime(staging, major)
                ?: throw IOException("Downloaded Java $major archive is incomplete or has the wrong architecture")
            if (stagedInfo.architecture != architecture) {
                throw IOException("Runtime architecture mismatch: expected $architecture, found ${stagedInfo.architecture}")
            }

            backup.deleteRecursively()
            if (destination.exists() && !destination.renameTo(backup)) {
                throw IOException("Could not move the previous Java $major runtime out of the way")
            }
            if (!staging.renameTo(destination)) {
                if (backup.exists()) backup.renameTo(destination)
                throw IOException("Could not activate the installed Java $major runtime")
            }
            backup.deleteRecursively()
            onProgress("Java $major runtime installed and verified")
            return readAndValidateRuntime(destination, major)
                ?: throw IOException("Java $major runtime failed its final integrity check")
        } finally {
            part.delete()
            archive.delete()
            staging.deleteRecursively()
            // Keep a backup only if moving the previous runtime back failed.
            if (destination.exists()) backup.deleteRecursively()
        }
    }

    private fun resolveRuntimeAsset(major: Int, architecture: String): RuntimeAsset {
        val assetName = "jre$major-android-$architecture.tar.xz"
        val apiUrl = "https://api.github.com/repos/AngelAuraMC/angelauramc-openjdk-build/releases/tags/download"
        val release = try {
            JSONObject(readHttpsText(apiUrl, "runtime release metadata", githubApi = true))
        } catch (e: Exception) {
            throw IOException("Could not fetch the runtime manifest from the upstream release service", e)
        }
        val assets = release.optJSONArray("assets") ?: throw IOException("Runtime release has no asset list")
        var matching: JSONObject? = null
        for (index in 0 until assets.length()) {
            val candidate = assets.optJSONObject(index) ?: continue
            if (candidate.optString("name") == assetName) {
                matching = candidate
                break
            }
        }
        val found = matching ?: throw IOException("The upstream runtime release does not contain $assetName")
        val url = found.optString("browser_download_url")
        val parsedUrl = runCatching { URL(url) }.getOrNull()
        if (parsedUrl?.protocol != "https" || parsedUrl.host.lowercase(Locale.ROOT) != "github.com") {
            throw IOException("Rejected an untrusted runtime download URL")
        }
        val digestText = found.optString("digest")
        var sha256 = digestText.removePrefix("sha256:").lowercase(Locale.ROOT)
        if (!sha256.matches(Regex("[a-f0-9]{64}"))) {
            sha256 = pinnedSha256(major, architecture).orEmpty()
        }
        if (!sha256.matches(Regex("[a-f0-9]{64}"))) {
            throw IOException("Upstream did not publish a usable SHA-256 digest for $assetName; refusing an unverifiable runtime")
        }
        val size = found.optLong("size", -1L)
        if (size <= 0L || size > MAX_ARCHIVE_BYTES) throw IOException("Runtime archive size is invalid")
        return RuntimeAsset(major, architecture, url, sha256, size)
    }

    private fun downloadArchive(asset: RuntimeAsset, destination: File, onProgress: (String) -> Unit) {
        var connection: HttpURLConnection? = null
        try {
            connection = (URL(asset.url).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 20_000
                readTimeout = 60_000
                instanceFollowRedirects = true
                setRequestProperty("User-Agent", "SallyLauncher/0.4.0 (Android)")
                setRequestProperty("Accept", "application/octet-stream")
            }
            val status = connection.responseCode
            if (status !in 200..299) throw IOException("Runtime download returned HTTP $status")
            val total = connection.contentLengthLong
            if (total > MAX_ARCHIVE_BYTES) throw IOException("Runtime archive is unexpectedly large")
            var downloaded = 0L
            val digest = MessageDigest.getInstance("SHA-256")
            BufferedInputStream(connection.inputStream).use { input ->
                FileOutputStream(destination).use { output ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        downloaded += count
                        if (downloaded > MAX_ARCHIVE_BYTES) throw IOException("Runtime download exceeded the size limit")
                        output.write(buffer, 0, count)
                        digest.update(buffer, 0, count)
                        if (downloaded % (1024 * 1024) < count) {
                            onProgress("Downloading Java ${asset.majorVersion}: ${downloaded / (1024 * 1024)} MB")
                        }
                    }
                    output.fd.sync()
                }
            }
            if (downloaded != asset.size) throw IOException("Runtime archive size mismatch")
            val actual = digest.digest().joinToString("") { "%02x".format(it) }
            if (!actual.equals(asset.sha256, ignoreCase = true)) {
                throw IOException("SHA-256 verification failed for Java ${asset.majorVersion}; archive rejected")
            }
        } catch (e: Exception) {
            destination.delete()
            if (e is IOException) throw e
            throw IOException("Runtime download failed: ${e.message ?: e.javaClass.simpleName}", e)
        } finally {
            connection?.disconnect()
        }
    }

    private fun extractTarXz(archive: File, staging: File, onProgress: (String) -> Unit) {
        val rootPath = staging.canonicalPath
        var entryCount = 0
        var totalUnpacked = 0L
        XZCompressorInputStream(BufferedInputStream(FileInputStream(archive))).use { xz ->
            TarArchiveInputStream(xz).use { tar ->
                var entry: TarArchiveEntry? = tar.nextTarEntry
                val buffer = ByteArray(64 * 1024)
                while (entry != null) {
                    entryCount++
                    if (entryCount > MAX_TAR_ENTRIES) throw IOException("Runtime archive contains too many entries")
                    val currentEntry = entry
                    val relative = sanitizeArchivePath(currentEntry.name)
                    if (relative.isEmpty()) {
                        if (currentEntry.isDirectory) {
                            entry = tar.nextTarEntry
                            continue
                        }
                        throw IOException("Runtime archive contains an empty path")
                    }
                    val output = File(staging, relative)
                    ensureInsideRoot(rootPath, output.canonicalPath)
                    if (currentEntry.size < 0L || currentEntry.size > MAX_UNPACKED_BYTES) {
                        throw IOException("Runtime archive contains an invalid entry size")
                    }

                    if (currentEntry.isDirectory) {
                        ensureSafeParent(rootPath, output.parentFile)
                        if (!output.exists() && !output.mkdirs()) throw IOException("Cannot create runtime directory")
                    } else if (currentEntry.isSymbolicLink) {
                        ensureSafeParent(rootPath, output.parentFile)
                        validateLinkTarget(rootPath, output.parentFile ?: staging, currentEntry.linkName)
                        if (output.exists() || output.absoluteFile.exists()) output.delete()
                        Os.symlink(currentEntry.linkName, output.absolutePath)
                    } else if (currentEntry.isLink) {
                        ensureSafeParent(rootPath, output.parentFile)
                        val linkRelative = sanitizeArchivePath(currentEntry.linkName)
                        val linkedFile = File(staging, linkRelative)
                        ensureInsideRoot(rootPath, linkedFile.canonicalPath)
                        if (!linkedFile.isFile) throw IOException("Runtime archive contains a dangling hard link")
                        if (!linkedFile.copyTo(output, overwrite = true).exists()) throw IOException("Cannot unpack runtime hard link")
                    } else if (currentEntry.isFile) {
                        ensureSafeParent(rootPath, output.parentFile)
                        if (output.exists() && !output.delete()) throw IOException("Cannot replace runtime file")
                        var remaining = currentEntry.size
                        totalUnpacked += remaining
                        if (totalUnpacked > MAX_UNPACKED_BYTES) throw IOException("Runtime archive expands beyond the safety limit")
                        FileOutputStream(output).use { out ->
                            while (remaining > 0L) {
                                val readLength = minOf(buffer.size.toLong(), remaining).toInt()
                                val count = tar.read(buffer, 0, readLength)
                                if (count < 0) throw IOException("Runtime archive ended inside ${currentEntry.name}")
                                out.write(buffer, 0, count)
                                remaining -= count
                            }
                            out.fd.sync()
                        }
                        applyTarMode(output, currentEntry.mode)
                    } else {
                        throw IOException("Unsupported runtime archive entry type: ${currentEntry.name}")
                    }
                    if (entryCount % 250 == 0) onProgress("Unpacking Java runtime: $entryCount entries…")
                    entry = tar.nextTarEntry
                }
            }
        }
    }

    private fun readAndValidateRuntime(home: File, expectedMajor: Int): RuntimeInfo? {
        if (!home.isDirectory) return null
        val release = File(home, "release")
        val javaBinary = File(home, "bin/java")
        if (!release.isFile || !javaBinary.isFile) return null
        val content = runCatching { release.readText(Charsets.UTF_8) }.getOrNull() ?: return null
        val versionValue = Regex("(?m)^JAVA_VERSION=\"([^\"]+)\"").find(content)?.groupValues?.getOrNull(1)
            ?: return null
        val parsedMajor = parseMajor(versionValue) ?: return null
        if (parsedMajor != expectedMajor) return null
        val osArch = Regex("(?m)^OS_ARCH=\"([^\"]+)\"").find(content)?.groupValues?.getOrNull(1)
            ?: return null
        val normalized = normalizeRuntimeArch(osArch) ?: return null
        if (normalized != currentRuntimeArchitecture()) return null
        if (!containsLibJvm(File(home, "lib"), 0)) return null
        // Preserve the executable bit if archive tooling did not do so.
        if (!javaBinary.canExecute()) javaBinary.setExecutable(true, true)
        return RuntimeInfo(parsedMajor, normalized, home)
    }

    private fun containsLibJvm(directory: File, depth: Int): Boolean {
        if (!directory.isDirectory || depth > 6) return false
        val files = directory.listFiles().orEmpty()
        if (files.any { it.isFile && it.name == "libjvm.so" }) return true
        return files.any { it.isDirectory && containsLibJvm(it, depth + 1) }
    }

    private fun parseMajor(value: String): Int? {
        val clean = value.trim()
        if (clean.startsWith("1.")) return clean.removePrefix("1.").substringBefore('.').toIntOrNull()
        return clean.takeWhile { it.isDigit() }.toIntOrNull()
    }

    private fun normalizeRuntimeArch(value: String): String? = when (value.lowercase(Locale.ROOT)) {
        "aarch64", "arm64", "arm64-v8a" -> "arm64"
        "arm", "arm32", "armv7l", "armeabi-v7a" -> "arm"
        "x86", "i386", "i486", "i586", "i686" -> "x86"
        "x86_64", "amd64" -> "x86_64"
        else -> null
    }

    private fun currentRuntimeArchitecture(): String {
        val abis = Build.SUPPORTED_ABIS.map { it.lowercase(Locale.ROOT) }
        return when {
            abis.any { it == "arm64-v8a" } -> "arm64"
            abis.any { it == "armeabi-v7a" || it == "armeabi" } -> "arm"
            abis.any { it == "x86_64" } -> "x86_64"
            abis.any { it == "x86" } -> "x86"
            else -> throw IOException("Unsupported Android CPU ABI: ${abis.joinToString()}")
        }
    }

    private fun supportedJavaMajors(architecture: String): List<Int> = when (architecture) {
        "arm", "arm64" -> listOf(8, 17, 21, 25)
        // The current upstream runtime release does not publish Java 25 for x86/x86_64.
        "x86", "x86_64" -> listOf(8, 17, 21)
        else -> emptyList()
    }

    private fun sanitizeArchivePath(raw: String): String {
        val normalized = raw.replace('\\', '/').trimEnd('/')
        if (normalized.isBlank() || normalized.startsWith('/')) throw IOException("Unsafe path in runtime archive")
        val segments = normalized.split('/').filter { it.isNotEmpty() && it != "." }
        if (segments.any { it == ".." || it.contains('\u0000') }) {
            throw IOException("Unsafe path in runtime archive")
        }
        return segments.joinToString("/")
    }

    private fun validateLinkTarget(rootPath: String, parent: File, rawTarget: String) {
        if (rawTarget.isBlank() || File(rawTarget).isAbsolute) throw IOException("Unsafe symbolic link in runtime archive")
        val target = File(parent, rawTarget).canonicalPath
        ensureInsideRoot(rootPath, target)
    }

    private fun ensureSafeParent(rootPath: String, parent: File?) {
        val actual = parent ?: throw IOException("Runtime archive contains a rootless path")
        if (!actual.exists() && !actual.mkdirs()) throw IOException("Cannot create runtime directory")
        ensureInsideRoot(rootPath, actual.canonicalPath)
    }

    private fun ensureInsideRoot(rootPath: String, candidatePath: String) {
        if (candidatePath != rootPath && !candidatePath.startsWith(rootPath + File.separator)) {
            throw IOException("Runtime archive path escapes the install directory")
        }
    }

    private fun applyTarMode(file: File, tarMode: Int) {
        val mode = tarMode and 0x1FF
        if (mode != 0) runCatching { Os.chmod(file.absolutePath, mode) }
    }

    private fun isValidArchive(file: File, asset: RuntimeAsset): Boolean {
        if (!file.isFile || file.length() != asset.size) return false
        return runCatching { sha256(file).equals(asset.sha256, ignoreCase = true) }.getOrDefault(false)
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().buffered().use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun readHttpsText(urlString: String, label: String, githubApi: Boolean = false): String {
        val parsed = runCatching { URL(urlString) }.getOrNull()
        if (parsed?.protocol != "https") throw IOException("Rejected non-HTTPS $label URL")
        if (githubApi && parsed.host != "api.github.com") throw IOException("Rejected non-GitHub runtime metadata URL")
        var connection: HttpURLConnection? = null
        try {
            connection = (parsed.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 15_000
                readTimeout = 25_000
                instanceFollowRedirects = false
                setRequestProperty("User-Agent", "SallyLauncher/0.4.0 (Android)")
                setRequestProperty("Accept", if (githubApi) "application/vnd.github+json" else "application/json")
                if (githubApi) setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
            }
            val code = connection.responseCode
            if (code !in 200..299) throw IOException("$label returned HTTP $code")
            val length = connection.contentLengthLong
            if (length > MAX_METADATA_BYTES) throw IOException("$label is unexpectedly large")
            val content = connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            if (content.toByteArray(Charsets.UTF_8).size > MAX_METADATA_BYTES) throw IOException("$label is unexpectedly large")
            return content
        } finally {
            connection?.disconnect()
        }
    }

    private fun ensureSufficientStorage(directory: File) {
        val available = StatFs(directory.absolutePath).availableBytes
        if (available < MIN_RUNTIME_FREE_BYTES) {
            throw IOException("Not enough free storage to install Java runtime; at least 300 MB is recommended")
        }
    }

    private fun ensureDirectory(directory: File) {
        if (!directory.exists() && !directory.mkdirs()) throw IOException("Cannot create ${directory.name}")
        if (!directory.isDirectory) throw IOException("${directory.name} is not a directory")
    }

    private fun safeId(value: String): String {
        val safe = value.replace(Regex("[^A-Za-z0-9._-]"), "_")
        require(safe.isNotBlank() && safe != "." && safe != "..") { "Invalid Minecraft version id" }
        return safe
    }

    private fun pinnedSha256(major: Int, architecture: String): String? = when (major to architecture) {
        17 to "arm" -> "4a9134f1ebf6340dd855805d351712462cddab6f3c2684da7e7da10ccf06648d"
        17 to "arm64" -> "e162c860fe05ee4a4e4af7606437419879f6c748386a7b09fa77d10db6a64091"
        17 to "x86" -> "223a2d54606a9eb853c8451cf1d6bda1a8f09cb357f40b790140e57d45731ed7"
        17 to "x86_64" -> "893e27d2aed8b40407f29fe939e2a0f193e5d55f72892a303db634b0808a2b61"
        21 to "arm" -> "96c297487def64666e379a9a363d9955c05b1a0b091b0cf24af88359a66f394a"
        21 to "arm64" -> "8d41ec401ee59f7722df60ed991f81ad146e130452804bfdd8a05d3436f7bbfe"
        21 to "x86" -> "9b8c7d10c5f751acb3b33506593da44ece52a0fd03e0b3c283ba08a7f285a40f"
        21 to "x86_64" -> "cb88723961f5f9ad63afa1f212eb199816c27cabfd7dc66567bde1d8fb69713b"
        25 to "arm" -> "9ae13aee9cba7b2d2d8f40965061667e876d7380d866f37a992db3eff296ffb5"
        25 to "arm64" -> "d3eb7afe2240c26728a1bb440502c5f18ac3883e932d202dd7f0c9bcbbce4c37"
        else -> null
    }

    private companion object {
        const val MAX_METADATA_BYTES = 4L * 1024L * 1024L
        const val MAX_ARCHIVE_BYTES = 150L * 1024L * 1024L
        const val MAX_UNPACKED_BYTES = 900L * 1024L * 1024L
        const val MAX_TAR_ENTRIES = 50_000
        const val MIN_RUNTIME_FREE_BYTES = 300L * 1024L * 1024L
    }
}
