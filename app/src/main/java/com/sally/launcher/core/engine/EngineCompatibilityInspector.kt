package com.sally.launcher.core.engine

import java.io.File
import java.security.MessageDigest

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
        "libexithook.so",
        "libbytehook.so",
        "libpojavexec_awt.so",
        "libawt_xawt.so",
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

        val javaMajor = parseJavaMajor(releaseValue(releaseText, "JAVA_VERSION"))
        if (javaMajor == 8 || javaMajor == 17 || javaMajor == 21 || javaMajor == 25) {
            val component = if (javaMajor == 8) "caciocavallo" else "caciocavallo17"
            val componentDir = File(minecraftRoot, component)
            val requiredJars = if (javaMajor == 8) {
                listOf("cacio-androidnw-1.10-SNAPSHOT.jar", "cacio-shared-1.10-SNAPSHOT.jar", "ResConfHack.jar")
            } else {
                listOf("cacio-tta-1.18-SNAPSHOT.jar", "cacio-shared-1.18-SNAPSHOT.jar")
            }
            val complete = File(componentDir, "version").isFile &&
                requiredJars.all { File(componentDir, it).let { jar -> jar.isFile && jar.length() > 0L } }
            checks += EngineRequirement(
                id = "runtime-$component",
                ready = complete,
                detail = if (complete) {
                    "Java $javaMajor AWT toolkit component $component is installed."
                } else {
                    "Java $javaMajor AWT toolkit component $component is incomplete at ${componentDir.absolutePath}. Reinstall engine runtime components."
                },
            )
        } else if (javaMajor != null) {
            checks += EngineRequirement(
                id = "runtime-java-awt-version",
                ready = false,
                detail = "This engine supports Java 8, 17, 21, and 25 only because its bundled Caciocavallo AWT toolkits do not match Java $javaMajor; this runtime cannot be launched safely yet.",
            )
        } else {
            checks += EngineRequirement(
                id = "runtime-java-version",
                ready = false,
                detail = "Cannot determine the Java major version from ${releaseFile.absolutePath}; AWT toolkit compatibility cannot be checked.",
            )
        }

        val patchedGlfwJar = File(minecraftRoot, "engine/pojav/${PojavEnginePayloadInstaller.GLFW_CLASSES_NAME}")
        val patchedGlfwValid = patchedGlfwJar.isFile && runCatching {
            sha256(patchedGlfwJar) == PojavEnginePayloadInstaller.GLFW_CLASSES_SHA256
        }.getOrDefault(false)
        checks += EngineRequirement(
            id = "patched-glfw-classes",
            ready = patchedGlfwValid,
            detail = if (patchedGlfwValid) {
                "Patched Android GLFW classes passed SHA-256 validation."
            } else {
                "Required patched GLFW classes (Pojav-compatible) are missing or corrupted at ${patchedGlfwJar.absolutePath}; reinstall the engine payload."
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

    private fun parseJavaMajor(raw: String): Int? {
        val value = raw.trim()
        if (value.startsWith("1.")) return value.removePrefix("1.").takeWhile { it.isDigit() }.toIntOrNull()
        return value.takeWhile { it.isDigit() }.toIntOrNull()
    }

    private fun releaseValue(text: String, key: String): String =
        text.lineSequence().firstOrNull { it.startsWith("$key=") }
            ?.substringAfter('=')
            ?.trim()
            ?.removeSurrounding("\"")
            .orEmpty()

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().buffered().use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

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
