package com.sally.launcher.core.engine

import java.io.File

/** A single engine prerequisite and the reason it blocks a real game launch. */
data class EngineRequirement(
    val id: String,
    val ready: Boolean,
    val detail: String,
) {
    val blocker: String? get() = if (ready) null else detail
}

/**
 * Inspects the boundary between the downloaded Minecraft profile and an Android-compatible
 * Pojav-style engine. Vanilla desktop Linux natives are intentionally not treated as Android
 * natives: the patched GLFW Java classes and matching Android ELF libraries must be supplied
 * by the engine bundle.
 */
object EngineCompatibilityInspector {
    private val requiredRuntimeLibraries = listOf("libjli.so", "libjvm.so")
    private val requiredEngineLibraries = listOf(
        "libpojavexec.so",
        "liblwjgl.so",
        "liblwjgl_opengl.so",
        "liblwjgl_stb.so",
        "libopenal.so",
        "libgl4es_114.so",
    )

    data class Report(val requirements: List<EngineRequirement>) {
        val ready: Boolean get() = requirements.all { it.ready }
        val blockers: List<String> get() = requirements.mapNotNull { it.blocker }
    }

    fun inspect(
        runtimeHome: File,
        minecraftRoot: File,
        appNativeLibraryDir: File?,
        deviceArchitecture: String = System.getProperty("os.arch").orEmpty(),
    ): Report {
        val checks = mutableListOf<EngineRequirement>()
        val releaseFile = File(runtimeHome, "release")
        val releaseText = runCatching { releaseFile.readText() }.getOrDefault("")
        val runtimeArchitecture = releaseValue(releaseText, "OS_ARCH")
        val runtimeAbi = normalizeArchitecture(runtimeArchitecture)
        val deviceAbi = normalizeArchitecture(deviceArchitecture)
        checks += EngineRequirement(
            id = "runtime-abi",
            ready = runtimeAbi != null && runtimeAbi == deviceAbi,
            detail = if (runtimeAbi != null && runtimeAbi == deviceAbi) {
                "Runtime architecture matches the app process ($deviceAbi)."
            } else {
                "Runtime/app ABI mismatch: runtime OS_ARCH='${runtimeArchitecture.ifBlank { "unknown" }}', " +
                    "app process ABI='${deviceAbi ?: deviceArchitecture.ifBlank { "unknown" }}'. Install a matching Android Java runtime."
            },
        )

        for (library in requiredRuntimeLibraries) {
            val present = findFileBounded(runtimeHome, library) != null
            checks += EngineRequirement(
                id = "runtime-$library",
                ready = present,
                detail = if (present) "$library is present in the selected runtime." else
                    "Required JVM library $library is missing from ${runtimeHome.absolutePath}. Reinstall a complete Android Java runtime.",
            )
        }

        val engineNatives = listOfNotNull(
            appNativeLibraryDir?.takeIf { it.isDirectory },
            File(minecraftRoot, "engine/pojav/natives").takeIf { it.isDirectory },
        )
        for (library in requiredEngineLibraries) {
            val present = engineNatives.any { File(it, library).isFile }
            checks += EngineRequirement(
                id = "engine-$library",
                ready = present,
                detail = if (present) "$library is available to the Android process." else
                    "Android engine library $library is not packaged. A desktop Linux native JAR is not a substitute.",
            )
        }

        val patchedGlfwJar = File(minecraftRoot, "engine/pojav/lwjgl-glfw-classes.jar")
        checks += EngineRequirement(
            id = "patched-glfw-classes",
            ready = patchedGlfwJar.isFile && patchedGlfwJar.length() > 0L,
            detail = if (patchedGlfwJar.isFile && patchedGlfwJar.length() > 0L) {
                "Patched Android GLFW classes are installed."
            } else {
                "Pojav-compatible patched GLFW classes are missing at ${patchedGlfwJar.absolutePath}."
            },
        )
        return Report(checks)
    }

    /** Maps common JVM/Android ELF architecture labels to Android ABI names. */
    fun normalizeArchitecture(value: String): String? = when (value.trim().lowercase()) {
        "aarch64", "arm64", "arm64-v8a" -> "arm64-v8a"
        "arm", "armv7", "armv7l", "armeabi-v7a" -> "armeabi-v7a"
        "x86_64", "amd64" -> "x86_64"
        "x86", "i386", "i486", "i586", "i686" -> "x86"
        else -> null
    }

    private fun releaseValue(text: String, key: String): String =
        text.lineSequence().firstOrNull { it.startsWith("$key=") }
            ?.substringAfter('=')
            ?.trim()
            ?.removeSurrounding("\"")
            .orEmpty()

    private fun findFileBounded(root: File, filename: String): File? {
        if (!root.isDirectory) return null
        val canonicalRoot = runCatching { root.canonicalFile }.getOrNull() ?: return null
        var inspected = 0
        fun visit(directory: File, depth: Int): File? {
            if (depth > 8 || inspected >= 5_000) return null
            val current = runCatching { directory.canonicalFile }.getOrNull() ?: return null
            if (current != canonicalRoot && !current.path.startsWith(canonicalRoot.path + File.separator)) return null
            inspected++
            val children = current.listFiles().orEmpty()
            children.firstOrNull { it.isFile && it.name == filename }?.let { return it }
            for (child in children) {
                if (inspected >= 5_000) break
                if (child.isDirectory) visit(child, depth + 1)?.let { return it }
            }
            return null
        }
        return visit(canonicalRoot, 0)
    }
}
