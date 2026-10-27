package com.sally.launcher.core.engine

import android.app.ActivityManager
import android.content.Context
import com.sally.launcher.data.storage.LauncherStorage
import android.os.Build
import java.io.File
import java.io.IOException
import java.util.LinkedHashSet
import java.util.Locale
import java.util.TimeZone

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
        val runtimeLibraryDirs = RuntimeLibraryDirectories.discover(File(runtimeHome, "lib"))
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
            vmArguments += "-Duser.home=${LauncherStorage.accessibleFilesDir(appContext).canonicalPath}"
        }
        addPojavRuntimeArguments(vmArguments, appNativeDir, runtimeHome, minecraftRoot)
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

        val (displayWidth, displayHeight) = GameDisplaySize.resolve(appContext)
        val env = linkedMapOf(
            "JAVA_HOME" to runtimeHome.absolutePath,
            "HOME" to LauncherStorage.accessibleFilesDir(appContext).canonicalPath,
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
            "AWTSTUB_WIDTH" to displayWidth.toString(),
            "AWTSTUB_HEIGHT" to displayHeight.toString(),
        )
        // Do not put <runtime>/lib/<arch>/server (or lib/server) in the process environment.
        // OpenJDK JLI detects libjvm.so in LD_LIBRARY_PATH and re-execs bin/java to refresh it;
        // that exec fails with EACCES on many Android app-private runtime layouts. The JVM
        // directory is added separately to Android's internal linker namespace by the executor.
        val environmentLibraryDirs = LinkedHashSet<String>().apply {
            addAll(RuntimeLibraryDirectories.discoverEnvironmentDirectories(File(runtimeHome, "lib")).map { it.absolutePath })
            if (nativesDirectory.isDirectory) add(nativesDirectory.absolutePath)
            if (appNativeDir?.isDirectory == true) add(appNativeDir.absolutePath)
            addAll(systemLibraryDirectories())
            System.getenv("LD_LIBRARY_PATH")
                ?.split(File.pathSeparator)
                ?.filter { it.isNotBlank() && !File(it, "libjvm.so").isFile }
                ?.let(::addAll)
        }
        if (environmentLibraryDirs.isNotEmpty()) {
            env["LD_LIBRARY_PATH"] = environmentLibraryDirs.joinToString(File.pathSeparator)
        }

        val compatibility = EngineCompatibilityInspector.inspect(
            runtimeHome = runtimeHome,
            minecraftRoot = minecraftRoot,
            appNativeLibraryDir = appNativeDir,
        )
        val blockers = compatibility.blockers.toMutableList()
        if (!profile.hasValidAccountSession) {
            blockers += "Select a valid Microsoft or local account profile before launching."
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

    /** Same vendor/system linker directories upstream Pojav appends to LD_LIBRARY_PATH (GPU drivers, etc.). */
    private fun systemLibraryDirectories(): List<String> =
        if (android.os.Process.is64Bit()) listOf("/system/lib64", "/vendor/lib64", "/vendor/lib64/hw")
        else listOf("/system/lib", "/vendor/lib", "/vendor/lib/hw")

    /**
     * Mirrors the default JVM arguments of upstream Pojav (JREUtils.getJavaArgs/launchJavaVM).
     * Anything already set by the Mojang version JSON is left untouched.
     */
    private fun addPojavRuntimeArguments(
        args: MutableList<String>,
        appNativeDir: File?,
        runtimeHome: File,
        minecraftRoot: File,
    ) {
        fun hasProperty(name: String) = args.any { it == name || it.startsWith("$name=") }
        fun addDefault(name: String, value: String) {
            if (!hasProperty(name)) args += "$name=$value"
        }

        val (width, height) = GameDisplaySize.resolve(appContext)
        // Upstream Pojav explicitly sets java.home; relying on launcher's inferred home
        // is fragile when JLI is called from a library instead of an executable.
        addDefault("-Djava.home", runtimeHome.absolutePath)
        addDefault("-Dos.name", "Linux")
        addDefault("-Dos.version", "Android-${Build.VERSION.RELEASE}")
        addDefault("-Duser.language", Locale.getDefault().language.ifBlank { "en" })
        addDefault("-Duser.timezone", TimeZone.getDefault().id)
        if (appNativeDir != null) addDefault("-Djna.boot.library.path", appNativeDir.absolutePath)
        addDefault("-Dorg.lwjgl.vulkan.libname", "libvulkan.so")
        // The patched GLFW stub reads the initial window size from these properties.
        addDefault("-Dglfwstub.windowWidth", width.toString())
        addDefault("-Dglfwstub.windowHeight", height.toString())
        addDefault("-Dglfwstub.initEgl", "false")
        addDefault("-Dlog4j2.formatMsgNoLookups", "true")
        addDefault("-Dnet.minecraft.clientmodname", "SallyLauncher")
        addDefault("-Dfml.earlyprogresswindow", "false")
        addDefault("-Dloader.disable_forked_guis", "true")
        // POSIX_SPAWN needs jspawnhelper, which does not work on Android.
        addDefault("-Djdk.lang.Process.launchMechanism", "FORK")
        addCaciocavalloArguments(args, runtimeHome, minecraftRoot, width, height)
        if (appNativeDir != null) {
            if (File(appNativeDir, PojavRuntimeLoader.GL4ES_LIBRARY).isFile) {
                addDefault("-Dorg.lwjgl.opengl.libname", PojavRuntimeLoader.GL4ES_LIBRARY)
            }
            val freetype = File(appNativeDir, "libfreetype.so")
            if (freetype.isFile) addDefault("-Dorg.lwjgl.freetype.libname", freetype.absolutePath)
        }
        // JDK 17 cgroup/container probing and the /tmp perf-data mmap crash or hang on many Android devices.
        for (flag in listOf("-XX:-UseContainerSupport", "-XX:-UsePerfData")) {
            if (flag !in args) args += flag
        }
        addDefault("-XX:ActiveProcessorCount", Runtime.getRuntime().availableProcessors().toString())

        val heapMb = runCatching {
            val info = ActivityManager.MemoryInfo()
            (appContext.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager).getMemoryInfo(info)
            (info.totalMem / (1024L * 1024L) * 35L / 100L).coerceIn(512L, 3072L)
        }.getOrDefault(1024L)
        if (args.none { it.startsWith("-Xmx") }) args += "-Xmx${heapMb}M"
        // A large Xms equal to Xmx unnecessarily reserves a large initial heap on Android.
        // Keep the initial heap modest and let HotSpot grow toward the calculated maximum.
        if (args.none { it.startsWith("-Xms") }) args += "-Xms${minOf(512L, heapMb)}M"
    }

    /** Adds Pojav's version-matched AWT toolkit configuration and bootclasspath. */
    private fun addCaciocavalloArguments(
        args: MutableList<String>,
        runtimeHome: File,
        minecraftRoot: File,
        width: Int,
        height: Int,
    ) {
        val releaseText = runCatching { File(runtimeHome, "release").readText() }.getOrDefault("")
        val versionText = releaseText.lineSequence()
            .firstOrNull { it.startsWith("JAVA_VERSION=") }
            ?.substringAfter('=')?.trim()?.removeSurrounding("\"")
            .orEmpty()
        val javaMajor = when {
            versionText.startsWith("1.") -> versionText.removePrefix("1.").substringBefore('.').toIntOrNull()
            else -> versionText.takeWhile { it.isDigit() }.toIntOrNull()
        } ?: throw IOException("Cannot determine Java runtime version from ${File(runtimeHome, "release").absolutePath}.")

        if (javaMajor !in setOf(8, 17, 21, 25)) {
            throw IOException(
                "The bundled Android AWT toolkit supports Java 8, 17, 21, and 25 only; Java $javaMajor is not supported by this engine build."
            )
        }
        val cacioDirectoryName = if (javaMajor == 8) "caciocavallo" else "caciocavallo17"
        val cacioDirectory = File(minecraftRoot, cacioDirectoryName).canonicalFile
        val root = minecraftRoot.canonicalFile
        if (!cacioDirectory.path.startsWith(root.path + File.separator)) {
            throw IOException("Caciocavallo runtime directory escaped the Minecraft data directory.")
        }
        val cacioJars = cacioDirectory.listFiles().orEmpty()
            .filter { it.isFile && it.name.endsWith(".jar") && it.length() > 0L }
            .sortedBy { it.name }
        val expectedCount = if (javaMajor == 8) 3 else 2
        if (cacioJars.size < expectedCount) {
            // CompatibilityInspector reports the actionable blocker; never silently build an
            // AWT bootclasspath containing a partial component.
            return
        }

        fun addDefault(name: String, value: String) {
            if (args.none { it == name || it.startsWith("$name=") }) args += "$name=$value"
        }
        addDefault("-Djava.awt.headless", "false")
        addDefault("-Dcacio.managed.screensize", "${width}x${height}")
        addDefault("-Dcacio.font.fontmanager", "sun.awt.X11FontManager")
        addDefault("-Dcacio.font.fontscaler", "sun.font.FreetypeFontScaler")
        addDefault("-Dswing.defaultlaf", "javax.swing.plaf.metal.MetalLookAndFeel")

        if (javaMajor == 8) {
            addDefault("-Dawt.toolkit", "net.java.openjdk.cacio.ctc.CTCToolkit")
            addDefault("-Djava.awt.graphicsenv", "net.java.openjdk.cacio.ctc.CTCGraphicsEnvironment")
        } else {
            addDefault("-Dawt.toolkit", "com.github.caciocavallosilano.cacio.ctc.CTCToolkit")
            addDefault("-Djava.awt.graphicsenv", "com.github.caciocavallosilano.cacio.ctc.CTCGraphicsEnvironment")
            // Amethyst uses the cacio-tta javaagent instead of the preload class loader,
            // because the class loader breaks mod loaders.
            val agent = cacioJars.firstOrNull { it.name.contains("cacio-tta") }
            if (agent != null && args.none { it.startsWith("-javaagent:") && it.contains("cacio-tta") }) {
                args += "-javaagent:" + agent.absolutePath
            }

            val moduleArguments = listOf(
                "--add-exports=java.desktop/java.awt=ALL-UNNAMED",
                "--add-exports=java.desktop/java.awt.peer=ALL-UNNAMED",
                "--add-exports=java.desktop/sun.awt.image=ALL-UNNAMED",
                "--add-exports=java.desktop/sun.java2d=ALL-UNNAMED",
                "--add-exports=java.desktop/java.awt.dnd.peer=ALL-UNNAMED",
                "--add-exports=java.desktop/sun.awt=ALL-UNNAMED",
                "--add-exports=java.desktop/sun.awt.event=ALL-UNNAMED",
                "--add-exports=java.desktop/sun.awt.datatransfer=ALL-UNNAMED",
                "--add-exports=java.desktop/sun.font=ALL-UNNAMED",
                "--add-exports=java.base/sun.security.action=ALL-UNNAMED",
                "--add-opens=java.base/java.util=ALL-UNNAMED",
                "--add-opens=java.desktop/java.awt=ALL-UNNAMED",
                "--add-opens=java.desktop/sun.font=ALL-UNNAMED",
                "--add-opens=java.desktop/sun.java2d=ALL-UNNAMED",
                "--add-opens=java.base/java.lang.reflect=ALL-UNNAMED",
                "--add-opens=java.base/java.net=ALL-UNNAMED",
            )
            for (argument in moduleArguments) if (argument !in args) args += argument
        }

        val bootOption = if (javaMajor == 8) "-Xbootclasspath/p:" else "-Xbootclasspath/a:"
        val existingIndex = args.indexOfFirst { it.startsWith(bootOption) }
        val cacioClasspath = cacioJars.joinToString(File.pathSeparator) { it.absolutePath }
        if (existingIndex >= 0) {
            val existing = args[existingIndex].substringAfter(':')
            val merged = LinkedHashSet<String>().apply {
                if (existing.isNotBlank()) addAll(existing.split(File.pathSeparator).filter { it.isNotBlank() })
                addAll(cacioJars.map { it.absolutePath })
            }
            args[existingIndex] = bootOption + merged.joinToString(File.pathSeparator)
        } else {
            args += bootOption + cacioClasspath
        }
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

    private fun containsSharedLibrary(directory: File): Boolean =
        directory.isDirectory && directory.listFiles().orEmpty().any { it.isFile && it.name.endsWith(".so") }

}
