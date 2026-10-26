package com.sally.launcher.core.engine

import java.io.File

/**
 * Probes whether the installed Android OpenJDK's native JLI launcher can be loaded
 * inside Sally's process. This deliberately does not start a second JVM or Minecraft.
 */
object JliBridgeDiagnostics {
    data class ProbeResult(
        val available: Boolean,
        val details: String,
    )

    private val nativeBridgeAvailable: Boolean by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        try {
            System.loadLibrary("sallybridge")
            true
        } catch (_: UnsatisfiedLinkError) {
            false
        } catch (_: SecurityException) {
            false
        }
    }

    fun probe(runtimeHome: File): ProbeResult {
        val canonicalHome = runCatching { runtimeHome.canonicalFile }.getOrElse {
            return ProbeResult(false, "Cannot resolve runtime home: ${it.message ?: "unknown error"}")
        }
        if (!canonicalHome.isDirectory) {
            return ProbeResult(false, "Runtime home does not exist: ${canonicalHome.absolutePath}")
        }
        if (!File(canonicalHome, "release").isFile || !File(canonicalHome, "bin/java").isFile) {
            return ProbeResult(false, "Runtime home is incomplete: release metadata or bin/java is missing.")
        }
        if (!nativeBridgeAvailable) {
            return ProbeResult(false, "Sally native bridge could not be loaded. Rebuild the APK with the NDK-enabled workflow.")
        }

        val response = try {
            nativeProbeJli(canonicalHome.absolutePath)
        } catch (error: Throwable) {
            return ProbeResult(false, "Native runtime probe failed: ${error.message ?: error.javaClass.simpleName}")
        }
        val success = response.startsWith("OK|")
        val details = response.substringAfter('|', response).ifBlank { "The native probe returned no details." }
        return ProbeResult(success, details)
    }

    private external fun nativeProbeJli(runtimeHome: String): String
}
