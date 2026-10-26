# Sally Launcher — Stage 7

A new Android Minecraft: Java Edition launcher codebase with a Material 3-style interface, official Mojang version catalog, version file installer, Android Java runtime manager, launch-profile resolver, and native diagnostics. Package name: `com.sally.launcher`.

## This stage adds

- `JliBridgeDiagnostics`, a Kotlin API that validates an installed runtime directory before calling into native code.
- A native runtime probe that searches only inside the selected runtime for `libjli.so` and `libjvm.so`, attempts to load them with Android's dynamic linker, and verifies that `JLI_Launch` is exported.
- Explicit diagnostics for missing libraries, dynamic-linker errors, and a runtime switch after a previous runtime has been mapped. Runtime libraries stay loaded for the process lifetime, so switching runtime versions requires restarting Sally Launcher.
- A **Probe installed JVM bridge** button in the main screen. The probe runs on the IO executor and reports the linker result.
- The existing bounded JNI input queue and GLES diagnostic screen remain intact.

## Important limitation

**This is still not a playable Minecraft engine.** A successful probe proves only that the runtime's JLI/JVM libraries were found and could be mapped and the `JLI_Launch` symbol resolved. It does not call `JLI_Launch`, start a JVM, or launch Minecraft. Mojang's desktop LWJGL native artifacts are not Android-compatible by default. The Android-compatible LWJGL/GLFW bridge, graphics renderer integration, audio, and event forwarding must be connected before the Play action can launch a game. The Play button therefore remains gated and does not claim that Minecraft has started.

## Build

Requirements: JDK 17, Android SDK platform 35, build-tools 35.0.0, NDK 26.3.11579264, and CMake 3.22.1.

```bash
chmod +x ./gradlew
./gradlew :app:assembleDebug
```

APK output: `app/build/outputs/apk/debug/app-debug.apk`. GitHub Actions uploads it as `sally-launcher-debug-apk`.

## Run the diagnostics

1. Install the debug APK on an Android device and install a Java runtime using the Java Runtime section.
2. Tap **Probe installed JVM bridge**. Sally checks the newest verified runtime installed on the device.
3. A passing probe reports paths for `libjli.so` and `libjvm.so` and confirms that `JLI_Launch` can be resolved. A failure includes the relevant linker error when Android provides one.
4. Tap **Test graphics & input bridge** to independently test the GLES diagnostic screen and JNI input event queue.

A successful loader probe does not mean Minecraft's launch path works, and loading JVM libraries may reserve that runtime for the current app process. Restart Sally Launcher before testing a different runtime after a probe.

## Upstream relationship

The loader strategy is informed by publicly available Pojav/Amethyst implementation patterns. This new probe does not copy their JRE launcher code or native artifacts. Any future reuse of upstream code or native artifacts must be reviewed for license compatibility and include appropriate notices. Runtime distribution details are available at https://github.com/AngelAuraMC/angelauramc-openjdk-build and https://github.com/AngelAuraMC/Amethyst-Android.
