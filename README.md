# Sally Launcher — Stage 6

A fresh Android Minecraft: Java Edition launcher codebase with a Material 3-style interface, official Mojang version catalog, version file installer, Android Java runtime manager, and diagnostic launch-profile builder. Package name: `com.sally.launcher`.

## This stage adds

- A C++17 JNI shared library (`libsallybridge.so`) built for ARM64, ARM32, x86_64 and x86 Android ABIs.
- A bounded, mutex-protected native input queue for touch, key, mouse move/button, scroll, and viewport diagnostic events. Queue overflow rejects new events instead of overwriting old ones.
- An Android OpenGL ES 2.0 diagnostic screen. The visible marker follows touch/mouse position, and the screen displays native bridge health plus event counts.
- A `Test graphics & input bridge` button in the main launcher UI.
- GitHub Actions setup for Android NDK 26.3 and CMake 3.22.1, so native compilation happens as part of `:app:assembleDebug`.

## Important limitation

**This is not a playable Minecraft engine yet.** This stage proves that Android GLES can draw a diagnostic scene and Android input can cross a JNI boundary into a native queue. The queue is not wired to LWJGL/GLFW callbacks, and the app does not yet invoke an embedded OpenJDK JVM. Minecraft's Android-compatible native libraries, the actual runtime/JNI invocation bridge, renderer integration, audio, and complete keyboard/mouse/touch/gamepad behavior still need to be integrated. The Play button deliberately continues to report that the game engine is not ready.

## Build

Requirements: JDK 17, Android SDK platform 35, build-tools 35.0.0, NDK 26.3.11579264, and CMake 3.22.1.

```bash
chmod +x ./gradlew
./gradlew :app:assembleDebug
```

APK output: `app/build/outputs/apk/debug/app-debug.apk`. GitHub Actions uploads the same artifact as `sally-launcher-debug-apk`.

## Test the diagnostic screen

1. Install the debug APK on an Android device.
2. Open Sally Launcher and tap **Test graphics & input bridge**.
3. Verify the geometric marker is drawn on a dark background.
4. Touch and drag across the canvas. With a physical mouse, try moving, clicking and scrolling. A physical keyboard can also send key events.
5. Check whether `Native events processed` increases and `Last event` changes.

A successful diagnostic means the GLES surface and diagnostic JNI event queue work; it does **not** prove that Minecraft input works.

## Upstream relationship

The architecture is informed by public Pojav/Amethyst engineering patterns, but this diagnostic bridge is newly authored and does not copy their native input implementation. Any future reuse of upstream code or native artifacts must be reviewed for license compatibility and include the appropriate notices. Runtime distribution details are documented upstream at https://github.com/AngelAuraMC/angelauramc-openjdk-build and https://github.com/AngelAuraMC/Amethyst-Android.
