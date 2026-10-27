# Sally Launcher Stage 12 — Native Input Pump

## What changed

- Added a typed `NativeInputEvent` and a decoder for the six-float JNI record contract:
  `type, code, action, x, y, value`.
- Invalid/non-finite records are skipped instead of being converted silently to zeros; partial trailing values are counted.
- Added `NativeInputPump`, which drains bounded batches in order and passes each event to an injected sink. Consumer/source exceptions are counted without crashing the GL render loop.
- The diagnostics screen now uses the pump on its GL render thread and reports delivered events, malformed records, and pump failures.
- Added JVM unit tests for decoding, malformed batches, bounded draining, ordering, sink failures, and source failures.

## Important boundary

This stage connects Android-generated events -> JNI queue -> typed native batch decoding -> a sink on the render thread. The diagnostics activity's sink currently records a summary only. It does **not** invoke GLFW callbacks or load an Android-compatible LWJGL/GLFW implementation, and it does not make Minecraft playable by itself. The pump is the extension seam for that later adapter.

## Validation

Run `./gradlew --no-daemon :app:testDebugUnitTest :app:assembleDebug` in GitHub Actions. A local host Kotlin smoke test does not replace an Android SDK/NDK build.
