package com.sally.nonelauncher.runtime;

import android.content.Context;
import android.os.Build;
import android.system.ErrnoException;
import android.system.Os;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

/** Runs small diagnostic commands against an installed Android Java runtime. */
public final class RuntimeExecutor {
    private static final long TIMEOUT_SECONDS = 30;
    private final Context context;

    public RuntimeExecutor(Context context) {
        this.context = context.getApplicationContext();
    }

    public TestResult testVersion(RuntimeManager.RuntimeInfo runtime) {
        if (runtime == null) return new TestResult(false, "No runtime selected.", -1);
        if (!runtime.javaExecutable.isFile()) return new TestResult(false, "Missing executable: " + runtime.javaExecutable, -1);
        // TAR extraction and Android filesystem permissions can lose executable bits.
        runtime.javaExecutable.setExecutable(true, true);
        try {
            Os.chmod(runtime.javaExecutable.getAbsolutePath(), 0755);
        } catch (ErrnoException error) {
            return new TestResult(false, "Cannot set execute permission on bin/java: " + error.getMessage(), -1);
        }
        if (!runtime.javaExecutable.canExecute()) {
            return new TestResult(false, "bin/java still is not executable after chmod. The filesystem or Android policy may block execution from this directory.", -1);
        }

        String arch = runtime.architecture == null ? "" : runtime.architecture.toLowerCase(Locale.ROOT);
        boolean runtime64 = arch.contains("aarch64") || arch.contains("arm64");
        boolean runtime32 = arch.contains("arm") && !runtime64;
        boolean device64 = false;
        boolean device32 = false;
        for (String abi : Build.SUPPORTED_ABIS) {
            String value = abi.toLowerCase(Locale.ROOT);
            if (value.equals("arm64-v8a") || value.equals("x86_64") || value.equals("riscv64")) device64 = true;
            if (value.equals("armeabi-v7a") || value.equals("armeabi") || value.equals("x86")) device32 = true;
        }
        if ((runtime64 && !device64) || (runtime32 && !device32)) {
            return new TestResult(false, "Runtime architecture " + runtime.architecture + " does not match device ABIs: " + join(Build.SUPPORTED_ABIS), -1);
        }

        Process process = null;
        try {
            File home = runtime.directory;
            File lib = new File(home, "lib");
            File server = new File(lib, "server");
            List<String> command = new ArrayList<>();
            command.add(runtime.javaExecutable.getAbsolutePath());
            command.add("-version");
            ProcessBuilder builder = new ProcessBuilder(command);
            builder.directory(home);
            builder.redirectErrorStream(true);
            builder.environment().put("JAVA_HOME", home.getAbsolutePath());
            builder.environment().put("HOME", context.getFilesDir().getAbsolutePath());
            builder.environment().put("TMPDIR", context.getCacheDir().getAbsolutePath());
            builder.environment().put("PATH", new File(home, "bin").getAbsolutePath() + ":/system/bin:/system/xbin");
            String nativePath = lib.getAbsolutePath() + ":" + server.getAbsolutePath();
            if (Build.VERSION.SDK_INT >= 21 && Build.SUPPORTED_64_BIT_ABIS.length > 0) {
                nativePath += ":/system/lib64:/vendor/lib64";
            } else {
                nativePath += ":/system/lib:/vendor/lib";
            }
            String oldLd = builder.environment().get("LD_LIBRARY_PATH");
            if (oldLd != null && !oldLd.isEmpty()) nativePath += ":" + oldLd;
            builder.environment().put("LD_LIBRARY_PATH", nativePath);

            process = builder.start();
            StringBuilder output = new StringBuilder();
            Process running = process;
            Thread reader = new Thread(() -> {
                try (BufferedReader in = new BufferedReader(new InputStreamReader(running.getInputStream(), StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = in.readLine()) != null) {
                        synchronized (output) {
                            if (output.length() < 12000) output.append(line).append('\n');
                        }
                    }
                } catch (IOException ignored) { }
            }, "none-java-output");
            reader.setDaemon(true);
            reader.start();
            if (!process.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                reader.join(1000);
                return new TestResult(false, "Java runtime timed out after " + TIMEOUT_SECONDS + " seconds.\n" + output.toString().trim(), -1);
            }
            reader.join(1500);
            int code = process.exitValue();
            String text;
            synchronized (output) { text = output.toString().trim(); }
            if (text.isEmpty()) text = "The Java process exited without output.";
            return new TestResult(code == 0, text + "\nExit code: " + code, code);
        } catch (Exception error) {
            if (process != null) process.destroyForcibly();
            String message = error.getMessage();
            if (message == null || message.trim().isEmpty()) message = error.getClass().getSimpleName();
            return new TestResult(false, "Could not execute Java: " + message, -1);
        }
    }

    private static String join(String[] values) {
        if (values == null || values.length == 0) return "unknown";
        StringBuilder result = new StringBuilder();
        for (String value : values) {
            if (result.length() > 0) result.append(", ");
            result.append(value);
        }
        return result.toString();
    }

    public static final class TestResult {
        public final boolean success;
        public final String output;
        public final int exitCode;
        TestResult(boolean success, String output, int exitCode) {
            this.success = success;
            this.output = output;
            this.exitCode = exitCode;
        }
    }
}
