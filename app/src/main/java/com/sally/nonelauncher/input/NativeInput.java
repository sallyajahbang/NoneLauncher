package com.sally.nonelauncher.input;

/** Small JNI boundary owned by NoneLauncher. It is intentionally independent of Pojav code. */
public final class NativeInput {
    static { System.loadLibrary("noneinput"); }
    public native void pointerDown(int id, float x, float y);
    public native void pointerUp(int id, float x, float y);
    public native void pointerDelta(float dx, float dy);
    public native void mouseButton(int button, boolean down);
    public native void scroll(float x, float y);
    public native void key(int key, int scanCode, boolean down, int mods);
    public native void charInput(int codePoint, int mods);
    public native void resize(int width, int height);
    public native void setGrabbed(boolean grabbed);
}
