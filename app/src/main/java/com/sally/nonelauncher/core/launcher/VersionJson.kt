// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 Buntuk

package com.sally.nonelauncher.core.launcher

import org.json.JSONArray
import org.json.JSONObject

/** Alamat unduh dan hash untuk satu file. */
data class Download(val url: String, val sha1: String, val size: Long)

/** File yang diunduh dengan path relatif terhadap folder libraries. */
data class Artifact(val path: String, val download: Download)

/** Satu aturan dari version.json, misalnya os.name == linux atau feature demo_user. */
data class Rule(
    val allow: Boolean,
    val osName: String?,
    val osArch: String?,
    val features: Map<String, Boolean>,
)

data class Library(
    val name: String,
    val artifact: Artifact?,
    val classifiers: Map<String, Artifact>,
    val natives: Map<String, String>,
    val rules: List<Rule>,
)

/** Satu entri argumen. Nilai bisa berupa banyak string, dan hanya berlaku jika rules lolos. */
data class ArgEntry(val values: List<String>, val rules: List<Rule>)

data class AssetIndexRef(val id: String, val download: Download)

data class VersionJson(
    val id: String,
    val mainClass: String,
    val assetIndex: AssetIndexRef,
    val client: Download,
    val libraries: List<Library>,
    val jvmArgs: List<ArgEntry>,
    val gameArgs: List<ArgEntry>,
    /** Format lama (1.12 ke bawah) memakai satu string minecraftArguments. */
    val legacyGameArgs: String?,
)

object VersionJsonParser {

    private const val DEFAULT_LIBRARY_BASE = "https://libraries.minecraft.net/"

    fun parse(text: String): VersionJson {
        val o = JSONObject(text)
        val downloads = o.getJSONObject("downloads")
        val assetIdx = o.getJSONObject("assetIndex")
        val (jvm, game) = parseArguments(o)

        return VersionJson(
            id = o.getString("id"),
            mainClass = o.getString("mainClass"),
            assetIndex = AssetIndexRef(assetIdx.getString("id"), assetIdx.toDownload()),
            client = downloads.getJSONObject("client").toDownload(),
            libraries = o.getJSONArray("libraries").mapObjects { parseLibrary(it) },
            jvmArgs = jvm,
            gameArgs = game,
            legacyGameArgs = if (o.has("minecraftArguments")) o.getString("minecraftArguments") else null,
        )
    }

    private fun parseLibrary(o: JSONObject): Library {
        val name = o.getString("name")
        val downloads = o.optJSONObject("downloads")

        val artifact = downloads?.optJSONObject("artifact")?.toArtifact()
            ?: if (downloads == null) mavenArtifact(o, name) else null

        val classifiers = downloads?.optJSONObject("classifiers")?.let { c ->
            c.keys().asSequence().associateWith { c.getJSONObject(it).toArtifact() }
        } ?: emptyMap()

        val natives = o.optJSONObject("natives")?.let { n ->
            n.keys().asSequence().associateWith { n.getString(it) }
        } ?: emptyMap()

        return Library(
            name = name,
            artifact = artifact,
            classifiers = classifiers,
            natives = natives,
            rules = parseRules(o.optJSONArray("rules")),
        )
    }

    /** Library lama tanpa blok downloads: path diturunkan dari nama Maven. */
    private fun mavenArtifact(o: JSONObject, name: String): Artifact {
        val path = MavenCoords.path(name)
        val base = o.optString("url", DEFAULT_LIBRARY_BASE)
        val sha1 = o.optString("sha1", "")
        return Artifact(path, Download(base + path, sha1, o.optLong("size", 0L)))
    }

    private fun parseRules(arr: JSONArray?): List<Rule> {
        if (arr == null) return emptyList()
        return arr.mapObjects { r ->
            val os = r.optJSONObject("os")
            val features = r.optJSONObject("features")?.let { f ->
                f.keys().asSequence().associateWith { f.getBoolean(it) }
            } ?: emptyMap()
            Rule(
                allow = r.getString("action") == "allow",
                osName = os?.optString("name")?.takeIf { it.isNotEmpty() },
                osArch = os?.optString("arch")?.takeIf { it.isNotEmpty() },
                features = features,
            )
        }
    }

    private fun parseArguments(o: JSONObject): Pair<List<ArgEntry>, List<ArgEntry>> {
        val args = o.optJSONObject("arguments") ?: return emptyList<ArgEntry>() to emptyList()
        return parseArgList(args.optJSONArray("jvm")) to parseArgList(args.optJSONArray("game"))
    }

    private fun parseArgList(arr: JSONArray?): List<ArgEntry> {
        if (arr == null) return emptyList()
        return (0 until arr.length()).map { i ->
            when (val item = arr.get(i)) {
                is String -> ArgEntry(listOf(item), emptyList())
                is JSONObject -> {
                    val v = item.get("value")
                    val values = if (v is JSONArray) {
                        (0 until v.length()).map { v.getString(it) }
                    } else {
                        listOf(v as String)
                    }
                    ArgEntry(values, parseRules(item.optJSONArray("rules")))
                }
                else -> ArgEntry(emptyList(), emptyList())
            }
        }
    }

    private fun JSONObject.toDownload(): Download =
        Download(getString("url"), getString("sha1"), getLong("size"))

    private fun JSONObject.toArtifact(): Artifact =
        Artifact(getString("path"), toDownload())

    private inline fun <T> JSONArray.mapObjects(transform: (JSONObject) -> T): List<T> =
        (0 until length()).map { transform(getJSONObject(it)) }
}

/** Mengubah nama Maven (group:artifact:version[:classifier]) menjadi path relatif. */
object MavenCoords {
    fun path(name: String): String {
        val parts = name.split(":")
        val group = parts[0].replace('.', '/')
        val artifact = parts[1]
        val version = parts[2]
        val classifier = parts.getOrNull(3)?.let { "-$it" } ?: ""
        return "$group/$artifact/$version/$artifact-$version$classifier.jar"
    }
}
