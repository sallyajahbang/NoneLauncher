package com.sally.launcher.data.storage

import android.content.Context
import java.io.File
import java.io.IOException

/**
 * User-visible app data lives in Android/data/<package>/files. Executable JVM runtimes stay in
 * internal app storage because Android external storage is commonly mounted noexec and can prevent
 * OpenJDK/JLI from launching or mapping executable runtime components.
 */
object LauncherStorage {
    private const val MIGRATION_MARKER = ".legacy-storage-migrated-v1"

    fun accessibleFilesDir(context: Context): File {
        val app = context.applicationContext
        val external = app.getExternalFilesDir(null)
        val root = external ?: app.filesDir
        if (!root.exists() && !root.mkdirs() && !root.isDirectory) {
            throw IOException("Cannot create launcher data directory: ${root.absolutePath}")
        }
        migrateLegacyMinecraftData(app, root)
        return root
    }

    fun minecraftRoot(context: Context): File = File(accessibleFilesDir(context), "minecraft")

    /** Keep the executable runtime private/internal to avoid Android external-storage noexec issues. */
    fun runtimesRoot(context: Context): File = File(File(context.applicationContext.filesDir, "minecraft"), "runtimes")

    fun diagnosticsRoot(context: Context): File = File(accessibleFilesDir(context), "diagnostics")

    private fun migrateLegacyMinecraftData(context: Context, externalFilesRoot: File) {
        val marker = File(externalFilesRoot, MIGRATION_MARKER)
        if (marker.exists()) return
        synchronized(this) {
            if (marker.exists()) return
            val oldMinecraft = File(context.filesDir, "minecraft")
            val newMinecraft = File(externalFilesRoot, "minecraft")
            if (oldMinecraft.isDirectory) {
                oldMinecraft.listFiles().orEmpty()
                    .filterNot { it.name == "runtimes" }
                    .forEach { source -> copyMissing(source, File(newMinecraft, source.name)) }
            }
            runCatching { marker.writeText("migrated") }
        }
    }

    /** Merge without overwriting files already created in the new location. */
    private fun copyMissing(source: File, destination: File) {
        if (destination.exists()) {
            if (source.isDirectory && destination.isDirectory) {
                source.listFiles().orEmpty().forEach { child -> copyMissing(child, File(destination, child.name)) }
            }
            return
        }
        if (source.isDirectory) {
            if (!destination.mkdirs() && !destination.isDirectory) return
            source.listFiles().orEmpty().forEach { child -> copyMissing(child, File(destination, child.name)) }
        } else if (source.isFile) {
            runCatching {
                destination.parentFile?.mkdirs()
                source.copyTo(destination, overwrite = false)
            }
        }
    }
}
