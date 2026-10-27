# Stage 14 — CI SDK setup and engine compatibility gate

## What changed

- Replaced the manually managed SDK installation step with `android-actions/setup-android@v4` and explicit package IDs. This avoids the retired `tools` package that failed in the supplied GitHub Actions log before Gradle started.
- Added an engine compatibility inspector. It checks that the Android Java runtime matches the app process ABI, that `libjli.so`/`libjvm.so` exist, and that the expected Android LWJGL/GL4ES/audio/input bridge plus patched GLFW classes are present.
- Updated the launch request's `java.library.path` to include the APK's `nativeLibraryDir`, runtime library directories, and game-native directory. The previous code only pointed the JVM at the game `natives` directory, which would omit libraries packaged inside the APK.
- Added host unit tests for architecture normalization and prerequisite reporting.

## What the supplied Actions log actually showed

The log failed in the Android SDK setup action with `Failed to find package 'tools'`. Gradle never reached Kotlin/C++ compilation in that run. The new workflow requests `platform-tools`, Android 35, Build Tools 35.0.0, NDK 26.3.11579264, and CMake 3.22.1 explicitly; it does not request the removed legacy `tools` package.

## Current engine status

The code intentionally remains launch-blocked until the complete matching Android engine bundle is present. Required files include `libpojavexec.so`, `liblwjgl.so`, `liblwjgl_opengl.so`, `liblwjgl_stb.so`, `libopenal.so`, `libgl4es_114.so`, and `minecraft/engine/pojav/lwjgl-glfw-classes.jar`. Stock Linux native-classifier JARs are not accepted as Android shared libraries. The runtime profile also still requires a real authenticated Minecraft session, a live EGL surface, and the native callback/render loop.

This patch does **not** claim that Minecraft is playable. It makes the CI failure actionable and prevents a future JLI call from being treated as ready before the required ABI/native pieces exist.

## Validation

Run `./gradlew --no-daemon --stacktrace :app:testDebugUnitTest` and `./gradlew --no-daemon --stacktrace :app:assembleDebug` in GitHub Actions. The host tests added here are not a replacement for Android NDK compilation or an on-device engine launch test.
