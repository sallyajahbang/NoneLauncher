package com.sally.nonelauncher.jvm

/**
 * Runs the JVM in a forked child process so the app survives when the JVM exits.
 * Native code lives in cpp/jre_launcher.c.
 */
object JvmProbe {

    init {
        System.loadLibrary("nonelauncher_jvm")
    }

    /**
     * Starts the JVM in a child process.
     *
     * @param args full argv, including the program name at index 0.
     * @param outFd write end of a pipe; the child's stdout and stderr go there.
     * @return the child's pid, or -1 on failure.
     */
    @JvmStatic
    external fun forkLaunch(args: Array<String>, outFd: Int): Int

    /** Blocks until [pid] exits and returns its exit code (-1 if it did not exit normally). */
    @JvmStatic
    external fun waitChild(pid: Int): Int
}
