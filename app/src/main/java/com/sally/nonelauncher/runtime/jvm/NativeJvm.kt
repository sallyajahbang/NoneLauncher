// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 Buntuk

package com.sally.nonelauncher.runtime.jvm

import android.view.Surface
import com.sally.nonelauncher.core.launcher.LaunchPlan
import java.io.File

/**
 * Pembungkus JNI untuk membuat JVM di dalam proses app dan menjalankan main().
 * Implementasinya ada di cpp/jvm_launcher.cpp dan cpp/egl_bridge.cpp.
 */
object NativeJvm {

    init {
        System.loadLibrary("nonelauncher_jvm")
    }

    /**
     * Membuat JVM dari [libjvmPath] dan menjalankan [mainClass].
     * Jika [surface] tidak null, EGL dibuat dari surface itu di thread ini sebelum main().
     * Memblokir sampai main() selesai. Panggil dari thread latar belakang.
     *
     * @return 0 jika berhasil. Nilai negatif jika gagal:
     *   -1 dlopen, -2 JNI_CreateJavaVM tidak ada, -3 JNI_CreateJavaVM gagal,
     *   -4 kelas main tidak ada, -5 method main tidak ada, -6 exception di main,
     *   -7 setup EGL gagal.
     */
    @JvmStatic
    external fun run(
        libjvmPath: String,
        jvmOptions: Array<String>,
        mainClass: String,
        args: Array<String>,
        surface: Surface?,
    ): Int

    /**
     * Mencari libjvm.so di dalam folder JRE. Lokasinya berbeda antar build,
     * jadi dicari secara rekursif. Mengembalikan null jika tidak ada.
     */
    fun findLibjvm(javaHome: File): File? =
        javaHome.walkTopDown().firstOrNull { it.isFile && it.name == "libjvm.so" }

    /**
     * Mengubah argumen JVM dari LaunchPlan menjadi opsi yang diterima JNI.
     * Opsi "-cp" diubah menjadi -Djava.class.path, karena JNI tidak menerima -cp.
     */
    fun toJvmOptions(plan: LaunchPlan): Array<String> {
        val out = mutableListOf<String>()
        var hasClasspath = false
        val iter = plan.jvmArgs.iterator()
        while (iter.hasNext()) {
            val arg = iter.next()
            if (arg == "-cp" || arg == "-classpath") {
                if (iter.hasNext()) {
                    out += "-Djava.class.path=${iter.next()}"
                    hasClasspath = true
                }
            } else {
                out += arg
            }
        }
        if (!hasClasspath) {
            out += "-Djava.class.path=" + plan.classpath.joinToString(":") { it.absolutePath }
        }
        return out.toTypedArray()
    }
}
