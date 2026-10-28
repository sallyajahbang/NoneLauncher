package com.sally.nonelauncher.core.util

import java.io.File
import java.security.MessageDigest

object Hashing {

    fun sha1(file: File): String {
        val digest = MessageDigest.getInstance("SHA-1")
        file.inputStream().use { input ->
            val buffer = ByteArray(8192)
            while (true) {
                val read = input.read(buffer)
                if (read == -1) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    fun matchesSha1(file: File, expected: String?): Boolean {
        if (expected == null) return true
        return file.exists() && sha1(file).equals(expected, ignoreCase = true)
    }
}
