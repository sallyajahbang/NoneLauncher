package com.sally.launcher.data.install

import android.content.Context
import com.sally.launcher.data.model.MinecraftVersion
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/** Downloads the official version metadata and client JAR into app-private storage.
 * This is intentionally only the core-file stage; it does not claim the version is launch-ready.
 */
class VersionInstaller(context: Context) {
    private val root = File(context.applicationContext.filesDir, "minecraft")

    data class Result(val versionId: String, val metadataFile: File, val clientJar: File, val downloadedBytes: Long)

    fun downloadCoreFiles(version: MinecraftVersion, onProgress: (String) -> Unit): Result {
        require(version.url.startsWith("https://")) { "Untrusted version metadata URL" }
        val versionDir = File(File(root, "versions"), safeId(version.id))
        if (!versionDir.exists() && !versionDir.mkdirs()) throw IOException("Cannot create version directory")

        val metadataFile = File(versionDir, "${safeId(version.id)}.json")
        val metadataPart = File(versionDir, "${metadataFile.name}.part")
        onProgress("Downloading ${version.id} metadata…")
        downloadTo(version.url, metadataPart, null) { _, _ -> }
        if (!metadataPart.renameTo(metadataFile)) {
            metadataPart.copyTo(metadataFile, overwrite = true)
            metadataPart.delete()
        }

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
        if (!clientUrl.startsWith("https://")) throw IOException("Invalid client download URL")
        val expectedSha1 = client.optString("sha1").takeIf { it.matches(Regex("[a-fA-F0-9]{40}")) }
        val clientJar = File(versionDir, "${safeId(version.id)}.jar")
        var downloaded = 0L

        if (clientJar.isFile && expectedSha1 != null && sha1(clientJar).equals(expectedSha1, ignoreCase = true)) {
            onProgress("Core files already downloaded and verified")
            return Result(version.id, metadataFile, clientJar, 0)
        }

        onProgress("Downloading Minecraft ${version.id} client…")
        val part = File(versionDir, "${clientJar.name}.part")
        downloaded = downloadTo(clientUrl, part, expectedSha1) { current, total ->
            onProgress(if (total > 0L) "Downloading ${version.id}: ${current / 1024} / ${total / 1024} KB" else "Downloading ${version.id}: ${current / 1024} KB")
        }
        if (!part.renameTo(clientJar)) {
            part.copyTo(clientJar, overwrite = true)
            part.delete()
        }
        onProgress("Core files downloaded and verified")
        return Result(version.id, metadataFile, clientJar, downloaded)
    }

    private fun downloadTo(urlString: String, destination: File, expectedSha1: String?, progress: (Long, Long) -> Unit): Long {
        var connection: HttpURLConnection? = null
        try {
            connection = (URL(urlString).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 15_000
                readTimeout = 30_000
                instanceFollowRedirects = true
                setRequestProperty("User-Agent", "SallyLauncher/0.2.0 (Android)")
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
            if (expectedSha1 != null && !sha1(destination).equals(expectedSha1, ignoreCase = true)) {
                destination.delete()
                throw IOException("SHA-1 verification failed; the downloaded file was rejected")
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

    private fun safeId(value: String): String {
        val safe = value.replace(Regex("[^A-Za-z0-9._-]"), "_")
        require(safe.isNotBlank() && safe != "." && safe != "..") { "Invalid Minecraft version ID" }
        return safe
    }
}
