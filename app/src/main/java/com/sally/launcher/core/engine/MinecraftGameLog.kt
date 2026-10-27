package com.sally.launcher.core.engine

import android.content.Context
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.nio.charset.StandardCharsets
import net.kdt.pojavlaunch.Logger

/** Handles the on-device Minecraft stdout/stderr log without exposing launch arguments/tokens. */
object MinecraftGameLog {
    private const val LOG_DIRECTORY = "logs"
    private const val LOG_NAME = "latest.log"
    private const val MAX_TAIL_BYTES = 64 * 1024

    @Volatile
    private var captureStarted = false

    fun file(context: Context): File = File(File(context.applicationContext.filesDir, "minecraft/$LOG_DIRECTORY"), LOG_NAME)

    /**
     * Starts native output capture once per Android process. The embedded JLI/native logger is
     * process-global, so silently starting a second capture would leave stale native pipe readers.
     */
    @Synchronized
    @Throws(IOException::class)
    fun beginCapture(context: Context): File {
        if (captureStarted) {
            throw IOException("This app process has already started an embedded Minecraft JVM. Fully close and reopen Sally Launcher before another launch.")
        }
        val target = prepare(context)
        try {
            Logger.begin(target.absolutePath)
        } catch (error: Throwable) {
            throw IOException("Native Minecraft output capture could not start: ${error.message ?: error.javaClass.simpleName}", error)
        }
        captureStarted = true
        return target
    }

    /** Native Logger.begin() opens with O_TRUNC but not O_CREAT, so create the file first. */
    @Throws(IOException::class)
    fun prepare(context: Context): File {
        val target = file(context).canonicalFile
        val parent = target.parentFile ?: throw IOException("Minecraft log directory could not be resolved.")
        if (!parent.isDirectory && !parent.mkdirs() && !parent.isDirectory) {
            throw IOException("Cannot create Minecraft log directory: ${parent.absolutePath}")
        }
        if (!target.exists() && !target.createNewFile()) {
            throw IOException("Cannot create Minecraft log file: ${target.absolutePath}")
        }
        if (!target.isFile || !target.canWrite()) {
            throw IOException("Minecraft log file is not writable: ${target.absolutePath}")
        }
        return target
    }

    /** Read only the final bytes so a long session cannot allocate a huge String on the UI thread. */
    fun readTail(context: Context, maxBytes: Int = MAX_TAIL_BYTES): String {
        val logFile = file(context)
        if (!logFile.isFile) return "No Minecraft log has been created yet. Start a game session first."
        return runCatching {
            RandomAccessFile(logFile, "r").use { input ->
                val length = input.length()
                val count = minOf(length, maxBytes.coerceAtLeast(1).toLong()).toInt()
                input.seek((length - count).coerceAtLeast(0L))
                val bytes = ByteArray(count)
                input.readFully(bytes)
                val text = String(bytes, StandardCharsets.UTF_8)
                val prefix = if (length > count) "[Showing the last $count bytes of ${length} bytes. Earlier output omitted.]\n\n" else ""
                prefix + sanitizeForDisplay(text)
            }
        }.getOrElse { error -> "Could not read Minecraft log: ${error.message ?: error.javaClass.simpleName}" }
    }

    /** Redact common credential formats before rendering log contents in the UI. */
    internal fun sanitizeForDisplay(text: String): String {
        var safe = CREDENTIAL_PATTERN.replace(text) { match -> "${match.groupValues[1]}=[REDACTED]" }
        safe = XBOX_TOKEN_PATTERN.replace(safe) { match -> "${match.groupValues[1]}[REDACTED]" }
        return safe
    }

    private val CREDENTIAL_PATTERN = Regex(
        "(?i)(access[_ -]?token|refresh[_ -]?token|id[_ -]?token|client[_ -]?token|authorization|bearer)\\s*(?:[:=]\\s*|\\s+)(?:Bearer\\s+)?(?:\"[^\"]*\"|'[^']*'|[^\\s,;]+)"
    )
    private val XBOX_TOKEN_PATTERN = Regex("(?i)(XBL3\\.0\\s+[^\\s=]+\\s*=\\s*)[^\\s,;]+")
}
