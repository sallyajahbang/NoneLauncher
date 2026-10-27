# Stage 11 — Relative Mouse Capture and Input Contract Tests

## Changes

- Adds an Android 8+ pointer-capture toggle to the GLES diagnostics screen.
- Routes captured mouse deltas using `MotionEvent.AXIS_RELATIVE_X/Y` with a distinct `RELATIVE_MOVE` action instead of treating them as absolute coordinates; processes historical samples to avoid dropping batched mouse movement.
- Sends capture acquired/released state through the native event queue.
- Releases pointer capture and tracked key/mouse/touch inputs when the activity loses focus, pauses, or is destroyed. Escape also releases capture.
- Adds host-side JUnit tests for GLFW key/action/mouse/gamepad mappings and trigger normalization.
- Runs `:app:testDebugUnitTest` before assembling the debug APK and uploads test/build reports even if a later step fails.

## Apply

Overlay the ZIP at the repository root. It contains only files changed for Stage 11, using the project's original relative paths.

## Validation

The pure `GlfwInputMapping` smoke test and host C++ syntax check passed in the patching environment. A complete Android Gradle build and device test were not available here; GitHub Actions must validate the Android APIs and NDK build.

## Important limitation

This is diagnostic input plumbing, not Minecraft input integration. The native queue records relative deltas and pointer-capture state, but no Android-compatible GLFW backend consumes these records yet. The diagnostics screen must not be presented as proof that Minecraft mouse look works.
