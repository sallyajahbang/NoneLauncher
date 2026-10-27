package com.sally.launcher.core.engine

/**
 * Converts Android input codes to the stable GLFW 3 key/button/action values.
 * The file deliberately has no Android imports so its mapping can be unit-smoke-tested on the host.
 * Unknown Android key codes map to GLFW_KEY_UNKNOWN (-1), never to an unrelated key.
 */
object GlfwInputMapping {
    const val KEY_UNKNOWN = -1
    const val RELEASE = 0
    const val PRESS = 1
    const val REPEAT = 2
    const val MOVE = 2
    /** Event action used when MotionEvent coordinates carry relative pointer deltas. */
    const val RELATIVE_MOVE = 3

    // GLFW key values follow the GLFW 3 public header.
    fun key(androidKeyCode: Int): Int = when (androidKeyCode) {
        in 7..16 -> 48 + (androidKeyCode - 7) // Android KEYCODE_0..KEYCODE_9
        in 29..54 -> 65 + (androidKeyCode - 29) // Android KEYCODE_A..KEYCODE_Z
        19 -> 265 // DPAD_UP
        20 -> 264 // DPAD_DOWN
        21 -> 263 // DPAD_LEFT
        22 -> 262 // DPAD_RIGHT
        55 -> 44 // COMMA
        56 -> 46 // PERIOD
        57 -> 342 // LEFT ALT
        58 -> 346 // RIGHT ALT
        59 -> 340 // LEFT SHIFT
        60 -> 344 // RIGHT SHIFT
        61 -> 258 // TAB
        62 -> 32 // SPACE
        66, 160 -> 257 // ENTER / NUMPAD_ENTER
        67 -> 259 // BACKSPACE
        68 -> 96 // GRAVE_ACCENT
        69 -> 45 // MINUS
        70 -> 61 // EQUAL
        71 -> 91 // LEFT_BRACKET
        72 -> 93 // RIGHT_BRACKET
        73 -> 92 // BACKSLASH
        74 -> 59 // SEMICOLON
        75 -> 39 // APOSTROPHE
        76 -> 47 // SLASH
        81 -> 61 // PLUS key maps to EQUAL; shift is represented in modifiers
        92 -> 266 // PAGE_UP
        93 -> 267 // PAGE_DOWN
        111 -> 256 // ESCAPE
        112 -> 261 // FORWARD_DELETE
        113 -> 341 // LEFT_CONTROL
        114 -> 345 // RIGHT_CONTROL
        115 -> 280 // CAPS_LOCK
        116 -> 281 // SCROLL_LOCK
        120 -> 283 // PRINT_SCREEN
        121 -> 284 // PAUSE
        122 -> 268 // MOVE_HOME
        123 -> 269 // MOVE_END
        124 -> 260 // INSERT
        in 131..142 -> 290 + (androidKeyCode - 131) // F1..F12
        143 -> 282 // NUM_LOCK
        in 144..153 -> 320 + (androidKeyCode - 144) // NUMPAD_0..NUMPAD_9
        154 -> 331 // NUMPAD_DIVIDE
        155 -> 332 // NUMPAD_MULTIPLY
        156 -> 333 // NUMPAD_SUBTRACT
        157 -> 334 // NUMPAD_ADD
        158, 159 -> 330 // NUMPAD_DOT / NUMPAD_COMMA
        161 -> 336 // NUMPAD_EQUAL
        82 -> 348 // MENU
        else -> KEY_UNKNOWN
    }

    /** Android KeyEvent actions are DOWN=0 and UP=1. */
    fun keyAction(androidAction: Int, repeatCount: Int): Int = when (androidAction) {
        0 -> if (repeatCount > 0) REPEAT else PRESS
        1 -> RELEASE
        else -> -1
    }

    /** Android meta state to GLFW modifier bits (SHIFT=1, CTRL=2, ALT=4, SUPER=8, CAPS=16, NUM=32). */
    fun modifiers(androidMetaState: Int): Int {
        var result = 0
        if (androidMetaState and (0x1 or 0x40 or 0x80) != 0) result = result or 0x0001
        if (androidMetaState and (0x1000 or 0x2000 or 0x4000) != 0) result = result or 0x0002
        if (androidMetaState and (0x2 or 0x10 or 0x20) != 0) result = result or 0x0004
        if (androidMetaState and (0x10000 or 0x20000 or 0x40000) != 0) result = result or 0x0008
        if (androidMetaState and 0x100000 != 0) result = result or 0x0010
        if (androidMetaState and 0x200000 != 0) result = result or 0x0020
        return result
    }

    /** Android BUTTON_* masks mapped to GLFW mouse button indices. */
    fun mouseButton(androidButton: Int): Int = when (androidButton) {
        1 -> 0 // BUTTON_PRIMARY -> GLFW_MOUSE_BUTTON_LEFT
        2 -> 1 // BUTTON_SECONDARY -> GLFW_MOUSE_BUTTON_RIGHT
        4 -> 2 // BUTTON_TERTIARY -> GLFW_MOUSE_BUTTON_MIDDLE
        8 -> 3 // BUTTON_BACK
        16 -> 4 // BUTTON_FORWARD
        else -> KEY_UNKNOWN
    }

    /** Standard Android gamepad button keycodes mapped to GLFW_GAMEPAD_BUTTON_* values. */
    fun gamepadButton(androidKeyCode: Int): Int = when (androidKeyCode) {
        19 -> 11 // DPAD_UP
        22 -> 12 // DPAD_RIGHT
        20 -> 13 // DPAD_DOWN
        21 -> 14 // DPAD_LEFT
        96 -> 0  // A
        97 -> 1  // B
        99 -> 2  // X
        100 -> 3 // Y
        102 -> 4 // LEFT_BUMPER
        103 -> 5 // RIGHT_BUMPER
        109 -> 6 // BACK / SELECT
        108 -> 7 // START
        110 -> 8 // GUIDE / MODE
        106 -> 9 // LEFT_THUMB
        107 -> 10 // RIGHT_THUMB
        else -> KEY_UNKNOWN
    }

    /** Android joystick axes mapped to GLFW gamepad axes; -1 means unsupported. */
    fun gamepadAxis(androidAxis: Int): Int = when (androidAxis) {
        0 -> 0  // AXIS_X -> LEFT_X
        1 -> 1  // AXIS_Y -> LEFT_Y
        12 -> 2 // AXIS_RX -> RIGHT_X
        13 -> 3 // AXIS_RY -> RIGHT_Y
        17 -> 4 // AXIS_LTRIGGER -> LEFT_TRIGGER
        18 -> 5 // AXIS_RTRIGGER -> RIGHT_TRIGGER
        else -> KEY_UNKNOWN
    }

    fun triggerToGlfw(androidValue: Float): Float = (androidValue.coerceIn(0f, 1f) * 2f) - 1f
}
