# RustCraft

**RustCraft** is an independent Rust-native voxel game project targeting compatibility with Minecraft Java Edition **1.21.11**. Development is currently **Android ARM64 only**. The first gameplay target is singleplayer.

> RustCraft is not affiliated with Mojang or Microsoft. This project does not bundle proprietary Minecraft code or game assets. Users must provide assets they are legally entitled to use.

## Current scope

- Android NativeActivity entry point written in Rust.
- `eframe` / `egui` application UI rendered through `wgpu`.
- Android-first responsive layout for portrait and compact landscape.
- Touch-sized controls, one-column scrolling content, bottom navigation in portrait, compact top navigation in landscape.
- Diagnostics moved into a dedicated Developer screen instead of a floating overlay.
- Home, Worlds, Settings, About, and Developer Diagnostics screens.
- Android system bars are left visible; the app does not force immersive fullscreen.
- ARM64 APK build through Gradle + `cargo-ndk`.
- GitHub Actions CI and unsigned release APK workflow.

The current voxel graphic is a UI preview, not yet the dedicated 3D chunk renderer. World generation, Anvil/NBT loading, gameplay, and Java protocol are not implemented yet.

## Tech stack

| Component | Technology |
|---|---|
| Language | Rust 2024 |
| Android entry point | NativeActivity + winit |
| UI | egui / eframe |
| GPU backend | wgpu |
| Native ABI | Android ARM64 (`arm64-v8a`) |
| Minimum Android | API 26 |
| Android compile/target SDK | 35 |
| Native build | cargo-ndk + NDK 27.2 |
| Android packaging | Gradle 8.11.1 + AGP 8.9.2 |

## Android UI notes

See [`docs/ANDROID_UI.md`](docs/ANDROID_UI.md) for the phone-first layout, landscape behavior, touch targets, and system-bar handling.

## Build locally

Requirements:

- Rust stable toolchain
- Android SDK Platform 35 and Build Tools 35.0.0
- Android NDK 27.2.12479018
- JDK 17
- Gradle 8.11.1
- `cargo-ndk`

Install the Rust target and helper:

```bash
rustup target add aarch64-linux-android
cargo install cargo-ndk --locked
```

Set `ANDROID_NDK_HOME` to the installed NDK directory. Then build the APK:

```bash
cd platforms/android
gradle assembleDebug
```

APK output:

```text
platforms/android/app/build/outputs/apk/debug/app-debug.apk
```

The Gradle `preBuild` task invokes `cargo-ndk` and places `librustcraft.so` in `app/src/main/jniLibs/arm64-v8a`.

## GitHub Actions

`.github/workflows/ci.yml` builds the Android ARM64 debug APK on every push, pull request, or manual dispatch. Download the APK from the workflow run's **Artifacts** section.

`.github/workflows/release.yml` builds an Android ARM64 release APK when manually dispatched or when a `v*` tag is pushed. The generated APK is currently **unsigned**; configure a signing keystore through GitHub Actions secrets before distributing it as a production release.

## Repository layout

```text
rustcraft/
├── .github/workflows/
│   ├── ci.yml
│   └── release.yml
├── docs/
├── platforms/android/
│   ├── app/
│   │   └── src/main/
│   │       ├── AndroidManifest.xml
│   │       ├── jniLibs/arm64-v8a/
│   │       └── res/values/styles.xml
│   ├── build.gradle.kts
│   └── settings.gradle.kts
├── src/
│   ├── app.rs
│   └── lib.rs
├── Cargo.toml
└── rust-toolchain.toml
```

## Roadmap

1. Android app shell and responsive UI.
2. Dedicated wgpu 3D renderer with camera, depth buffer, WGSL shaders, and GPU mesh buffers.
3. Voxel chunk representation and greedy meshing.
4. Singleplayer world generation, player movement, collision, and block interaction.
5. NBT / Anvil reading and Java Edition compatibility adapters.
6. Save/load, inventory, crafting, entities, and further gameplay systems.

## License

The RustCraft source is dual-licensed under MIT OR Apache-2.0. See `LICENSE-MIT.txt` and `LICENSE-APACHE.txt`.
