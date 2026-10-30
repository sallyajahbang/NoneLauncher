package com.sally.javoxel.runtime

import com.sally.javoxel.util.AppLog
import java.io.File

/**
 * Menjalankan JVM di dalam proses app lewat libjli.so dari JRE.
 * Native-nya ada di app/src/main/cpp/javoxel_jvm.c (lib: libjavoxel_jvm.so).
 *
 * [launch] memblokir sampai JVM selesai. Jalankan dari thread IO.
 * Output JVM dan crash native masuk ke file log app (lihat AppLog).
 * Catatan: JLI_Launch biasanya memanggil exit() saat Minecraft tutup,
 * jadi proses app ikut berhenti.
 */
object JvmLauncher {

    init {
        System.loadLibrary("javoxel_jvm")
    }

    /** Arahkan stdout dan stderr native ke file log, dan pasang catcher crash native. */
    private external fun redirectOutput(path: String)

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

        AppLog.i("JRE: ${javaHome.path} (Java $javaMajor)")
        AppLog.i("libjli: ${jli.path}")
        AppLog.i("Argumen JVM (${args.size}): ${args.joinToString(" ")}")

        // Tanpa class library, JVM gagal start lalu memanggil exit() dengan thread masih hidup,
        // yang berujung crash. Cek dulu supaya errornya jelas dan tidak crash.
        val classLib = findClassLibrary(javaHome)
        if (classLib == null) {
            val msg = "Class library JRE $javaMajor tidak ditemukan (rt.jar atau modules). " +
                "Isi JRE di assets hanya binary. Tambahkan universal.tar.xz yang berisi lib/ " +
                "di assets/jre/jre-$javaMajor/, lalu tekan \"Siapkan Java\" lagi."
            AppLog.e(msg)
            error(msg)
        }
        AppLog.i("Class library: ${classLib.path}")

        redirectOutput(AppLog.latestPath())
        return launchJvm(
            jliPath = jli.path,
            fullVersion = "$javaMajor.0.0-javoxel",
            dotVersion = dot,
            args = args.toTypedArray(),
        )
    }

    /** JRE 8 memakai rt.jar, JRE 9 ke atas memakai file modules. */
    private fun findClassLibrary(javaHome: File): File? =
        javaHome.walkTopDown()
            .maxDepth(3)
            .firstOrNull { it.isFile && (it.name == "rt.jar" || it.name == "modules") }

    /** Lokasi libjli.so berbeda antar JDK: lib/libjli.so (9+) atau lib/<arch>/jli/libjli.so (8). */
    private fun findLibJli(javaHome: File): File? =
        javaHome.walkTopDown()
            .maxDepth(4)
            .firstOrNull { it.isFile && it.name == "libjli.so" }
}
