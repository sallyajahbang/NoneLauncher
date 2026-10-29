package com.sally.nonelauncher.core

import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.util.Collections
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger
import java.util.zip.ZipFile

/**
 * Downloads everything needed to start the latest release into [root]/.minecraft:
 * version JSON, client jar, libraries, natives, and assets.
 */
class GameInstaller(private val root: File) {

    private val gameDir = File(root, ".minecraft")
    private val librariesDir = File(gameDir, "libraries")
    private val assetsDir = File(gameDir, "assets")

    class Installed(
        val versionId: String,
        val versionJson: String,
        val mainClass: String,
        val configJson: String,
    )

    fun install(onStatus: (String) -> Unit): Installed {
        onStatus("Mengambil manifest...")
        val manifest = JSONObject(checked(NativeBridge.fetchManifest()))
        val release = manifest.getJSONObject("latest").getString("release")
        val versions = manifest.getJSONArray("versions")
        val versionUrl = (0 until versions.length())
            .map { versions.getJSONObject(it) }
            .firstOrNull { it.getString("id") == release }
            ?.getString("url")
            ?: throw IOException("Versi $release tidak ada di manifest")

        onStatus("Mengambil metadata $release...")
        val versionJson = checked(NativeBridge.fetchText(versionUrl))
        val version = JSONObject(versionJson)
        val versionId = version.getString("id")
        val mainClass = version.getString("mainClass")

        val versionDir = File(gameDir, "versions/$versionId").apply { mkdirs() }
        File(versionDir, "$versionId.json").writeText(versionJson)
        val clientJar = File(versionDir, "$versionId.jar")
        val nativesDir = File(versionDir, "natives")

        onStatus("Mengambil asset index...")
        val indexRef = version.getJSONObject("assetIndex")
        val indexJson = checked(NativeBridge.fetchText(indexRef.getString("url")))
        File(assetsDir, "indexes/${indexRef.getString("id")}.json").apply {
            parentFile?.mkdirs()
            writeText(indexJson)
        }

        val configJson = JSONObject()
            .put("javaPath", "java")
            .put("gameDir", gameDir.path)
            .put("librariesDir", librariesDir.path)
            .put("assetsDir", assetsDir.path)
            .put("nativesDir", nativesDir.path)
            .put("clientJar", clientJar.path)
            .put("username", "Steve")
            .put("uuid", NativeBridge.offlineUuid("Steve"))
            .put("accessToken", "0")
            .put("userType", "legacy")
            .put("memoryMb", 2048)
            .put("width", 1280)
            .put("height", 720)
            .put(
                "extraJvmArgs",
                JSONArray()
                    .put("-Djava.home=${File(root, "runtime").path}")
                    .put("-Dorg.lwjgl.librarypath=${nativesDir.path}"),
            )
            .toString()

        onStatus("Menghitung file...")
        val gameTasks = JSONArray(checked(NativeBridge.downloadTasks(versionJson, configJson)))
        val assetTasks = JSONArray(checked(NativeBridge.assetTasks(indexJson, assetsDir.path)))

        val all = JSONArray()
        for (list in listOf(gameTasks, assetTasks)) {
            for (i in 0 until list.length()) all.put(list.getJSONObject(i))
        }
        downloadAll(all, onStatus)
        extractNatives(gameTasks, nativesDir)

        return Installed(versionId, versionJson, mainClass, configJson)
    }

    /** Full JVM argument list (options, main class, game args) for the installed version. */
    fun buildArgs(installed: Installed): JSONArray {
        val cmd = JSONObject(NativeBridge.buildCommand(installed.versionJson, installed.configJson))
        if (cmd.has("error")) throw IOException(cmd.getString("error"))
        return cmd.getJSONArray("args")
    }

    private fun downloadAll(tasks: JSONArray, onStatus: (String) -> Unit) {
        val total = tasks.length()
        val done = AtomicInteger(0)
        val pool = Executors.newFixedThreadPool(8)
        try {
            val futures = (0 until total).map { i ->
                val taskJson = tasks.getJSONObject(i).toString()
                pool.submit(Callable {
                    val result = JSONObject(NativeBridge.downloadFile(taskJson))
                    if (result.has("error")) throw IOException(result.getString("error"))
                    val n = done.incrementAndGet()
                    if (n % 25 == 0 || n == total) onStatus("Mengunduh $n / $total file")
                })
            }
            futures.forEach { it.get() }
        } finally {
            pool.shutdownNow()
        }
    }

    /** Unpacks the .so files from LWJGL native jars into the natives folder. */
    private fun extractNatives(tasks: JSONArray, nativesDir: File) {
        nativesDir.mkdirs()
        for (i in 0 until tasks.length()) {
            val jar = File(tasks.getJSONObject(i).getString("dest"))
            if (!jar.name.endsWith(".jar") || !jar.name.contains("natives")) continue
            ZipFile(jar).use { zip ->
                for (entry in Collections.list(zip.entries())) {
                    if (entry.isDirectory || !entry.name.endsWith(".so")) continue
                    val out = File(nativesDir, File(entry.name).name)
                    zip.getInputStream(entry).use { input ->
                        out.outputStream().use { input.copyTo(it) }
                    }
                }
            }
        }
    }

    private fun checked(text: String): String {
        if (text.startsWith("{\"error\"")) {
            throw IOException(JSONObject(text).getString("error"))
        }
        return text
    }
}
