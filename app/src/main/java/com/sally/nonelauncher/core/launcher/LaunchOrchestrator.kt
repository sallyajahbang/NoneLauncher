// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 Buntuk

package com.sally.nonelauncher.core.launcher

import com.sally.nonelauncher.runtime.jre.JavaRuntimeProvider
import com.sally.nonelauncher.runtime.jre.JavaVersionPolicy
import com.sally.nonelauncher.runtime.jvm.NativeJvm
import java.io.File
import java.util.UUID
import java.util.zip.ZipInputStream

/**
 * Menjalankan seluruh alur launch secara berurutan:
 * manifest → version.json → client.jar → libraries → aset → native → JVM.
 *
 * Blocking. Panggil dari thread latar belakang.
 *
 * @param root Folder data aplikasi. Semua unduhan disimpan di bawahnya.
 * @param log Callback untuk progres, dipanggil dari thread latar belakang.
 */
class LaunchOrchestrator(
    private val root: File,
    private val javaProvider: JavaRuntimeProvider,
    private val log: (String) -> Unit,
) {
    private val versionsDir = File(root, "versions")
    private val librariesDir = File(root, "libraries")
    private val assetsDir = File(root, "assets")
    private val nativesRoot = File(root, "natives")
    private val gameDir = File(root, "game")

    fun launch(versionId: String, username: String): Int {
        log("Mengambil manifest versi...")
        val manifestFile = File(root, "cache/version_manifest_v2.json")
        manifestFile.delete() // selalu ambil manifest terbaru
        Downloader.fetch(Download(MojangApi.VERSION_MANIFEST_URL, "", 0), manifestFile)
        val versionUrl = MojangApi.findVersionUrl(manifestFile.readText(), versionId)
            ?: throw IllegalStateException("Versi $versionId tidak ada di manifest")

        val versionDir = File(versionsDir, versionId).apply { mkdirs() }
        val jsonFile = File(versionDir, "$versionId.json")
        Downloader.fetch(Download(versionUrl, "", 0), jsonFile)
        val version = VersionJsonParser.parse(jsonFile.readText())
        log("Versi ${version.id}, main class ${version.mainClass}")

        log("Mengunduh client.jar...")
        val clientJar = File(versionDir, "$versionId.jar")
        Downloader.fetch(version.client, clientJar)

        log("Mengunduh libraries...")
        for (lib in version.libraries) {
            if (!RuleEvaluator.isAllowed(lib.rules)) continue
            lib.artifact?.let { Downloader.fetch(it.download, File(librariesDir, it.path)) }
            lib.natives["linux"]?.let { classifier ->
                lib.classifiers[classifier]?.let { Downloader.fetch(it.download, File(librariesDir, it.path)) }
            }
        }

        log("Mengunduh indeks aset...")
        val indexFile = File(assetsDir, "indexes/${version.assetIndex.id}.json")
        Downloader.fetch(version.assetIndex.download, indexFile)
        val objectsDir = File(assetsDir, "objects")
        val assets = MojangApi.assetDownloads(indexFile.readText(), objectsDir)
        log("Mengunduh ${assets.size} aset...")
        assets.forEachIndexed { i, (download, dest) ->
            Downloader.fetch(download, dest)
            if ((i + 1) % 500 == 0) log("Aset ${i + 1}/${assets.size}")
        }

        val javaMajor = JavaVersionPolicy.requiredJavaFor(version.id)
        log("Menyiapkan Java $javaMajor...")
        val javaHome = javaProvider.resolve(javaMajor).home
        val libjvm = NativeJvm.findLibjvm(javaHome)
            ?: throw IllegalStateException("libjvm.so tidak ditemukan di ${javaHome.absolutePath}")

        gameDir.mkdirs()
        val nativesDir = File(nativesRoot, versionId).apply { mkdirs() }
        val settings = LaunchSettings(
            username = username,
            uuid = offlineUuid(username),
            accessToken = "0",
            userType = "legacy",
            gameDir = gameDir,
            assetsDir = assetsDir,
        )
        val plan = LaunchPlanner.plan(version, settings, librariesDir, clientJar, nativesDir)

        log("Mengekstrak ${plan.nativeJars.size} native jar...")
        plan.nativeJars.forEach { extractNatives(it, nativesDir) }

        log("Menjalankan ${plan.mainClass}...")
        val code = NativeJvm.run(
            libjvm.absolutePath,
            NativeJvm.toJvmOptions(plan),
            plan.mainClass,
            plan.gameArgs.toTypedArray(),
        )
        log("JVM selesai dengan kode $code")
        return code
    }

    /** Ekstrak hanya file .so dari native jar. */
    private fun extractNatives(jar: File, dest: File) {
        ZipInputStream(jar.inputStream().buffered()).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                val name = entry.name
                if (!entry.isDirectory && name.endsWith(".so") && !name.startsWith("META-INF")) {
                    File(dest, File(name).name).outputStream().use { zip.copyTo(it) }
                }
                zip.closeEntry()
            }
        }
    }

    /** UUID offline, sama seperti yang dipakai launcher resmi untuk akun tanpa login. */
    private fun offlineUuid(name: String): String =
        UUID.nameUUIDFromBytes("OfflinePlayer:$name".toByteArray(Charsets.UTF_8)).toString()
}
