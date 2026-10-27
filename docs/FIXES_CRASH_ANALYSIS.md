# Engine crash-repair notes

Date: 2026-10-27

## Scope

This change set repairs the source-level mismatches found by comparing the supplied `NoneLauncher.zip` against the supplied `PojavLauncher-3_openjdk.zip`. It is a source project, not a compiled APK. The original archive was not overwritten.

## Changes applied

1. **Restored the AWT runtime payload.** Bundled the upstream Caciocavallo components for Java 8 (`caciocavallo`) and Java 17 (`caciocavallo17`) under `app/src/main/assets/engine/components/`. `PojavEnginePayloadInstaller` now installs the components through a staging directory, checks the expected version marker and non-empty JARs, and includes them in its readiness report.
2. **Added missing native targets.** CMake now builds `pojavexec_awt`, `awt_xawt`, and `exithook`, and links ByteHook for the exit-hook implementation. The ARM64 `ADRENO_POSSIBLE` compile definition now matches Pojav's original `Android.mk`.
3. **Repaired AWT JNI host lookup.** `awt_bridge.c` now resolves callbacks against `com/sally/launcher/core/engine/AwtBridgeHost` rather than the Pojav `MainActivity` which is not part of this application. Link and clipboard callbacks use the appropriate signatures. Missing Caciocavallo classes/methods are handled as errors instead of process-aborting `assert()` paths.
4. **Added Android-side AWT callbacks.** Added `AwtBridgeHost.kt` and `AWTInputBridge.java` for clipboard, URL/path handoff, and AWT event bridging. Added the corresponding `JREUtils` native declarations.
5. **Completed runtime setup.** The bootstrap installs AWT components, loads the native bridge and exit hooks, and produces actionable diagnostics when required files or libraries are absent. Runtime preparation mirrors Pojav's FreeType and `libawt_xawt.so` compatibility preparation.
6. **Made compatibility checks stricter.** The compatibility inspector checks for AWT/native bridge components and verifies the SHA-256 of the patched GLFW classes JAR. Runtime selection and launch-profile creation refuse Java versions other than 8 and 17, because those are the only versions matching the bundled toolkit.
7. **Corrected JVM launch configuration.** `JliLaunchSpec` selects the Java-version-specific Caciocavallo toolkit/classpath arguments, adds required Java 17 module exports/opens, merges the bootclasspath, and uses a conservative initial heap size rather than reserving the maximum heap at startup.
8. **Improved failure reporting and loading order.** The runtime loader fails early with a readable error if GL4ES, JLI/JVM, or required AWT libraries cannot be loaded; optional OpenAL preload failure is logged. Native exit hook initialization and launch exceptions are surfaced instead of silently proceeding.
9. **Added a regression test** for refusing Java versions without the matching Caciocavallo toolkit and documented the repairs in the project README.

## Validation performed

- CMake configure/generate succeeded using a temporary local stand-in for the ByteHook Prefab package. This checks the CMake project graph and referenced source paths; it does **not** compile Android ABI binaries.
- `clang -fsyntax-only` succeeded for `app/src/main/cpp/pojav/awt_bridge.c` against the installed host JDK JNI headers.
- `javac` succeeded for `app/src/main/java/net/kdt/pojavlaunch/AWTInputBridge.java`.
- A script checked all 21 CMake references to Pojav C/C++ sources and all seven Caciocavallo payload files; none were missing.
- A full `:app:assembleDebug` / unit-test run could not be completed in this environment. The Gradle wrapper needs to download Gradle 8.9 from `services.gradle.org`, but name resolution failed (`UnknownHostException`); Android SDK/NDK artifacts are not installed in this workspace.

## Remaining verification required

These repairs target known structural/runtime incompatibilities, but they are not proof that every crash is fixed. Run `:app:testDebugUnitTest` and `:app:assembleDebug` in Android CI with SDK platform 35, NDK 26.3.11579264, and CMake 3.22.1, then install the resulting APK on a real Android device. Test a Java 8 game and a Java 17 game, including AWT/Swing initialization, renderer creation, clipboard/input, exit, and relaunch behavior. If it still crashes, capture `adb logcat -c` before launch and save `adb logcat -d` along with `files/minecraft/logs/latest.log`; the actual crash stack is needed to identify any remaining device-specific issue.

## Source provenance

Caciocavallo assets and Pojav JNI/native sources were taken from the user-supplied PojavLauncher archive. Preserve upstream files and their applicable licenses/notices when redistributing this project.
