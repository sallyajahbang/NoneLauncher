package com.sally.launcher.data.download

import java.io.BufferedInputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InterruptedIOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/**
 * Streaming HTTPS downloader with bounded retries and HTTP Range resume support.
 * Partial data is kept after transient network failures and resumed when the server supports it.
 * A destination is only considered complete after its expected size/digest checks pass.
 */
internal object ResilientDownloader {
    private const val MAX_ATTEMPTS = 4
    private val RETRY_DELAYS_MS = longArrayOf(700L, 1_400L, 2_800L)
    private const val CONNECT_TIMEOUT_MS = 15_000
    private const val READ_TIMEOUT_MS = 30_000
    private const val BUFFER_SIZE = 128 * 1024

    private class HttpStatusException(val statusCode: Int, message: String) : IOException(message)

    fun download(
        urlString: String,
        destination: File,
        expectedSize: Long = -1L,
        expectedDigest: String? = null,
        digestAlgorithm: String? = null,
        maximumBytes: Long = Long.MAX_VALUE,
        onProgress: (downloaded: Long, total: Long) -> Unit = { _, _ -> },
        onStatus: (String) -> Unit = {},
    ): Long {
        val parsed = runCatching { URL(urlString) }.getOrNull()
            ?: throw IOException("Invalid download URL")
        if (parsed.protocol != "https") throw IOException("Rejected non-HTTPS download URL")
        if (maximumBytes <= 0L) throw IOException("Invalid download size limit")
        if (expectedSize > maximumBytes) throw IOException("Expected download exceeds the configured size limit")
        if (expectedDigest != null && digestAlgorithm.isNullOrBlank()) {
            throw IOException("A digest algorithm is required when checksum verification is enabled")
        }

        if (isComplete(destination, expectedSize, expectedDigest, digestAlgorithm)) return 0L

        var transferredThisRun = 0L
        var lastError: Exception? = null
        for (attempt in 1..MAX_ATTEMPTS) {
            try {
                downloadAttempt(
                    urlString = urlString,
                    destination = destination,
                    expectedSize = expectedSize,
                    expectedDigest = expectedDigest,
                    digestAlgorithm = digestAlgorithm,
                    maximumBytes = maximumBytes,
                    onBytesTransferred = { transferredThisRun += it },
                    onProgress = onProgress,
                )
                return transferredThisRun
            } catch (error: Exception) {
                lastError = error
                if (attempt == MAX_ATTEMPTS || !isRetryable(error)) {
                    throw IOException(
                        "Download failed after $attempt/${MAX_ATTEMPTS} attempt(s): ${error.message ?: error.javaClass.simpleName}",
                        error,
                    )
                }
                val delay = RETRY_DELAYS_MS[(attempt - 1).coerceAtMost(RETRY_DELAYS_MS.lastIndex)]
                onStatus("Download interrupted (${attempt}/${MAX_ATTEMPTS}). Resuming automatically in ${delay / 1000.0}s: ${error.message ?: "network error"}")
                try {
                    Thread.sleep(delay)
                } catch (interrupted: InterruptedException) {
                    Thread.currentThread().interrupt()
                    throw IOException("Download cancelled while waiting to retry", interrupted)
                }
            }
        }
        throw IOException("Download failed", lastError)
    }

    private fun downloadAttempt(
        urlString: String,
        destination: File,
        expectedSize: Long,
        expectedDigest: String?,
        digestAlgorithm: String?,
        maximumBytes: Long,
        onBytesTransferred: (Long) -> Unit,
        onProgress: (Long, Long) -> Unit,
    ): Long {
        var connection: HttpURLConnection? = null
        try {
            var existing = destination.takeIf { it.isFile }?.length() ?: 0L
            // Small metadata responses without a published size/checksum cannot be safely resumed
            // after a complete transfer was interrupted before the caller promoted the .part file.
            if (expectedSize <= 0L && expectedDigest == null) {
                destination.delete()
                existing = 0L
            }
            if (existing < 0L || (expectedSize > 0L && existing > expectedSize) || existing > maximumBytes ||
                (expectedSize > 0L && existing == expectedSize && !isComplete(destination, expectedSize, expectedDigest, digestAlgorithm))) {
                destination.delete()
                existing = 0L
            }
            connection = (URL(urlString).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = READ_TIMEOUT_MS
                instanceFollowRedirects = true
                useCaches = false
                setRequestProperty("User-Agent", "SallyLauncher/0.5.0 (Android)")
                setRequestProperty("Accept-Encoding", "identity")
                if (existing > 0L) setRequestProperty("Range", "bytes=$existing-")
            }
            val status = connection.responseCode
            if (status == 416 && existing > 0L && isComplete(destination, expectedSize, expectedDigest, digestAlgorithm)) {
                return 0L
            }
            if (status !in 200..299) throw HttpStatusException(status, "HTTP $status")
            if (status == HttpURLConnection.HTTP_PARTIAL && existing == 0L) {
                throw IOException("Server returned partial content without a resumable partial file")
            }

            val append = existing > 0L && status == HttpURLConnection.HTTP_PARTIAL
            if (!append) existing = 0L
            val responseLength = connection.contentLengthLong
            val total = when {
                expectedSize > 0L -> expectedSize
                responseLength > 0L -> existing + responseLength
                else -> -1L
            }
            var current = existing
            var attemptTransferred = 0L
            BufferedInputStream(connection.inputStream, BUFFER_SIZE).use { input ->
                FileOutputStream(destination, append).use { output ->
                    val buffer = ByteArray(BUFFER_SIZE)
                    while (true) {
                        if (Thread.currentThread().isInterrupted) throw InterruptedIOException("Download was interrupted")
                        val count = input.read(buffer)
                        if (count < 0) break
                        current += count
                        attemptTransferred += count
                        if (current > maximumBytes || (expectedSize > 0L && current > expectedSize)) {
                            destination.delete()
                            throw IOException("Download exceeded the expected size for ${destination.name}")
                        }
                        output.write(buffer, 0, count)
                        onBytesTransferred(count.toLong())
                        onProgress(current, total)
                    }
                    output.fd.sync()
                }
            }

            if (expectedSize > 0L && current != expectedSize) {
                if (current > expectedSize) destination.delete()
                throw IOException("Incomplete download for ${destination.name}: $current/$expectedSize bytes")
            }
            if (expectedDigest != null && !digestFile(destination, digestAlgorithm!!).equals(expectedDigest, ignoreCase = true)) {
                destination.delete()
                throw IOException("Checksum verification failed for ${destination.name}; file rejected")
            }
            return attemptTransferred
        } catch (error: Exception) {
            if (error !is IOException) throw IOException("Download failed: ${error.message ?: error.javaClass.simpleName}", error)
            throw error
        } finally {
            connection?.disconnect()
        }
    }

    private fun isComplete(file: File, expectedSize: Long, expectedDigest: String?, digestAlgorithm: String?): Boolean {
        if (!file.isFile || file.length() <= 0L) return false
        if (expectedSize > 0L && file.length() != expectedSize) return false
        if (expectedDigest == null) return expectedSize > 0L && file.length() == expectedSize
        return runCatching { digestFile(file, digestAlgorithm!!).equals(expectedDigest, ignoreCase = true) }.getOrDefault(false)
    }

    private fun digestFile(file: File, algorithm: String): String {
        val digest = MessageDigest.getInstance(algorithm)
        file.inputStream().buffered(BUFFER_SIZE).use { input ->
            val buffer = ByteArray(BUFFER_SIZE)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun isRetryable(error: Exception): Boolean {
        if (error is HttpStatusException) {
            return error.statusCode == 408 || error.statusCode == 425 || error.statusCode == 429 || error.statusCode in 500..599
        }
        val message = error.message.orEmpty().lowercase()
        if (listOf("rejected non-https", "exceeds the configured size", "exceeded the expected size", "not enough space", "no space left", "permission denied", "cannot create", "invalid download url").any(message::contains)) {
            return false
        }
        return error is IOException
    }
}
