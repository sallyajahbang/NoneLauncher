package com.sally.launcher.core.engine

import android.view.InputDevice
import android.view.KeyCharacterMap
import android.view.KeyEvent

/**
 * Converts Android keyboard/gamepad/mouse input into stable GLFW-shaped records and tracks held
 * inputs so focus loss cannot leave an input stuck. The queue is still a transport boundary;
 * actual GLFW callbacks are supplied by the platform engine adapter.
 */
class AndroidInputTranslator(
    private val enqueue: (Int, Int, Int, Float, Float, Float) -> Boolean = NativeEngineBridge::sendInput,
) {
    private data class HeldKey(val glfwCode: Int, val scanCode: Int, val modifiers: Int)

    private val heldKeyboardKeys = LinkedHashMap<Int, HeldKey>()
    private val heldGamepadButtons = LinkedHashMap<Int, Int>()
    private val heldMouseButtons = LinkedHashSet<Int>()
    private var lastMouseX = 0f
    private var lastMouseY = 0f

    fun sendKey(event: KeyEvent): Boolean {
        val action = GlfwInputMapping.keyAction(event.action, event.repeatCount)
        if (action < 0) return false

        val gamepadSource = event.isFromSource(InputDevice.SOURCE_GAMEPAD) ||
            event.isFromSource(InputDevice.SOURCE_JOYSTICK)
        val androidKey = event.keyCode
        val code = if (gamepadSource) GlfwInputMapping.gamepadButton(androidKey)
        else GlfwInputMapping.key(androidKey)
        if (code == GlfwInputMapping.KEY_UNKNOWN) return false

        val modifiers = GlfwInputMapping.modifiers(event.metaState)
        val scanCode = event.scanCode.coerceAtLeast(0)
        val eventType = if (gamepadSource) NativeEngineBridge.EVENT_GAMEPAD_BUTTON
        else NativeEngineBridge.EVENT_KEY
        val accepted = enqueue(
            eventType,
            code,
            action,
            if (gamepadSource) 0f else scanCode.toFloat(),
            0f,
            if (gamepadSource) 0f else modifiers.toFloat(),
        )
        if (!accepted) return false

        if (gamepadSource) {
            if (action == GlfwInputMapping.RELEASE) heldGamepadButtons.remove(androidKey)
            else heldGamepadButtons[androidKey] = code
        } else {
            if (action == GlfwInputMapping.RELEASE) heldKeyboardKeys.remove(androidKey)
            else heldKeyboardKeys[androidKey] = HeldKey(code, scanCode, modifiers)

            // GLFW's key callback does not replace its character callback. Queue Unicode separately
            // only for the initial key-down, never for release/repeat; skip Android dead-key accents.
            if (action == GlfwInputMapping.PRESS && event.isPrintingKey) {
                val unicode = event.getUnicodeChar(event.metaState)
                if (unicode > 0 && (unicode and KeyCharacterMap.COMBINING_ACCENT) == 0 && unicode <= 0x10FFFF) {
                    enqueue(
                        NativeEngineBridge.EVENT_CHAR,
                        unicode,
                        GlfwInputMapping.PRESS,
                        0f,
                        0f,
                        modifiers.toFloat(),
                    )
                }
            }
        }
        return true
    }

    fun sendMouseButton(
        androidButton: Int,
        pressed: Boolean,
        x: Float,
        y: Float,
        androidMetaState: Int = 0,
    ): Boolean {
        val glfwButton = GlfwInputMapping.mouseButton(androidButton)
        if (glfwButton == GlfwInputMapping.KEY_UNKNOWN) return false
        val action = if (pressed) GlfwInputMapping.PRESS else GlfwInputMapping.RELEASE
        val modifiers = GlfwInputMapping.modifiers(androidMetaState)
        lastMouseX = x
        lastMouseY = y
        val accepted = enqueue(
            NativeEngineBridge.EVENT_MOUSE_BUTTON,
            glfwButton,
            action,
            x,
            y,
            modifiers.toFloat(),
        )
        if (accepted) {
            if (pressed) heldMouseButtons.add(glfwButton) else heldMouseButtons.remove(glfwButton)
        }
        return accepted
    }

    fun sendGamepadAxis(androidAxis: Int, value: Float): Boolean {
        val axis = GlfwInputMapping.gamepadAxis(androidAxis)
        if (axis == GlfwInputMapping.KEY_UNKNOWN || !value.isFinite()) return false
        val normalized = if (axis >= 4) GlfwInputMapping.triggerToGlfw(value) else value.coerceIn(-1f, 1f)
        return enqueue(
            NativeEngineBridge.EVENT_GAMEPAD_AXIS, axis, GlfwInputMapping.PRESS,
            normalized, 0f, 0f,
        )
    }

    /** Try to release every held input. Keep any failed releases tracked for a future retry. */
    fun releaseAll() {
        val keyboardIterator = heldKeyboardKeys.iterator()
        while (keyboardIterator.hasNext()) {
            val entry = keyboardIterator.next()
            val key = entry.value
            if (enqueue(
                    NativeEngineBridge.EVENT_KEY, key.glfwCode, GlfwInputMapping.RELEASE,
                    key.scanCode.toFloat(), 0f, key.modifiers.toFloat(),
                )) keyboardIterator.remove()
        }

        val gamepadIterator = heldGamepadButtons.iterator()
        while (gamepadIterator.hasNext()) {
            val entry = gamepadIterator.next()
            if (enqueue(
                    NativeEngineBridge.EVENT_GAMEPAD_BUTTON, entry.value, GlfwInputMapping.RELEASE,
                    0f, 0f, 0f,
                )) gamepadIterator.remove()
        }

        val mouseIterator = heldMouseButtons.iterator()
        while (mouseIterator.hasNext()) {
            val button = mouseIterator.next()
            if (enqueue(
                    NativeEngineBridge.EVENT_MOUSE_BUTTON, button, GlfwInputMapping.RELEASE,
                    lastMouseX, lastMouseY, 0f,
                )) mouseIterator.remove()
        }
    }
}
