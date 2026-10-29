package com.sally.unilauncher.core.instance

import java.io.File

/**
 * Satu versi Minecraft yang terpasang di folder game.
 * Nilai diambil dari file JSON versi: versions/<id>/<id>.json
 */
data class Instance(
    val id: String,
    val type: String?,
    val mainClass: String?,
    val inheritsFrom: String?,
    val jsonFile: File,
)
