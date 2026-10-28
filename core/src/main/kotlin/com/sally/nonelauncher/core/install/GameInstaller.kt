package com.sally.nonelauncher.core.install

import com.sally.nonelauncher.core.LauncherPaths
import com.sally.nonelauncher.core.download.Downloader
import com.sally.nonelauncher.core.launch.InstalledVersion
import com.sally.nonelauncher.core.model.RuntimeContext
import com.sally.nonelauncher.core.mojang.LibraryResolver
import com.sally.nonelauncher.core.mojang.ManifestVersion
import com.sally.nonelauncher.core.mojang.VersionMeta
import com.sally.nonelauncher.core.mojang.VersionMetaParser
import com.sally.nonelauncher.core.util.LauncherJson
import com.sally.nonelauncher.core.util.long
import com.sally.nonelauncher.core.util.obj
import com.sally.nonelauncher.core.util.str
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.jsonObject
import java.io.File
import java.util.zip.ZipInputStream

/**
 * Downloads everything needed to start a Minecraft version:
 * version json, client jar, libraries, natives, and assets.
 *
 * Already-valid files are skipped, so re-running install is cheap.
 */
class GameInstaller(
    private val paths: LauncherPaths,
    private val downloader: Downloader,
    private val platform: RuntimeContext,
    private val log: (String) -> Unit,
    private val assetBaseUrl: String,
) {
    companion object {
        const val ASSET_BASE_URL = "https://resources.download.minecraft.net/"
    }

    suspend fun install(entry: ManifestVersion): InstalledVersion {
        log("Reading metadata for ${entry.id}")
        val json = downloader.fetchText(entry.url)
        paths.versionJson(entry.id).writeText(json)
        val meta = VersionMetaParser.parse(json)
        return installFromMeta(meta)
    }

    suspend fun installFromMeta(meta: VersionMeta): InstalledVersion = coroutineScope {
        val id = meta.id

        log("Client jar")
        downloader.ensureFile(meta.clientJar.url, paths.clientJar(id), meta.clientJar.sha1)

        log("Libraries")
        val libs = LibraryResolver.resolve(meta.libraries, platform)
        // awaitAll keeps declaration order, which the classpath depends on.
        val files = libs.map { lib ->
            async {
                val file = File(paths.libraries, lib.relativePath)
                downloader.ensureFile(lib.url, file, lib.sha1)
                lib to file
            }
        }.awaitAll()

        val classpath = files.filter { !it.first.isNative }.map { it.second }
        val nativesDir = paths.nativesDir(id)
        files.filter { it.first.isNative }.forEach { extractNatives(it.second, nativesDir) }

        log("Asset index ${meta.assetIndex.id}")
        val indexFile = paths.assetIndexFile(meta.assetIndex.id)
        downloader.ensureFile(meta.assetIndex.url, indexFile, meta.assetIndex.sha1)
        val objects = parseAssetObjects(indexFile)

        log("Assets (${objects.size})")
        objects.map { (_, obj) ->
            async {
                val target = paths.assetObjectFile(obj.hash)
                downloader.ensureFile(assetBaseUrl + obj.hash.take(2) + "/" + obj.hash, target, obj.hash)
            }
        }.awaitAll()

        log("Install complete: $id")
        InstalledVersion(
            meta = meta,
            clientJar = paths.clientJar(id),
            libraryClasspath = classpath.map { it.absolutePath },
            nativesDir = nativesDir,
        )
    }

    /** Extracts a native jar into [dest], skipping META-INF as vanilla does. */
    private fun extractNatives(jar: File, dest: File) {
        ZipInputStream(jar.inputStream().buffered()).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                val name = entry.name
                if (!entry.isDirectory && !name.startsWith("META-INF/")) {
                    val out = File(dest, File(name).name)
                    out.outputStream().use { zip.copyTo(it) }
                }
                zip.closeEntry()
                entry = zip.nextEntry
            }
        }
    }

    private data class AssetObject(val hash: String, val size: Long?)

    private fun parseAssetObjects(indexFile: File): Map<String, AssetObject> {
        val root = LauncherJson.parseToJsonElement(indexFile.readText()).jsonObject
        val objects = root.obj("objects") ?: return emptyMap()
        return objects.keys.associateWith { name ->
            val o = objects.obj(name)!!
            AssetObject(hash = requireNotNull(o.str("hash")), size = o.long("size"))
        }
    }
}
