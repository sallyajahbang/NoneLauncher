package com.sally.unilauncher.core.instance

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class InstanceScannerTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private fun writeVersion(gameDir: java.io.File, id: String, json: String) {
        val dir = java.io.File(gameDir, "versions/$id").apply { mkdirs() }
        java.io.File(dir, "$id.json").writeText(json)
    }

    @Test
    fun `reads id, type and mainClass from version json`() {
        val game = tmp.newFolder(".minecraft")
        writeVersion(game, "1.21.1", """{"type":"release","mainClass":"net.minecraft.client.main.Main"}""")

        val result = InstanceScanner(game).scan()

        assertEquals(1, result.size)
        assertEquals("1.21.1", result[0].id)
        assertEquals("release", result[0].type)
        assertEquals("net.minecraft.client.main.Main", result[0].mainClass)
        assertNull(result[0].inheritsFrom)
    }

    @Test
    fun `skips broken json and folders without json`() {
        val game = tmp.newFolder(".minecraft")
        writeVersion(game, "good", """{"type":"release"}""")
        writeVersion(game, "broken", "{not json")
        java.io.File(game, "versions/empty").mkdirs()

        val result = InstanceScanner(game).scan()

        assertEquals(listOf("good"), result.map { it.id })
    }

    @Test
    fun `returns empty list when versions folder is missing`() {
        val game = tmp.newFolder(".minecraft")
        assertEquals(0, InstanceScanner(game).scan().size)
    }

    @Test
    fun `sorts by id case-insensitively`() {
        val game = tmp.newFolder(".minecraft")
        writeVersion(game, "b-version", "{}")
        writeVersion(game, "A-version", "{}")

        val ids = InstanceScanner(game).scan().map { it.id }

        assertEquals(listOf("A-version", "b-version"), ids)
    }
}
