package com.sally.launcher.core.engine

import android.content.Context
import java.io.File
import java.io.IOException
import java.util.LinkedHashSet

/**
 * Immutable, shell-free description of a future JLI_Launch invocation.
 * Building this object never loads native libraries, mutates process environment, or starts a JVM.
 */
data class JliLaunchSpec(
    val runtimeHome: File,
    val workingDirectory: File,
    val argv: List<String>,
    val environment: Map<String, String>,
    val nativeLibraryDirectories: List<File>,
    val blockers: List<String>,
) {
    val canAttemptLaunch: Boolean get() = blockers.isEmpty()
}

/** Builds the exact argv/environment payload that the native JLI bridge will consume later. */
class JliLaunchSpecBuilder(context: Context) {
    private val appContext = context.applicationContext

    fun build(profile: LaunchProfileBuilder.LaunchProfile): JliLaunchSpec {
        val runtimeHome = profile.javaHome.canonicalFile
        if (!runtimeHome.isDirectory || !File(runtimeHome, "release").isFile ||
            !File(runtimeHome, "bin/java").isFile) {
            throw IOException("Selected Java runtime is incomplete: ${runtimeHome.absolutePath}")
        }
        if (!profile.clientJar.isFile || profile.classpathEntries.any { !it.isFile }) {
            throw IOException("One or more Minecraft classpath files are missing. Reinstall the version files first.")
        }
        if (profile.mainClass.isBlank()) throw IOException("Minecraft main class is empty.")
        if (profile.jvmArguments.any { it.indexOf('\u0000') >= 0 } ||
            profile.gameArguments.any { it.indexOf('\u0000') >= 0 }) {
            throw IOException("A launch argument contains an invalid NUL character.")
        }

        val minecraftRoot = profile.workingDirectory.parentFile?.canonicalFile
            ?: throw IOException("Cannot resolve the Minecraft data directory.")
        val nativesDirectory = File(minecraftRoot, "natives").canonicalFile
        val tempDirectory = File(minecraftRoot, "tmp").canonicalFile
        if (!tempDirectory.isDirectory && !tempDirectory.mkdirs() && !tempDirectory.isDirectory) {
            throw IOException("Cannot create JVM temporary directory: ${tempDirectory.absolutePath}")
        }

        // Keep each argument as an individual string. Never join/split with a shell: paths and
        // values may contain spaces, and JLI_Launch expects argv-style values.
        val vmArguments = profile.jvmArguments.toMutableList()
        if (vmArguments.none { it.startsWith("-Djava.library.path=") }) {
            vmArguments += "-Djava.library.path=${nativesDirectory.absolutePath}"
        }
        if (vmArguments.none { it.startsWith("-Djava.io.tmpdir=") }) {
            vmArguments += "-Djava.io.tmpdir=${tempDirectory.absolutePath}"
        }
        if (vmArguments.none { it.startsWith("-Duser.home=") }) {
            vmArguments += "-Duser.home=${appContext.filesDir.canonicalPath}"
        }
        ensureClasspath(vmArguments, profile.classpath)

        val argv = buildList {
            add("java") // JLI_Launch follows the C convention: argv[0] names the launcher.
            addAll(vmArguments)
            add(profile.mainClass)
            addAll(profile.gameArguments)
        }
        if (argv.any { it.indexOf('\u0000') >= 0 }) {
            throw IOException("A native launch argument contains an invalid NUL character.")
        }

        val appNativeDir = appContext.applicationInfo.nativeLibraryDir
            .takeIf { it.isNotBlank() }
            ?.let { File(it).canonicalFile }
        val runtimeLibraryDirs = findSharedLibraryDirectories(File(runtimeHome, "lib"))
        val gameNativeDirs = if (nativesDirectory.isDirectory && containsSharedLibrary(nativesDirectory)) {
            listOf(nativesDirectory)
        } else emptyList()
        val allLibraryDirs = LinkedHashSet<File>().apply {
            addAll(runtimeLibraryDirs)
            if (appNativeDir?.isDirectory == true) add(appNativeDir)
            addAll(gameNativeDirs)
        }.toList()

        val env = linkedMapOf(
            "JAVA_HOME" to runtimeHome.absolutePath,
            "HOME" to appContext.filesDir.canonicalPath,
            "TMPDIR" to tempDirectory.absolutePath,
            "TMP" to tempDirectory.absolutePath,
            "CLASSPATH" to profile.classpath,
            "MINECRAFT_DIR" to minecraftRoot.absolutePath,
            "SALLY_NATIVE_LIBRARY_DIR" to (appNativeDir?.absolutePath ?: ""),
        )
        val ldLibraryPath = LinkedHashSet<String>().apply {
            addAll(allLibraryDirs.map { it.absolutePath })
            System.getenv("LD_LIBRARY_PATH")
                ?.split(File.pathSeparator)
                ?.filter { it.isNotBlank() }
                ?.let(::addAll)
        }
        if (ldLibraryPath.isNotEmpty()) env["LD_LIBRARY_PATH"] = ldLibraryPath.joinToString(File.pathSeparator)

        val blockers = mutableListOf<String>()
        if (allLibraryDirs.none { File(it, "libjli.so").isFile }) {
            blockers += "libjli.so was not found in the runtime library directories; run the JVM bridge probe."
        }
        if (allLibraryDirs.none { File(it, "libjvm.so").isFile }) {
            blockers += "libjvm.so was not found in the runtime library directories; run the JVM bridge probe."
        }
        if (!containsSharedLibrary(nativesDirectory)) {
            blockers += "Minecraft native libraries are not extracted. The current installer intentionally skips desktop native classifier JARs."
        }
        blockers += "Android-compatible LWJGL/GLFW and audio native libraries are not integrated."
        blockers += "The GLES screen and input queue are diagnostics only; rendering and events are not wired to GLFW."
        blockers += "This stage builds a JLI request only. It does not invoke JLI_Launch or start Minecraft."

        return JliLaunchSpec(
            runtimeHome = runtimeHome,
            workingDirectory = profile.workingDirectory.canonicalFile,
            argv = argv.toList(),
            environment = env.toMap(),
            nativeLibraryDirectories = allLibraryDirs,
            blockers = blockers.distinct(),
        )
    }

    private fun ensureClasspath(arguments: MutableList<String>, classpath: String) {
        var index = 0
        while (index < arguments.size) {
            val value = arguments[index]
            when {
                value == "-cp" || value == "-classpath" || value == "--class-path" -> {
                    val valueIndex = index + 1
                    if (valueIndex >= arguments.size || arguments[valueIndex].isBlank()) {
                        throw IOException("Minecraft JVM arguments contain an incomplete classpath option.")
                    }
                    return
                }
                value.startsWith("--class-path=") || value.startsWith("-Djava.class.path=") -> return
            }
            index++
        }
        arguments += "-cp"
        arguments += classpath
    }

    private fun findSharedLibraryDirectories(root: File): List<File> {
        if (!root.isDirectory) return emptyList()
        val rootCanonical = runCatching { root.canonicalFile }.getOrNull() ?: return emptyList()
        val found = LinkedHashSet<File>()
        val visited = HashSet<String>()
        var budget = 0

        fun visit(directory: File, depth: Int) {
            if (depth > 7 || budget >= MAX_SCANNED_ENTRIES) return
            val canonical = runCatching { directory.canonicalFile }.getOrNull() ?: return
            val rootPath = rootCanonical.path
            if (canonical.path != rootPath && !canonical.path.startsWith(rootPath + File.separator)) return
            if (!visited.add(canonical.path)) return
            budget++
            val children = canonical.listFiles().orEmpty()
            if (children.any { it.isFile && it.name.endsWith(".so") }) found += canonical
            for (child in children) {
                if (budget >= MAX_SCANNED_ENTRIES) break
                if (child.isDirectory) visit(child, depth + 1)
            }
        }

        visit(rootCanonical, 0)
        return found.toList().sortedBy { it.absolutePath }
    }

    private fun containsSharedLibrary(directory: File): Boolean =
        directory.isDirectory && directory.listFiles().orEmpty().any { it.isFile && it.name.endsWith(".so") }

    private companion object {
        const val MAX_SCANNED_ENTRIES = 5_000
    }
}
