# Sally Launcher Stage 10 — Input transport boundary

## Included

- `GlfwInputMapping.kt`: dependency-light mapping of Android key/action/modifier codes, mouse button masks, standard gamepad buttons/d-pad, and joystick axes to GLFW 3 values.
- `AndroidInputTranslator.kt`: translates key/gamepad button events into the native queue and remembers held inputs so keyboard, mouse and gamepad buttons can be released on focus loss or pause.
- `EngineDiagnosticsActivity.kt`: forwards a pointer-down/up only for the changed pointer, forwards all pointers on movement, adds joystick axis forwarding, normalizes mouse buttons, fixes event counting, and releases tracked inputs on focus loss/pause.
- `NativeEngineBridge.kt`: names the two additional event kinds for gamepad axes/buttons.

## Event record

The native queue still uses six floats per event:

`type, code, action, x, y, value`

Event types 7 and 8 are `GAMEPAD_AXIS` and `GAMEPAD_BUTTON`. `code` is already normalized to GLFW indices. Key events put GLFW modifier bits in `value`; mouse button events carry cursor x/y; gamepad axis values are placed in `x` and use GLFW-style `[-1, 1]` values (Android trigger values are remapped from `[0, 1]` to `[-1, 1]`).

## Boundaries / remaining work

This queue remains a diagnostics transport. It is not yet polled by an Android-compatible LWJGL/GLFW implementation, so the mapping is not enough to make Minecraft input work. The next engine milestone must connect the consumer on the correct game/GLFW thread, add cursor capture/relative-motion semantics, and validate it against the selected Android GLFW port. Gamepad axis conventions vary by controller; this map is a baseline and should be tested on real hardware.

## Validation performed

The pure Kotlin mapping compiled with `kotlinc` and passed a smoke test for keys, actions, modifiers, mouse buttons, gamepad buttons/axes, trigger normalization, and unknown key handling. Full Android Gradle build was not available in the current environment because the Gradle distribution could not be fetched and no Android SDK installation was present.
