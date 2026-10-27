# Sally Launcher — Stage 16

Sally Launcher is an Android Minecraft: Java Edition launcher project using Kotlin, AndroidX and a Material 3-style interface. Its current foundation includes an official version catalog, version-file and asset downloads, an Android Java runtime installer, launch-profile resolution, the Pojav-derived Android GLFW/LWJGL bridge, input/surface diagnostics, and a native JLI launch adapter.

## Stage 16 — native JLI launch adapter

Stage 16 adds the Java `VMLauncher.launchJVM(String[])` entry point matching the included Pojav native bridge, correct native declarations for Android linker-path updates and `chdir`, a guarded launch executor, a landscape game host Activity, and an in-memory session contract that rejects offline placeholder tokens. The executor configures the process environment and working directory before calling the native JLI entry point, and it refuses to launch from the UI thread or before the game surface is bound.

**Minecraft is not yet verified playable.** The Microsoft/Xbox Live/XSTS/Minecraft authentication flow is not implemented or connected to the UI, so the Play button intentionally remains blocked until a real Java account session is supplied. The added session model validates structure only; its store must only be populated by a successful authentication provider. Native compilation, APK build, device-side JLI startup, and actual game rendering must still be checked in GitHub Actions/on a device.

See [Stage 16 launch adapter notes](docs/STAGE16_JLI_LAUNCH_ADAPTER.md) and [Stage 15 bridge notes](docs/STAGE15_POJAV_ENGINE_BRIDGE.md). Keep the LGPL-3.0 notices under `app/src/main/assets/engine/pojav/` with redistributed Pojav-derived code.

## GitHub Actions build

The workflow targets JDK 17, Android SDK platform 35, Build Tools 35.0.0, NDK 26.3.11579264, and CMake 3.22.1. It runs `:app:testDebugUnitTest`, builds the debug APK, and uploads `sally-launcher-debug-apk` as an Actions artifact.

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
