# Sally Launcher — Stage 9

This is a new Android Minecraft: Java Edition launcher project using Kotlin, AndroidX, and a Material 3-style interface. The project now has an official version catalog, version-file installer, Android Java-runtime installer, launch-profile resolver, JNI/GLES diagnostics, and a JLI launch-request builder.

## Previous-stage foundation (Stages 1–8)

- Adds `JliLaunchSpecBuilder`, which creates an immutable, shell-free `argv` list and environment map for a future `JLI_Launch` call.
- Preserves arguments as individual strings (including paths with spaces); it never shell-joins the command.
- Checks runtime, client JAR, and classpath files, lists candidate native library directories, and reports concrete engine blockers in the UI.
- Fixes the Android SDK workflow setup by using the runner's preinstalled command-line tools directly instead of running the setup action that failed while requesting the removed `tools` package.
- Keeps native ABI narrowing fix from the previous patch; the stage patch itself only contains files changed for Stage 8.

## Important status

**This is still not playable Minecraft.** Stage 8 only built a launch request; Stage 9 can invoke `JLI_Launch` for a harmless `-version` JVM smoke test but does not execute the Minecraft main class. The current downloader intentionally skips desktop native classifier JARs, and desktop LWJGL binaries cannot simply be loaded as Android-native binaries. The Android-compatible LWJGL/GLFW + audio layer, native extraction, GL surface/renderer connection, lifecycle handling, and forwarding of touch/gamepad events into GLFW still need to be integrated. The Play button stays gated so it does not pretend that a game started.

A successful JLI/JVM loader probe only verifies that the runtime libraries can be mapped and the `JLI_Launch` symbol is present. It does not prove that Minecraft will start or render.

## GitHub Actions build

The workflow targets JDK 17, Android SDK platform 35, Build Tools 35.0.0, NDK 26.3.11579264, and CMake 3.22.1. The workflow validates those SDK packages, builds the debug APK, and uploads `sally-launcher-debug-apk` as an Actions artifact.

Run locally with:

```bash
chmod +x ./gradlew
./gradlew --no-daemon --stacktrace :app:assembleDebug
```

APK path: `app/build/outputs/apk/debug/app-debug.apk`.

## Upstream relationship

Pojav/Amethyst code was used as an architectural reference only for the investigation. This new request builder does not copy its native launcher implementation or native artifacts. Before incorporating upstream source or binaries, review the applicable license and preserve required notices.


## Stage 9 — JVM startup self-test

The engine diagnostics card now has **Run Java runtime self-test**. It uses the NDK JNI bridge to resolve the installed runtime's `libjli.so`/`libjvm.so`, prepare the Android linker library path, invoke `JLI_Launch` with only `-version`, and save its output under the app-private `files/diagnostics/jli-version.log` directory. The diagnostic runs in a separate, disposable Android process: if JVM initialization crashes or aborts, the launcher UI process is kept separate, and the diagnostic process is terminated after reporting so a later test gets fresh JVM state.

This is a real JVM startup check, **not a Minecraft launch**. Game launch remains intentionally disabled until the Android-compatible LWJGL/GLFW Java/native layer, per-version native extraction and patches, EGL/GLES renderer, and event callbacks are integrated and verified. The check can also fail on a runtime build that exposes JLI symbols but is not compatible with the device's Android linker namespace.

The GitHub Actions workflow installs explicit SDK packages and does not request the retired `tools` package. The uploaded build artifact remains `sally-launcher-debug-apk`.
