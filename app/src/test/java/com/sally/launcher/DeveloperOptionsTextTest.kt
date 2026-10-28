package com.sally.launcher

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DeveloperOptionsTextTest {
    private val actions = DeveloperOptionsText.actions

    @Test
    fun exposesTheDiagnosticsInTheExpectedOrder() {
        assertEquals(
            listOf("prepare", "probe", "smoke", "graphics", "argv", "pojav"),
            actions.map { it.id },
        )
    }

    @Test
    fun everyTitleAndDescriptionIsNonBlank() {
        actions.forEach { action ->
            assertFalse("title blank for ${action.id}", action.title.isBlank())
            assertFalse("description blank for ${action.id}", action.description.isBlank())
        }
    }

    @Test
    fun descriptionsEndWithAPeriod() {
        actions.forEach { action ->
            assertTrue("description of ${action.id} must end with '.'", action.description.endsWith("."))
        }
    }

    @Test
    fun idsAndTitlesAreUnique() {
        assertEquals(actions.size, actions.map { it.id }.toSet().size)
        assertEquals(actions.size, actions.map { it.title }.toSet().size)
    }

    @Test
    fun busyKeysMatchTheirActionOrAreNullForInlineTests() {
        assertEquals("prepare", actions.first { it.id == "prepare" }.busyKey)
        assertEquals("probe", actions.first { it.id == "probe" }.busyKey)
        assertEquals("smoke", actions.first { it.id == "smoke" }.busyKey)
        assertNull(actions.first { it.id == "graphics" }.busyKey)
        assertNull(actions.first { it.id == "argv" }.busyKey)
        assertNull(actions.first { it.id == "pojav" }.busyKey)
    }

    @Test
    fun probeDescriptionStatesItDoesNotLaunchMinecraft() {
        val probe = actions.first { it.id == "probe" }
        assertTrue(probe.description.contains("Tidak menjalankan Minecraft"))
    }

    @Test
    fun sectionLabelsAreFixed() {
        assertEquals("Developer", DeveloperOptionsText.GROUP_TITLE)
        assertEquals("Matikan mode developer", DeveloperOptionsText.DISABLE_LABEL)
        assertEquals("Ketuk 7 kali untuk mode developer.", DeveloperOptionsText.ENABLE_HINT)
    }

    @Test
    fun actionLookupByIdFindsEveryEntry() {
        listOf("prepare", "probe", "smoke", "graphics", "argv", "pojav").forEach { id ->
            assertNotNull(actions.firstOrNull { it.id == id })
        }
    }
}
