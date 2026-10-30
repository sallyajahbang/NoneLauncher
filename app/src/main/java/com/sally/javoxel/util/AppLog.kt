package com.sally.javoxel.util

import java.io.File
import java.io.RandomAccessFile
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Log yang bisa dibaca di dalam app, tanpa logcat.
 *
 * Dua file di filesDir/logs/:
 *  - latest.log   : sesi app yang sedang jalan. Termasuk output JVM dan native (stdout, stderr).
 *  - previous.log : sesi sebelumnya. Dipakai setelah app crash lalu dibuka lagi.
 */
object AppLog {

    enum class Session { CURRENT, PREVIOUS }

    private const val LATEST = "latest.log"
    private const val PREVIOUS = "previous.log"

    private val lock = Any()
    private var dir: File? = null
    private val timeFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)

    /**
     * Panggil saat app mulai. Log sesi sebelumnya dipindah ke previous.log.
     * Idempotent: pemanggilan berikutnya tidak mengulang rotasi.
     */
    fun init(filesDir: File) {
        synchronized(lock) {
            if (dir != null) return
            val d = File(filesDir, "logs").apply { mkdirs() }
            val latest = File(d, LATEST)
            val previous = File(d, PREVIOUS)
            if (latest.exists()) {
                previous.delete()
                latest.renameTo(previous)
            }
            latest.createNewFile()
            dir = d
        }
    }

    /** Path latest.log, untuk diarahkan ke stdout/stderr JVM. */
    fun latestPath(): String = File(checkNotNull(dir) { "AppLog belum di-init" }, LATEST).path

    fun i(msg: String) = write("I", msg)

    fun e(msg: String) = write("E", msg)

    fun write(level: String, msg: String) {
        val d = dir ?: return
        val line = "${timeFormat.format(Date())} $level $msg\n"
        synchronized(lock) {
            try {
                File(d, LATEST).appendText(line)
            } catch (e: Exception) {
                // Log tidak boleh membuat app ikut gagal.
            }
        }
    }

    /** Ambil bagian akhir log, supaya layar tetap ringan walau log besar. */
    fun readTail(session: Session, maxBytes: Int = 100_000): String {
        val d = dir ?: return "(log belum siap)"
        val f = File(d, if (session == Session.CURRENT) LATEST else PREVIOUS)
        if (!f.isFile) return "(kosong)"
        return try {
            synchronized(lock) {
                RandomAccessFile(f, "r").use { raf ->
                    val len = raf.length()
                    val start = (len - maxBytes).coerceAtLeast(0L)
                    val bytes = ByteArray((len - start).toInt())
                    raf.seek(start)
                    raf.readFully(bytes)
                    String(bytes, Charsets.UTF_8)
                }
            }
        } catch (e: Exception) {
            "Gagal baca log: ${e.message}"
        }
    }
}
