package com.sally.javoxel.data

import java.io.File
import java.util.concurrent.Callable
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executors
import java.util.concurrent.ExecutorCompletionService
import java.util.concurrent.atomic.AtomicInteger
import org.json.JSONObject

/**
 * Mengunduh satu versi Minecraft ke [baseDir]:
 *  - versions/<id>/<id>.json dan <id>.jar
 *  - libraries/...
 *  - assets/indexes/<id>.json dan assets/objects/xx/<hash>
 *
 * Library dan aset diunduh paralel ([THREADS] koneksi). File yang sudah ada dan
 * SHA-1-nya cocok dilewati, jadi install ulang hanya mengunduh yang kurang.
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
        val libTotal = meta.libraries.size
        log("Unduh $libTotal library...")
        val libDone = AtomicInteger(0)
        runParallel(meta.libraries) { lib ->
            Downloader.downloadFile(lib.url, File(libDir, lib.path), lib.sha1)
            val n = libDone.incrementAndGet()
            if (n % 10 == 0 || n == libTotal) log("  library $n/$libTotal")
        }

        log("Unduh indeks aset ${meta.assetIndex.id}...")
        val indexFile = File(baseDir, "assets/indexes/${meta.assetIndex.id}.json")
        Downloader.downloadFile(meta.assetIndex.url, indexFile, meta.assetIndex.sha1)

        val objects = JSONObject(indexFile.readText()).getJSONObject("objects")
        // Ambil hash di thread utama. JSONObject tidak dijamin aman dipakai dari banyak thread.
        val hashes = objects.keys().asSequence().toList()
            .map { name -> objects.getJSONObject(name).getString("hash") }

        val objDir = File(baseDir, "assets/objects")
        val assetTotal = hashes.size
        log("Unduh $assetTotal aset...")
        val assetDone = AtomicInteger(0)
        runParallel(hashes) { hash ->
            val prefix = hash.substring(0, 2)
            Downloader.downloadFile(
                url = "https://resources.download.minecraft.net/$prefix/$hash",
                target = File(objDir, "$prefix/$hash"),
                sha1 = hash,
            )
            val n = assetDone.incrementAndGet()
            if (n % 250 == 0 || n == assetTotal) log("  aset $n/$assetTotal")
        }

        log("Selesai: ${meta.id}")
        return meta
    }

    /**
     * Jalankan [action] untuk tiap item dengan [THREADS] thread. Begitu satu item gagal,
     * sisanya dibatalkan dan error-nya dilempar lagi ke pemanggil.
     */
    private fun <T> runParallel(items: List<T>, action: (T) -> Unit) {
        if (items.isEmpty()) return
        val pool = Executors.newFixedThreadPool(THREADS)
        try {
            val completion = ExecutorCompletionService<Unit>(pool)
            items.forEach { item -> completion.submit(Callable { action(item) }) }
            repeat(items.size) {
                try {
                    completion.take().get()
                } catch (e: ExecutionException) {
                    throw e.cause ?: e
                }
            }
        } finally {
            pool.shutdownNow()
        }
    }

    companion object {
        private const val THREADS = 8
    }
}
