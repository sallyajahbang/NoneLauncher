package net.kdt.pojavlaunch.utils;

import android.content.Context;

/** Minimal Android-side JNI entry points required by the Pojav EGL/window and runtime bridge. */
public final class JREUtils {
    private JREUtils() {}

    public static native void setupBridgeWindow(Object surface);
    public static native void releaseBridgeWindow();
    public static native void setLdLibraryPath(String ldLibraryPath);
    public static native int chdir(String path);
    /** dlopen(RTLD_GLOBAL|RTLD_LAZY) as upstream Pojav does before JLI_Launch. Returns false on failure. */
    public static native boolean dlopen(String libPath);
    /** Registers the Android-side receiver used by Pojav's fatal JVM/signal path. */
    public static native void setupExitMethod(Context context);
    /** Installs the exit/atexit hooks used to report non-zero JVM termination to Android. */
    public static native void initializeHooks();
    /** Copies the Caciocavallo framebuffer to Android when an AWT UI is used. */
    public static native int[] renderAWTScreenFrame();
}
