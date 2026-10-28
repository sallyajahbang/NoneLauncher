// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 Buntuk

package com.sally.nonelauncher.runtime.jre

import java.io.File

/**
 * Mencari JRE yang sudah ditaruh manual di salah satu [roots], misalnya [root]/java8.
 * Folder pertama yang berisi JRE yang dipakai.
 *
 * Ini implementasi sementara. Unduhan otomatis belum ada karena artifact jre8-pojav
 * ada di GitHub Actions dan butuh login untuk diunduh.
 */
class LocalJavaRuntimeProvider(private val roots: List<File>) : JavaRuntimeProvider {

    override fun resolve(majorVersion: Int): JavaRuntime {
        val name = "java$majorVersion"
        roots.map { File(it, name) }.firstOrNull { it.isDirectory }?.let {
            return JavaRuntime(majorVersion, it)
        }
        val searched = roots.joinToString { File(it, name).absolutePath }
        throw IllegalStateException("JRE Java $majorVersion belum ada. Dicari di: $searched")
    }
}
