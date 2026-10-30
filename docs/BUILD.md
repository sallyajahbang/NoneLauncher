# Android Build Guide

RustCraft targets **Android ARM64 only**. Windows and Linux builds are intentionally not maintained.

## Required tools

- Rust stable
- `aarch64-linux-android` Rust target
- Android SDK Platform 35
- Android Build Tools 35.0.0
- Android NDK 27.2.12479018
- JDK 17
- Gradle 8.11.1
- `cargo-ndk`

## Local setup

```bash
rustup target add aarch64-linux-android
cargo install cargo-ndk --locked
```

Set `ANDROID_NDK_HOME` to the installed NDK root, for example:

```bash
export ANDROID_NDK_HOME="$ANDROID_HOME/ndk/27.2.12479018"
export ANDROID_NDK_ROOT="$ANDROID_NDK_HOME"
```

Build a debug APK:

```bash
cd platforms/android
gradle --no-daemon assembleDebug
```

Output:

```text
app/build/outputs/apk/debug/app-debug.apk
```

The Gradle `preBuild` task runs `cargo ndk --platform 26 -t arm64-v8a ... build --release --lib` and copies `librustcraft.so` into the Android app's `jniLibs` directory.

## GitHub Actions

The `RustCraft Android CI` workflow:

1. Installs JDK 17.
2. Installs Android SDK 35 and NDK 27.2.12479018.
3. Installs Rust stable with the ARM64 Android target.
4. Installs `cargo-ndk`.
5. Checks Rust formatting.
6. Builds `assembleDebug`.
7. Uploads the APK and native `.so` as workflow artifacts.

The `RustCraft Android Release` workflow creates an unsigned release APK for tags beginning with `v` or manual workflow dispatch.

## Troubleshooting

- **NDK not found:** verify that `ANDROID_NDK_HOME` points to `ndk/27.2.12479018`.
- **Missing `librustcraft.so`:** check the `buildRust` task output before the Gradle `preBuild` task.
- **Vulkan initialization fails:** collect `adb logcat` output. A GLES fallback policy can be added after checking device GPU support.
- **APK installs but closes immediately:** collect `adb logcat` and inspect the native crash / `RustCraft Android runtime failed` messages.

The release APK is not signed for production distribution yet.
