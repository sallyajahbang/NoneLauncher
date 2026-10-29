package com.sally.javoxel.runtime

import java.io.File

/**
 * Menjalankan JVM di dalam proses app lewat libjli.so dari JRE.
 * Native-nya ada di app/src/main/cpp/javoxel_jvm.c (lib: libjavoxel_jvm.so).
 *
 * [launch] memblokir sampai JVM selesai. Jalankan dari thread IO.
 * Catatan: JLI_Launch biasanya memanggil exit() saat Minecraft tutup,
 * jadi proses app ikut berhenti. Itu wajar untuk tahap ini.
 */
object JvmLauncher {

    init {
        System.loadLibrary("javoxel_jvm")
    }

    private external fun launchJvm(
        jliPath: String,
        fullVersion: String,
        dotVersion: String,
        args: Array<String>,
    ): Int

    /**
     * [args] adalah hasil LaunchCommand.build: elemen pertama path ke bin/java,
     * lalu opsi JVM, main class, dan argumen game.
     */
    fun launch(javaHome: File, javaMajor: Int, args: List<String>): Int {
        val jli = findLibJli(javaHome)
            ?: error("libjli.so tidak ditemukan di ${javaHome.path}")
        val dot = if (javaMajor == 8) "1.8" else javaMajor.toString()
        return launchJvm(
            jliPath = jli.path,
            fullVersion = "$javaMajor.0.0-javoxel",
            dotVersion = dot,
            args = args.toTypedArray(),
        )
    }

    /** Lokasi libjli.so berbeda antar JDK: lib/libjli.so (9+) atau lib/<arch>/jli/libjli.so (8). */
    private fun findLibJli(javaHome: File): File? =
        javaHome.walkTopDown()
            .maxDepth(4)
            .firstOrNull { it.isFile && it.name == "libjli.so" }
}
