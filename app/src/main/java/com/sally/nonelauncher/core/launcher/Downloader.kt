// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 Buntuk

package com.sally.nonelauncher.core.launcher

import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

class DownloadException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * Mengunduh file ke disk dan memverifikasi SHA-1.
 * Dipanggil dari thread latar belakang (blocking). Jangan panggil dari main thread.
 *
 * Jaringan lambat atau tidak stabil sering menyebabkan timeout, jadi unduhan
 * diulang beberapa kali dengan jeda yang makin panjang.
 */
object Downloader {

    private const val MAX_ATTEMPTS = 4
    private const val CONNECT_TIMEOUT_MS = 30_000
    private const val READ_TIMEOUT_MS = 90_000

    /** Unduh [download] ke [dest]. Dilewati jika file sudah ada dan hash-nya cocok. */
    fun fetch(download: Download, dest: File) {
        if (dest.isFile && (download.sha1.isEmpty() || sha1(dest).equals(download.sha1, ignoreCase = true))) {
            return
        }
        var lastError: IOException? = null
        for (attempt in 1..MAX_ATTEMPTS) {
            try {
                fetchOnce(download, dest)
                return
            } catch (e: IOException) {
                lastError = e
                if (attempt < MAX_ATTEMPTS) Thread.sleep(attempt * 2_000L)
            }
        }
        throw DownloadException(
            "Gagal setelah $MAX_ATTEMPTS percobaan (${lastError?.message ?: "error tidak diketahui"}): ${download.url}",
            lastError,
        )
    }

    private fun fetchOnce(download: Download, dest: File) {
        dest.parentFile?.mkdirs()
        val tmp = File(dest.parentFile, dest.name + ".part")

        val conn = (URL(download.url).openConnection() as HttpURLConnection).apply {
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
            instanceFollowRedirects = true
        }
        try {
            if (conn.responseCode !in 200..299) {
                // Error HTTP dari server tidak diulang. Langsung gagal.
                throw DownloadException("HTTP ${conn.responseCode} untuk ${download.url}")
            }
            conn.inputStream.use { input ->
                tmp.outputStream().use { output -> input.copyTo(output) }
            }
        } finally {
            conn.disconnect()
        }

        if (download.sha1.isNotEmpty() && !sha1(tmp).equals(download.sha1, ignoreCase = true)) {
            tmp.delete()
            throw DownloadException("SHA-1 tidak cocok: ${download.url}")
        }
        if (!tmp.renameTo(dest)) {
            tmp.copyTo(dest, overwrite = true)
            tmp.delete()
        }
    }

    fun sha1(file: File): String {
        val md = MessageDigest.getInstance("SHA-1")
        file.inputStream().use { input ->
            val buf = ByteArray(8192)
            while (true) {
                val n = input.read(buf)
                if (n < 0) break
                md.update(buf, 0, n)
            }
        }
        return md.digest().joinToString("") { "%02x".format(it) }
    }
}
