# NoneLauncher

Standalone Android launcher foundation built from scratch.

Package: `com.sally.nonelauncher`

## Build

The repository includes the Gradle wrapper, so `gradlew` is part of the project.

```bash
./gradlew :app:assembleDebug
```

GitHub Actions installs Android API 35, Build Tools 35.0.0, CMake 3.22.1 and NDK 27.2.12479018, then builds the debug APK.

The input layer is owned by NoneLauncher and is not copied from PojavLauncher. It currently validates Android touch, multi-touch pointer tracking, mouse buttons, mouse movement, scroll and keyboard routing through the native JNI boundary.
