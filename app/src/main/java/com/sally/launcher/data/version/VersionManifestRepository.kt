package com.sally.launcher.data.version

import android.content.Context
import com.sally.launcher.data.model.MinecraftVersion
import com.sally.launcher.data.model.VersionCatalog
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/** Loads Mojang's public version manifest and stores the raw JSON for offline fallback. */
class VersionManifestRepository(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    fun fetchCatalog(): VersionCatalog {
        var connection: HttpURLConnection? = null
        try {
            connection = (URL(MANIFEST_URL).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = READ_TIMEOUT_MS
                setRequestProperty("Accept", "application/json")
                setRequestProperty("User-Agent", USER_AGENT)
            }

            val responseCode = connection.responseCode
            if (responseCode !in 200..299) {
                throw IOException("Version service returned HTTP $responseCode")
            }

            val rawJson = connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            val catalog = parseManifest(rawJson, loadedFromCache = false)
            preferences.edit().putString(CACHE_KEY, rawJson).apply()
            return catalog
        } catch (networkError: Exception) {
            val cachedJson = preferences.getString(CACHE_KEY, null)
                ?: throw IOException("Could not load the official version catalog and no cache is available", networkError)

            return try {
                parseManifest(cachedJson, loadedFromCache = true)
            } catch (cacheError: Exception) {
                throw IOException("Could not load the official version catalog or its saved cache", networkError)
            }
        } finally {
            connection?.disconnect()
        }
    }

    private fun parseManifest(rawJson: String, loadedFromCache: Boolean): VersionCatalog {
        val root = JSONObject(rawJson)
        val latest = root.optJSONObject("latest")
        val versionArray = root.optJSONArray("versions")
            ?: throw IOException("Version manifest has no versions array")
        val result = ArrayList<MinecraftVersion>(versionArray.length())

        for (index in 0 until versionArray.length()) {
            val item = versionArray.optJSONObject(index) ?: continue
            val id = item.optString("id").takeIf { it.isNotBlank() } ?: continue
            val versionUrl = item.optString("url").takeIf { it.startsWith("https://") } ?: continue
            result += MinecraftVersion(
                id = id,
                type = item.optString("type", "unknown"),
                url = versionUrl,
                releaseTime = item.optString("releaseTime", ""),
            )
        }

        if (result.isEmpty()) throw IOException("Version manifest contains no usable versions")

        return VersionCatalog(
            versions = result,
            latestReleaseId = latest?.optString("release")?.takeIf { it.isNotBlank() },
            latestSnapshotId = latest?.optString("snapshot")?.takeIf { it.isNotBlank() },
            loadedFromCache = loadedFromCache,
        )
    }

    private companion object {
        const val PREFERENCES_NAME = "version_catalog_cache"
        const val CACHE_KEY = "manifest_json"
        const val MANIFEST_URL = "https://piston-meta.mojang.com/mc/game/version_manifest_v2.json"
        const val USER_AGENT = "SallyLauncher/0.1.0 (Android)"
        const val CONNECT_TIMEOUT_MS = 12_000
        const val READ_TIMEOUT_MS = 15_000
    }
}
