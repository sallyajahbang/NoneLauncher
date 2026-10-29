package com.sally.javoxel.game

/**
 * Java major version yang dibutuhkan untuk menjalankan Minecraft Java Edition
 * dengan versi tertentu (misalnya "1.16.5", "1.20.4", "1.21.1").
 */
fun requiredJavaMajor(minecraftVersion: String): Int {
    val parts = minecraftVersion.split(".").map { it.toIntOrNull() ?: 0 }
    val minor = parts.getOrElse(1) { 0 }
    val patch = parts.getOrElse(2) { 0 }

    return when {
        minor <= 16 -> 8
        minor < 20 -> 17
        minor == 20 && patch < 5 -> 17
        else -> 21
    }
}
