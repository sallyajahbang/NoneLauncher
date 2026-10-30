package com.rustcraft

/** JNI bridge to the Rust game core. A fallback keeps the UI previewable in IDE previews. */
class NativeBridge {
    val isAvailable: Boolean

    init {
        isAvailable = try {
            System.loadLibrary("rustcraft_android")
            true
        } catch (_: UnsatisfiedLinkError) {
            false
        } catch (_: SecurityException) {
            false
        }
    }

    external fun nativeEngineVersion(): String
    external fun nativeGenerateSurfaceMap(width: Int, depth: Int, seed: Long): IntArray

    fun engineVersion(): String = if (isAvailable) {
        runCatching { nativeEngineVersion() }.getOrDefault("Rust core unavailable")
    } else {
        "Rust core not loaded (fallback preview)"
    }

    fun generateSurfaceMap(width: Int, depth: Int, seed: Long): IntArray {
        if (isAvailable) {
            runCatching { nativeGenerateSurfaceMap(width, depth, seed) }
                .getOrNull()
                ?.takeIf { it.size == width * depth }
                ?.let { return it }
        }

        return IntArray(width * depth) { index ->
            val x = index % width
            val z = index / width
            val noise = ((x * 17 + z * 31 + seed.toInt()) xor (x * z * 13)) and 0x7fffffff
            when (noise % 19) {
                0, 1, 2, 3 -> 4 // water
                4, 5 -> 5 // sand
                else -> 1 // grass
            }
        }
    }
}
