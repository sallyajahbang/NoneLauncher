package com.sally.javoxel.data

import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/** Helper unduhan HTTP sederhana dengan verifikasi SHA-1. Dipanggil dari thread IO. */
object Downloader {

    fun fetchText(url: String): String {
        val conn = open(url)
        try {
            checkStatus(conn, url)
            return conn.inputStream.bufferedReader().use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }

    /**
     * Unduh [url] ke [target]. Dilewati jika file sudah ada dan SHA-1-nya cocok.
     * Jika [sha1] null, verifikasi dilewati.
     */
    fun downloadFile(url: String, target: File, sha1: String?) {
        if (target.exists() && sha1 != null && sha1Of(target).equals(sha1, ignoreCase = true)) {
            return
        }
        target.parentFile?.mkdirs()
        val tmp = File(target.parentFile, target.name + ".part")

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
            error("SHA-1 tidak cocok untuk ${target.name}")
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

    private fun open(url: String): HttpURLConnection =
        (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 30_000
            instanceFollowRedirects = true
        }

    private fun checkStatus(conn: HttpURLConnection, url: String) {
        if (conn.responseCode !in 200..299) {
            error("HTTP ${conn.responseCode} untuk $url")
        }
    }
}
