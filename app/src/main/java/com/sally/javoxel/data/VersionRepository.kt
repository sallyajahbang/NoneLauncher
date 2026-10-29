package com.sally.javoxel.data

import org.json.JSONArray
import org.json.JSONObject

private const val MANIFEST_URL = "https://piston-meta.mojang.com/mc/game/version_manifest_v2.json"

data class ClientDownload(val url: String, val sha1: String, val size: Long)

data class Library(val path: String, val url: String, val sha1: String?)

data class AssetIndexRef(val id: String, val url: String, val sha1: String?)

data class VersionMeta(
    val id: String,
    val mainClass: String,
    val client: ClientDownload,
    val libraries: List<Library>,
    val assetIndex: AssetIndexRef,
    /** Java major dari field javaVersion; null jika versi lama tidak mencantumkannya. */
    val javaMajor: Int?,
    /** JSON mentah versi, disimpan apa adanya untuk launcher nanti. */
    val raw: String,
)

object VersionRepository {

    fun findVersionUrl(id: String): String? {
        val versions = JSONObject(Downloader.fetchText(MANIFEST_URL)).getJSONArray("versions")
        for (i in 0 until versions.length()) {
            val entry = versions.getJSONObject(i)
            if (entry.getString("id") == id) return entry.getString("url")
        }
        return null
    }

    fun loadMeta(id: String): VersionMeta {
        val url = findVersionUrl(id) ?: error("Versi $id tidak ada di manifest Mojang")
        val raw = Downloader.fetchText(url)
        val json = JSONObject(raw)

        val client = json.getJSONObject("downloads").getJSONObject("client")
        val assetIndex = json.getJSONObject("assetIndex")

        return VersionMeta(
            id = json.getString("id"),
            mainClass = json.getString("mainClass"),
            client = ClientDownload(
                url = client.getString("url"),
                sha1 = client.getString("sha1"),
                size = client.optLong("size", 0L),
            ),
            libraries = parseLibraries(json.getJSONArray("libraries")),
            assetIndex = AssetIndexRef(
                id = assetIndex.getString("id"),
                url = assetIndex.getString("url"),
                sha1 = assetIndex.optString("sha1").ifEmpty { null },
            ),
            javaMajor = json.optJSONObject("javaVersion")
                ?.optInt("majorVersion", 0)
                ?.takeIf { it > 0 },
            raw = raw,
        )
    }

    private fun parseLibraries(libs: JSONArray): List<Library> {
        val result = mutableListOf<Library>()
        for (i in 0 until libs.length()) {
            val lib = libs.getJSONObject(i)
            if (!isAllowed(lib.optJSONArray("rules"))) continue

            // Hanya artifact utama. Natives per-classifier belum ditangani.
            val artifact = lib.optJSONObject("downloads")?.optJSONObject("artifact") ?: continue
            result += Library(
                path = artifact.getString("path"),
                url = artifact.getString("url"),
                sha1 = artifact.optString("sha1").ifEmpty { null },
            )
        }
        return result
    }

    /**
     * Evaluasi rules Mojang. Default ditolak, aturan terakhir yang cocok menang.
     * Aturan dengan kondisi "os" (linux, windows, osx) atau "features" tidak dianggap cocok,
     * karena Android bukan target OS yang dikenali di rules Mojang.
     */
    private fun isAllowed(rules: JSONArray?): Boolean {
        if (rules == null) return true
        var allowed = false
        for (i in 0 until rules.length()) {
            val rule = rules.getJSONObject(i)
            val matches = rule.optJSONObject("os") == null && !rule.has("features")
            if (matches) allowed = rule.getString("action") == "allow"
        }
        return allowed
    }
}
