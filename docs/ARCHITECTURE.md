# Architecture

RustCraft is an Android-only Rust application. The current target ABI is `arm64-v8a`; there are no Windows or Linux app targets or desktop entry points.

## Runtime

```text
Android NativeActivity
        |
        v
Rust android_main
        |
        v
 eframe / egui application
        |
        v
     wgpu renderer
```

The app shell owns Android lifecycle integration and UI state. The game engine modules will remain Rust crates/modules and will be added incrementally.

## UI layout

- Portrait: app header, scrollable one-column content, bottom navigation.
- Compact landscape: compact top navigation, scrollable content, no bottom navigation.
- Developer diagnostics: separate screen, not a floating overlay.
- System bars: not immersive fullscreen; themed to match the app background.

## Planned game systems

The next stages add a dedicated 3D wgpu renderer, chunk and block storage, greedy meshing, world generation, physics, singleplayer gameplay, and Java Edition data compatibility.

## Platform policy

Only Android ARM64 is supported during this project phase. Keep the workspace and dependency feature set free of desktop window backends until Android gameplay is stable. This is an intentional focus decision, not a claim that the code can never be ported later.
