# RustCraft

**Android-only voxel sandbox prototype using a Rust game core and Kotlin/Jetpack Compose shell.**

> Status: Phase 0 foundation / early prototype. This is not yet a playable Minecraft Java Edition port.

## Target

- Platform: Android only (ARM64 build target in CI)
- Game direction: clean-room Java Edition-compatible implementation
- Compatibility target: Minecraft Java Edition 1.21.11
- Gameplay priority: single-player / local worlds
- Core: Rust
- Android UI and lifecycle: Kotlin + Jetpack Compose
- Native bridge: JNI, built with Android NDK and `cargo-ndk`

## What works in this starter

- Android landscape application shell and main menu
- Rust native library loaded through JNI
- Deterministic Rust terrain/surface-map generation
- Basic 16x384x16 chunk data structure and terrain fill routine
- Top-down terrain preview with touch movement controls
- GitHub Actions workflow that tests Rust and builds an ARM64 Android debug APK
- Kotlin fallback terrain preview when the native library is not present (useful for IDE previews)

## What is not implemented yet

- 3D renderer / OpenGL ES or Vulkan backend
- First-person camera, collision, gravity, block breaking and placement
- Minecraft Java world-generation parity
- Java Edition registry, data pack, resource pack, model and shader compatibility
- Inventory, crafting, mobs, audio, lighting, redstone, dimensions and survival systems
- Java Edition save import/export and multiplayer protocol
- Microsoft account authentication or online services

## Build locally

Requirements:

- Android Studio with Android SDK 35
- Android NDK `27.2.12479018`
- JDK 17
- Rust stable toolchain
- `cargo-ndk`
- Gradle 8.10.2

Install the Rust Android target and cargo-ndk:

```bash
rustup target add aarch64-linux-android
cargo install cargo-ndk
```

Build Rust native library, then Android APK:

```bash
cargo test --workspace
cargo ndk -t arm64-v8a -o app/src/main/jniLibs build --release -p rustcraft-android
gradle assembleDebug
```

APK output:

```text
app/build/outputs/apk/debug/app-debug.apk
```

## GitHub Actions

Push this repository to GitHub. The workflow at `.github/workflows/android-build.yml` runs Rust tests, compiles the ARM64 JNI library, builds the Android debug APK, and uploads the APK as the `rustcraft-android-debug` workflow artifact.

## Repository layout

```text
RustCraft/
├── app/                         # Android app: Kotlin + Compose
├── crates/
│   ├── game-core/                # Platform-independent Rust world/chunk logic
│   └── android-bridge/           # JNI exports compiled as lib
├── docs/                         # Design and implementation notes
├── .github/workflows/            # CI build
├── Cargo.toml                    # Rust workspace
└── README.md
```

## Compatibility and licensing

RustCraft is an independent implementation. It does not include Minecraft's source code, proprietary binaries, sounds, textures, models, fonts, or other game assets. The current terrain algorithm is original prototype code and is not intended to reproduce Mojang's world generator.

Users must provide files they are legally entitled to use. Do not commit or redistribute Minecraft game files or assets in this repository or its releases. Review the official [Minecraft EULA](https://www.minecraft.net/eula) and applicable terms before building asset import or distribution features.

The RustCraft source in this repository is licensed under MIT. This license does not grant rights to Minecraft or Mojang/Microsoft intellectual property.

## Roadmap

1. **Phase 0 — Foundation:** Android project, Rust workspace, JNI, CI and prototype world preview.
2. **Phase 1 — 3D engine:** Vulkan/OpenGL ES backend, chunk meshing, camera, touch controls and frame pacing.
3. **Phase 2 — World system:** chunk streaming, deterministic terrain, world persistence and save migration design.
4. **Phase 3 — Core gameplay:** player physics, block interaction, inventory, crafting and basic lighting.
5. **Phase 4 — Java data compatibility:** registries, block states, resource-pack parsing, data-driven content and version-specific behavior.
6. **Phase 5 — 1.21.11 parity work:** incremental implementation and regression testing against the Java Edition target.

A full Java Edition-compatible implementation is a large, multi-phase engineering project. Reaching the target means implementing and testing each subsystem; matching the version number in the UI alone is not compatibility.
