package net.kdt.pojavlaunch;

/**
 * Small Java-side endpoint for the matching Pojav-derived stdio logger in libpojavexec.so.
 * The library redirects the embedded JVM's stdout/stderr to the supplied log file.
 */
public final class Logger {
    private Logger() {}

    /** Listener ABI expected by stdio_is.c; kept with the exact upstream JNI class/signature. */
    public interface eventLogListener {
        void onEventLogged(String event);
    }

    /** Must be called after libpojavexec.so has loaded and logPath already exists. */
    public static native void begin(String logPath);

    public static native void appendToLog(String text);

    public static native void setLogListener(eventLogListener listener);
}
