package com.oracle.dalvik;

/**
 * JNI entry point exported by the Pojav-derived libpojavexec.so launcher bridge.
 * This starts an embedded Android OpenJDK runtime; it is not ProcessBuilder or desktop java.
 */
public final class VMLauncher {
    private VMLauncher() {}

    /**
     * Invoke the runtime's JLI_Launch with an argv-style array. argv[0] must be "java".
     * Call from a dedicated worker thread after configuring the runtime and native library paths.
     */
    public static native int launchJVM(String[] args);
}
