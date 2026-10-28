// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 Buntuk

package com.sally.nonelauncher.runtime.jre

import java.io.File

/**
 * JRE yang sudah terpasang di perangkat dan siap dipakai untuk menjalankan JVM.
 *
 * @property majorVersion Versi Java utama, misalnya 8 atau 17.
 * @property home Folder root JRE (berisi bin/, lib/, dll.).
 */
data class JavaRuntime(
    val majorVersion: Int,
    val home: File,
)

/**
 * Menyediakan JRE untuk versi Java tertentu.
 *
 * Implementasi awal akan mengunduh artifact prebuilt (jre8-pojav untuk Java 8)
 * dan mengekstraknya ke folder aplikasi. Lihat README bagian "Sumber JRE".
 */
interface JavaRuntimeProvider {
    /** Mengembalikan JRE untuk [majorVersion], memasangnya dulu jika belum ada. */
    suspend fun resolve(majorVersion: Int): JavaRuntime
}

/**
 * Pemilihan versi Java berdasarkan versi Minecraft.
 * Minecraft 1.16.5 dan yang lebih lama memakai Java 8.
 * Minecraft 1.17 sampai 1.20.4 memakai Java 17.
 * Minecraft 1.20.5 ke atas memakai Java 21.
 */
object JavaVersionPolicy {

    fun requiredJavaFor(minecraftVersion: String): Int {
        val parts = minecraftVersion.split(".").mapNotNull { it.toIntOrNull() }
        val minor = parts.getOrNull(1) ?: return 8
        val patch = parts.getOrNull(2) ?: 0
        return when {
            minor <= 16 -> 8
            minor < 20 || (minor == 20 && patch <= 4) -> 17
            else -> 21
        }
    }
}
