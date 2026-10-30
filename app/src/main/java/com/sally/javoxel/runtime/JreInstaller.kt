package com.sally.javoxel.runtime

import android.content.res.AssetManager
import java.io.File
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.Paths
import java.nio.file.StandardCopyOption
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.tukaani.xz.XZInputStream

/**
 * Menyiapkan JAVA_HOME untuk versi Java tertentu dari arsip yang di-bundle di assets:
 *   assets/jre/jre-<major>/universal.tar.xz  (opsional, bagian Java non-native)
 *   assets/jre/jre-<major>/bin-arm64.tar.xz  (bin dan native arm64)
 *
 * Hasil ekstrak ada di [baseDir]/java-<major>/. Jangan pakai penyimpanan eksternal (sdcard):
 * di sana symlink tidak bisa dibuat ("Operation not permitted").
 * Harus dipanggil dari thread IO.
 */
class JreInstaller(
    private val baseDir: File,
    private val assets: AssetManager,
    private val log: (String) -> Unit,
) {

    /** Mengembalikan folder JAVA_HOME. Ekstrak hanya jika belum ada. */
    fun ensureJre(major: Int): File {
        val home = File(baseDir, "java-$major")
        // Ekstrak ulang kalau class library belum ada (JRE lama yang hanya berisi bin).
        if (File(home, "bin/java").exists() && hasClassLibrary(home)) {
            log("JRE $major sudah ada")
            return home
        }

        val assetDir = "jre/jre-$major"
        val parts = listOf("universal.tar.xz", "bin-arm64.tar.xz")
            .filter { name -> assets.list(assetDir)?.contains(name) == true }
        if (parts.isEmpty()) error("JRE $major tidak ada di assets/$assetDir")

        log("Ekstrak JRE $major...")
        val tmp = File(baseDir, "java-$major.tmp")
        tmp.deleteRecursively()
        for (name in parts) {
            assets.open("$assetDir/$name").use { input -> extract(input, tmp) }
        }

        home.deleteRecursively()
        if (!tmp.renameTo(home)) {
            tmp.deleteRecursively()
            error("Gagal memindahkan JRE $major ke ${home.path}")
        }
        if (!File(home, "bin/java").exists()) {
            error("JRE $major tidak punya bin/java setelah ekstrak")
        }

        log("JRE $major siap")
        return home
    }

    /** rt.jar untuk JRE 8, file modules untuk JRE 9 ke atas. */
    private fun hasClassLibrary(home: File): Boolean =
        home.walkTopDown().maxDepth(3).any { it.isFile && (it.name == "rt.jar" || it.name == "modules") }

    private fun extract(input: InputStream, dest: File) {
        dest.mkdirs()
        val destPath = dest.canonicalFile.toPath()

        TarArchiveInputStream(XZInputStream(input.buffered())).use { tar ->
            var entry = tar.nextTarEntry
            while (entry != null) {
                val out = destPath.resolve(entry.name).normalize()
                if (!out.startsWith(destPath)) {
                    error("Path tidak aman di dalam arsip: ${entry.name}")
                }

                when {
                    entry.isDirectory -> Files.createDirectories(out)

                    entry.isSymbolicLink -> {
                        Files.createDirectories(out.parent)
                        Files.deleteIfExists(out)
                        Files.createSymbolicLink(out, Paths.get(entry.linkName))
                    }

                    entry.isFile -> {
                        Files.createDirectories(out.parent)
                        Files.copy(tar, out, StandardCopyOption.REPLACE_EXISTING)
                        // Bit eksekusi owner/group/other: 0o111
                        if ((entry.mode and 0x49) != 0) {
                            out.toFile().setExecutable(true, false)
                        }
                    }
                }
                entry = tar.nextTarEntry
            }
        }
    }
}
