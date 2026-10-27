package com.sally.launcher.data.skin

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.sally.launcher.data.storage.LauncherStorage
import java.io.File
import java.util.zip.ZipFile

/** The two default player models. Steve uses wide (4px) arms, Alex uses slim (3px) arms. */
enum class SkinModel(val label: String, val slim: Boolean) {
    STEVE("Steve", slim = false),
    ALEX("Alex", slim = true);

    companion object {
        fun fromKey(key: String?): SkinModel = entries.firstOrNull { it.name.equals(key, ignoreCase = true) } ?: STEVE
    }
}

/**
 * Reads the default Steve or Alex texture from an installed Minecraft client JAR.
 * The texture ships inside the client, so this needs no network access and no account token.
 */
object SkinProvider {
    private val textures = mapOf(
        SkinModel.STEVE to listOf(
            "assets/minecraft/textures/entity/player/wide/steve.png",
            "assets/minecraft/textures/entity/steve.png",
        ),
        SkinModel.ALEX to listOf(
            "assets/minecraft/textures/entity/player/slim/alex.png",
            "assets/minecraft/textures/entity/alex.png",
        ),
    )
    private val cache = HashMap<SkinModel, Bitmap>()

    /** Returns the texture from the most recently installed client, or null when no client JAR exists yet. */
    @Synchronized
    fun load(context: Context, model: SkinModel): Bitmap? {
        cache[model]?.let { return it }
        val versionsDir = File(LauncherStorage.minecraftRoot(context), "versions")
        val jars = versionsDir.listFiles().orEmpty()
            .filter { it.isDirectory }
            .map { File(it, "${it.name}.jar") }
            .filter { it.isFile && it.length() > 0L }
            .sortedByDescending { it.lastModified() }
        for (jar in jars) {
            val bitmap = runCatching { readTexture(jar, textures.getValue(model)) }.getOrNull() ?: continue
            cache[model] = bitmap
            return bitmap
        }
        return null
    }

    private fun readTexture(jar: File, entryNames: List<String>): Bitmap? =
        ZipFile(jar).use { zip ->
            entryNames.firstNotNullOfOrNull { name ->
                zip.getEntry(name)?.let { entry ->
                    zip.getInputStream(entry).use { BitmapFactory.decodeStream(it) }
                }
            }
        }
}
