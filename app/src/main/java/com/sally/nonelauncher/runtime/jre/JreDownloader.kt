// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 Buntuk

package com.sally.nonelauncher.runtime.jre

import com.sally.nonelauncher.core.launcher.Download
import com.sally.nonelauncher.core.launcher.Downloader
import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.xz.XZCompressorInputStream
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Paths

/**
 * Mengunduh dan memasang JRE prebuilt dari GitHub Releases
 * AngelAuraMC/angelauramc-openjdk-build, dengan pola URL yang sama seperti Amethyst Launcher.
 *
 * Hasilnya disimpan di [installDir]/java{N}. Dipanggil sekali saat app dibuka,
 * dan dilewati jika JRE sudah ada.
 */
object JreDownloader {

    private const val RELEASE_BASE =
        "https://github.com/AngelAuraMC/angelauramc-openjdk-build/releases/download"

    /** Arsitektur perangkat. Untuk sekarang hanya arm64, sesuai ABI yang dibangun. */
    private const val ARCH = "arm64"

    fun downloadUrl(majorVersion: Int): String =
        "$RELEASE_BASE/download_jre$majorVersion/jre$majorVersion-android-$ARCH.tar.xz"

    /**
     * Memastikan JRE [majorVersion] sudah ada. Blocking. Panggil dari thread latar belakang.
     * @return folder JRE (java{N}) yang berisi libjvm.so.
     */
    @Synchronized
    fun ensureInstalled(majorVersion: Int, installDir: File, log: (String) -> Unit): File {
        val home = File(installDir, "java$majorVersion")
        if (home.isDirectory && hasLibjvm(home)) {
            log("JRE $majorVersion sudah ada di ${home.absolutePath}")
            return home
        }

        installDir.mkdirs()
        val archive = File(installDir, "jre$majorVersion-android-$ARCH.tar.xz")
        log("Mengunduh JRE $majorVersion (bisa beberapa menit)...")
        Downloader.fetch(Download(downloadUrl(majorVersion), "", 0), archive)

        val staging = File(installDir, "java$majorVersion.part")
        staging.deleteRecursively()
        log("Mengekstrak JRE $majorVersion...")
        extract(archive, staging)

        home.deleteRecursively()
        if (!staging.renameTo(home)) {
            throw IOException("Gagal memindahkan JRE ke ${home.absolutePath}")
        }
        if (!hasLibjvm(home)) {
            throw IllegalStateException("libjvm.so tidak ditemukan setelah ekstrak")
        }
        archive.delete()
        log("JRE $majorVersion siap di ${home.absolutePath}")
        return home
    }

    private fun hasLibjvm(home: File): Boolean =
        home.walkTopDown().any { it.isFile && it.name == "libjvm.so" }

    /** Ekstrak .tar.xz, termasuk symlink dan bit eksekusi pemilik. */
    private fun extract(archive: File, dest: File) {
        dest.mkdirs()
        val destCanonical = dest.canonicalPath
        TarArchiveInputStream(XZCompressorInputStream(archive.inputStream().buffered())).use { tar ->
            var entry: TarArchiveEntry? = tar.nextTarEntry
            while (entry != null) {
                val out = File(dest, entry.name)
                if (!out.canonicalPath.startsWith(destCanonical)) {
                    throw IOException("Path di luar folder tujuan: ${entry.name}")
                }
                when {
                    entry.isDirectory -> out.mkdirs()
                    entry.isSymbolicLink -> {
                        out.parentFile?.mkdirs()
                        Files.deleteIfExists(out.toPath())
                        Files.createSymbolicLink(out.toPath(), Paths.get(entry.linkName))
                    }
                    entry.isFile -> {
                        out.parentFile?.mkdirs()
                        out.outputStream().use { tar.copyTo(it) }
                        if ((entry.mode and 0x40) != 0) out.setExecutable(true, false)
                    }
                }
                entry = tar.nextTarEntry
            }
        }
    }
}
