package com.sally.unilauncher.core.instance

/**
 * Satu library yang dibutuhkan versi, sudah digabung dari JSON induk dan anak.
 *
 * @param key    identitas tanpa versi, misalnya "org.lwjgl:lwjgl" atau "org.lwjgl:lwjgl:natives-linux".
 *               Dipakai untuk membuang duplikat: versi dari JSON anak menang atas JSON induk.
 * @param path   path relatif di folder libraries, misalnya "org/lwjgl/lwjgl/3.3.3/lwjgl-3.3.3.jar"
 * @param url    URL unduhan. Null hanya kalau tidak ada sumber sama sekali.
 */
data class Library(
    val key: String,
    val path: String,
    val url: String?,
    val sha1: String?,
    val size: Long?,
)

data class AssetIndexRef(
    val id: String,
    val url: String?,
    val sha1: String?,
    val size: Long?,
)

data class ResolvedVersion(
    val id: String,
    val mainClass: String?,
    val libraries: List<Library>,
    val assetIndex: AssetIndexRef?,
    /** Rantai versi dari anak ke induk, misalnya ["fabric-loader-0.16", "1.21.1"]. */
    val chain: List<String>,
)

/** Konversi koordinat Maven ("group:artifact:version[:classifier]"). */
object MavenCoordinates {

    /** Identitas tanpa versi: "group:artifact" atau "group:artifact:classifier". */
    fun keyOf(name: String): String? {
        val parts = name.split(":")
        if (parts.size < 3) return null
        val classifier = parts.getOrNull(3)
        return if (classifier == null) "${parts[0]}:${parts[1]}" else "${parts[0]}:${parts[1]}:$classifier"
    }

    /** Path relatif file jar, mengikuti tata letak repositori Maven. */
    fun pathOf(name: String): String? {
        val parts = name.split(":")
        if (parts.size < 3) return null
        val group = parts[0]
        val artifact = parts[1]
        val version = parts[2]
        val classifier = parts.getOrNull(3)
        val file = if (classifier == null) "$artifact-$version.jar" else "$artifact-$version-$classifier.jar"
        return "${group.replace('.', '/')}/$artifact/$version/$file"
    }
}
