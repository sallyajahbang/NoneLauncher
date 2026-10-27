# Sally Launcher — Stage 16

Sally Launcher is an Android Minecraft: Java Edition launcher project using Kotlin, AndroidX and a Material 3-style interface. Its current foundation includes an official version catalog, version-file and asset downloads, an Android Java runtime installer, launch-profile resolution, the Pojav-derived Android GLFW/LWJGL bridge, input/surface diagnostics, and a native JLI launch adapter.

## Stage 16 — native JLI launch adapter

Stage 16 adds the Java `VMLauncher.launchJVM(String[])` entry point matching the included Pojav native bridge, correct native declarations for Android linker-path updates and `chdir`, a guarded launch executor, a landscape game host Activity, and an in-memory session contract that rejects offline placeholder tokens. The executor configures the process environment and working directory before calling the native JLI entry point, and it refuses to launch from the UI thread or before the game surface is bound.

**Minecraft is not yet verified playable.** The Microsoft/Xbox Live/XSTS/Minecraft authentication flow is not implemented or connected to the UI, so the Play button intentionally remains blocked until a real Java account session is supplied. The added session model validates structure only; its store must only be populated by a successful authentication provider. Native compilation, APK build, device-side JLI startup, and actual game rendering must still be checked in GitHub Actions/on a device.

See [Stage 16 launch adapter notes](docs/STAGE16_JLI_LAUNCH_ADAPTER.md) and [Stage 15 bridge notes](docs/STAGE15_POJAV_ENGINE_BRIDGE.md). Keep the LGPL-3.0 notices under `app/src/main/assets/engine/pojav/` with redistributed Pojav-derived code.

## GitHub Actions build

The workflow targets JDK 17, Android SDK platform 35, Build Tools 35.0.0, NDK 26.3.11579264, CMake 3.22.1, Android Gradle Plugin 8.7.3, Kotlin Android plugin 2.0.21, and Gradle 8.9. ByteHook 1.1.1 brings ShadowHook 1.1.1 transitively, avoiding the API 37 metadata requirement of ByteHook 1.1.2 / ShadowHook 2.0.1. It runs `:app:testDebugUnitTest`, builds the debug APK, and uploads `sally-launcher-debug-apk` as an Actions artifact.

Run locally with:

```bash
chmod +x ./gradlew
./gradlew --no-daemon --stacktrace :app:testDebugUnitTest
./gradlew --no-daemon --stacktrace :app:assembleDebug
```

APK path: `app/build/outputs/apk/debug/app-debug.apk`.

## Earlier stages

- **Stage 15:** Added the Pojav-derived native bridge source, patched GLFW classes JAR, and ABI-specific LWJGL/GL4ES/OpenAL libraries. The bridge and surface need Android CI/device verification.
- **Stage 14:** Replaced the retired legacy SDK `tools` package request with explicit supported SDK package IDs and added engine compatibility checks.
- **Stages 1–13:** Added the app skeleton, version/runtime installers, launch metadata builder, JLI smoke probe, graphics/input diagnostics and input callback routing.

## Engine crash repairs (2026-10)

The current source includes a more complete Pojav-compatible runtime payload: Java 8 and Java 17 Caciocavallo assets, native AWT/GLFW bridges, exit hooks, and stricter launch-time compatibility checks. The runtime installer refuses Java versions that do not match the bundled AWT toolkit and checks the patched GLFW classes by SHA-256 before launch. The CMake port also mirrors the arm64 Adreno compile definition from upstream Pojav's `Android.mk`.

These are source-level repairs, not a verified release APK. The first CI log showed that ByteHook 1.1.2 / ShadowHook 2.0.1 require compileSdk 37; the attempted SDK 37 workaround then failed because GitHub Actions' `sdkmanager` could not find `platforms;android-37`. The current source therefore returns to the known SDK 35 / AGP 8.7.3 / Gradle 8.9 toolchain and pins ByteHook to 1.1.1 (with ShadowHook 1.1.1 transitively). A fresh CI run is still required; this workspace cannot download Gradle from `services.gradle.org` or perform a full Android build. CMake configure, the host-side `awt_bridge.c` syntax check, and compilation of `AWTInputBridge.java` passed previously. Before distributing the launcher, run the project CI build and test on Android hardware; a real crash root cause can only be confirmed against the device's `logcat` and `latest.log`. See `docs/FIXES_CRASH_ANALYSIS.md`.

## Material 3 UI refresh

The launcher, local-profile manager, and Microsoft device-code sign-in screens now use Jetpack Compose + Material 3. It includes Play, Versions, Account, Settings, and DEV tabs. Version channels are separated, installed versions are grouped by channel, and Play provides a searchable picker restricted to installed versions. See `docs/COMPOSE_MD3_UI_REDESIGN.md` for the change overview and validation status.
