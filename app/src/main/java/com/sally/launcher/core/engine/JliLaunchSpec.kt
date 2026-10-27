package com.sally.launcher.core.engine

import android.content.Context
import java.io.File
import java.io.IOException
import java.util.LinkedHashSet

/**
 * Immutable, shell-free description of a JLI_Launch invocation.
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

/** Builds the exact argv/environment payload consumed by the Pojav-derived native JLI bridge. */
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
        val appNativeDir = appContext.applicationInfo.nativeLibraryDir
            .takeIf { it.isNotBlank() }
            ?.let { File(it).canonicalFile }
        val runtimeLibraryDirs = findSharedLibraryDirectories(File(runtimeHome, "lib"))
        val gameNativeDirs = if (nativesDirectory.isDirectory && containsSharedLibrary(nativesDirectory)) {
            listOf(nativesDirectory)
        } else emptyList()
        val allLibraryDirs = LinkedHashSet<File>().apply {
            if (nativesDirectory.isDirectory) add(nativesDirectory)
            if (appNativeDir?.isDirectory == true) add(appNativeDir)
            addAll(runtimeLibraryDirs)
            addAll(gameNativeDirs)
        }.toList()

        val vmArguments = profile.jvmArguments.toMutableList()
        // Mojang version JSON frequently sets -Djava.library.path to only the extracted game
        // natives directory. Replace/extend that property so System.loadLibrary can also find
        // APK-packaged Pojav LWJGL/GL4ES/OpenAL and the selected Android JRE's native libraries.
        val propertyIndex = vmArguments.indexOfFirst { it.startsWith("-Djava.library.path=") }
        val existingNativePath = if (propertyIndex >= 0) {
            vmArguments[propertyIndex].substringAfter('=').split(File.pathSeparator).filter { it.isNotBlank() }
        } else emptyList()
        val completeNativePath = LinkedHashSet<String>().apply {
            addAll(allLibraryDirs.map { it.absolutePath })
            addAll(existingNativePath)
        }.joinToString(File.pathSeparator)
        val nativePathArgument = "-Djava.library.path=$completeNativePath"
        if (propertyIndex >= 0) vmArguments[propertyIndex] = nativePathArgument
        else vmArguments += nativePathArgument
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

        val env = linkedMapOf(
            "JAVA_HOME" to runtimeHome.absolutePath,
            "HOME" to appContext.filesDir.canonicalPath,
            "TMPDIR" to tempDirectory.absolutePath,
            "TMP" to tempDirectory.absolutePath,
            "PATH" to (File(runtimeHome, "bin").absolutePath + File.pathSeparator + System.getenv("PATH").orEmpty()),
            "CLASSPATH" to profile.classpath,
            "MINECRAFT_DIR" to minecraftRoot.absolutePath,
            "SALLY_NATIVE_LIBRARY_DIR" to (appNativeDir?.absolutePath ?: ""),
            // Pojav's Android GLFW bridge reads these variables before initializing EGL/GL4ES.
            "POJAV_NATIVEDIR" to (appNativeDir?.absolutePath ?: ""),
            "POJAV_RENDERER" to "opengles2",
            "LIBGL_ES" to "2",
            "LIBGL_MIPMAP" to "3",
            "LIBGL_NOERROR" to "1",
            "LIBGL_NOINTOVLHACK" to "1",
            "LIBGL_NORMALIZE" to "1",
            "FORCE_VSYNC" to "true",
            "MESA_GLSL_CACHE_DIR" to appContext.cacheDir.canonicalPath,
            "AWTSTUB_WIDTH" to "854",
            "AWTSTUB_HEIGHT" to "480",
        )
        val ldLibraryPath = LinkedHashSet<String>().apply {
            addAll(allLibraryDirs.map { it.absolutePath })
            System.getenv("LD_LIBRARY_PATH")
                ?.split(File.pathSeparator)
                ?.filter { it.isNotBlank() }
                ?.let(::addAll)
        }
        if (ldLibraryPath.isNotEmpty()) env["LD_LIBRARY_PATH"] = ldLibraryPath.joinToString(File.pathSeparator)

        val compatibility = EngineCompatibilityInspector.inspect(
            runtimeHome = runtimeHome,
            minecraftRoot = minecraftRoot,
            appNativeLibraryDir = appNativeDir,
        )
        val blockers = compatibility.blockers.toMutableList()
        if (!profile.isAuthenticatedSession) {
            blockers += "Sign in with a Microsoft account linked to Minecraft Java Edition before launching. Offline placeholder tokens are not accepted."
        }

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
