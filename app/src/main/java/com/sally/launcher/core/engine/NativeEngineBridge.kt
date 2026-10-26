package com.sally.launcher.core.engine

/**
 * Small JNI boundary for the native engine work. This bridge currently transports
 * input diagnostics only; it is not an embedded JVM or a GLFW/LWJGL implementation.
 */
object NativeEngineBridge {
    const val EVENT_TOUCH = 1
    const val EVENT_KEY = 2
    const val EVENT_POINTER_MOVE = 3
    const val EVENT_MOUSE_BUTTON = 4
    const val EVENT_SCROLL = 5
    const val EVENT_VIEWPORT = 6

    val isAvailable: Boolean by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        try {
            System.loadLibrary("sallybridge")
            true
        } catch (_: UnsatisfiedLinkError) {
            false
        } catch (_: SecurityException) {
            false
        }
    }

    fun bridgeVersion(): String = if (isAvailable) {
        runCatching { nativeBridgeVersion() }.getOrDefault("Sally Native Bridge (version unavailable)")
    } else {
        "Sally Native Bridge unavailable"
    }

    /** Returns false if the native queue is full or the library is unavailable. */
    fun sendInput(type: Int, code: Int, action: Int, x: Float, y: Float, value: Float): Boolean {
        if (!isAvailable) return false
        return runCatching { nativeSendInput(type, code, action, x, y, value) }.getOrDefault(false)
    }

    /** Each native event is six floats: type, code, action, x, y, value. */
    fun pollEvents(maxEvents: Int = 64): FloatArray {
        if (!isAvailable || maxEvents <= 0) return FloatArray(0)
        return runCatching { nativePollEvents(maxEvents.coerceAtMost(256)) }.getOrDefault(FloatArray(0))
    }

    fun queuedEventCount(): Int = if (isAvailable) {
        runCatching { nativeQueuedEventCount() }.getOrDefault(0)
    } else 0

    private external fun nativeBridgeVersion(): String
    private external fun nativeSendInput(
        type: Int,
        code: Int,
        action: Int,
        x: Float,
        y: Float,
        value: Float,
    ): Boolean
    private external fun nativePollEvents(maxEvents: Int): FloatArray
    private external fun nativeQueuedEventCount(): Int
}
