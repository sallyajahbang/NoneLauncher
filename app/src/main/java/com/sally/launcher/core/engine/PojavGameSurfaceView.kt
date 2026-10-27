package com.sally.launcher.core.engine

import android.content.Context
import android.os.Build
import android.view.InputDevice
import android.view.KeyCharacterMap
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.SurfaceHolder
import android.view.SurfaceView
import org.lwjgl.glfw.CallbackBridge
import net.kdt.pojavlaunch.utils.JREUtils
import java.io.File

/**
 * Android surface/input endpoint for the Pojav-compatible engine.
 * This component supplies the surface and event path; it does not start Minecraft by itself.
 */
class PojavGameSurfaceView(
    context: Context,
    private val minecraftRoot: File = File(context.filesDir, "minecraft"),
    private val status: (String) -> Unit = {},
) : SurfaceView(context), SurfaceHolder.Callback {
    private var nativeLibraryLoaded = false
    private var surfaceBound = false
    private var pointerX = 0f
    private var pointerY = 0f
    private var surfaceWidth = 0
    private var surfaceHeight = 0

    init {
        isFocusable = true
        isFocusableInTouchMode = true
        holder.addCallback(this)
        CallbackBridge.setGrabStateListener { grabbing ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                post {
                    if (grabbing && hasWindowFocus()) requestPointerCapture()
                    else if (hasPointerCapture()) releasePointerCapture()
                }
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            setOnCapturedPointerListener(object : android.view.View.OnCapturedPointerListener {
                override fun onCapturedPointer(view: android.view.View, event: MotionEvent): Boolean {
                    if (event.actionMasked != MotionEvent.ACTION_MOVE) return false
                    val dx = event.getAxisValue(MotionEvent.AXIS_RELATIVE_X)
                    val dy = event.getAxisValue(MotionEvent.AXIS_RELATIVE_Y)
                    if (dx == 0f && dy == 0f) return true
                    pointerX = (pointerX + dx).coerceIn(0f, surfaceWidth.toFloat().coerceAtLeast(1f))
                    pointerY = (pointerY + dy).coerceIn(0f, surfaceHeight.toFloat().coerceAtLeast(1f))
                    CallbackBridge.sendCursorPos(pointerX, pointerY)
                    return true
                }
            })
        }
    }

    override fun surfaceCreated(holder: SurfaceHolder) {
        val result = PojavEngineBootstrap.prepare(context, minecraftRoot)
        nativeLibraryLoaded = result.ready
        status(result.details)
        if (!result.ready) return
        runCatching {
            JREUtils.setupBridgeWindow(holder.surface)
            surfaceBound = true
            requestFocus()
            if (surfaceWidth > 0 && surfaceHeight > 0) {
                CallbackBridge.sendScreenSize(surfaceWidth, surfaceHeight)
            }
            status("Pojav JNI bridge loaded and Android surface bound. This diagnostic does not start Minecraft.")
        }.onFailure {
            surfaceBound = false
            status("Could not bind Android surface to Pojav: ${it.message ?: it.javaClass.simpleName}")
        }
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        surfaceWidth = width.coerceAtLeast(1)
        surfaceHeight = height.coerceAtLeast(1)
        pointerX = pointerX.coerceIn(0f, surfaceWidth.toFloat())
        pointerY = pointerY.coerceIn(0f, surfaceHeight.toFloat())
        if (nativeLibraryLoaded && surfaceBound) CallbackBridge.sendScreenSize(surfaceWidth, surfaceHeight)
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        CallbackBridge.setGrabStateListener(null)
        if (surfaceBound) runCatching { JREUtils.releaseBridgeWindow() }
        surfaceBound = false
        nativeLibraryLoaded = false
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val isGamepad = event.isFromSource(InputDevice.SOURCE_GAMEPAD) ||
            event.isFromSource(InputDevice.SOURCE_JOYSTICK)
        val action = when (event.action) {
            KeyEvent.ACTION_DOWN -> if (event.repeatCount > 0) GlfwInputMapping.REPEAT else GlfwInputMapping.PRESS
            KeyEvent.ACTION_UP -> GlfwInputMapping.RELEASE
            else -> return super.dispatchKeyEvent(event)
        }
        // Calling a registered native method before System.loadLibrary/ JNI_OnLoad would crash.
        if (!nativeLibraryLoaded) return super.dispatchKeyEvent(event)
        if (isGamepad) {
            val button = GlfwInputMapping.gamepadButton(event.keyCode)
            if (button == GlfwInputMapping.KEY_UNKNOWN) return super.dispatchKeyEvent(event)
            CallbackBridge.sendGamepadButton(button, action != GlfwInputMapping.RELEASE)
            return true
        }

        val key = GlfwInputMapping.key(event.keyCode)
        if (key == GlfwInputMapping.KEY_UNKNOWN) return super.dispatchKeyEvent(event)
        val rawCharacter = if (action == GlfwInputMapping.PRESS && event.isPrintingKey) {
            event.getUnicodeChar(event.metaState).takeIf { it > 0 && (it and KeyCharacterMap.COMBINING_ACCENT) == 0 } ?: 0
        } else 0
        CallbackBridge.sendKey(
            key,
            event.scanCode.coerceAtLeast(0),
            action,
            GlfwInputMapping.modifiers(event.metaState),
            rawCharacter,
        )
        return true
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!nativeLibraryLoaded || CallbackBridge.isGrabbing) return super.onTouchEvent(event)
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                pointerX = event.x
                pointerY = event.y
                CallbackBridge.sendCursorPos(pointerX, pointerY)
                CallbackBridge.sendMouseButton(0, GlfwInputMapping.PRESS, GlfwInputMapping.modifiers(event.metaState))
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                pointerX = event.x
                pointerY = event.y
                CallbackBridge.sendCursorPos(pointerX, pointerY)
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                pointerX = event.x
                pointerY = event.y
                CallbackBridge.sendCursorPos(pointerX, pointerY)
                CallbackBridge.sendMouseButton(0, GlfwInputMapping.RELEASE, GlfwInputMapping.modifiers(event.metaState))
                return true
            }
        }
        return true
    }

    override fun dispatchGenericMotionEvent(event: MotionEvent): Boolean {
        if (!nativeLibraryLoaded) return super.dispatchGenericMotionEvent(event)
        val sources = event.source
        val isGamepad = sources and InputDevice.SOURCE_JOYSTICK == InputDevice.SOURCE_JOYSTICK
        if (isGamepad) {
            CallbackBridge.sendGamepadAxis(0, event.getAxisValue(MotionEvent.AXIS_X))
            CallbackBridge.sendGamepadAxis(1, event.getAxisValue(MotionEvent.AXIS_Y))
            CallbackBridge.sendGamepadAxis(2, event.getAxisValue(MotionEvent.AXIS_RX))
            CallbackBridge.sendGamepadAxis(3, event.getAxisValue(MotionEvent.AXIS_RY))
            CallbackBridge.sendGamepadAxis(4, GlfwInputMapping.triggerToGlfw(event.getAxisValue(MotionEvent.AXIS_LTRIGGER)))
            CallbackBridge.sendGamepadAxis(5, GlfwInputMapping.triggerToGlfw(event.getAxisValue(MotionEvent.AXIS_RTRIGGER)))
            return true
        }
        val isPointer = sources and InputDevice.SOURCE_MOUSE == InputDevice.SOURCE_MOUSE ||
            sources and InputDevice.SOURCE_STYLUS == InputDevice.SOURCE_STYLUS
        if (!isPointer) return super.dispatchGenericMotionEvent(event)

        when (event.actionMasked) {
            MotionEvent.ACTION_HOVER_MOVE, MotionEvent.ACTION_MOVE -> {
                pointerX = event.x
                pointerY = event.y
                CallbackBridge.sendCursorPos(pointerX, pointerY)
                return true
            }
            MotionEvent.ACTION_SCROLL -> {
                CallbackBridge.sendScroll(
                    event.getAxisValue(MotionEvent.AXIS_HSCROLL).toDouble(),
                    event.getAxisValue(MotionEvent.AXIS_VSCROLL).toDouble(),
                )
                return true
            }
            MotionEvent.ACTION_BUTTON_PRESS, MotionEvent.ACTION_BUTTON_RELEASE -> {
                val pressed = event.actionMasked == MotionEvent.ACTION_BUTTON_PRESS
                val buttons = listOf(
                    MotionEvent.BUTTON_PRIMARY to 0,
                    MotionEvent.BUTTON_SECONDARY to 1,
                    MotionEvent.BUTTON_TERTIARY to 2,
                    MotionEvent.BUTTON_BACK to 3,
                    MotionEvent.BUTTON_FORWARD to 4,
                )
                var handled = false
                for ((mask, glfwButton) in buttons) {
                    if (event.actionButton == mask) {
                        pointerX = event.x
                        pointerY = event.y
                        CallbackBridge.sendCursorPos(pointerX, pointerY)
                        CallbackBridge.sendMouseButton(glfwButton, if (pressed) GlfwInputMapping.PRESS else GlfwInputMapping.RELEASE, GlfwInputMapping.modifiers(event.metaState))
                        handled = true
                        break
                    }
                }
                return handled || super.dispatchGenericMotionEvent(event)
            }
        }
        return super.dispatchGenericMotionEvent(event)
    }

    override fun onWindowFocusChanged(hasWindowFocus: Boolean) {
        super.onWindowFocusChanged(hasWindowFocus)
        if (!hasWindowFocus && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && hasPointerCapture()) {
            releasePointerCapture()
        } else if (hasWindowFocus && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && CallbackBridge.isGrabbing) {
            requestPointerCapture()
        }
    }
}
