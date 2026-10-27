package net.kdt.pojavlaunch.utils;

import android.content.Context;

/** Minimal Android-side JNI entry points required by the Pojav EGL/window and runtime bridge. */
public final class JREUtils {
    private JREUtils() {}

    public static native void setupBridgeWindow(Object surface);
    public static native void releaseBridgeWindow();
    public static native void setLdLibraryPath(String ldLibraryPath);
    public static native int chdir(String path);
    /** Registers the Android-side receiver used by Pojav's fatal JVM/signal path. */
    public static native void setupExitMethod(Context context);
}
