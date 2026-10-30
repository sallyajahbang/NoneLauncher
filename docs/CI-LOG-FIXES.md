# CI / Android UI update notes

This revision moves RustCraft from its previous desktop-shaped application shell to an Android-only application.

## Android UI changes

- Removed the fixed 190-point desktop sidebar.
- Added a compact top-row navigation mode for short landscape viewports.
- Added bottom navigation for portrait and taller layouts.
- Made all primary content single-column and vertically scrollable.
- Removed the floating runtime diagnostics window that covered the home screen.
- Moved FPS, frame time, uptime, render distance, graphics backend, and world status to a dedicated Developer Diagnostics screen.
- Removed the forced fullscreen viewport so Android system bars are not intentionally covered by app content.
- Changed the Android Activity orientation from forced landscape to sensor orientation.

## Android-only build changes

- Removed the desktop binary entry point and `run_desktop` function.
- Removed desktop-only `eframe` window backend features.
- Kept the Android NativeActivity + ARM64 target.
- Replaced the multi-platform CI matrix with a single Android ARM64 APK job.
- Replaced multi-platform release jobs with one Android release APK job.
- Updated README and build documentation to describe Android-only development.

## Validation limitation

The current authoring environment does not have Rust/Cargo or the Android SDK installed. YAML and XML are validated, source structure is inspected, and ZIP integrity is checked; the next GitHub Actions run remains the authoritative Rust compile and APK build test.
