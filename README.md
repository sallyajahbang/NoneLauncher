# NoneLauncher

Standalone Android launcher foundation for an eventual Minecraft Java launcher.

## Current stage

- Java Runtime Manager stores runtimes in app-private storage.
- Bundled Java 17 ARM64 runtime: `app/src/main/assets/runtimes/jre17.tar.xz`.
- The bundled runtime is extracted and validated on first open of the runtime manager.
- Runtime cards show Java version and architecture and provide **TEST JAVA**, which executes `bin/java -version` with a bounded timeout and displays output/exit status.
- Runtime ZIP import is supported. Imported archives must contain `release` and `bin/java`.
- Runtime extraction applies archive path checks and a 1 GiB expanded-size limit.
- Existing native input diagnostics screen remains separate.

## Not implemented yet

- Minecraft version download/manifest handling.
- Game asset and library resolution.
- Minecraft launch arguments and Java process lifecycle controls.
- Rendering Minecraft inside the launcher.

The runtime smoke test is an execution check, not proof that Minecraft can launch. Android execution compatibility depends on the device ABI, Android version, and the bundled runtime's native dependencies.

## Build

Run `./gradlew :app:assembleDebug` or use the GitHub Actions workflow in `.github/workflows/android.yml`.

### Stage 5.1: Runtime execute-permission fix
- Runtime extraction now applies TAR executable mode bits with `Os.chmod` as well as `File.setExecutable`.
- The bundled `bin/java` is explicitly chmod'ed to `0755` and validated after extraction.
- Existing installations receive a permission repair when the bundled runtime installer is invoked.
- The TEST JAVA action repairs the executable permission before launching and reports a clearer error if Android/filesystem policy still blocks execution.
