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
 *   assets/jre/jre-<major>/universal.tar.xz  (class library, berisi *.pack untuk Java 8)
 *   assets/jre/jre-<major>/bin-arm64.tar.xz  (bin dan native arm64)
 *
 * Hasil ekstrak ada di [baseDir]/java-<major>/. Jangan pakai penyimpanan eksternal (sdcard):
 * di sana symlink tidak bisa dibuat ("Operation not permitted").
 *
 * Untuk Java 8, file *.pack (pack200) harus di-unpack ke jar dulu. Android tidak punya
 * Pack200 di Java, jadi dipakai libunpack200.so (dari [nativeLibDir], ikut APK).
 * Harus dipanggil dari thread IO.
 */
class JreInstaller(
    private val baseDir: File,
    private val assets: AssetManager,
    private val nativeLibDir: File,
    private val log: (String) -> Unit,
) {

    /** Mengembalikan folder JAVA_HOME. Ekstrak hanya jika belum ada atau belum lengkap. */
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

        if (major == 8) unpackPackFiles(tmp)

        home.deleteRecursively()
        if (!tmp.renameTo(home)) {
            tmp.deleteRecursively()
            error("Gagal memindahkan JRE $major ke ${home.path}")
        }
        if (!File(home, "bin/java").exists()) {
            error("JRE $major tidak punya bin/java setelah ekstrak")
        }
        if (!hasClassLibrary(home)) {
            error("JRE $major tidak punya class library (rt.jar / modules) setelah ekstrak")
        }

        log("JRE $major siap")
        return home
    }

    /** rt.jar untuk JRE 8, file modules untuk JRE 9 ke atas. */
    private fun hasClassLibrary(home: File): Boolean =
        home.walkTopDown().maxDepth(3).any { it.isFile && (it.name == "rt.jar" || it.name == "modules") }

    /**
     * Ubah semua *.pack menjadi jar dengan libunpack200.so, lalu hapus file .pack-nya (-r).
     * Sama seperti yang dilakukan Zalith Launcher 2.
     */
    private fun unpackPackFiles(root: File) {
        val tool = File(nativeLibDir, "libunpack200.so")
        if (!tool.exists()) error("libunpack200.so tidak ada di ${nativeLibDir.path}")

        val packs = root.walkTopDown().filter { it.isFile && it.name.endsWith(".pack") }.toList()
        log("Unpack ${packs.size} file .pack...")
        for (pack in packs) {
            val dest = File(pack.parentFile, pack.name.removeSuffix(".pack"))
            val proc = ProcessBuilder(tool.path, "-r", pack.path, dest.path)
                .redirectErrorStream(true)
                .start()
            val output = proc.inputStream.bufferedReader().use { it.readText() }
            val code = proc.waitFor()
            if (code != 0 || !dest.exists()) {
                error("unpack200 gagal untuk ${pack.name} (kode $code): ${output.take(300)}")
            }
        }
    }

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
