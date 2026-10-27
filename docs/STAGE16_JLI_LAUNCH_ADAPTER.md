# Stage 16 — native JLI launch adapter and session gate

## What changed

- Added `com.oracle.dalvik.VMLauncher`, the Java JNI declaration matching `Java_com_oracle_dalvik_VMLauncher_launchJVM` exported by the included Pojav-derived `jre_launcher.c`.
- Expanded the minimal `JREUtils` declarations to expose the native link-path update and working-directory methods already implemented in the bridge.
- Added a short-lived in-memory `MinecraftLaunchSession` model. It checks username/UUID/token shape, requires Microsoft user type `msa`, validates profile-properties JSON and rejects placeholder access tokens/client IDs. It does not claim to perform OAuth itself.
- Updated launch-profile placeholder expansion so a session supplied by a future/connected account provider flows into the real game arguments without being logged or copied into an Intent. Default profile preparation remains diagnostic-only and is not launchable.
- Added `PojavJliLaunchExecutor`, which checks the profile/session match, gate results, bound-surface state, native payload, runtime identity, argv integrity, runtime environment, Android linker path, and game working directory before it invokes `VMLauncher.launchJVM` off the UI thread.
- Added `PojavGameActivity`, which hosts the Android surface and waits for it to bind before attempting the launch. Back/close is guarded while JLI is running because destroying the surface mid-render can crash the VM.
- Hardened the Pojav linker-path helper: if Android does not expose the private linker update symbol, it now raises `UnsatisfiedLinkError` instead of dereferencing a null function pointer.
- Added the host-side `ExitActivity.showExitMessage(Context, int, boolean)` callback and registered it before JLI, matching the JNI signature used by Pojav's fatal-signal path.
- Added unit tests for session structural validation and rejection of placeholder/invalid data. The test scope supplies a real `org.json` implementation so local JVM tests do not use Android stub constructors.

## Security and launch gate

The access token is deliberately not persisted by this stage, displayed in diagnostics, written to logs, or sent through Intent extras. An account provider must call `MinecraftLaunchSessionStore.installAfterAuthentication()` only after the Microsoft OAuth, Xbox Live, XSTS, and Minecraft Java profile flow has succeeded. Structural checks in `MinecraftLaunchSession` are not a substitute for that server-side validation.

The main Play button remains blocked when no validated session is in memory. This stage does not add the Microsoft sign-in flow, so an unmodified build will still report that account authentication is not connected. No offline placeholder token is treated as a real account.

## Known limitations

- The APK has not been built with Android SDK/NDK in this environment. The full workflow must validate CMake and Kotlin compilation.
- There is no implemented Microsoft sign-in UI/provider yet, so an authenticated session cannot currently be created through the UI.
- The JNI launch path and EGL surface binding have not been smoke-tested on a physical Android device; their presence in source does not prove a game reaches the title screen.
- This remains an early vanilla-launch path. Native classifier extraction, mod-loader compatibility, renderer selection, process shutdown/recovery and device-specific lifecycle testing still require follow-up.

## Validation to run in GitHub Actions

```bash
./gradlew --no-daemon --stacktrace :app:testDebugUnitTest
./gradlew --no-daemon --stacktrace :app:assembleDebug
```

Then use a test device to exercise the Pojav surface diagnostic first. Do not proceed to gameplay testing until the APK loads `libpojavexec.so`, binds the surface, and the Java runtime's `JLI_Launch` smoke test passes.
