# Sally Launcher — Material 3 Compose UI redesign

## Overview

The launcher home activity has been migrated from programmatic Android Views (`ScrollView`, `LinearLayout`, Material Components widgets) to Jetpack Compose with Material 3 and AndroidX.

## Navigation

The main launcher now has five bottom-navigation destinations:

- **Play** — quick launch using installed versions only; the version chooser is searchable and does not display uninstalled catalog entries.
- **Versions** — version manager with separate channels: Installed, Vanilla / Release, Snapshot, Old Beta, Old Alpha, and Other.
- **Account** — Microsoft sign-in entry point and local/offline profile management.
- **Settings** — dark theme preference, keep-screen-awake preference, and storage/runtime notes.
- **DEV** — launch profile preparation, JVM native loader probe, isolated Java `-version` test, graphics/input diagnostics, and Pojav surface bridge diagnostics.

The Installed filter groups installed versions by their channel instead of rendering all installed builds as one mixed list. Choosing a not-yet-installed catalog version only selects it in Version Manager; it does not change the active Play version until installation succeeds.

## Implementation notes

- Compose compiler plugin is aligned to Kotlin 2.0.21.
- Compose dependencies are managed using the Compose BOM `2024.09.03`.
- Material 3 color schemes are provided for dark and light modes.
- Existing version download, runtime installation, account session, JLI diagnostics, and game Activity flows are kept and called by the Compose UI.
- Java runtime executables remain in internal app storage; normal game data stays in the user-accessible app files directory.

## Validation

- Kotlin parser pass: no syntax/parse errors reported by the local `kotlinc` invocation.
- ZIP integrity: checked when the distribution archive was created.
- Full Android/Compose compilation: **not verified in this environment**. `./gradlew :app:compileDebugKotlin --no-daemon` stopped before Gradle started because DNS resolution for `services.gradle.org` failed (`UnknownHostException`). Run the existing GitHub Actions build to resolve dependency APIs and complete the actual Android compilation.
