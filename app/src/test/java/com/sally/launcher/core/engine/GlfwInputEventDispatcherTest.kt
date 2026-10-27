package com.sally.launcher.core.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GlfwInputEventDispatcherTest {
    private class RecordingCallbacks : GlfwInputCallbacks {
        val calls = mutableListOf<String>()
        override fun onKey(key: Int, scanCode: Int, action: Int, modifiers: Int) {
            calls += "key:$key:$scanCode:$action:$modifiers"
        }
        override fun onChar(codePoint: Int, modifiers: Int) { calls += "char:$codePoint:$modifiers" }
        override fun onCursorPosition(x: Double, y: Double) { calls += "cursor:$x:$y" }
        override fun onCursorDelta(dx: Double, dy: Double) { calls += "delta:$dx:$dy" }
        override fun onMouseButton(button: Int, action: Int, modifiers: Int) {
            calls += "mouse:$button:$action:$modifiers"
        }
        override fun onScroll(xOffset: Double, yOffset: Double) { calls += "scroll:$xOffset:$yOffset" }
        override fun onWindowSize(width: Int, height: Int) { calls += "size:$width:$height" }
        override fun onGamepadButton(button: Int, action: Int) { calls += "padButton:$button:$action" }
        override fun onGamepadAxis(axis: Int, value: Float) { calls += "padAxis:$axis:$value" }
        override fun onTouch(pointerId: Int, action: Int, x: Float, y: Float, pressure: Float) {
            calls += "touch:$pointerId:$action:$x:$y:$pressure"
        }
        override fun onPointerCaptureChanged(captured: Boolean) { calls += "capture:$captured" }
    }

    private val callbacks = RecordingCallbacks()
    private val dispatcher = GlfwInputEventDispatcher(callbacks)

    @Test
    fun keyRecordPreservesScanCodeActionAndModifiers() {
        assertTrue(dispatcher.dispatch(NativeInputEvent(NativeEngineBridge.EVENT_KEY, 65, 2, 30f, 0f, 3f)))
        assertEquals(listOf("key:65:30:2:3"), callbacks.calls)
    }

    @Test
    fun charRecordPreservesUnicodeCodePointAndModifiers() {
        assertTrue(dispatcher.dispatch(NativeInputEvent(NativeEngineBridge.EVENT_CHAR, 0x1F642, 1, 0f, 0f, 1f)))
        assertEquals(listOf("char:128578:1"), callbacks.calls)
    }

    @Test
    fun pointerMoveDistinguishesAbsoluteAndRelativeMotion() {
        dispatcher.dispatch(NativeInputEvent(NativeEngineBridge.EVENT_POINTER_MOVE, 0, GlfwInputMapping.MOVE, 12f, 14f, 0f))
        dispatcher.dispatch(NativeInputEvent(NativeEngineBridge.EVENT_POINTER_MOVE, 0, GlfwInputMapping.RELATIVE_MOVE, -2f, 3f, 0f))
        assertEquals(listOf("cursor:12.0:14.0", "delta:-2.0:3.0"), callbacks.calls)
    }

    @Test
    fun mouseDispatchesCursorPositionBeforeButtonAndPreservesModifiers() {
        dispatcher.dispatch(NativeInputEvent(NativeEngineBridge.EVENT_MOUSE_BUTTON, 1, 0, 8f, 9f, 5f))
        assertEquals(listOf("cursor:8.0:9.0", "mouse:1:0:5"), callbacks.calls)
    }

    @Test
    fun routesScrollViewportGamepadTouchAndCapture() {
        dispatcher.dispatch(NativeInputEvent(NativeEngineBridge.EVENT_SCROLL, 0, 1, 1f, -2f, 0f))
        dispatcher.dispatch(NativeInputEvent(NativeEngineBridge.EVENT_VIEWPORT, 0, 0, 1280f, 720f, 0f))
        dispatcher.dispatch(NativeInputEvent(NativeEngineBridge.EVENT_GAMEPAD_AXIS, 2, 1, .5f, 0f, 0f))
        dispatcher.dispatch(NativeInputEvent(NativeEngineBridge.EVENT_GAMEPAD_BUTTON, 3, 1, 0f, 0f, 0f))
        dispatcher.dispatch(NativeInputEvent(NativeEngineBridge.EVENT_TOUCH, 7, 2, 6f, 4f, .8f))
        dispatcher.dispatch(NativeInputEvent(NativeEngineBridge.EVENT_CURSOR_CAPTURE, 0, 1, 0f, 0f, 0f))
        assertEquals(
            listOf("scroll:1.0:-2.0", "size:1280:720", "padAxis:2:0.5", "padButton:3:1", "touch:7:2:6.0:4.0:0.8", "capture:true"),
            callbacks.calls,
        )
    }

    @Test
    fun unknownEventTypeIsNotDispatched() {
        assertFalse(dispatcher.dispatch(NativeInputEvent(999, 0, 0, 0f, 0f, 0f)))
        assertTrue(callbacks.calls.isEmpty())
    }
}
