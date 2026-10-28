package com.sally.nonelauncher.core.download

import com.sally.nonelauncher.core.util.Hashing
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/**
 * Downloads files with a concurrency limit, retries, and SHA-1 verification.
 *
 * A file is only ever moved into place after its hash checks out, so a
 * half-downloaded file never looks like a valid cached one.
 */
class Downloader(
    maxConcurrent: Int = 8,
    private val retries: Int = 3,
    private val connectTimeoutMs: Int = 15_000,
    private val readTimeoutMs: Int = 30_000,
) {
    private val permits = Semaphore(maxConcurrent)

    /** Returns the body of [url] as text. */
    suspend fun fetchText(url: String): String = withContext(Dispatchers.IO) {
        withRetries(url) { connection ->
            connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        }
    }

    /**
     * Ensures [dest] exists with the expected [sha1] (if given).
     *
     * @return true if the file was downloaded, false if a valid copy was already present.
     */
    suspend fun ensureFile(url: String, dest: File, sha1: String? = null): Boolean =
        permits.withPermit {
            withContext(Dispatchers.IO) {
                if (Hashing.matchesSha1(dest, sha1) && dest.exists()) {
                    return@withContext false
                }
                dest.parentFile?.mkdirs()
                val tmp = File(dest.parentFile, dest.name + ".part")
                withRetries(url) { connection ->
                    connection.inputStream.use { input ->
                        tmp.outputStream().use { output -> input.copyTo(output) }
                    }
                }
                if (!Hashing.matchesSha1(tmp, sha1)) {
                    tmp.delete()
                    throw IOException("SHA-1 mismatch for $url")
                }
                if (dest.exists()) dest.delete()
                check(tmp.renameTo(dest)) { "could not move ${tmp.path} to ${dest.path}" }
                true
            }
        }

    private fun <T> withRetries(url: String, block: (HttpURLConnection) -> T): T {
        var lastError: Exception? = null
        repeat(retries) { attempt ->
            try {
                val connection = URL(url).openConnection() as HttpURLConnection
                connection.connectTimeout = connectTimeoutMs
                connection.readTimeout = readTimeoutMs
                connection.instanceFollowRedirects = true
                connection.setRequestProperty("User-Agent", "NoneLauncher/0.1")
                try {
                    val code = connection.responseCode
                    if (code !in 200..299) throw IOException("HTTP $code for $url")
                    return block(connection)
                } finally {
                    connection.disconnect()
                }
            } catch (e: Exception) {
                lastError = e
                if (attempt < retries - 1) Thread.sleep(500L * (attempt + 1))
            }
        }
        throw IOException("failed after $retries attempts: $url", lastError)
    }
}
