package com.sally.launcher.core.engine

import android.view.KeyEvent

/**
 * Converts Android keyboard/gamepad key events into GLFW-compatible records and tracks held
 * inputs so window focus loss or pause can release them instead of leaving stuck keys/buttons.
 * This is an input transport boundary; it does not call GLFW until an Android-compatible GLFW
 * implementation is wired into the native engine.
 */
class AndroidInputTranslator {
    private val heldKeyboardKeys = LinkedHashMap<Int, Int>()
    private val heldGamepadButtons = LinkedHashMap<Int, Int>()
    private val heldMouseButtons = LinkedHashSet<Int>()

    fun sendKey(event: KeyEvent): Boolean {
        val action = GlfwInputMapping.keyAction(event.action, event.repeatCount)
        if (action < 0) return false

        val gamepadSource = event.isFromSource(android.view.InputDevice.SOURCE_GAMEPAD) ||
            event.isFromSource(android.view.InputDevice.SOURCE_JOYSTICK)
        val androidKey = event.keyCode
        val code = if (gamepadSource) GlfwInputMapping.gamepadButton(androidKey)
        else GlfwInputMapping.key(androidKey)
        if (code == GlfwInputMapping.KEY_UNKNOWN) return false

        val eventType = if (gamepadSource) NativeEngineBridge.EVENT_GAMEPAD_BUTTON
        else NativeEngineBridge.EVENT_KEY
        val accepted = NativeEngineBridge.sendInput(
            eventType,
            code,
            action,
            0f,
            0f,
            GlfwInputMapping.modifiers(event.metaState).toFloat(),
        )
        if (!accepted) return false

        val held = if (gamepadSource) heldGamepadButtons else heldKeyboardKeys
        if (action == GlfwInputMapping.RELEASE) held.remove(androidKey)
        else held[androidKey] = code
        return true
    }

    fun sendMouseButton(androidButton: Int, pressed: Boolean, x: Float, y: Float): Boolean {
        val glfwButton = GlfwInputMapping.mouseButton(androidButton)
        if (glfwButton == GlfwInputMapping.KEY_UNKNOWN) return false
        val action = if (pressed) GlfwInputMapping.PRESS else GlfwInputMapping.RELEASE
        val accepted = NativeEngineBridge.sendInput(
            NativeEngineBridge.EVENT_MOUSE_BUTTON, glfwButton, action, x, y, 0f,
        )
        if (accepted) {
            if (pressed) heldMouseButtons.add(glfwButton) else heldMouseButtons.remove(glfwButton)
        }
        return accepted
    }

    fun sendGamepadAxis(androidAxis: Int, value: Float): Boolean {
        val axis = GlfwInputMapping.gamepadAxis(androidAxis)
        if (axis == GlfwInputMapping.KEY_UNKNOWN) return false
        val normalized = if (axis >= 4) GlfwInputMapping.triggerToGlfw(value) else value.coerceIn(-1f, 1f)
        return NativeEngineBridge.sendInput(
            NativeEngineBridge.EVENT_GAMEPAD_AXIS, axis, GlfwInputMapping.PRESS,
            normalized, 0f, 0f,
        )
    }

    /** Try to release every held input. Keep any failed releases tracked for a future retry. */
    fun releaseAll() {
        val keyboardIterator = heldKeyboardKeys.iterator()
        while (keyboardIterator.hasNext()) {
            val entry = keyboardIterator.next()
            if (NativeEngineBridge.sendInput(
                    NativeEngineBridge.EVENT_KEY, entry.value, GlfwInputMapping.RELEASE,
                    0f, 0f, 0f,
                )) keyboardIterator.remove()
        }

        val gamepadIterator = heldGamepadButtons.iterator()
        while (gamepadIterator.hasNext()) {
            val entry = gamepadIterator.next()
            if (NativeEngineBridge.sendInput(
                    NativeEngineBridge.EVENT_GAMEPAD_BUTTON, entry.value, GlfwInputMapping.RELEASE,
                    0f, 0f, 0f,
                )) gamepadIterator.remove()
        }

        val mouseIterator = heldMouseButtons.iterator()
        while (mouseIterator.hasNext()) {
            val button = mouseIterator.next()
            if (NativeEngineBridge.sendInput(
                    NativeEngineBridge.EVENT_MOUSE_BUTTON, button, GlfwInputMapping.RELEASE,
                    0f, 0f, 0f,
                )) mouseIterator.remove()
        }
    }
}
