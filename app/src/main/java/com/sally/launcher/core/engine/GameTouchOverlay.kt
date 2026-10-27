package com.sally.launcher.core.engine

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.SparseArray
import android.view.MotionEvent
import android.view.View
import org.lwjgl.glfw.CallbackBridge
import java.io.File
import kotlin.math.hypot

/**
 * Pojav-style touch controls drawn above the game surface.
 *
 * While the game grabs the cursor (in-world):
 *  - left joystick = W/A/S/D (push to the edge = sprint)
 *  - drag on empty screen = look around
 *  - quick tap = right click (use/place), press and hold still = hold left click (mine/attack)
 *  - invisible hotbar zones over Minecraft's hotbar select slots 1-9
 *  - buttons: jump, sneak (toggle), drop, ESC, inventory, chat, F5
 *  - L / R buttons: hold to press left / right mouse button. Slide the same finger, or another finger
 *    on empty screen, while holding them to drag the cursor (drag items, select, aim with a held button).
 * While a GUI is open the cursor is not grabbed: only ESC/E/T/F5 and L/R stay active. Other touches are
 * left to PojavGameSurfaceView, which acts as the pointer for menus, unless an L/R button is held, in
 * which case a slide drags the cursor here.
 */
class GameTouchOverlay(context: Context) : View(context) {
    @Volatile
    var sensitivity: Float = 1.0f

    /** Minecraft's options.txt, read to match the hotbar touch zones to the in-game GUI scale. */
    var optionsFile: File? = null

    private class Control(val label: String, val key: Int, val alwaysActive: Boolean, val toggle: Boolean = false, val mouseButton: Int = -1) {
        val rect = RectF()
        var down = false
    }

    private sealed class Role {
        object Joystick : Role()
        object Camera : Role()
        class Button(val control: Control) : Role()
        class Hotbar(val key: Int) : Role()
    }

    private val density = resources.displayMetrics.density
    private fun dp(value: Float) = value * density

    private val handler = Handler(Looper.getMainLooper())
    private val roles = SparseArray<Role>()

    private val escape = Control("ESC", KEY_ESCAPE, true)
    private val inventory = Control("E", KEY_E, true)
    private val chat = Control("T", KEY_T, true)
    private val perspective = Control("F5", KEY_F5, true)
    private val drop = Control("Q", KEY_Q, false)
    private val jump = Control("JUMP", KEY_SPACE, false)
    private val sneak = Control("SNEAK", KEY_LEFT_SHIFT, false, toggle = true)
    private val leftMouse = Control("L", -1, true, mouseButton = MOUSE_LEFT)
    private val rightMouse = Control("R", -1, true, mouseButton = MOUSE_RIGHT)
    private val controls = listOf(escape, inventory, chat, perspective, drop, jump, sneak, leftMouse, rightMouse)

    // Joystick
    private val joystickKeys = HashSet<Int>()
    private var joyCx = 0f
    private var joyCy = 0f
    private var joyR = 0f
    private var thumbX = 0f
    private var thumbY = 0f

    // Camera / tap gestures
    private var cameraPointer = -1
    private var camLastX = 0f
    private var camLastY = 0f
    private var camStartX = 0f
    private var camStartY = 0f
    private var camDownTime = 0L
    private var leftHeld = false
    private var guiScaleOverride = 0

    // Last known position of each pointer that is holding an L/R button, so a slide moves the cursor.
    private val mouseDrag = SparseArray<FloatArray>()

    private val longPress = Runnable {
        if (CallbackBridge.isGrabbing && cameraPointer != -1 && hypot(camLastX - camStartX, camLastY - camStartY) <= dp(9f)) {
            CallbackBridge.sendMouseButton(MOUSE_LEFT, GlfwInputMapping.PRESS, 0)
            leftHeld = true
        }
    }

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(60, 255, 255, 255) }
    private val fillDown = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(130, 255, 255, 255) }
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(2f)
        color = Color.argb(150, 255, 255, 255)
    }
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textAlign = Paint.Align.CENTER
        textSize = dp(12f)
        isFakeBoldText = true
    }

    /** Called when the game grabs/releases the cursor (and when the controls are shown). */
    fun onGrabChanged(grabbing: Boolean) {
        endAll()
        refreshGuiScale()
        invalidate()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        joyR = dp(60f)
        joyCx = dp(30f) + joyR + dp(20f)
        joyCy = h - dp(30f) - joyR
        thumbX = joyCx
        thumbY = joyCy
        place(jump, w - dp(80f), h - dp(95f), dp(64f))
        place(sneak, w - dp(165f), h - dp(60f), dp(52f))
        place(drop, w - dp(70f), h - dp(170f), dp(44f))
        place(leftMouse, w - dp(175f), h - dp(135f), dp(56f))
        place(rightMouse, w - dp(250f), h - dp(110f), dp(56f))
        place(escape, dp(34f), dp(34f), dp(44f))
        place(inventory, dp(86f), dp(34f), dp(44f))
        place(chat, dp(138f), dp(34f), dp(44f))
        place(perspective, dp(190f), dp(34f), dp(44f))
        refreshGuiScale()
    }

    private fun place(control: Control, centerX: Float, centerY: Float, size: Float) {
        control.rect.set(centerX - size / 2, centerY - size / 2, centerX + size / 2, centerY + size / 2)
    }

    override fun onDraw(canvas: Canvas) {
        val grabbing = CallbackBridge.isGrabbing
        if (grabbing) {
            canvas.drawCircle(joyCx, joyCy, joyR, fill)
            canvas.drawCircle(joyCx, joyCy, joyR, stroke)
            canvas.drawCircle(thumbX, thumbY, joyR * 0.4f, fillDown)
        }
        for (control in controls) {
            if (!control.alwaysActive && !grabbing) continue
            val pressed = control.down
            canvas.drawRoundRect(control.rect, dp(10f), dp(10f), if (pressed) fillDown else fill)
            canvas.drawRoundRect(control.rect, dp(10f), dp(10f), stroke)
            canvas.drawText(
                control.label,
                control.rect.centerX(),
                control.rect.centerY() - (text.ascent() + text.descent()) / 2f,
                text,
            )
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                val index = event.actionIndex
                val id = event.getPointerId(index)
                val role = hitTest(event.getX(index), event.getY(index))
                // Not ours (menu touch): let the game surface below act as the menu pointer.
                if (role == null) return roles.size() > 0
                roles.put(id, role)
                begin(role, event.getX(index), event.getY(index), id)
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                for (index in 0 until event.pointerCount) {
                    val id = event.getPointerId(index)
                    val role = roles.get(id) ?: continue
                    val x = event.getX(index)
                    val y = event.getY(index)
                    when (role) {
                        is Role.Joystick -> updateJoystick(x, y)
                        is Role.Camera -> moveCamera(x, y)
                        is Role.Button -> if (role.control.mouseButton >= 0) dragWithButton(id, x, y)
                        else -> Unit
                    }
                }
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> {
                val index = event.actionIndex
                val id = event.getPointerId(index)
                val role = roles.get(id)
                if (role != null) {
                    end(role, id, event.getX(index), event.getY(index))
                    roles.remove(id)
                }
                return true
            }
            MotionEvent.ACTION_CANCEL -> {
                endAll()
                return true
            }
        }
        return true
    }

    private fun hitTest(x: Float, y: Float): Role? {
        val grabbing = CallbackBridge.isGrabbing
        for (control in controls) {
            if ((control.alwaysActive || grabbing) && control.rect.contains(x, y)) return Role.Button(control)
        }
        if (!grabbing) {
            // Menus: only take a touch while an L/R button is held, so the slide can move the cursor.
            return if (mouseButtonHeld() && cameraPointer == -1) Role.Camera else null
        }
        if (!joystickActive() && hypot(x - joyCx, y - joyCy) <= joyR * 1.5f) return Role.Joystick
        hotbarSlot(x, y)?.let { return Role.Hotbar(KEY_1 + it) }
        return if (cameraPointer == -1) Role.Camera else null
    }

    private fun joystickActive(): Boolean {
        for (i in 0 until roles.size()) if (roles.valueAt(i) is Role.Joystick) return true
        return false
    }

    private fun mouseButtonHeld(): Boolean {
        for (i in 0 until roles.size()) {
            val role = roles.valueAt(i)
            if (role is Role.Button && role.control.mouseButton >= 0) return true
        }
        return false
    }

    private fun begin(role: Role, x: Float, y: Float, pointerId: Int) {
        when (role) {
            is Role.Button -> {
                val control = role.control
                when {
                    control.mouseButton >= 0 -> {
                        control.down = true
                        mouseDrag.put(pointerId, floatArrayOf(x, y))
                        CallbackBridge.sendMouseButton(control.mouseButton, GlfwInputMapping.PRESS, 0)
                    }
                    control.toggle -> {
                        control.down = !control.down
                        sendKey(control.key, control.down)
                    }
                    else -> {
                        control.down = true
                        sendKey(control.key, true)
                    }
                }
                invalidate()
            }
            is Role.Hotbar -> sendKey(role.key, true)
            is Role.Joystick -> updateJoystick(x, y)
            is Role.Camera -> {
                cameraPointer = pointerId
                camLastX = x
                camLastY = y
                camStartX = x
                camStartY = y
                camDownTime = SystemClock.uptimeMillis()
                leftHeld = false
                handler.postDelayed(longPress, LONG_PRESS_MS)
            }
        }
    }

    private fun end(role: Role, pointerId: Int, x: Float, y: Float) {
        when (role) {
            is Role.Button -> {
                val control = role.control
                when {
                    control.mouseButton >= 0 -> {
                        control.down = false
                        mouseDrag.remove(pointerId)
                        CallbackBridge.sendMouseButton(control.mouseButton, GlfwInputMapping.RELEASE, 0)
                        invalidate()
                    }
                    !control.toggle -> {
                        control.down = false
                        sendKey(control.key, false)
                        invalidate()
                    }
                    else -> Unit
                }
            }
            is Role.Hotbar -> sendKey(role.key, false)
            is Role.Joystick -> releaseJoystick()
            is Role.Camera -> {
                handler.removeCallbacks(longPress)
                val still = hypot(x - camStartX, y - camStartY) <= dp(9f)
                val quick = SystemClock.uptimeMillis() - camDownTime <= TAP_MS
                if (leftHeld) {
                    CallbackBridge.sendMouseButton(MOUSE_LEFT, GlfwInputMapping.RELEASE, 0)
                } else if (still && quick && CallbackBridge.isGrabbing) {
                    CallbackBridge.sendMouseButton(MOUSE_RIGHT, GlfwInputMapping.PRESS, 0)
                    CallbackBridge.sendMouseButton(MOUSE_RIGHT, GlfwInputMapping.RELEASE, 0)
                }
                leftHeld = false
                cameraPointer = -1
            }
        }
    }

    private fun endAll() {
        val ended = ArrayList<Pair<Int, Role>>()
        for (i in 0 until roles.size()) ended += roles.keyAt(i) to roles.valueAt(i)
        roles.clear()
        for ((pointerId, role) in ended) end(role, pointerId, camLastX, camLastY)
        mouseDrag.clear()
        cameraPointer = -1
        releaseJoystick()
    }

    private fun moveCamera(x: Float, y: Float) {
        val dx = (x - camLastX) * sensitivity
        val dy = (y - camLastY) * sensitivity
        camLastX = x
        camLastY = y
        if (dx == 0f && dy == 0f) return
        CallbackBridge.sendCursorPos(CallbackBridge.mouseX + dx, CallbackBridge.mouseY + dy)
    }

    /** Slide while an L/R button is held: the cursor follows the finger, so the held button drags. */
    private fun dragWithButton(pointerId: Int, x: Float, y: Float) {
        val last = mouseDrag.get(pointerId) ?: return
        val dx = (x - last[0]) * sensitivity
        val dy = (y - last[1]) * sensitivity
        last[0] = x
        last[1] = y
        if (dx == 0f && dy == 0f) return
        CallbackBridge.sendCursorPos(CallbackBridge.mouseX + dx, CallbackBridge.mouseY + dy)
    }

    private fun updateJoystick(x: Float, y: Float) {
        var dx = x - joyCx
        var dy = y - joyCy
        val distance = hypot(dx, dy)
        if (distance > joyR) {
            dx = dx / distance * joyR
            dy = dy / distance * joyR
        }
        thumbX = joyCx + dx
        thumbY = joyCy + dy
        val threshold = joyR * 0.3f
        val forward = dy < -threshold
        setJoystickKey(KEY_W, forward)
        setJoystickKey(KEY_S, dy > threshold)
        setJoystickKey(KEY_A, dx < -threshold)
        setJoystickKey(KEY_D, dx > threshold)
        setJoystickKey(KEY_LEFT_CONTROL, forward && distance >= joyR * 0.95f) // sprint
        invalidate()
    }

    private fun releaseJoystick() {
        for (key in joystickKeys.toList()) setJoystickKey(key, false)
        thumbX = joyCx
        thumbY = joyCy
        invalidate()
    }

    private fun setJoystickKey(key: Int, down: Boolean) {
        if (down) {
            if (joystickKeys.add(key)) sendKey(key, true)
        } else if (joystickKeys.remove(key)) {
            sendKey(key, false)
        }
    }

    private fun sendKey(key: Int, down: Boolean) {
        CallbackBridge.sendKey(key, 0, if (down) GlfwInputMapping.PRESS else GlfwInputMapping.RELEASE, 0, 0)
    }

    /** Index 0..8 of the hotbar slot under (x, y), using Minecraft's GUI scale rules; null if none. */
    private fun hotbarSlot(x: Float, y: Float): Int? {
        val scale = guiScale().toFloat()
        val left = (width - 182f * scale) / 2f
        if (y < height - 23f * scale) return null
        val slot = ((x - left - scale) / (20f * scale)).toInt()
        if (x < left + scale || slot !in 0..8) return null
        return slot
    }

    private fun guiScale(): Int {
        if (guiScaleOverride > 0) return guiScaleOverride
        var scale = 1
        while (scale < 16 && width / (scale + 1) >= 320 && height / (scale + 1) >= 240) scale++
        return scale
    }

    private fun refreshGuiScale() {
        guiScaleOverride = runCatching {
            optionsFile?.takeIf { it.isFile }?.useLines { lines ->
                lines.firstOrNull { it.startsWith("guiScale:") }?.substringAfter(':')?.trim()?.toIntOrNull()
            }
        }.getOrNull()?.takeIf { it > 0 } ?: 0
    }

    private companion object {
        const val LONG_PRESS_MS = 300L
        const val TAP_MS = 200L
        const val MOUSE_LEFT = 0
        const val MOUSE_RIGHT = 1
        const val KEY_1 = 49
        const val KEY_A = 65
        const val KEY_D = 68
        const val KEY_E = 69
        const val KEY_Q = 81
        const val KEY_S = 83
        const val KEY_T = 84
        const val KEY_W = 87
        const val KEY_SPACE = 32
        const val KEY_ESCAPE = 256
        const val KEY_F5 = 294
        const val KEY_LEFT_SHIFT = 340
        const val KEY_LEFT_CONTROL = 341
    }
}
