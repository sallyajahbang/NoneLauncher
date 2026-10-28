package com.sally.nonelauncher.core.runtime

import com.sally.nonelauncher.core.LauncherPaths
import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RuntimeInstallerTest {

    private fun tarOf(vararg files: Triple<String, String, Int>): ByteArray {
        val bos = ByteArrayOutputStream()
        TarArchiveOutputStream(bos).use { tar ->
            for ((name, content, mode) in files) {
                val data = content.toByteArray()
                val entry = TarArchiveEntry(name).apply {
                    size = data.size.toLong()
                    this.mode = mode
                }
                tar.putArchiveEntry(entry)
                tar.write(data)
                tar.closeArchiveEntry()
            }
        }
        return bos.toByteArray()
    }

    private fun tempPaths(): LauncherPaths =
        LauncherPaths(File(System.getProperty("java.io.tmpdir"), "nl-rt-${System.nanoTime()}"))

    @Test
    fun `installs runtime files and keeps the executable bit`() {
        val paths = tempPaths()
        val tar = tarOf(
            Triple("bin/", "", 0x1ED),
            Triple("bin/java", "BIN", 0x1ED),
            Triple("lib/libjli.so", "LIB", 0x1A4),
        )
        val home = RuntimeInstaller.install(21, "2", paths) { ByteArrayInputStream(tar) }

        assertEquals(paths.runtimeHome(21), home)
        assertEquals("BIN", File(home, "bin/java").readText())
        assertEquals("LIB", File(home, "lib/libjli.so").readText())
        assertTrue(File(home, "bin/java").canExecute())
        assertFalse(File(home, "lib/libjli.so").canExecute())
        assertEquals(File(home, "bin/java").absolutePath, paths.javaExecutable(21).absolutePath)
    }

    @Test
    fun `does not reinstall when the same version is already present`() {
        val paths = tempPaths()
        val tar = tarOf(Triple("bin/java", "BIN", 0x1ED))
        RuntimeInstaller.install(21, "2", paths) { ByteArrayInputStream(tar) }

        var opened = 0
        RuntimeInstaller.install(21, "2", paths) { opened++; ByteArrayInputStream(tar) }
        assertEquals(0, opened)
    }

    @Test
    fun `reinstalls when the bundled version changes`() {
        val paths = tempPaths()
        RuntimeInstaller.install(21, "2", paths) { ByteArrayInputStream(tarOf(Triple("bin/java", "OLD", 0x1ED))) }

        RuntimeInstaller.install(21, "3", paths) { ByteArrayInputStream(tarOf(Triple("bin/java", "NEW", 0x1ED))) }
        assertEquals("NEW", File(paths.runtimeHome(21), "bin/java").readText())
    }

    @Test
    fun `rejects entries that escape the destination`() {
        val paths = tempPaths()
        val tar = tarOf(Triple("../evil.txt", "x", 0x1A4))
        assertFailsWith<IllegalArgumentException> {
            RuntimeInstaller.install(21, "2", paths) { ByteArrayInputStream(tar) }
        }
        assertFalse(File(paths.runtimes, "evil.txt").exists())
    }
}
