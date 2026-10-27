package net.kdt.pojavlaunch.utils;

/** Minimal Android-side JNI names required by the Pojav EGL/window bridge. */
public final class JREUtils {
    private JREUtils() {}

    public static native void setupBridgeWindow(Object surface);
    public static native void releaseBridgeWindow();
}
