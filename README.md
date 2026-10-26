# Sally Launcher — Stage 1

A fresh Android launcher codebase informed by the architecture study of PojavLauncher. This is **not a rebranded PojavLauncher**: it has a new package, UI, app structure, version-catalog implementation and engine boundary. No PojavLauncher app classes or native renderer binaries are included in this stage.

## What works in this stage

- Simple Material 3-style Android UI.
- Loads Mojang's public `version_manifest_v2.json` over HTTPS.
- Lists official release and snapshot versions from the manifest.
- Remembers the selected version.
- Caches the manifest and falls back to that saved copy when offline.
- Defines an independent `EngineLauncher` interface so the UI is not coupled to JVM/native implementation details.
- GitHub Actions builds a debug APK and uploads it as an artifact.

## What is not implemented yet

This APK **does not launch Minecraft yet**. The Java runtime distribution/selection, game library and asset installer, JVM/JLI JNI bridge, graphics context/renderer bridge, and full keyboard/mouse/touch/gamepad event path have not been integrated. Pressing Play reports this status rather than pretending the game started.

The next build stage should add one verifiable layer at a time: (1) version detail + download manifest, (2) verified game libraries/assets and runtime manager, (3) JVM launch adapter, (4) rendering bridge, then (5) input/control mapping. Renderer/native components and any reused upstream code must be evaluated against their own licenses before redistribution.

## Build locally

Requirements: JDK 17 and Android SDK platform 35/build-tools 35.0.0.

```bash
./gradlew :app:assembleDebug
```

APK output:

```text
app/build/outputs/apk/debug/app-debug.apk
```

## Build with GitHub Actions

Push this project to GitHub. The workflow at `.github/workflows/build.yml` runs on pushes to `main`/`master`, pull requests, and manual dispatch. Open **Actions → Build Sally Launcher APK → run → Artifacts** and download `sally-launcher-debug-apk`.

The supplied debug build uses the standard Android debug signing config. A future distributable release must use a private, stable keystore stored in GitHub Actions Secrets; never commit release keys to the repository.

## Reference and licensing note

PojavLauncher was used as a technical reference for architectural boundaries—Android UI/session orchestration, version/runtime management, JVM launch, graphics and input bridges. This stage is newly authored and does not include PojavLauncher application source or its native binaries. Refer to the upstream project and each dependency's own license before porting or distributing any components: https://github.com/PojavLauncherTeam/PojavLauncher
