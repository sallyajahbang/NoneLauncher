package com.sally.nonelauncher.core.model

/**
 * Describes the platform the launcher is running on, used to evaluate
 * Mojang "rules" (OS name, architecture, feature flags).
 *
 * Android runs Minecraft through a Linux-compatible userland, so the OS
 * name is always "linux" as far as Mojang metadata is concerned.
 */
data class RuntimeContext(
    val osName: String = "linux",
    val arch: String,
    val features: Set<String> = emptySet(),
) {
    /** 64 or 32, used to replace `${arch}` inside native classifiers. */
    val archBits: String
        get() = if (arch.contains("64")) "64" else "32"

    companion object {
        fun current(features: Set<String> = emptySet()): RuntimeContext {
            val raw = System.getProperty("os.arch") ?: "aarch64"
            val arch = when {
                raw == "aarch64" || raw == "arm64" -> "arm64"
                raw.startsWith("arm") -> "arm"
                raw == "amd64" || raw == "x86_64" -> "x86_64"
                else -> "x86"
            }
            return RuntimeContext(arch = arch, features = features)
        }
    }
}
