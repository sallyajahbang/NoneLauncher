package com.sally.unilauncher.core.launch

/**
 * Tahap-tahap peluncuran game. Ini adalah kerangka yang nanti diisi satu per satu.
 *
 * Urutan ini mengikuti pola yang juga dipakai launcher Zalith (lihat docs/ZALITH_STUDY.md):
 *   1. Siapkan runtime Java (JRE) yang cocok dengan versi Minecraft
 *   2. Resolusi library dan asset dari JSON versi (termasuk inheritsFrom)
 *   3. Unduh file yang belum ada
 *   4. Susun argumen JVM dan argumen game
 *   5. Jalankan JVM di proses terpisah, lalu tampilkan output
 *
 * Tahap 4 dan 5 membutuhkan bridge native (renderer, input). Itu dikerjakan setelah
 * fondasi di atas stabil.
 */
enum class LaunchStep(val title: String) {
    PREPARE_RUNTIME("Menyiapkan runtime Java"),
    RESOLVE_LIBRARIES("Membaca library dan asset"),
    DOWNLOAD_MISSING("Mengunduh file yang belum ada"),
    BUILD_ARGUMENTS("Menyusun argumen JVM"),
    START_PROCESS("Menjalankan game"),
}
