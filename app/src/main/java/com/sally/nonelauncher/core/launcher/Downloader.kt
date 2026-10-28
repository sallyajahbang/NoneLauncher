// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 Buntuk

package com.sally.nonelauncher.core.launcher

import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

class DownloadException(message: String) : Exception(message)

/**
 * Mengunduh file ke disk dan memverifikasi SHA-1.
 * Dipanggil dari thread latar belakang (blocking). Jangan panggil dari main thread.
 */
object Downloader {

    /** Unduh [download] ke [dest]. Dilewati jika file sudah ada dan hash-nya cocok. */
    fun fetch(download: Download, dest: File) {
        if (dest.isFile && (download.sha1.isEmpty() || sha1(dest).equals(download.sha1, ignoreCase = true))) {
            return
        }
        dest.parentFile?.mkdirs()
        val tmp = File(dest.parentFile, dest.name + ".part")

        val conn = (URL(download.url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 30_000
            instanceFollowRedirects = true
        }
        try {
            if (conn.responseCode !in 200..299) {
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
