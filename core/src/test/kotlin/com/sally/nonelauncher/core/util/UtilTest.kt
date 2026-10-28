package com.sally.nonelauncher.core.util

import com.sally.nonelauncher.core.auth.OfflineAccount
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class UtilTest {

    @Test
    fun `sha1 matches a known digest`() {
        val f = File.createTempFile("sha", ".txt")
        f.writeText("abc")
        assertEquals("a9993e364706816aba3e25717850c26c9cd0d89d", Hashing.sha1(f))
        assertTrue(Hashing.matchesSha1(f, "A9993E364706816ABA3E25717850C26C9CD0D89D"))
        assertFalse(Hashing.matchesSha1(f, "0000"))
        f.delete()
    }

    @Test
    fun `missing file never matches a non-null hash`() {
        assertFalse(Hashing.matchesSha1(File("does-not-exist.jar"), "abc"))
        assertTrue(Hashing.matchesSha1(File("does-not-exist.jar"), null))
    }

    @Test
    fun `offline uuid is stable and version 3`() {
        // Well-known value for the name "Notch" under the offline-mode derivation.
        assertEquals("b50ad385-829d-3141-a216-7e7d7539ba7f", OfflineAccount.offlineUuid("Notch"))
    }
}
