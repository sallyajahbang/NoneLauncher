package com.sally.unilauncher.core.instance

import org.json.JSONArray
import org.json.JSONObject
import java.io.File

class VersionNotFoundException(id: String) : Exception("Versi $id tidak ditemukan")

/**
 * Membaca JSON versi beserta induknya (inheritsFrom), lalu menggabungkannya.
 *
 * Aturan penggabungan (anak selalu menang):
 *  - mainClass dan assetIndex: pakai yang paling dekat dengan anak.
 *  - libraries: digabung, jika ada key yang sama pakai versi dari anak.
 *  - Library yang aturannya tidak cocok dengan OS ini dibuang.
 *
 * Tidak ada unduhan di sini. Hasilnya adalah daftar file yang dibutuhkan.
 *
 * @param osName nama OS untuk mencocokkan rules. Default "linux" karena Android berjalan di kernel Linux.
 */
class VersionResolver(
    private val gameDir: File,
    private val osName: String = "linux",
) {

    private data class Node(val id: String, val json: JSONObject)

    fun resolve(id: String): ResolvedVersion {
        val chain = buildChain(id)

        var mainClass: String? = null
        var assetIndex: AssetIndexRef? = null
        val libraries = LinkedHashMap<String, Library>()

        for (node in chain) {
            if (mainClass == null) mainClass = node.json.optStringOrNull("mainClass")
            if (assetIndex == null) assetIndex = parseAssetIndex(node.json)

            val libs = node.json.optJSONArray("libraries") ?: continue
            for (i in 0 until libs.length()) {
                val lib = parseLibrary(libs.getJSONObject(i)) ?: continue
                if (!libraries.containsKey(lib.key)) libraries[lib.key] = lib
            }
        }

        return ResolvedVersion(
            id = id,
            mainClass = mainClass,
            libraries = libraries.values.toList(),
            assetIndex = assetIndex,
            chain = chain.map { it.id },
        )
    }

    /** Dari anak ke induk. Gagal kalau ada siklus atau rantai terlalu dalam. */
    private fun buildChain(id: String): List<Node> {
        val nodes = mutableListOf<Node>()
        val seen = mutableSetOf<String>()
        var current: String? = id

        while (current != null) {
            if (!seen.add(current)) throw IllegalStateException("inheritsFrom berputar di $current")
            if (nodes.size >= MAX_DEPTH) throw IllegalStateException("rantai inheritsFrom terlalu dalam")

            val file = File(gameDir, "versions/$current/$current.json")
            if (!file.isFile) throw VersionNotFoundException(current)

            val json = JSONObject(file.readText())
            nodes += Node(current, json)
            current = json.optStringOrNull("inheritsFrom")
        }
        return nodes
    }

    private fun parseAssetIndex(json: JSONObject): AssetIndexRef? {
        val ai = json.optJSONObject("assetIndex") ?: return null
        val id = ai.optStringOrNull("id") ?: return null
        return AssetIndexRef(
            id = id,
            url = ai.optNonBlank("url"),
            sha1 = ai.optNonBlank("sha1"),
            size = ai.optLongOrNull("size"),
        )
    }

    private fun parseLibrary(obj: JSONObject): Library? {
        if (!isAllowed(obj.optJSONArray("rules"))) return null

        val name = obj.optStringOrNull("name") ?: return null
        val key = MavenCoordinates.keyOf(name) ?: return null

        val artifact = obj.optJSONObject("downloads")?.optJSONObject("artifact")
        val path = artifact?.optNonBlank("path") ?: MavenCoordinates.pathOf(name) ?: return null

        // Sumber unduhan: url di artifact > base "url" di library > repositori default Mojang.
        val url = artifact?.optNonBlank("url") ?: run {
            val base = obj.optNonBlank("url") ?: DEFAULT_LIBRARY_BASE
            base.trimEnd('/') + "/" + path
        }

        return Library(
            key = key,
            path = path,
            url = url,
            sha1 = artifact?.optNonBlank("sha1"),
            size = artifact?.optLongOrNull("size"),
        )
    }

    /**
     * Logika rules seperti di launcher Minecraft:
     * tanpa rules, library dipakai. Kalau ada rules, mulai dari "tidak", lalu
     * setiap rule yang cocok mengubah keputusan. Rule yang memakai "features"
     * dianggap tidak cocok karena launcher ini belum punya fitur opsional.
     */
    private fun isAllowed(rules: JSONArray?): Boolean {
        if (rules == null || rules.length() == 0) return true

        var allowed = false
        for (i in 0 until rules.length()) {
            val rule = rules.getJSONObject(i)
            if (rule.has("features")) continue

            val os = rule.optJSONObject("os")
            val osMatches = os == null || !os.has("name") || os.getString("name") == osName
            if (osMatches) {
                allowed = rule.optString("action") == "allow"
            }
        }
        return allowed
    }

    private fun JSONObject.optStringOrNull(key: String): String? =
        if (has(key) && !isNull(key)) getString(key) else null

    private fun JSONObject.optNonBlank(key: String): String? =
        optStringOrNull(key)?.takeIf { it.isNotBlank() }

    private fun JSONObject.optLongOrNull(key: String): Long? =
        if (has(key) && !isNull(key)) optLong(key) else null

    companion object {
        private const val MAX_DEPTH = 8
        private const val DEFAULT_LIBRARY_BASE = "https://libraries.minecraft.net"
    }
}
