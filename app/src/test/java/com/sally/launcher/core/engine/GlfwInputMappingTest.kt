package com.sally.launcher.core.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/** Host-side contract tests for Android -> GLFW code translation. */
class GlfwInputMappingTest {
    @Test
    fun lettersDigitsAndFunctionKeysUseGlfwCodes() {
        assertEquals(65, GlfwInputMapping.key(29)) // Android A -> GLFW A
        assertEquals(90, GlfwInputMapping.key(54)) // Android Z -> GLFW Z
        assertEquals(48, GlfwInputMapping.key(7))  // Android 0 -> GLFW 0
        assertEquals(57, GlfwInputMapping.key(16)) // Android 9 -> GLFW 9
        assertEquals(290, GlfwInputMapping.key(131)) // Android F1
        assertEquals(301, GlfwInputMapping.key(142)) // Android F12
    }

    @Test
    fun navigationAndEditingKeysUseGlfwCodes() {
        assertEquals(256, GlfwInputMapping.key(111)) // Escape
        assertEquals(257, GlfwInputMapping.key(66))  // Enter
        assertEquals(259, GlfwInputMapping.key(67))  // Backspace
        assertEquals(261, GlfwInputMapping.key(112)) // Delete
        assertEquals(263, GlfwInputMapping.key(21))  // Left
        assertEquals(262, GlfwInputMapping.key(22))  // Right
        assertEquals(268, GlfwInputMapping.key(122)) // Home
        assertEquals(269, GlfwInputMapping.key(123)) // End
    }

    @Test
    fun keyActionsDistinguishPressRepeatReleaseAndRejectUnknown() {
        assertEquals(GlfwInputMapping.PRESS, GlfwInputMapping.keyAction(0, 0))
        assertEquals(GlfwInputMapping.REPEAT, GlfwInputMapping.keyAction(0, 1))
        assertEquals(GlfwInputMapping.REPEAT, GlfwInputMapping.keyAction(0, 4))
        assertEquals(GlfwInputMapping.RELEASE, GlfwInputMapping.keyAction(1, 0))
        assertEquals(-1, GlfwInputMapping.keyAction(2, 0))
        assertEquals(GlfwInputMapping.KEY_UNKNOWN, GlfwInputMapping.key(9999))
    }

    @Test
    fun mouseButtonsMapToGlfwIndicesRatherThanAndroidMasks() {
        assertEquals(0, GlfwInputMapping.mouseButton(1))
        assertEquals(1, GlfwInputMapping.mouseButton(2))
        assertEquals(2, GlfwInputMapping.mouseButton(4))
        assertEquals(3, GlfwInputMapping.mouseButton(8))
        assertEquals(4, GlfwInputMapping.mouseButton(16))
        assertEquals(GlfwInputMapping.KEY_UNKNOWN, GlfwInputMapping.mouseButton(32))
    }

    @Test
    fun gamepadButtonsAndAxesMapToStableGlfwIndices() {
        assertEquals(0, GlfwInputMapping.gamepadButton(96))  // A
        assertEquals(1, GlfwInputMapping.gamepadButton(97))  // B
        assertEquals(11, GlfwInputMapping.gamepadButton(19)) // D-pad up
        assertEquals(0, GlfwInputMapping.gamepadAxis(0))
        assertEquals(1, GlfwInputMapping.gamepadAxis(1))
        assertEquals(2, GlfwInputMapping.gamepadAxis(12))
        assertEquals(3, GlfwInputMapping.gamepadAxis(13))
        assertEquals(4, GlfwInputMapping.gamepadAxis(17))
        assertEquals(5, GlfwInputMapping.gamepadAxis(18))
        assertEquals(GlfwInputMapping.KEY_UNKNOWN, GlfwInputMapping.gamepadAxis(99))
    }

    @Test
    fun triggerValuesNormalizeToGlfwRangeAndCaptureHasDistinctAction() {
        assertEquals(-1f, GlfwInputMapping.triggerToGlfw(0f), 0.0001f)
        assertEquals(0f, GlfwInputMapping.triggerToGlfw(0.5f), 0.0001f)
        assertEquals(1f, GlfwInputMapping.triggerToGlfw(1f), 0.0001f)
        assertEquals(-1f, GlfwInputMapping.triggerToGlfw(-5f), 0.0001f)
        assertEquals(3, GlfwInputMapping.RELATIVE_MOVE)
        assertNotEquals(GlfwInputMapping.MOVE.toLong(), GlfwInputMapping.RELATIVE_MOVE.toLong())
    }
}
