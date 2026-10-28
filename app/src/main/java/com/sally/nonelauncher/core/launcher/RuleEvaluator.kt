// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 Buntuk

package com.sally.nonelauncher.core.launcher

/**
 * Mengevaluasi aturan dari version.json terhadap perangkat ini.
 * Perangkat dianggap linux/arm64. Tidak ada feature opsional yang aktif.
 */
object RuleEvaluator {

    private const val HOST_OS = "linux"
    private const val HOST_ARCH = "arm64"

    /**
     * Aturan kosong berarti diizinkan. Jika ada aturan, hasil akhirnya
     * ditentukan oleh aturan terakhir yang cocok, dan default-nya tidak diizinkan.
     */
    fun isAllowed(rules: List<Rule>, enabledFeatures: Set<String> = emptySet()): Boolean {
        if (rules.isEmpty()) return true
        var allowed = false
        for (rule in rules) {
            if (rule.appliesTo(enabledFeatures)) allowed = rule.allow
        }
        return allowed
    }

    private fun Rule.appliesTo(enabled: Set<String>): Boolean {
        val osOk = osName == null ||
            (osName == HOST_OS && (osArch == null || osArch == HOST_ARCH))
        val featuresOk = features.all { (name, want) -> (name in enabled) == want }
        return osOk && featuresOk
    }
}
