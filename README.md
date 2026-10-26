# NoneLauncher

Standalone Android launcher foundation built from scratch.

- **Package:** `com.sally.nonelauncher`
- **Android:** minimum API 26, compile/target API 35
- **Native layer:** C++ JNI library named `noneinput`
- **Build:** `./gradlew :app:assembleDebug`

## Current stage

The app contains a launcher screen, native-input diagnostics, and a Java runtime archive manager. It is **not yet a Minecraft runtime** and does not currently download, install, or launch Minecraft.

The input bridge is owned by NoneLauncher and is not copied from PojavLauncher. Android touch, multi-touch, mouse movement/buttons/relative motion, scroll, keyboard keys, printable character input, display resize, and pointer-grab state are delivered to a bounded native event queue. `NativeInput.drainEvents()` returns pending events in groups of six float values:

`type, code, x, y, value, modifiers`

The queue retains up to 512 pending events. When it fills, the oldest event is discarded and counted by `droppedEventCount()`. This gives a future rendering/runtime adapter a single input boundary to consume without tying the routing code to a particular game engine.

## Build locally

```bash
./gradlew :app:assembleDebug
```

GitHub Actions installs Android API 35, Build Tools 35.0.0, CMake 3.22.1 and NDK 27.2.12479018, then builds and uploads the debug APK.

## Try the input diagnostics

1. Install and open the debug APK.
2. Tap **OPEN INPUT DIAGNOSTICS**.
3. Touch and drag with one or more fingers, connect a mouse/keyboard, or scroll with a wheel/trackpad.
4. Watch the recent-event list. The **DROPPED** counter should normally stay at `0`; it only increases if pending events exceed the 512-event queue capacity before they are drained.

The diagnostic screen drains events for display. A future game-runtime adapter should become the consumer instead of the diagnostic view.


## Java runtime manager (stage 2)

The launcher can import a local Java runtime ZIP through Android's document picker, extract it into app-private storage (`files/runtimes`), and list archives that contain both `release` and `bin/java`. Imports are staged before registration and reject path traversal, archives with more than 30,000 entries, and expanded data over 1 GiB. The manager displays Java version and architecture metadata from the runtime's `release` file.

The project now bundles `jre17.tar.xz` as an app asset and automatically extracts it to app-private storage on first opening the Runtime Manager. The archive identifies itself as Java 17 with `OS_ARCH="aarch64"`, meaning **64-bit ARM (ARM64)**. The runtime archive is 36 MB compressed and needs additional private storage after extraction. The app also retains ZIP import for other runtimes.

This stage is still runtime **catalog and installation only**. It does not yet execute `bin/java`, set up Android native-library loading, download Minecraft manifests/assets/libraries, or launch a game. The bundled runtime is an older 2021 ARM64 build. It is intended for 64-bit ARM Android devices; compatibility still depends on the runtime's Android/Bionic build and device environment.
