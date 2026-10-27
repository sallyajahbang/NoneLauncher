package com.sally.launcher.core.engine

import android.content.Context
import java.io.File
import java.io.IOException
import java.security.MessageDigest

/** Installs the patched GLFW classes required by the matching Android LWJGL native payload. */
class PojavEnginePayloadInstaller(context: Context) {
    private val appContext = context.applicationContext

    data class PayloadReport(
        val classesJar: File,
        val classesHashMatches: Boolean,
        val presentNativeLibraries: List<String>,
        val missingNativeLibraries: List<String>,
    ) {
        val ready: Boolean get() = classesHashMatches && missingNativeLibraries.isEmpty()
    }

    fun installPatchedGlfwClasses(minecraftRoot: File): File {
        val root = minecraftRoot.canonicalFile
        val engineDir = File(root, "engine/pojav").canonicalFile
        if (!engineDir.path.startsWith(root.path + File.separator)) {
            throw IOException("Pojav engine path escaped the Minecraft data directory.")
        }
        if (!engineDir.isDirectory && !engineDir.mkdirs() && !engineDir.isDirectory) {
            throw IOException("Cannot create Pojav engine directory: ${engineDir.absolutePath}")
        }
        val target = File(engineDir, GLFW_CLASSES_NAME)
        if (target.isFile && target.length() > 0L && sha256(target) == GLFW_CLASSES_SHA256) return target

        val temporary = File(engineDir, "$GLFW_CLASSES_NAME.tmp")
        try {
            appContext.assets.open("engine/pojav/$GLFW_CLASSES_NAME").use { input ->
                temporary.outputStream().use { output -> input.copyTo(output) }
            }
            val actualHash = sha256(temporary)
            if (actualHash != GLFW_CLASSES_SHA256) {
                throw IOException("Bundled patched GLFW classes failed SHA-256 validation.")
            }
            if (target.exists() && !target.delete()) {
                throw IOException("Cannot replace old patched GLFW classes file.")
            }
            if (!temporary.renameTo(target)) {
                // File.renameTo may not support an atomic replacement across filesystems.
                temporary.copyTo(target, overwrite = true)
                if (!temporary.delete()) temporary.deleteOnExit()
            }
            return target
        } finally {
            if (temporary.exists()) temporary.delete()
        }
    }

    fun inspect(minecraftRoot: File): PayloadReport {
        val classesJar = File(minecraftRoot, "engine/pojav/$GLFW_CLASSES_NAME")
        val hashMatches = classesJar.isFile && runCatching { sha256(classesJar) == GLFW_CLASSES_SHA256 }.getOrDefault(false)
        val nativeDir = File(appContext.applicationInfo.nativeLibraryDir)
        val present = REQUIRED_NATIVE_LIBRARIES.filter { File(nativeDir, it).isFile }
        val missing = REQUIRED_NATIVE_LIBRARIES - present.toSet()
        return PayloadReport(classesJar, hashMatches, present, missing)
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().buffered().use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    companion object {
        const val GLFW_CLASSES_NAME = "lwjgl-glfw-classes.jar"
        const val GLFW_CLASSES_SHA256 = "1bf89a90cbcc0bb4bd07511afc92e8345ff221d2a7ab1a04f2fcbc1bd09a3ffd"
        val REQUIRED_NATIVE_LIBRARIES = listOf(
            "libpojavexec.so",
            "liblwjgl.so",
            "liblwjgl_opengl.so",
            "liblwjgl_stb.so",
            "libopenal.so",
            "libgl4es_114.so",
        )
    }
}
