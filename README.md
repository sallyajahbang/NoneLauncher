# Sally Launcher — Stage 4

A new Android Minecraft: Java Edition launcher codebase informed by Pojav/Amethyst runtime architecture. This is not a rebranded PojavLauncher. Package name: `com.sally.launcher`.

## Included in this stage

- Material 3-style Android UI and official Mojang version catalog with offline cache.
- Minecraft version file installer for the client JAR, common Java library artifacts and assets, with SHA-1 verification when supplied by official metadata.
- Android Java runtime manager that reads the selected version's declared Java major, selects a runtime for the device ABI, obtains the upstream runtime asset digest, verifies SHA-256, and safely extracts the runtime into app-private storage.
- Runtime archive extraction guards against path traversal, oversized archives and excessive entry counts.
- GitHub Actions workflow that builds a debug APK and uploads `sally-launcher-debug-apk`.

## Important limitation

**This stage does not launch Minecraft yet.** Runtime files can be downloaded and installed, but the JVM/JNI invocation bridge, LWJGL/graphics context integration, native game libraries and keyboard/mouse/touch/gamepad input bridge are not integrated. The Play button deliberately reports this instead of pretending the game started. Java desktop `bin/java` cannot simply be spawned as an ordinary Android process; a compatible native bridge is required.

## Build

Requirements: JDK 17, Android SDK platform 35 and build-tools 35.0.0.

```bash
chmod +x ./gradlew
./gradlew :app:assembleDebug
```

APK output: `app/build/outputs/apk/debug/app-debug.apk`.

GitHub Actions runs on pushes to `main`/`master`, pull requests and manual dispatch. Download the artifact named `sally-launcher-debug-apk` from the workflow run.

## Runtime source and licensing

The Android OpenJDK runtime download format currently follows the public Amethyst Android runtime distribution. Runtime assets are not bundled into the APK. Runtime downloads must pass SHA-256 verification before extraction. Review upstream licensing and notices before redistribution: https://github.com/AngelAuraMC/angelauramc-openjdk-build and https://github.com/AngelAuraMC/Amethyst-Android.

The project is a fresh implementation; reusing any upstream native/Java components in later stages requires separate compatibility and license review.
