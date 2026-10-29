package com.sally.javoxel.data

import java.io.File
import org.json.JSONObject

/**
 * Mengunduh satu versi Minecraft ke [baseDir]:
 *  - versions/<id>/<id>.json dan <id>.jar
 *  - libraries/...
 *  - assets/indexes/<id>.json dan assets/objects/xx/<hash>
 *
 * Harus dipanggil dari thread IO.
 */
class MinecraftInstaller(
    private val baseDir: File,
    private val log: (String) -> Unit,
) {

    fun install(versionId: String): VersionMeta {
        val meta = VersionRepository.loadMeta(versionId)
        log("Versi ${meta.id}: main class ${meta.mainClass}, Java ${meta.javaMajor ?: "tidak disebutkan"}")

        val versionDir = File(baseDir, "versions/${meta.id}")
        versionDir.mkdirs()
        File(versionDir, "${meta.id}.json").writeText(meta.raw)

        log("Unduh client.jar...")
        Downloader.downloadFile(
            url = meta.client.url,
            target = File(versionDir, "${meta.id}.jar"),
            sha1 = meta.client.sha1,
        )

        val libDir = File(baseDir, "libraries")
        log("Unduh ${meta.libraries.size} library...")
        meta.libraries.forEachIndexed { i, lib ->
            Downloader.downloadFile(lib.url, File(libDir, lib.path), lib.sha1)
            if ((i + 1) % 25 == 0) log("  library ${i + 1}/${meta.libraries.size}")
        }

        log("Unduh indeks aset ${meta.assetIndex.id}...")
        val indexFile = File(baseDir, "assets/indexes/${meta.assetIndex.id}.json")
        Downloader.downloadFile(meta.assetIndex.url, indexFile, meta.assetIndex.sha1)

        val objects = JSONObject(indexFile.readText()).getJSONObject("objects")
        val names = objects.keys().asSequence().toList()
        val objDir = File(baseDir, "assets/objects")
        log("Unduh ${names.size} aset...")
        names.forEachIndexed { i, name ->
            val hash = objects.getJSONObject(name).getString("hash")
            val prefix = hash.substring(0, 2)
            Downloader.downloadFile(
                url = "https://resources.download.minecraft.net/$prefix/$hash",
                target = File(objDir, "$prefix/$hash"),
                sha1 = hash,
            )
            if ((i + 1) % 500 == 0) log("  aset ${i + 1}/${names.size}")
        }

        log("Selesai: ${meta.id}")
        return meta
    }
}
