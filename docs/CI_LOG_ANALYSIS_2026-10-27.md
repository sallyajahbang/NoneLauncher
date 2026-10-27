# CI log analysis — 2026-10-27

## What the uploaded ZIP contains

The uploaded archive is GitHub Actions output for **Build debug APK**, not a device `logcat` or Minecraft `latest.log`. It therefore identifies a build failure, not a runtime crash on the phone.

## Confirmed failure

Job step: `8_Run input mapping, engine compatibility, and callback bridge unit tests.txt`

Failed task: `:app:checkDebugAarMetadata`

The log reports two metadata violations:

- `com.bytedance:bytehook:1.1.2` requires `compileSdk` 37 or newer; the app was compiled against Android 35.
- `com.bytedance.android:shadowhook:2.0.1` requires `compileSdk` 37 or newer; the app was compiled against Android 35.

The build was using AGP 8.7.3, whose log-reported recommended maximum compile SDK was 35. The Kotlin/native unit tests did not run: Gradle stopped in AAR metadata validation before compilation/tests.

## Changes made in this package

- `app/build.gradle`: `compileSdk` raised to 37 and the separate Kotlin Android plugin / old `kotlinOptions` block removed because AGP 9 supplies built-in Kotlin. ByteHook 1.1.2 remains pinned. `targetSdk` is intentionally unchanged at 35 to avoid changing runtime behavior as part of a compile toolchain repair.
- `build.gradle`: AGP upgraded to 9.1.1.
- `gradle/wrapper/gradle-wrapper.properties`: Gradle wrapper upgraded to 9.3.1.
- `.github/workflows/build.yml`: CI now installs Android SDK platform 37 and Build Tools 36.0.0.
- `README.md` and `docs/FIXES_CRASH_ANALYSIS.md`: documentation updated to reflect the CI failure and the repair.

AGP 9.1.1 officially supports API 37 and requires Gradle 9.3.1. It provides built-in Kotlin support, so the old `org.jetbrains.kotlin.android` plugin was removed rather than applied alongside AGP's built-in implementation.

## What is not yet proven

A new GitHub Actions run must still confirm unit tests, CMake configuration for all four ABIs, and `assembleDebug` with this updated toolchain. The uploaded logs contain no Android device crash stack, so they do not establish whether a remaining Minecraft launch crash exists. If the build passes but the game still crashes, collect `adb logcat` from just before Play is tapped until process exit and the resulting `latest.log`.
