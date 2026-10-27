package com.sally.launcher.core.engine

import android.util.Log
import net.kdt.pojavlaunch.utils.JREUtils
import java.io.File
import java.io.IOException

/**
 * Port of upstream Pojav's JREUtils.loadGraphicsLibrary() + initJavaRuntime(): map the renderer and the
 * JRE's native libraries RTLD_GLOBAL before JLI_Launch. Call on the launch worker thread after
 * JREUtils.setLdLibraryPath(), so bare library names resolve through the runtime linker path.
 */
object PojavRuntimeLoader {
    private const val TAG = "SallyRuntimeLoader"
    const val GL4ES_LIBRARY = "libgl4es_114.so"

    // libjsig must never be loaded globally into an Android process: it hijacks ART's signal handlers.
    // Always use the APK's xawt compatibility shim instead of any host-JDK implementation.
    private val SKIPPED = setOf("libjsig.so", "libawt_xawt.so")

    fun preload(runtimeHome: File, nativeLibraryDir: String?, ldLibraryPath: String) {
        val searchDirs = ldLibraryPath.split(File.pathSeparator).filter { it.isNotBlank() }.map(::File)

        fun find(name: String): String? = searchDirs
            .map { File(it, name) }
            .firstOrNull { it.isFile }
            ?.absolutePath

        fun load(name: String, fallbackDir: String? = null): Boolean {
            val path = find(name)
            if (path != null && dlopen(path)) return true
            if (dlopen(name)) return true
            return fallbackDir != null && dlopen("$fallbackDir/$name")
        }

        // 1. Renderer (GL4ES), as loadGraphicsLibrary() does first. Continuing without it
        // typically produces a much less useful JVM crash later when GLFW creates its context.
        if (!load(GL4ES_LIBRARY, nativeLibraryDir)) {
            throw IOException("Could not load the Android renderer $GL4ES_LIBRARY from ${nativeLibraryDir ?: "the runtime search path"}.")
        }

        // 2. JVM + launcher libraries.
        val libDir = File(runtimeHome, "lib")
        val jvmDir = File(libDir, "server").takeIf { File(it, "libjvm.so").isFile }
            ?: File(libDir, "client").takeIf { File(it, "libjvm.so").isFile }
            ?: libDir
        if (!load("libjli.so")) throw IOException("Could not load libjli.so from the selected Java runtime.")
        if (!load("libjvm.so", jvmDir.absolutePath)) {
            throw IOException("Could not load libjvm.so from ${jvmDir.absolutePath}.")
        }
        for (name in listOf(
            "libverify.so", "libjava.so", "libnet.so", "libnio.so",
            "libawt.so", "libawt_headless.so", "libfreetype.so", "libfontmanager.so",
        )) load(name)

        // 3. Everything else shipped with the JRE.
        for (library in locateLibraries(libDir)) dlopen(library.absolutePath)

        // 4. Pojav's AWT JNI bridge and the X11-free xawt compatibility shim from the APK.
        if (nativeLibraryDir != null) {
            for (name in listOf("libpojavexec_awt.so", "libawt_xawt.so")) {
                if (!dlopen("$nativeLibraryDir/$name")) {
                    throw IOException("Could not load required AWT compatibility library $name from $nativeLibraryDir.")
                }
            }
            if (!dlopen("$nativeLibraryDir/libopenal.so")) {
                Log.w(TAG, "OpenAL could not be preloaded; games using audio may fail when sound initializes.")
            }
        }
    }

    private fun locateLibraries(root: File): List<File> {
        val found = ArrayList<File>()
        fun visit(directory: File, depth: Int) {
            if (depth > 6) return
            for (child in directory.listFiles().orEmpty().sortedBy { it.name }) {
                when {
                    child.isDirectory -> visit(child, depth + 1)
                    child.isFile && child.name.endsWith(".so") && child.name !in SKIPPED -> found += child
                }
            }
        }
        if (root.isDirectory) visit(root, 0)
        return found
    }

    private fun dlopen(path: String): Boolean = try {
        JREUtils.dlopen(path)
    } catch (error: Throwable) {
        Log.w(TAG, "dlopen failed for $path: ${error.message}")
        false
    }
}
