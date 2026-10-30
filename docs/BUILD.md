# Build guide

RustCraft is built from one Rust codebase for Linux, Windows, and Android ARM64.
The Android APK is assembled by Gradle; its `preBuild` task invokes `cargo-ndk`
to compile `librustcraft.so` for `arm64-v8a`.

## Requirements

- Current stable Rust toolchain (the project declares Rust 1.95 or newer).
- Desktop Linux: X11/Wayland development libraries, OpenSSL, ALSA, Vulkan headers,
  and `pkg-config`.
- Android: JDK 17, Android SDK platform 35, Build Tools 35.0.0, NDK 27.2.12479018,
  `cargo-ndk`, and Gradle 8.11.1.
- Windows: Visual Studio Build Tools with the MSVC C++ workload.

## Desktop Linux

```bash
cargo fmt --all -- --check
cargo check --all-targets
cargo test --all-targets
cargo build --release
```

## Desktop Windows

Open a Developer PowerShell with the MSVC Build Tools installed:

```powershell
cargo check --all-targets
cargo build --release
```

## Android ARM64 native library

```bash
rustup target add aarch64-linux-android
cargo install cargo-ndk --locked

# Set ANDROID_NDK_HOME to the installed NDK directory first.
cargo ndk --platform 26 -t arm64-v8a -o platforms/android/app/src/main/jniLibs build --release --lib
```

## Android APK

Install JDK 17, SDK platform 35, Build Tools 35.0.0, NDK 27.2.12479018,
`cargo-ndk`, and Gradle 8.11.1. Then:

```bash
cd platforms/android
gradle --no-daemon assembleDebug
```

The APK is written to:

`platforms/android/app/build/outputs/apk/debug/app-debug.apk`

For a release APK, run `gradle --no-daemon assembleRelease`.

## eframe 0.37 API note

The UI is written against eframe/egui 0.37. `eframe::App` requires `ui()` and
provides `logic()` for per-frame non-UI work. Panels are composed through
`egui::Panel` and `egui::CentralPanel` using a `&mut egui::Ui`; theme changes
use `Context::global_style_mut`. Older egui snippets using `App::update`,
`SidePanel`, `TopBottomPanel`, `Context::style`, or `Context::set_style` will
not compile against this dependency version.

## GitHub Actions

`.github/workflows/ci.yml` runs on every push, pull request, and manual dispatch.
It validates and builds Linux and Windows desktop binaries, builds the Android
ARM64 debug APK, and uploads each output as a workflow artifact.

`.github/workflows/release.yml` builds release-mode outputs when manually started
or when a `v*` tag is pushed. Download artifacts from the workflow run's
**Artifacts** section.

The CI workflow provisions Gradle directly with `gradle/actions/setup-gradle`,
so a Gradle wrapper is not required in this starter repository. For local use,
you may generate and commit one with `gradle wrapper --gradle-version 8.11.1`.
