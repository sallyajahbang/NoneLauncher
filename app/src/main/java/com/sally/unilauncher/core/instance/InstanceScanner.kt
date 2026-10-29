package com.sally.unilauncher.core.instance

import org.json.JSONObject
import java.io.File

/**
 * Mencari semua versi terpasang di folder game.
 *
 * Struktur yang diharapkan (sama seperti launcher Minecraft pada umumnya):
 *   <gameDir>/versions/<id>/<id>.json
 *
 * Folder yang JSON-nya tidak ada atau rusak dilewati, bukan menggagalkan seluruh daftar.
 */
class InstanceScanner(private val gameDir: File) {

    fun scan(): List<Instance> {
        val versionsDir = File(gameDir, "versions")
        val folders = versionsDir.listFiles { file -> file.isDirectory } ?: return emptyList()

        return folders
            .mapNotNull { folder -> parse(folder) }
            .sortedBy { it.id.lowercase() }
    }

    private fun parse(folder: File): Instance? {
        val id = folder.name
        val jsonFile = File(folder, "$id.json")
        if (!jsonFile.isFile) return null

        return try {
            val json = JSONObject(jsonFile.readText())
            Instance(
                id = id,
                type = json.optStringOrNull("type"),
                mainClass = json.optStringOrNull("mainClass"),
                inheritsFrom = json.optStringOrNull("inheritsFrom"),
                jsonFile = jsonFile,
            )
        } catch (e: Exception) {
            null
        }
    }

    private fun JSONObject.optStringOrNull(key: String): String? =
        if (has(key) && !isNull(key)) getString(key) else null
}
