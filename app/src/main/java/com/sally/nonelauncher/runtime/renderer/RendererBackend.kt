// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 Buntuk

package com.sally.nonelauncher.runtime.renderer

/**
 * Backend yang menerjemahkan panggilan OpenGL Minecraft ke API yang tersedia di Android.
 *
 * Milestone 1 memakai [GL4ES] karena dependensinya paling sederhana,
 * dan cukup untuk Minecraft 1.16.5 di perangkat dengan Adreno.
 */
enum class RendererBackend(val displayName: String) {
    /** OpenGL 2.x/3.x ke OpenGL ES. Dipakai untuk milestone 1. */
    GL4ES("gl4es"),

    /** OpenGL ke OpenGL ES lewat ANGLE. Rencana untuk versi yang lebih baru. */
    ANGLE("ANGLE"),

    /** OpenGL ke Vulkan lewat Zink. Eksperimental. */
    ZINK("Zink");

    companion object {
        val DEFAULT = GL4ES
    }
}
