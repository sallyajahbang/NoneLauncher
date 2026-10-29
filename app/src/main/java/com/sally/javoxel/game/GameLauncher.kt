package com.sally.javoxel.game

import com.sally.javoxel.data.VersionRepository
import com.sally.javoxel.runtime.JvmLauncher
import java.io.File

/**
 * Menyatukan langkah launch: baca versi lokal, susun argumen, lalu jalankan JVM.
 * Semua fungsi di sini memblokir, panggil dari thread IO.
 */
object GameLauncher {

    /**
     * Java major untuk versi lokal. Pakai field javaVersion dari JSON Mojang kalau ada,
     * kalau tidak pakai tebakan dari nomor versi.
     */
    fun javaMajorFor(baseDir: File, versionId: String): Int =
        VersionRepository.readLocal(baseDir, versionId)?.javaMajor
            ?: requiredJavaMajor(versionId)

    /**
     * Jalankan versi yang sudah diunduh. [javaHome] harus sudah siap (JreInstaller.ensureJre).
     * Mengembalikan kode keluar JVM jika JVM kembali tanpa exit().
     */
    fun launch(
        baseDir: File,
        javaHome: File,
        javaMajor: Int,
        versionId: String,
        playerName: String,
        memoryMb: Int,
        log: (String) -> Unit,
    ): Int {
        val meta = VersionRepository.readLocal(baseDir, versionId)
            ?: error("Versi $versionId belum lengkap. Tekan \"Unduh versi\" dan tunggu sampai selesai dulu.")

        val gameDir = File(baseDir, "game").apply { mkdirs() }
        val nativesDir = File(baseDir, "natives/${meta.id}").apply { mkdirs() }

        val args = LaunchCommand.build(
            meta,
            LaunchParams(
                baseDir = baseDir,
                gameDir = gameDir,
                javaHome = javaHome,
                nativesDir = nativesDir,
                playerName = playerName,
                memoryMb = memoryMb,
            ),
        )

        log("Menjalankan ${meta.id} (Java $javaMajor, ${args.size} argumen)")
        return JvmLauncher.launch(javaHome, javaMajor, args)
    }
}
