# Sally Launcher — Stage 15

This is a new Android Minecraft: Java Edition launcher project using Kotlin, AndroidX, and a Material 3-style interface. The project includes an official version catalog, version-file installer, Android Java-runtime installer, launch-profile resolver, JNI/GLES diagnostics, JLI launch-request builder, and a Pojav-derived Android GLFW/LWJGL bridge payload.

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

## Stage 15 — Pojav Android engine bridge

Stage 15 vendors the LGPL-3.0 Pojav Android JNI compatibility source, its matching patched GLFW classes JAR, and native LWJGL/GL4ES/OpenAL payloads for four Android ABIs. CMake builds `libpojavexec.so`; the launch-profile builder installs the patched GLFW JAR and places it first on the JVM classpath. A `PojavGameSurfaceView` now provides a surface + input bridge component.

**This is still not playable Minecraft.** The native bridge has not been proven to compile in Android CI or load on a physical device, and the app does not yet invoke the Minecraft main class through JLI_Launch with a complete renderer/game lifecycle and authenticated session. The Play button remains gated. Do not infer gameplay readiness from the presence of the libraries. See [Stage 15 bridge notes](docs/STAGE15_POJAV_ENGINE_BRIDGE.md) and `app/src/main/assets/engine/pojav/THIRD_PARTY_NOTICES.txt` for scope and license provenance.


## Stage 9 — JVM startup self-test

The engine diagnostics card now has **Run Java runtime self-test**. It uses the NDK JNI bridge to resolve the installed runtime's `libjli.so`/`libjvm.so`, prepare the Android linker library path, invoke `JLI_Launch` with only `-version`, and save its output under the app-private `files/diagnostics/jli-version.log` directory. The diagnostic runs in a separate, disposable Android process: if JVM initialization crashes or aborts, the launcher UI process is kept separate, and the diagnostic process is terminated after reporting so a later test gets fresh JVM state.

This is a real JVM startup check, **not a Minecraft launch**. Game launch remains intentionally disabled until the Android-compatible LWJGL/GLFW Java/native layer, per-version native extraction and patches, EGL/GLES renderer, and event callbacks are integrated and verified. The check can also fail on a runtime build that exposes JLI symbols but is not compatible with the device's Android linker namespace.

The GitHub Actions workflow installs explicit SDK packages and does not request the retired `tools` package. The uploaded build artifact remains `sally-launcher-debug-apk`.
