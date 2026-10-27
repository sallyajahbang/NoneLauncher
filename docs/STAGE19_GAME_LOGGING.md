# Stage 19 — Native Minecraft Log Capture

This patch connects Sally Launcher to the matching Pojav-derived `libpojavexec.so` logging JNI already included in the engine bridge.

## Changes

- Adds the Java `net.kdt.pojavlaunch.Logger` JNI class with the nested listener ABI expected by `stdio_is.c`.
- Creates `files/minecraft/logs/latest.log` before the native logger opens it, then redirects embedded JVM stdout/stderr to the file.
- Adds a **View game log** action to the landscape game host. It reads only the final 64 KiB, supports refresh while a launch is running, and redacts common access-token fields before showing the text.
- Keeps logs local to app-private storage and never logs launch argv or the account session from Kotlin.
- Guards native capture to one start per app process because this logger and embedded JLI state are process-global. Fully close/reopen Sally Launcher before a subsequent JVM launch if the embedded JLI returned.
- Adds unit tests for credential redaction and ordinary crash output.

## Log location

`<app-private files>/minecraft/logs/latest.log`

The log viewer shows only the final 64 KiB to avoid loading an unbounded session log into the UI. This does not prove that Minecraft reaches the title screen; it gives actionable native/JVM output when launch fails.

## Apply

Extract the changed-file ZIP at the Sally Launcher project root and overwrite the files at their relative paths. Re-run the existing GitHub Actions workflow.
