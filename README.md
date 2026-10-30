# RustCraft

**RustCraft** is an independent Rust voxel-game/reimplementation research project. The compatibility baseline is **Minecraft Java Edition 1.21.11**; the initial gameplay priority is singleplayer. It targets Android ARM64, Windows x86_64, and Linux x86_64.

> This repository is not affiliated with Mojang Studios or Microsoft. It contains no Minecraft JAR, proprietary source code, textures, sounds, models, or other proprietary game assets. Use assets you created yourself or that you are licensed to use. Compatibility with Java Edition is a long-term engineering goal, not a claim that the current prototype can load or run Minecraft worlds.

## Current scope: Phase 0 + Phase 1 foundation

- Rust 2024 application foundation (single package initially; split into crates as engine boundaries stabilize).
- `eframe` + `egui` UI rendered through the `wgpu` backend.
- Desktop executable for Windows and Linux.
- Android `NativeActivity` entry point for ARM64, with no Kotlin UI layer.
- Home, Worlds, Settings, About screens.
- Runtime diagnostics overlay (FPS estimate, uptime, placeholder renderer/world status).
- Isometric voxel preview drawn as egui primitives to validate layout and UI composition.

### Important renderer status

The current preview is **not yet a dedicated 3D voxel GPU mesh pipeline**. `eframe` uses `egui-wgpu`/`wgpu` to render the application UI, while the isometric block preview is composed from egui shapes. The next rendering milestone is a custom `wgpu` render pipeline (WGSL shaders, vertex/index buffers, depth buffer, camera matrices) for actual 3D cubes and chunk meshes. This separation is intentional: it avoids pretending that the chunk renderer or Minecraft world runtime already exists.

## Requirements

- Rust stable (2024 edition; use a current stable toolchain).
- Desktop Linux: X11/Wayland, Vulkan, OpenSSL, and ALSA development libraries. Ubuntu/Debian users can install the packages listed in [docs/BUILD.md](docs/BUILD.md).

- Android: Android SDK + NDK, Java 17, `cargo-ndk`, and the `aarch64-linux-android` Rust target.

## Desktop build

```bash
cargo run
# or
cargo build --release
```

## Android ARM64 build

Install the Android SDK/NDK, JDK 17, Rust Android target, and cargo-ndk:

```bash
rustup target add aarch64-linux-android
cargo install cargo-ndk --locked
```

Build the APK from the Gradle project (JDK 17, Android SDK 35, Build Tools 35.0.0, NDK 27.2.12479018, and Gradle 8.11.1):

```bash
cd platforms/android
gradle --no-daemon assembleDebug
```

The Gradle `preBuild` task invokes `cargo-ndk` with Android API 26 and places `librustcraft.so` under `app/src/main/jniLibs/arm64-v8a`. The APK is written to `app/build/outputs/apk/debug/app-debug.apk`.

## Project layout

```text
rustcraft/
├── Cargo.toml
├── rust-toolchain.toml
├── src/
│   ├── lib.rs          # desktop + Android entry points
│   ├── main.rs         # desktop binary
│   └── app.rs          # egui app, screens, debug overlay, voxel preview
├── platforms/android/  # NativeActivity + Gradle build configuration
├── docs/               # architecture and implementation notes
└── .github/workflows/  # Linux, Windows, Android CI
```

## Next implementation steps

1. Add a real custom `wgpu` 3D pipeline and camera controls.
2. Add a single 16×16×16 test chunk and cube mesh generation.
3. Add block registry and face/greedy meshing.
4. Add worker-thread chunk generation and streaming.
5. Add NBT reader and Anvil region reader for the selected Java version.
6. Add independent world generation, player controller, collision, block interaction, and save/load.

## License

The starter code is dual-licensed under MIT OR Apache-2.0. Third-party crates retain their own licenses. No Minecraft assets are included.

## egui / eframe version compatibility

This project currently targets `eframe` / `egui` 0.37. The application uses the
0.34+ API split (`App::logic` + `App::ui`), `egui::Panel`, `CentralPanel::show`,
and the `Context::global_style_mut` theme API. Do not paste examples written for
pre-0.34 egui without migrating their API calls.

## GitHub Actions builds

The CI workflow runs on every push, pull request, and manual dispatch. It builds
Linux and Windows desktop binaries and an Android ARM64 debug APK. The generated
files are uploaded to the workflow run as downloadable artifacts. Tag a commit
with `v*` or manually run the release workflow to build release-mode outputs.

See [docs/BUILD.md](docs/BUILD.md) for the local build requirements and commands.
