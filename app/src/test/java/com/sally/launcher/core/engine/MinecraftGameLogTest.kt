package com.sally.launcher.core.engine

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MinecraftGameLogTest {
    @Test
    fun redactsCommonCredentialFields() {
        val raw = "accessToken=mc-secret refresh_token: 'refresh-secret' Authorization: Bearer oauth-secret"
        val safe = MinecraftGameLog.sanitizeForDisplay(raw)

        assertFalse(safe.contains("mc-secret"))
        assertFalse(safe.contains("refresh-secret"))
        assertFalse(safe.contains("oauth-secret"))
        assertTrue(safe.contains("[REDACTED]"))
    }

    @Test
    fun redactsXboxUserTokenFormat() {
        val safe = MinecraftGameLog.sanitizeForDisplay("XBL3.0 x=the-xbox-token")
        assertFalse(safe.contains("the-xbox-token"))
        assertTrue(safe.contains("[REDACTED]"))
    }

    @Test
    fun leavesOrdinaryCrashDetailsReadable() {
        val safe = MinecraftGameLog.sanitizeForDisplay("java.lang.NoClassDefFoundError: org/lwjgl/glfw/GLFW")
        assertTrue(safe.contains("NoClassDefFoundError"))
        assertTrue(safe.contains("org/lwjgl/glfw/GLFW"))
    }
}
