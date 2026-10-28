// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 Buntuk

package com.sally.nonelauncher.core.launcher

import org.json.JSONObject
import java.io.File

/**
 * Endpoint publik Mojang. Launcher hanya mengunduh dari sini,
 * tidak membundel JAR atau aset Minecraft ke dalam APK.
 */
object MojangApi {

    const val VERSION_MANIFEST_URL = "https://piston-meta.mojang.com/mc/game/version_manifest_v2.json"
    private const val ASSET_BASE_URL = "https://resources.download.minecraft.net/"

    /** Mengembalikan URL version.json untuk [versionId], atau null jika tidak ada. */
    fun findVersionUrl(manifestJson: String, versionId: String): String? {
        val versions = JSONObject(manifestJson).getJSONArray("versions")
        for (i in 0 until versions.length()) {
            val v = versions.getJSONObject(i)
            if (v.getString("id") == versionId) return v.getString("url")
        }
        return null
    }

    /**
     * Daftar aset dari asset index: pasangan (unduhan, file tujuan).
     * Di Mojang, nama file aset di server sama dengan SHA-1-nya.
     */
    fun assetDownloads(indexJson: String, objectsDir: File): List<Pair<Download, File>> {
        val objects = JSONObject(indexJson).getJSONObject("objects")
        return objects.keys().asSequence().map { key ->
            val o = objects.getJSONObject(key)
            val hash = o.getString("hash")
            val download = Download(
                url = ASSET_BASE_URL + hash.substring(0, 2) + "/" + hash,
                sha1 = hash,
                size = o.getLong("size"),
            )
            download to File(objectsDir, hash.substring(0, 2) + "/" + hash)
        }.toList()
    }
}
