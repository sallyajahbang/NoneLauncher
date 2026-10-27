package com.sally.launcher.core.engine

import android.content.Context
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.widget.Button
import android.widget.FrameLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.math.max

/**
 * Stage 6 diagnostic screen: verifies the Android GLES context and the native input queue.
 * This is intentionally not presented as a Minecraft renderer or GLFW bridge.
 */
class EngineDiagnosticsActivity : AppCompatActivity(), GLSurfaceView.Renderer {
    private lateinit var glView: GLSurfaceView
    private lateinit var infoView: TextView
    private lateinit var captureButton: Button
    private val inputTranslator = AndroidInputTranslator()
    private val inputPump = NativeInputPump()
    private val activeTouchPointers = LinkedHashSet<Int>()
    private var captureEscapeHandled = false

    @Volatile private var pointerX = 0f
    @Volatile private var pointerY = 0f
    @Volatile private var hasPointer = false
    @Volatile private var processedEvents = 0
    @Volatile private var malformedInputRecords = 0
    @Volatile private var inputSinkFailures = 0
    @Volatile private var lastEventSummary = "No input received yet"

    private var lastUiReportAt = 0L
    private var program = 0
    private var positionAttribute = -1
    private var colorUniform = -1
    private val shapeBuffer: FloatBuffer = ByteBuffer.allocateDirect(6 * 4)
        .order(ByteOrder.nativeOrder()).asFloatBuffer()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = "Engine diagnostics"

        val root = FrameLayout(this)
        glView = (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            CaptureAwareGlSurfaceView(this, ::handlePointerCaptureChanged)
        } else {
            GLSurfaceView(this)
        }).apply {
            setEGLContextClientVersion(2)
            preserveEGLContextOnPause = false
            setRenderer(this@EngineDiagnosticsActivity)
            renderMode = GLSurfaceView.RENDERMODE_CONTINUOUSLY
            isFocusable = true
            isFocusableInTouchMode = true
            setOnTouchListener { _, event -> handleTouch(event) }
            setOnGenericMotionListener { _, event -> handleGenericMotion(event) }
        }
        root.addView(glView, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT,
        ))

        infoView = TextView(this).apply {
            textSize = 13f
            setTextColor(0xFFFFFFFF.toInt())
            setBackgroundColor(0xCC101923.toInt())
            setPadding(dp(16), dp(12), dp(16), dp(12))
            text = initialStatus()
            contentDescription = "Native engine diagnostics and input event counter"
        }
        root.addView(infoView, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.WRAP_CONTENT,
            android.view.Gravity.TOP,
        ))

        captureButton = Button(this).apply {
            text = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) "Capture mouse" else "Mouse capture needs Android 8+"
            isEnabled = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
            setOnClickListener { togglePointerCapture() }
            contentDescription = "Capture or release the physical mouse pointer for relative movement testing"
        }
        val captureParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT,
            FrameLayout.LayoutParams.WRAP_CONTENT,
            android.view.Gravity.BOTTOM or android.view.Gravity.END,
        ).apply {
            val margin = dp(12)
            setMargins(margin, margin, margin, margin)
        }
        root.addView(captureButton, captureParams)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // Use the explicit Java listener type instead of SAM lambda inference. This works
            // across Kotlin compiler versions and Android SDK stubs that fail to infer the
            // two parameters for setOnCapturedPointerListener.
            glView.setOnCapturedPointerListener(object : View.OnCapturedPointerListener {
                override fun onCapturedPointer(view: View, event: MotionEvent): Boolean {
                    return handleCapturedPointer(event)
                }
            })
        }

        setContentView(root)
        glView.requestFocus()
    }

    override fun onResume() {
        super.onResume()
        if (::glView.isInitialized) glView.onResume()
    }

    override fun onPause() {
        releasePointerCaptureIfHeld()
        releaseTrackedInput()
        if (::glView.isInitialized) glView.onPause()
        super.onPause()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (!hasFocus) {
            releasePointerCaptureIfHeld()
            releaseTrackedInput()
        }
    }

    override fun onDestroy() {
        releasePointerCaptureIfHeld()
        releaseTrackedInput()
        if (::glView.isInitialized) glView.queueEvent {
            if (program != 0) GLES20.glDeleteProgram(program)
            program = 0
        }
        super.onDestroy()
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.keyCode == KeyEvent.KEYCODE_ESCAPE && captureEscapeHandled) {
            if (event.action == KeyEvent.ACTION_UP) captureEscapeHandled = false
            return true
        }
        if (event.keyCode == KeyEvent.KEYCODE_ESCAPE &&
            event.action == KeyEvent.ACTION_DOWN &&
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            ::glView.isInitialized && glView.hasPointerCapture()) {
            captureEscapeHandled = true
            glView.releasePointerCapture()
            inputTranslator.releaseAll()
            return true
        }
        val systemKey = event.keyCode == KeyEvent.KEYCODE_BACK ||
            event.keyCode == KeyEvent.KEYCODE_HOME ||
            event.keyCode == KeyEvent.KEYCODE_VOLUME_UP ||
            event.keyCode == KeyEvent.KEYCODE_VOLUME_DOWN ||
            event.keyCode == KeyEvent.KEYCODE_POWER
        if (!systemKey && inputTranslator.sendKey(event)) return true
        return super.dispatchKeyEvent(event)
    }

    override fun onGenericMotionEvent(event: MotionEvent): Boolean {
        return if (handleGenericMotion(event)) true else super.onGenericMotionEvent(event)
    }

    private fun handleTouch(event: MotionEvent): Boolean {
        val action = event.actionMasked
        when (action) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                val index = event.actionIndex
                sendTouch(event, index, GlfwInputMapping.PRESS)
                activeTouchPointers.add(event.getPointerId(index))
            }
            MotionEvent.ACTION_MOVE -> {
                for (index in 0 until event.pointerCount) {
                    sendTouch(event, index, GlfwInputMapping.MOVE)
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> {
                val index = event.actionIndex
                sendTouch(event, index, GlfwInputMapping.RELEASE)
                activeTouchPointers.remove(event.getPointerId(index))
            }
            MotionEvent.ACTION_CANCEL -> releaseTouchPointers()
        }
        return true
    }

    private fun sendTouch(event: MotionEvent, index: Int, action: Int) {
        if (index !in 0 until event.pointerCount) return
        val x = event.getX(index)
        val y = event.getY(index)
        pointerX = x / max(1, glView.width).toFloat()
        pointerY = y / max(1, glView.height).toFloat()
        hasPointer = true
        NativeEngineBridge.sendInput(
            NativeEngineBridge.EVENT_TOUCH,
            event.getPointerId(index),
            action,
            x,
            y,
            event.getPressure(index),
        )
    }

    private fun releaseTouchPointers() {
        activeTouchPointers.toList().forEach { pointerId ->
            NativeEngineBridge.sendInput(
                NativeEngineBridge.EVENT_TOUCH, pointerId, GlfwInputMapping.RELEASE,
                pointerX * max(1, glView.width), pointerY * max(1, glView.height), 0f,
            )
        }
        activeTouchPointers.clear()
    }

    private fun releaseTrackedInput() {
        inputTranslator.releaseAll()
        releaseTouchPointers()
    }

    /** Called by the GLSurfaceView override when Android grants or revokes capture. */
    private fun handlePointerCaptureChanged(hasCapture: Boolean) {
        if (::captureButton.isInitialized) {
            captureButton.text = if (hasCapture) "Release mouse" else "Capture mouse"
        }
        NativeEngineBridge.sendInput(
            NativeEngineBridge.EVENT_CURSOR_CAPTURE,
            0,
            if (hasCapture) GlfwInputMapping.PRESS else GlfwInputMapping.RELEASE,
            0f,
            0f,
            0f,
        )
        if (::infoView.isInitialized) infoView.text = statusWithEvents()
    }

    private fun togglePointerCapture() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O || !::glView.isInitialized) {
            return
        }
        if (glView.hasPointerCapture()) {
            glView.releasePointerCapture()
        } else {
            glView.requestFocus()
            // Pointer capture requires a focused view hierarchy; run after focus has settled.
            glView.post {
                if (glView.hasWindowFocus() && glView.hasFocus()) {
                    glView.requestPointerCapture()
                } else {
                    infoView.text = initialStatus() + "\n\nCould not capture mouse: the game surface has not received focus. Tap Capture mouse again."
                }
            }
        }
    }

    private fun releasePointerCaptureIfHeld() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            ::glView.isInitialized && glView.hasPointerCapture()) {
            glView.releasePointerCapture()
        }
    }

    /** Captured mouse motion is relative; do not feed its deltas to the absolute-position path. */
    private fun handleCapturedPointer(event: MotionEvent): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return false
        when (event.actionMasked) {
            MotionEvent.ACTION_MOVE -> {
                // Relative axes are per-sample, not accumulated. Preserve every history sample
                // or high-rate mice can lose movement when Android batches MotionEvents.
                for (historyIndex in 0 until event.historySize) {
                    sendRelativePointerDelta(
                        event.getHistoricalAxisValue(MotionEvent.AXIS_RELATIVE_X, 0, historyIndex),
                        event.getHistoricalAxisValue(MotionEvent.AXIS_RELATIVE_Y, 0, historyIndex),
                    )
                }
                sendRelativePointerDelta(
                    event.getAxisValue(MotionEvent.AXIS_RELATIVE_X),
                    event.getAxisValue(MotionEvent.AXIS_RELATIVE_Y),
                )
                return true
            }
            MotionEvent.ACTION_BUTTON_PRESS,
            MotionEvent.ACTION_BUTTON_RELEASE,
            MotionEvent.ACTION_SCROLL,
            MotionEvent.ACTION_HOVER_MOVE -> return handleGenericMotion(event)
        }
        return false
    }

    private fun sendRelativePointerDelta(dx: Float, dy: Float) {
        NativeEngineBridge.sendInput(
            NativeEngineBridge.EVENT_POINTER_MOVE,
            0,
            GlfwInputMapping.RELATIVE_MOVE,
            dx,
            dy,
            0f,
        )
        pointerX = (pointerX + dx / max(1, glView.width).toFloat()).coerceIn(0f, 1f)
        pointerY = (pointerY + dy / max(1, glView.height).toFloat()).coerceIn(0f, 1f)
        hasPointer = true
    }

    private fun handleGenericMotion(event: MotionEvent): Boolean {
        val isJoystick = event.isFromSource(InputDevice.SOURCE_JOYSTICK) &&
            event.actionMasked == MotionEvent.ACTION_MOVE
        if (isJoystick) {
            inputTranslator.sendGamepadAxis(MotionEvent.AXIS_X, event.getAxisValue(MotionEvent.AXIS_X))
            inputTranslator.sendGamepadAxis(MotionEvent.AXIS_Y, event.getAxisValue(MotionEvent.AXIS_Y))
            inputTranslator.sendGamepadAxis(MotionEvent.AXIS_RX, event.getAxisValue(MotionEvent.AXIS_RX))
            inputTranslator.sendGamepadAxis(MotionEvent.AXIS_RY, event.getAxisValue(MotionEvent.AXIS_RY))
            inputTranslator.sendGamepadAxis(MotionEvent.AXIS_LTRIGGER, event.getAxisValue(MotionEvent.AXIS_LTRIGGER))
            inputTranslator.sendGamepadAxis(MotionEvent.AXIS_RTRIGGER, event.getAxisValue(MotionEvent.AXIS_RTRIGGER))
            return true
        }

        val isMouse = event.isFromSource(InputDevice.SOURCE_MOUSE) ||
            event.isFromSource(InputDevice.SOURCE_MOUSE_RELATIVE)
        if (!isMouse) return false

        when (event.actionMasked) {
            MotionEvent.ACTION_HOVER_MOVE, MotionEvent.ACTION_MOVE -> {
                pointerX = event.x / max(1, glView.width).toFloat()
                pointerY = event.y / max(1, glView.height).toFloat()
                hasPointer = true
                NativeEngineBridge.sendInput(
                    NativeEngineBridge.EVENT_POINTER_MOVE, 0, GlfwInputMapping.MOVE, event.x, event.y, 0f,
                )
                return true
            }
            MotionEvent.ACTION_SCROLL -> {
                NativeEngineBridge.sendInput(
                    NativeEngineBridge.EVENT_SCROLL,
                    0,
                    GlfwInputMapping.PRESS,
                    event.getAxisValue(MotionEvent.AXIS_HSCROLL),
                    event.getAxisValue(MotionEvent.AXIS_VSCROLL),
                    0f,
                )
                return true
            }
            MotionEvent.ACTION_BUTTON_PRESS, MotionEvent.ACTION_BUTTON_RELEASE -> {
                val accepted = inputTranslator.sendMouseButton(
                    event.actionButton,
                    event.actionMasked == MotionEvent.ACTION_BUTTON_PRESS,
                    event.x,
                    event.y,
                )
                return accepted
            }
        }
        return false
    }

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        GLES20.glClearColor(0.035f, 0.055f, 0.085f, 1f)
        val vertexShader = try {
            compileShader(GLES20.GL_VERTEX_SHADER, VERTEX_SHADER)
        } catch (error: Exception) {
            runOnUiThread { infoView.text = initialStatus() + "\n\nVertex shader setup failed: ${error.message}" }
            return
        }
        val fragmentShader = try {
            compileShader(GLES20.GL_FRAGMENT_SHADER, FRAGMENT_SHADER)
        } catch (error: Exception) {
            GLES20.glDeleteShader(vertexShader)
            runOnUiThread { infoView.text = initialStatus() + "\n\nFragment shader setup failed: ${error.message}" }
            return
        }
        program = GLES20.glCreateProgram().also { id ->
            GLES20.glAttachShader(id, vertexShader)
            GLES20.glAttachShader(id, fragmentShader)
            GLES20.glLinkProgram(id)
        }
        GLES20.glDeleteShader(vertexShader)
        GLES20.glDeleteShader(fragmentShader)
        val linked = IntArray(1)
        GLES20.glGetProgramiv(program, GLES20.GL_LINK_STATUS, linked, 0)
        if (linked[0] == 0) {
            val log = GLES20.glGetProgramInfoLog(program)
            GLES20.glDeleteProgram(program)
            program = 0
            runOnUiThread { infoView.text = initialStatus() + "\n\nGLES shader setup failed: $log" }
            return
        }
        positionAttribute = GLES20.glGetAttribLocation(program, "aPosition")
        colorUniform = GLES20.glGetUniformLocation(program, "uColor")
        NativeEngineBridge.sendInput(NativeEngineBridge.EVENT_VIEWPORT, 0, 0, 0f, 0f, 0f)
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        GLES20.glViewport(0, 0, width, height)
        NativeEngineBridge.sendInput(NativeEngineBridge.EVENT_VIEWPORT, width, height, 0f, 0f, 0f)
    }

    override fun onDrawFrame(gl: GL10?) {
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)
        if (program != 0) {
            GLES20.glUseProgram(program)
            drawShape(floatArrayOf(-0.28f, -0.17f, 0.28f, -0.17f, 0f, 0.31f), 0.18f, 0.78f, 0.72f, 1f)
            drawShape(floatArrayOf(-0.12f, -0.16f, 0.12f, -0.16f, 0f, 0.06f), 0.10f, 0.28f, 0.47f, 1f)
            if (hasPointer) {
                val x = (pointerX.coerceIn(0f, 1f) * 2f) - 1f
                val y = 1f - (pointerY.coerceIn(0f, 1f) * 2f)
                val s = 0.035f
                drawShape(floatArrayOf(x - s, y - s, x + s, y - s, x, y + s), 1f, 0.78f, 0.32f, 1f)
            }
        }

        // Drain on the GL/render thread so a future GLFW adapter can consume the same ordered
        // events on the engine thread. At this stage the sink only updates diagnostics.
        val pumpResult = inputPump.drain(64) { event ->
            lastEventSummary = event.toDebugString()
        }
        processedEvents += pumpResult.deliveredEventCount
        malformedInputRecords += pumpResult.malformedRecordCount
        inputSinkFailures += pumpResult.sinkFailureCount + pumpResult.sourceFailureCount
        if (pumpResult.recordsRead > 0 || pumpResult.malformedRecordCount > 0 ||
            pumpResult.sourceFailureCount > 0 || pumpResult.sinkFailureCount > 0) {
            val now = SystemClock.uptimeMillis()
            if (now - lastUiReportAt > 250) {
                lastUiReportAt = now
                runOnUiThread { infoView.text = statusWithEvents() }
            }
        }
    }

    private fun drawShape(vertices: FloatArray, red: Float, green: Float, blue: Float, alpha: Float) {
        shapeBuffer.clear()
        shapeBuffer.put(vertices)
        shapeBuffer.position(0)
        GLES20.glEnableVertexAttribArray(positionAttribute)
        GLES20.glVertexAttribPointer(positionAttribute, 2, GLES20.GL_FLOAT, false, 0, shapeBuffer)
        GLES20.glUniform4f(colorUniform, red, green, blue, alpha)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, vertices.size / 2)
        GLES20.glDisableVertexAttribArray(positionAttribute)
    }

    private fun compileShader(type: Int, source: String): Int {
        val shader = GLES20.glCreateShader(type)
        GLES20.glShaderSource(shader, source)
        GLES20.glCompileShader(shader)
        val compiled = IntArray(1)
        GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, compiled, 0)
        if (compiled[0] == 0) {
            val log = GLES20.glGetShaderInfoLog(shader)
            GLES20.glDeleteShader(shader)
            throw IllegalStateException("GLES shader compile failed: $log")
        }
        return shader
    }

    private fun initialStatus(): String = buildString {
        append("SALLY ENGINE DIAGNOSTICS\n")
        append(if (NativeEngineBridge.isAvailable) NativeEngineBridge.bridgeVersion() else "Native bridge failed to load")
        append("\nOpenGL ES 2.0 surface active when rendered\n")
        append("Touch the canvas, move a mouse, or press a physical key.\n")
        append("Input is GLFW-normalized, but callbacks are not connected to Minecraft yet.")
    }

    private fun statusWithEvents(): String = initialStatus() +
        "\n\nNative events delivered to diagnostic sink: $processedEvents" +
        "\nMalformed records: $malformedInputRecords" +
        "\nPump/source failures: $inputSinkFailures" +
        "\nQueue depth: ${NativeEngineBridge.queuedEventCount()}" +
        "\nLast event: $lastEventSummary"

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    @androidx.annotation.RequiresApi(Build.VERSION_CODES.O)
    private class CaptureAwareGlSurfaceView(
        context: Context,
        private val captureChanged: (Boolean) -> Unit,
    ) : GLSurfaceView(context) {
        override fun onPointerCaptureChange(hasCapture: Boolean) {
            super.onPointerCaptureChange(hasCapture)
            captureChanged(hasCapture)
        }
    }

    private companion object {
        const val VERTEX_SHADER = """
            attribute vec2 aPosition;
            void main() { gl_Position = vec4(aPosition, 0.0, 1.0); }
        """
        const val FRAGMENT_SHADER = """
            precision mediump float;
            uniform vec4 uColor;
            void main() { gl_FragColor = uColor; }
        """
    }
}
