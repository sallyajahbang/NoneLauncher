# Stage 15 — Pojav Android engine bridge payload

## Included

- A source-built `libpojavexec.so` target, based on the PojavLauncher `v3_openjdk` JNI compatibility layer.
- The matching Android-native LWJGL, GL4ES and OpenAL shared libraries for `arm64-v8a`, `armeabi-v7a`, `x86_64`, and `x86` copied from the supplied PojavLauncher snapshot.
- The matching `lwjgl-glfw-classes.jar` copied to app assets and installed into `files/minecraft/engine/pojav/` with a SHA-256 check.
- An Android-VM `org.lwjgl.glfw.CallbackBridge` with the JNI methods expected by this native bridge, plus the exact `JREUtils` JNI entry-point class for binding/releasing the Android surface.
- `PojavGameSurfaceView`, which connects Android surface lifecycle, keyboard, pointer, touch, scroll, and gamepad input to the bridge.
- A dedicated landscape diagnostic screen/button that exercises library loading and native surface binding without claiming to start a game.
- Pojav-oriented JVM environment values (`POJAV_NATIVEDIR`, `POJAV_RENDERER=opengles2`, and baseline GL4ES settings), and correction of `java.library.path` so the Android app-native directory is not lost when version metadata sets its own native path.
- Launch-profile classpath ordering that places the patched GLFW classes before the ordinary Minecraft/LWJGL Java libraries.

## Important engine compatibility details

The patched GLFW JAR must precede the regular LWJGL JARs in the Java classpath. Otherwise JVM class resolution may choose the desktop GLFW class and try loading an incompatible desktop native library. The native `pojavexec` bridge is built separately from `libsallybridge.so`: the Pojav bridge owns its two-VM JNI handshake and its input event queue, while the Sally bridge remains for prior diagnostics/tests.

The app-side callback class must be initialized and have an application context installed before `System.loadLibrary("pojavexec")`, because JNI_OnLoad resolves its static clipboard and grab-state callbacks. `PojavGameSurfaceView` follows that ordering.

## Not yet playable

This stage supplies and connects a bridge component but does not yet make a playable game. There is not yet a game-session authentication flow, a supported per-version native extraction/compatibility matrix, a fully configured renderer/lifecycle, or a working launcher invocation that runs the Minecraft main class through `JLI_Launch` and attaches it to an active render loop. The existing `EngineLauncherNotIntegrated`/launch blockers must remain in place until these are implemented and tested. Merely seeing `libpojavexec.so` in the APK is not proof of a successful game launch.

The bundled native payload does not yet include all optional Pojav components (for example the separate exit hook/linker hook or all AWT compatibility targets). Some mod loaders, Java versions, renderers and Minecraft releases may require additional components and version-specific adaptation.

## License and source provenance

The Pojav JNI compatibility source, patched GLFW classes, and native libraries were taken from the user-supplied `PojavLauncher-3_openjdk.zip` snapshot. The upstream tree declares LGPL-3.0; `app/src/main/assets/engine/pojav/LICENSE.txt` and `THIRD_PARTY_NOTICES.txt` are carried with the bundle. The relevant JNI source is included under `app/src/main/cpp/pojav/` so `libpojavexec.so` is built from source by the Android NDK. Preserve this notice and the license when distributing modified builds. The other prebuilt engine libraries remain binaries from the upstream snapshot; rebuild/replace them from their corresponding licensed source when changing their implementation.

## Validation status

- Structural check: CMake-listed native sources exist; ABI folders contain all expected copied shared libraries; the GLFW JAR matches the declared SHA-256; ZIP integrity is checked when patch is generated.
- Not validated here: Android NDK compilation of the new native target, full Gradle APK build, device linker/JNI handshake, renderer startup, and actual Minecraft gameplay. Those require GitHub Actions and device testing.
