# CI log analysis — 2026-10-27

## What the uploaded archives contain

Both uploaded archives are GitHub Actions logs for **Build debug APK**, not Android device `logcat` or Minecraft `latest.log`. They show build-pipeline failures, not a confirmed crash while Minecraft is running.

## First CI log

The build reached Gradle but failed at `:app:checkDebugAarMetadata`:

- `com.bytedance:bytehook:1.1.2` requires `compileSdk` 37 or newer.
- Its dependency `com.bytedance.android:shadowhook:2.0.1` also requires `compileSdk` 37 or newer.

The configured toolchain used Android API 35 / AGP 8.7.3.

## Second CI log

The attempted workaround moved the workflow to Android SDK platform 37 and Build Tools 36.0.0. It failed earlier, inside `android-actions/setup-android`, with:

`Warning: Failed to find package 'platforms;android-37'`

No Gradle task, test, CMake configure, or APK build ran in this second job. This is the direct cause of that run's failure.

## Correction in the current source package

To handle both logged failures without depending on an unavailable SDK package, the project has been returned to its original known build toolchain:

- Android Gradle Plugin 8.7.3
- Gradle 8.9
- Kotlin Android plugin 2.0.21
- `compileSdk` / SDK platform 35; Build Tools 35.0.0
- ByteHook 1.1.1, which declares ShadowHook 1.1.1 as its dependency (Maven Central metadata: https://mvnrepository.com/artifact/com.bytedance/bytehook/1.1.1)

This is intended to remove the compileSdk-37 metadata constraint and the unavailable SDK-37 setup dependency. A fresh GitHub Actions run must confirm it. I cannot claim the APK builds until that run completes.

## What is not proven

Neither CI archive contains a device crash stack. If CI completes but the game crashes on launch, collect `adb logcat` from before pressing Play until the process exits, plus the resulting `files/minecraft/logs/latest.log`.
