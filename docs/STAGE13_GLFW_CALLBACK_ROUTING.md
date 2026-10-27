# Stage 13 — GLFW-shaped callback routing

This patch creates a typed callback contract between Sally's native queue and the future Android-compatible GLFW/LWJGL engine. It is based on the input concepts used by Pojav's `org.lwjgl.glfw.CallbackBridge`, while avoiding a compile-time dependency on a GLFW implementation that is not yet packaged in Sally.

## Record layout

The existing JNI queue remains a stable six-float record:

`type, code, action, x, y, value`

- Keyboard: `code=GLFW key`, `action=GLFW action`, `x=Android scan code`, `value=GLFW modifiers`.
- Character: `code=Unicode code point`, `value=GLFW modifiers`.
- Pointer: `action=2` for absolute position, `action=3` for relative delta.
- Mouse button: button/action plus cursor coordinates and modifiers.
- Scroll: horizontal/vertical offsets in `x`/`y`.
- Viewport: width/height in `x`/`y`.
- Gamepad: axis value in `x`, or button/action in `code`/`action`.
- Touch and capture events retain their current typed fields.

`GlfwInputEventDispatcher` runs on the thread that drains the queue. It emits callback-shaped calls through `GlfwInputCallbacks`; exceptions are allowed to reach `NativeInputPump`, which counts per-event failures and continues. The diagnostic screen uses `DiagnosticGlfwInputCallbacks` only to trace which callbacks would be invoked.

## Scope and limitation

This is **not yet a live GLFW connection**: the diagnostic callback implementation only records callback-shaped events. Sally still needs an Android-compatible GLFW/LWJGL runtime and a concrete implementation of `GlfwInputCallbacks` backed by that runtime before input reaches Minecraft. The patch does preserve scan codes, modifier flags, Unicode character events, mouse coordinates, and correct viewport field placement so the eventual engine adapter does not need to guess their meaning.

## Tests

`GlfwInputEventDispatcherTest` checks key/scancode/modifier preservation, Unicode code points, absolute and relative pointer motion, cursor-before-button ordering, scroll, viewport, touch, gamepad and capture records, and unknown event rejection.
