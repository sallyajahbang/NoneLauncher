package com.sally.javoxel.data

import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/**
 * Helper unduhan HTTP dengan verifikasi SHA-1 dan retry.
 * Koneksi lambat atau timeout dicoba ulang dengan jeda bertambah.
 * Error 4xx tidak diulang karena kemungkinan besar bukan masalah jaringan.
 * Dipanggil dari thread IO.
 */
object Downloader {

    private const val MAX_ATTEMPTS = 4

    fun fetchText(url: String): String = withRetry(url) {
        val conn = open(url)
        try {
            checkStatus(conn, url)
            conn.inputStream.bufferedReader().use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }

    /**
     * Unduh [url] ke [target]. Dilewati jika file sudah ada dan SHA-1-nya cocok.
     * Jika [sha1] null, verifikasi dilewati. Unduhan yang terputus dimulai ulang
     * dari awal (file .part dibuang).
     */
    fun downloadFile(url: String, target: File, sha1: String?) {
        if (target.exists() && sha1 != null && sha1Of(target).equals(sha1, ignoreCase = true)) {
            return
        }
        target.parentFile?.mkdirs()
        val tmp = File(target.parentFile, target.name + ".part")

        withRetry(url) {
            tmp.delete()
            val conn = open(url)
            try {
                checkStatus(conn, url)
                conn.inputStream.use { input ->
                    tmp.outputStream().use { output -> input.copyTo(output) }
                }
            } finally {
                conn.disconnect()
            }

            if (sha1 != null && !sha1Of(tmp).equals(sha1, ignoreCase = true)) {
                tmp.delete()
                throw IOException("SHA-1 tidak cocok untuk ${target.name}")
            }
        }

        target.delete()
        if (!tmp.renameTo(target)) {
            tmp.delete()
            error("Gagal menyimpan ${target.name}")
        }
    }

    fun sha1Of(file: File): String {
        val digest = MessageDigest.getInstance("SHA-1")
        file.inputStream().use { input ->
            val buffer = ByteArray(8192)
            while (true) {
                val read = input.read(buffer)
                if (read <= 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    /** Coba [block] sampai [MAX_ATTEMPTS] kali. Hanya IOException yang diulang. */
    private fun <T> withRetry(what: String, block: () -> T): T {
        var last: IOException? = null
        for (attempt in 1..MAX_ATTEMPTS) {
            try {
                return block()
            } catch (e: IOException) {
                last = e
                if (attempt < MAX_ATTEMPTS) {
                    Thread.sleep(1_000L * attempt * attempt)
                }
            }
        }
        throw IOException("Gagal setelah $MAX_ATTEMPTS percobaan ($what): ${last?.message}", last)
    }

    private fun open(url: String): HttpURLConnection =
        (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 30_000
            readTimeout = 60_000
            instanceFollowRedirects = true
        }

    private fun checkStatus(conn: HttpURLConnection, url: String) {
        val code = conn.responseCode
        when {
            code in 200..299 -> return
            // Error server sementara: coba lagi.
            code >= 500 -> throw IOException("HTTP $code untuk $url")
            // Error klien (404 dll): tidak diulang.
            else -> error("HTTP $code untuk $url")
        }
    }
}
