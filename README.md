# Sally Launcher — Stage 8

This is a new Android Minecraft: Java Edition launcher project using Kotlin, AndroidX, and a Material 3-style interface. The project now has an official version catalog, version-file installer, Android Java-runtime installer, launch-profile resolver, JNI/GLES diagnostics, and a JLI launch-request builder.

## Stage 8 changes

- Adds `JliLaunchSpecBuilder`, which creates an immutable, shell-free `argv` list and environment map for a future `JLI_Launch` call.
- Preserves arguments as individual strings (including paths with spaces); it never shell-joins the command.
- Checks runtime, client JAR, and classpath files, lists candidate native library directories, and reports concrete engine blockers in the UI.
- Fixes the Android SDK workflow setup by using the runner's preinstalled command-line tools directly instead of running the setup action that failed while requesting the removed `tools` package.
- Keeps native ABI narrowing fix from the previous patch; the stage patch itself only contains files changed for Stage 8.

## Important status

**This is still not playable Minecraft.** Stage 8 does not invoke `JLI_Launch` or start a JVM. The current downloader intentionally skips desktop native classifier JARs, and desktop LWJGL binaries cannot simply be loaded as Android-native binaries. The Android-compatible LWJGL/GLFW + audio layer, native extraction, GL surface/renderer connection, lifecycle handling, and forwarding of touch/gamepad events into GLFW still need to be integrated. The Play button stays gated so it does not pretend that a game started.

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
