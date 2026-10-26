package com.sally.nonelauncher.input;

/**
 * JNI boundary for the launcher-owned input backend.
 *
 * Events drained from native code use six floats per event in this order:
 * type, code, x, y, value, modifiers. Integer fields are stored as floats so
 * the queue can be transferred in one JNI array without creating one object
 * per input event.
 */
public final class NativeInput {
    public static final int EVENT_POINTER_DOWN = 1;
    public static final int EVENT_POINTER_UP = 2;
    public static final int EVENT_POINTER_DELTA = 3;
    public static final int EVENT_MOUSE_BUTTON = 4;
    public static final int EVENT_SCROLL = 5;
    public static final int EVENT_KEY = 6;
    public static final int EVENT_CHAR = 7;
    public static final int EVENT_RESIZE = 8;
    public static final int EVENT_GRAB = 9;
    public static final int EVENT_POINTER_MOVE = 10;
    public static final int EVENT_MOUSE_DELTA = 11;

    public static final int EVENT_STRIDE = 6;

    static {
        System.loadLibrary("noneinput");
    }

    public native void pointerDown(int id, float x, float y);
    public native void pointerUp(int id, float x, float y);
    public native void pointerMove(int id, float x, float y);
    public native void pointerDelta(float dx, float dy);
    public native void mouseDelta(float dx, float dy);
    public native void mouseButton(int button, boolean down);
    public native void scroll(float x, float y);
    public native void key(int key, int scanCode, boolean down, int mods);
    public native void charInput(int codePoint, int mods);
    public native void resize(int width, int height);
    public native void setGrabbed(boolean grabbed);

    /** Returns and removes all pending events from the native queue. */
    public native float[] drainEvents();

    /** Number of old events discarded because the native queue was full. */
    public native long droppedEventCount();
}
