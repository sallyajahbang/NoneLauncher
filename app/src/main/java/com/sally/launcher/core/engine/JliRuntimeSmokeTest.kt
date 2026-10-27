package com.sally.launcher.core.engine

import java.io.File
import java.io.IOException

/**
 * Executes a real, harmless `java -version` request through the runtime's JLI_Launch
 * entry point. This verifies that the installed Android JVM can initialize inside the
 * app process; it does not launch Minecraft and must be run at most once per app process.
 */
object JliRuntimeSmokeTest {
    data class Result(
        val passed: Boolean,
        val details: String,
        val logFile: File,
    )

    private val bridgeAvailable: Boolean by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        try {
            System.loadLibrary("sallybridge")
            true
        } catch (_: UnsatisfiedLinkError) {
            false
        } catch (_: SecurityException) {
            false
        }
    }

    fun run(runtimeHome: File, workingDirectory: File, logFile: File): Result {
        val home = runCatching { runtimeHome.canonicalFile }.getOrElse {
            throw IOException("Cannot resolve runtime home", it)
        }
        val work = runCatching { workingDirectory.canonicalFile }.getOrElse {
            throw IOException("Cannot resolve JVM test working directory", it)
        }
        val log = runCatching { logFile.canonicalFile }.getOrElse {
            throw IOException("Cannot resolve JVM test log path", it)
        }
        if (!home.isDirectory || !File(home, "release").isFile || !File(home, "bin/java").isFile) {
            throw IOException("Selected runtime is incomplete: expected release metadata and bin/java.")
        }
        if (!work.isDirectory && !work.mkdirs() && !work.isDirectory) {
            throw IOException("Cannot create JVM test working directory: ${work.absolutePath}")
        }
        val logParent = log.parentFile ?: throw IOException("JVM log path has no parent directory")
        if (!logParent.isDirectory && !logParent.mkdirs() && !logParent.isDirectory) {
            throw IOException("Cannot create JVM log directory: ${logParent.absolutePath}")
        }
        if (!bridgeAvailable) {
            throw IOException("Sally native bridge is unavailable. Rebuild the APK with the NDK-enabled workflow.")
        }

        // The returned status comes from JLI_Launch. stdout/stderr are written to logFile.
        val response = try {
            nativeRunVersion(home.absolutePath, work.absolutePath, log.absolutePath)
        } catch (error: Throwable) {
            throw IOException("JLI runtime test failed in native code: ${error.message ?: error.javaClass.simpleName}", error)
        }
        val passed = response.startsWith("OK|")
        val details = response.substringAfter('|', response).ifBlank { "JLI returned no diagnostic details." }
        return Result(passed, details, log)
    }

    private external fun nativeRunVersion(runtimeHome: String, workingDirectory: String, logFile: String): String
}
