package com.sally.launcher.core.engine

/**
 * Callback surface shaped like the input callbacks a GLFW/LWJGL platform adapter needs.
 * Implementations must run on the engine/render thread. This interface deliberately avoids
 * a compile-time dependency on a particular LWJGL build so the launcher can select its actual
 * Android-compatible implementation when that engine is packaged.
 */
interface GlfwInputCallbacks {
    fun onKey(key: Int, scanCode: Int, action: Int, modifiers: Int)
    fun onChar(codePoint: Int, modifiers: Int)
    fun onCursorPosition(x: Double, y: Double)
    fun onCursorDelta(dx: Double, dy: Double)
    fun onMouseButton(button: Int, action: Int, modifiers: Int)
    fun onScroll(xOffset: Double, yOffset: Double)
    fun onWindowSize(width: Int, height: Int)
    fun onGamepadButton(button: Int, action: Int)
    fun onGamepadAxis(axis: Int, value: Float)
    fun onTouch(pointerId: Int, action: Int, x: Float, y: Float, pressure: Float)
    fun onPointerCaptureChanged(captured: Boolean)
}

/**
 * Decodes stable native queue records into callback-shaped input on the caller's thread.
 * The current diagnostics screen injects a trace callback; an Android GLFW engine port can
 * implement [GlfwInputCallbacks] without changing Android event collection or the JNI queue.
 */
class GlfwInputEventDispatcher(private val callbacks: GlfwInputCallbacks) {
    /** Returns false only for an unrecognized event type. Callback failures are propagated to the pump. */
    fun dispatch(event: NativeInputEvent): Boolean {
        when (event.type) {
            NativeEngineBridge.EVENT_TOUCH -> callbacks.onTouch(
                event.code, event.action, event.x, event.y, event.value,
            )
            NativeEngineBridge.EVENT_KEY -> callbacks.onKey(
                event.code, positiveInt(event.x), event.action, positiveInt(event.value),
            )
            NativeEngineBridge.EVENT_CHAR -> callbacks.onChar(event.code, positiveInt(event.value))
            NativeEngineBridge.EVENT_POINTER_MOVE -> {
                if (event.action == GlfwInputMapping.RELATIVE_MOVE) {
                    callbacks.onCursorDelta(event.x.toDouble(), event.y.toDouble())
                } else {
                    callbacks.onCursorPosition(event.x.toDouble(), event.y.toDouble())
                }
            }
            NativeEngineBridge.EVENT_MOUSE_BUTTON -> {
                // Pojav's callback bridge updates cursor position before sending a mouse-button event.
                callbacks.onCursorPosition(event.x.toDouble(), event.y.toDouble())
                callbacks.onMouseButton(event.code, event.action, positiveInt(event.value))
            }
            NativeEngineBridge.EVENT_SCROLL -> callbacks.onScroll(event.x.toDouble(), event.y.toDouble())
            NativeEngineBridge.EVENT_VIEWPORT -> callbacks.onWindowSize(
                nonNegativeInt(event.x), nonNegativeInt(event.y),
            )
            NativeEngineBridge.EVENT_GAMEPAD_AXIS -> callbacks.onGamepadAxis(event.code, event.x)
            NativeEngineBridge.EVENT_GAMEPAD_BUTTON -> callbacks.onGamepadButton(event.code, event.action)
            NativeEngineBridge.EVENT_CURSOR_CAPTURE -> callbacks.onPointerCaptureChanged(
                event.action == GlfwInputMapping.PRESS,
            )
            else -> return false
        }
        return true
    }

    private fun positiveInt(value: Float): Int = value.toInt().coerceAtLeast(0)
    private fun nonNegativeInt(value: Float): Int = value.toInt().coerceAtLeast(0)
}


/** Diagnostic implementation used until a real Android-compatible GLFW callback backend is bundled. */
class DiagnosticGlfwInputCallbacks(
    private val onTrace: (String) -> Unit,
) : GlfwInputCallbacks {
    override fun onKey(key: Int, scanCode: Int, action: Int, modifiers: Int) =
        onTrace("key=$key scan=$scanCode action=$action mods=$modifiers")

    override fun onChar(codePoint: Int, modifiers: Int) =
        onTrace("char=U+${codePoint.toString(16).uppercase()} mods=$modifiers")

    override fun onCursorPosition(x: Double, y: Double) = onTrace("cursor=($x,$y)")
    override fun onCursorDelta(dx: Double, dy: Double) = onTrace("cursorDelta=($dx,$dy)")

    override fun onMouseButton(button: Int, action: Int, modifiers: Int) =
        onTrace("mouse=$button action=$action mods=$modifiers")

    override fun onScroll(xOffset: Double, yOffset: Double) = onTrace("scroll=($xOffset,$yOffset)")
    override fun onWindowSize(width: Int, height: Int) = onTrace("viewport=${width}x$height")
    override fun onGamepadButton(button: Int, action: Int) = onTrace("gamepadButton=$button action=$action")
    override fun onGamepadAxis(axis: Int, value: Float) = onTrace("gamepadAxis=$axis value=$value")

    override fun onTouch(pointerId: Int, action: Int, x: Float, y: Float, pressure: Float) =
        onTrace("touch=$pointerId action=$action pos=($x,$y) pressure=$pressure")

    override fun onPointerCaptureChanged(captured: Boolean) = onTrace("pointerCapture=$captured")
}
