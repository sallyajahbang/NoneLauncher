package com.sally.javoxel.runtime

/** Sumber arsip JRE (tar.xz) untuk Android arm64. */
data class JavaSource(val major: Int, val url: String, val sha1: String?)

/**
 * Daftar sumber JRE per versi Java.
 *
 * BELUM DIISI. URL build OpenJDK Android arm64 (mis. dari Amethyst / PojavLauncher)
 * harus diverifikasi dulu sebelum dimasukkan di sini. Sampai itu, [forMajor]
 * akan melempar error yang jelas.
 */
object JavaSources {

    private val sources = mapOf(
        8 to JavaSource(8, url = "", sha1 = null),
        17 to JavaSource(17, url = "", sha1 = null),
        21 to JavaSource(21, url = "", sha1 = null),
    )

    fun forMajor(major: Int): JavaSource {
        val source = sources[major] ?: error("Tidak ada sumber JRE untuk Java $major")
        check(source.url.isNotEmpty()) {
            "Sumber JRE $major belum dikonfigurasi di JavaSources.kt"
        }
        return source
    }
}
