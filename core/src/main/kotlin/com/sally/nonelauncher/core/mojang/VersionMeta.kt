package com.sally.nonelauncher.core.mojang

import com.sally.nonelauncher.core.util.LauncherJson
import com.sally.nonelauncher.core.util.array
import com.sally.nonelauncher.core.util.int
import com.sally.nonelauncher.core.util.long
import com.sally.nonelauncher.core.util.obj
import com.sally.nonelauncher.core.util.str
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** Entry in Mojang's `version_manifest_v2.json`. */
data class ManifestVersion(
    val id: String,
    val type: String,
    val url: String,
    val sha1: String? = null,
    val releaseTime: String? = null,
)

data class Latest(val release: String, val snapshot: String)

data class VersionManifest(
    val latest: Latest,
    val versions: List<ManifestVersion>,
) {
    companion object {
        /** Parses `version_manifest_v2.json`. */
        fun parse(json: String): VersionManifest {
            val root = LauncherJson.parseToJsonElement(json).jsonObject
            val latest = requireNotNull(root.obj("latest")) { "manifest has no latest" }
            val versions = root.array("versions")?.mapNotNull { element ->
                val v = element as? JsonObject ?: return@mapNotNull null
                ManifestVersion(
                    id = requireNotNull(v.str("id")),
                    type = v.str("type") ?: "unknown",
                    url = requireNotNull(v.str("url")),
                    sha1 = v.str("sha1"),
                    releaseTime = v.str("releaseTime"),
                )
            } ?: emptyList()
            return VersionManifest(
                latest = Latest(
                    release = requireNotNull(latest.str("release")),
                    snapshot = requireNotNull(latest.str("snapshot")),
                ),
                versions = versions,
            )
        }
    }
}

/** A downloadable file as described in version metadata. */
data class DownloadArtifact(
    val path: String?,
    val url: String,
    val sha1: String?,
    val size: Long?,
)

data class AssetIndexRef(
    val id: String,
    val url: String,
    val sha1: String?,
    val size: Long?,
)

/** One entry of `arguments.game` / `arguments.jvm`: a value, possibly gated by rules. */
data class ArgumentEntry(
    val values: List<String>,
    val rules: JsonArray?,
)

data class LibrarySpec(
    /** Maven coordinate, e.g. `org.lwjgl:lwjgl:3.3.3:natives-linux`. */
    val name: String,
    val artifact: DownloadArtifact?,
    /** OS name -> classifier key, e.g. `"linux" -> "natives-linux"`. */
    val natives: Map<String, String>,
    /** Classifier key -> artifact, used together with [natives]. */
    val classifiers: Map<String, DownloadArtifact>,
    val rules: JsonArray?,
    /** Maven base URL for libraries that ship without a `downloads` block. */
    val baseUrl: String?,
)

data class VersionMeta(
    val id: String,
    val type: String,
    val mainClass: String,
    val assetIndex: AssetIndexRef,
    val clientJar: DownloadArtifact,
    val libraries: List<LibrarySpec>,
    val javaMajorVersion: Int,
    val jvmArguments: List<ArgumentEntry>,
    val gameArguments: List<ArgumentEntry>,
    /** Pre-1.13 style single string of game arguments, if present. */
    val legacyGameArguments: String?,
)

/** Parses a version JSON document (the one referenced by [ManifestVersion.url]). */
object VersionMetaParser {

    fun parse(json: String): VersionMeta {
        val root = LauncherJson.parseToJsonElement(json).jsonObject

        val id = requireNotNull(root.str("id")) { "version json has no id" }
        val assetIndexObj = requireNotNull(root.obj("assetIndex")) { "version json has no assetIndex" }
        val clientObj = requireNotNull(root.obj("downloads")?.obj("client")) {
            "version json has no client download"
        }

        val arguments = root.obj("arguments")
        return VersionMeta(
            id = id,
            type = root.str("type") ?: "release",
            mainClass = requireNotNull(root.str("mainClass")) { "version json has no mainClass" },
            assetIndex = AssetIndexRef(
                id = requireNotNull(assetIndexObj.str("id")),
                url = requireNotNull(assetIndexObj.str("url")),
                sha1 = assetIndexObj.str("sha1"),
                size = assetIndexObj.long("size"),
            ),
            clientJar = parseArtifact(clientObj),
            libraries = root.array("libraries")?.mapNotNull { parseLibrary(it as? JsonObject) } ?: emptyList(),
            javaMajorVersion = root.obj("javaVersion")?.int("majorVersion") ?: 8,
            jvmArguments = arguments?.array("jvm")?.let(::parseArgumentList) ?: emptyList(),
            gameArguments = arguments?.array("game")?.let(::parseArgumentList) ?: emptyList(),
            legacyGameArguments = root.str("minecraftArguments"),
        )
    }

    private fun parseArtifact(obj: JsonObject): DownloadArtifact = DownloadArtifact(
        path = obj.str("path"),
        url = requireNotNull(obj.str("url")) { "download entry has no url" },
        sha1 = obj.str("sha1"),
        size = obj.long("size"),
    )

    private fun parseArgumentList(array: JsonArray): List<ArgumentEntry> =
        array.mapNotNull { element ->
            when (element) {
                is JsonPrimitive -> ArgumentEntry(values = listOf(element.content), rules = null)
                is JsonObject -> {
                    val value = element["value"]
                    val values = when (value) {
                        is JsonArray -> value.map { it.jsonPrimitive.content }
                        is JsonPrimitive -> listOf(value.content)
                        else -> emptyList()
                    }
                    ArgumentEntry(values = values, rules = element.array("rules"))
                }
                else -> null
            }
        }

    private fun parseLibrary(obj: JsonObject?): LibrarySpec? {
        if (obj == null) return null
        val name = obj.str("name") ?: return null
        val downloads = obj.obj("downloads")

        val natives = obj.obj("natives")?.let { nat ->
            nat.keys.associateWith { key -> nat.str(key) ?: "" }.filterValues { it.isNotEmpty() }
        } ?: emptyMap()

        val classifiers = downloads?.obj("classifiers")?.let { cls ->
            cls.keys.associateWith { key -> parseArtifact(cls.obj(key)!!) }
        } ?: emptyMap()

        return LibrarySpec(
            name = name,
            artifact = downloads?.obj("artifact")?.let(::parseArtifact),
            natives = natives,
            classifiers = classifiers,
            rules = obj.array("rules"),
            baseUrl = obj.str("url"),
        )
    }
}
