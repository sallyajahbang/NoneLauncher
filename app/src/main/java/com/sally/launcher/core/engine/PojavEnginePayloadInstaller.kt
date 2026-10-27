package com.sally.launcher.core.engine

import android.content.Context
import java.io.File
import java.io.IOException
import java.security.MessageDigest

/** Installs and verifies the patched GLFW and Caciocavallo payloads shipped with this engine. */
class PojavEnginePayloadInstaller(context: Context) {
    private val appContext = context.applicationContext

    data class PayloadReport(
        val classesJar: File,
        val classesHashMatches: Boolean,
        val presentNativeLibraries: List<String>,
        val missingNativeLibraries: List<String>,
        val presentComponents: List<String>,
        val missingComponents: List<String>,
    ) {
        val ready: Boolean
            get() = classesHashMatches && missingNativeLibraries.isEmpty() && missingComponents.isEmpty()
    }

    fun installPatchedGlfwClasses(minecraftRoot: File): File {
        val root = minecraftRoot.canonicalFile
        val engineDir = childInside(root, "engine/pojav")
        ensureDirectory(engineDir, "Pojav engine directory")
        val target = File(engineDir, GLFW_CLASSES_NAME)
        if (target.isFile && target.length() > 0L && sha256(target) == GLFW_CLASSES_SHA256) return target

        val temporary = File(engineDir, "$GLFW_CLASSES_NAME.tmp")
        try {
            appContext.assets.open("engine/pojav/$GLFW_CLASSES_NAME").use { input ->
                temporary.outputStream().buffered().use { output -> input.copyTo(output) }
            }
            val actualHash = sha256(temporary)
            if (actualHash != GLFW_CLASSES_SHA256) {
                throw IOException("Bundled patched GLFW classes failed SHA-256 validation.")
            }
            replaceFile(temporary, target, "patched GLFW classes")
            return target
        } finally {
            if (temporary.exists()) temporary.delete()
        }
    }

    /**
     * Extracts the upstream AWT toolkit components to the directory layout expected by Pojav's
     * -Xbootclasspath arguments. The launcher ships both Java 8 and Java 9+ variants because
     * their class/package names differ.
     */
    fun installCaciocavalloComponents(minecraftRoot: File): List<File> {
        val root = minecraftRoot.canonicalFile
        ensureDirectory(root, "Minecraft data directory")
        return CACIO_COMPONENTS.map { component -> installComponent(root, component) }
    }

    fun inspect(minecraftRoot: File): PayloadReport {
        val root = runCatching { minecraftRoot.canonicalFile }.getOrElse { minecraftRoot.absoluteFile }
        val classesJar = File(root, "engine/pojav/$GLFW_CLASSES_NAME")
        val hashMatches = classesJar.isFile &&
            runCatching { sha256(classesJar) == GLFW_CLASSES_SHA256 }.getOrDefault(false)
        val nativeDir = File(appContext.applicationInfo.nativeLibraryDir)
        val present = REQUIRED_NATIVE_LIBRARIES.filter { File(nativeDir, it).isFile }
        val missing = REQUIRED_NATIVE_LIBRARIES - present.toSet()
        val presentComponents = CACIO_COMPONENTS.filter { component -> componentReady(root, component) }
        val missingComponents = CACIO_COMPONENTS - presentComponents.toSet()
        return PayloadReport(classesJar, hashMatches, present, missing, presentComponents, missingComponents)
    }

    private fun installComponent(root: File, component: String): File {
        val assetDir = "$COMPONENT_ASSET_ROOT/$component"
        val target = childInside(root, component)
        val expectedVersion = appContext.assets.open("$assetDir/version").bufferedReader().use { it.readText().trim() }
        if (componentReady(root, component) &&
            runCatching { File(target, "version").readText().trim() == expectedVersion }.getOrDefault(false)
        ) return target

        val staging = childInside(root, ".$component.installing")
        staging.deleteRecursively()
        ensureDirectory(staging, "Staging directory for $component")
        try {
            val children = appContext.assets.list(assetDir).orEmpty()
            if (children.isEmpty()) throw IOException("Bundled component $component is empty.")
            var copiedJarCount = 0
            for (name in children) {
                // Components are flat directories containing only a version marker and JAR files.
                if (name != "version" && !name.endsWith(".jar")) continue
                val destination = File(staging, name)
                appContext.assets.open("$assetDir/$name").use { input ->
                    destination.outputStream().buffered().use { output -> input.copyTo(output) }
                }
                if (name.endsWith(".jar")) {
                    if (destination.length() <= 0L) throw IOException("Bundled component file $component/$name is empty.")
                    copiedJarCount++
                }
            }
            if (copiedJarCount == 0 || !File(staging, "version").isFile) {
                throw IOException("Bundled component $component is missing its version marker or JAR files.")
            }
            if (target.exists() && !target.deleteRecursively()) {
                throw IOException("Cannot replace existing $component component directory.")
            }
            if (!staging.renameTo(target)) {
                copyDirectory(staging, target)
                if (!staging.deleteRecursively()) staging.deleteOnExit()
            }
            if (!componentReady(root, component)) throw IOException("Installed $component component failed validation.")
            return target
        } finally {
            if (staging.exists()) staging.deleteRecursively()
        }
    }

    private fun componentReady(root: File, component: String): Boolean {
        val dir = runCatching { childInside(root, component) }.getOrNull() ?: return false
        if (!dir.isDirectory || !File(dir, "version").isFile) return false
        val expectedVersion = runCatching {
            appContext.assets.open("$COMPONENT_ASSET_ROOT/$component/version").bufferedReader().use { it.readText().trim() }
        }.getOrNull() ?: return false
        if (runCatching { File(dir, "version").readText().trim() != expectedVersion }.getOrDefault(true)) return false
        val requiredJars = when (component) {
            "caciocavallo" -> listOf("cacio-androidnw-1.10-SNAPSHOT.jar", "cacio-shared-1.10-SNAPSHOT.jar", "ResConfHack.jar")
            "caciocavallo17" -> listOf("cacio-tta-1.18-SNAPSHOT.jar", "cacio-shared-1.18-SNAPSHOT.jar")
            else -> return false
        }
        return requiredJars.all { File(dir, it).let { file -> file.isFile && file.length() > 0L } }
    }

    private fun childInside(root: File, relativePath: String): File {
        val candidate = File(root, relativePath).canonicalFile
        if (!candidate.path.startsWith(root.canonicalPath + File.separator)) {
            throw IOException("Pojav engine path escaped the Minecraft data directory.")
        }
        return candidate
    }

    private fun ensureDirectory(directory: File, label: String) {
        if (!directory.isDirectory && !directory.mkdirs() && !directory.isDirectory) {
            throw IOException("Cannot create $label: ${directory.absolutePath}")
        }
    }

    private fun replaceFile(source: File, target: File, label: String) {
        if (target.exists() && !target.delete()) throw IOException("Cannot replace old $label file.")
        if (!source.renameTo(target)) {
            source.copyTo(target, overwrite = true)
            if (!source.delete()) source.deleteOnExit()
        }
    }

    private fun copyDirectory(source: File, target: File) {
        ensureDirectory(target, "replacement component directory")
        for (entry in source.listFiles().orEmpty()) {
            val output = File(target, entry.name)
            if (entry.isDirectory) copyDirectory(entry, output)
            else entry.copyTo(output, overwrite = true)
        }
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
        private const val COMPONENT_ASSET_ROOT = "engine/components"
        private val CACIO_COMPONENTS = listOf("caciocavallo", "caciocavallo17")

        val REQUIRED_NATIVE_LIBRARIES = listOf(
            "libpojavexec.so",
            "libexithook.so",
            "libpojavexec_awt.so",
            "libbytehook.so",
            "libawt_xawt.so",
            "liblwjgl.so",
            "liblwjgl_opengl.so",
            "liblwjgl_stb.so",
            "libopenal.so",
            "libgl4es_114.so",
        )
    }
}
