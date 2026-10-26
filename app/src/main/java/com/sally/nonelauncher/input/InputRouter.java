package com.sally.nonelauncher.input;

import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.MotionEvent;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * Routes Android touch, mouse, wheel and keyboard input to NoneLauncher's JNI
 * boundary. A stable pointer ID is used for camera motion so an extra finger
 * cannot unexpectedly take over the active camera control.
 */
public final class InputRouter {
    private final NativeInput nativeInput;
    private final Map<Integer, PointerPosition> activePointers = new LinkedHashMap<>();
    private final Map<Integer, KeyState> pressedKeys = new LinkedHashMap<>();
    private final Set<Integer> pressedMouseButtons = new LinkedHashSet<>();

    private int cameraPointerId = MotionEvent.INVALID_POINTER_ID;
    private float lastCameraX;
    private float lastCameraY;

    private boolean hasMousePosition;
    private float lastMouseX;
    private float lastMouseY;

    public InputRouter(NativeInput input) {
        if (input == null) throw new IllegalArgumentException("input must not be null");
        nativeInput = input;
    }

    public boolean touch(MotionEvent event, boolean cameraMode) {
        final int action = event.getActionMasked();
        final int index = event.getActionIndex();

        if (action == MotionEvent.ACTION_CANCEL) {
            // Some devices send ACTION_CANCEL with incomplete pointer data. First
            // refresh known positions, then release every pointer we still track.
            updatePointerPositions(event);
            for (Map.Entry<Integer, PointerPosition> entry : activePointers.entrySet()) {
                PointerPosition position = entry.getValue();
                nativeInput.pointerUp(entry.getKey(), position.x, position.y);
            }
            activePointers.clear();
            cameraPointerId = MotionEvent.INVALID_POINTER_ID;
            return true;
        }

        final int pointerId = event.getPointerId(index);
        final float x = event.getX(index);
        final float y = event.getY(index);

        switch (action) {
            case MotionEvent.ACTION_DOWN:
                activePointers.put(pointerId, new PointerPosition(x, y));
                nativeInput.pointerDown(pointerId, x, y);
                if (cameraMode) beginCamera(pointerId, x, y);
                return true;

            case MotionEvent.ACTION_POINTER_DOWN:
                activePointers.put(pointerId, new PointerPosition(x, y));
                nativeInput.pointerDown(pointerId, x, y);
                // Keep the original camera finger until it is lifted.
                return true;

            case MotionEvent.ACTION_MOVE:
                updatePointerPositions(event);
                // Forward positions for every finger, not just the camera finger.
                for (int i = 0; i < event.getPointerCount(); i++) {
                    nativeInput.pointerMove(event.getPointerId(i), event.getX(i), event.getY(i));
                }

                if (cameraMode && cameraPointerId != MotionEvent.INVALID_POINTER_ID) {
                    final int cameraIndex = event.findPointerIndex(cameraPointerId);
                    if (cameraIndex >= 0) {
                        final float cameraX = event.getX(cameraIndex);
                        final float cameraY = event.getY(cameraIndex);
                        nativeInput.pointerDelta(cameraX - lastCameraX, cameraY - lastCameraY);
                        lastCameraX = cameraX;
                        lastCameraY = cameraY;
                    }
                }
                return true;

            case MotionEvent.ACTION_POINTER_UP:
                updatePointerPositions(event);
                nativeInput.pointerUp(pointerId, x, y);
                activePointers.remove(pointerId);
                if (pointerId == cameraPointerId) {
                    cameraPointerId = MotionEvent.INVALID_POINTER_ID;
                    // Another finger may take over, without a jump in relative motion.
                    for (int i = 0; i < event.getPointerCount(); i++) {
                        if (i == index) continue;
                        cameraPointerId = event.getPointerId(i);
                        lastCameraX = event.getX(i);
                        lastCameraY = event.getY(i);
                        break;
                    }
                }
                return true;

            case MotionEvent.ACTION_UP:
                nativeInput.pointerUp(pointerId, x, y);
                activePointers.remove(pointerId);
                cameraPointerId = MotionEvent.INVALID_POINTER_ID;
                return true;

            default:
                return false;
        }
    }

    private void updatePointerPositions(MotionEvent event) {
        for (int i = 0; i < event.getPointerCount(); i++) {
            activePointers.put(event.getPointerId(i),
                    new PointerPosition(event.getX(i), event.getY(i)));
        }
    }

    private void beginCamera(int pointerId, float x, float y) {
        cameraPointerId = pointerId;
        lastCameraX = x;
        lastCameraY = y;
    }

    public boolean genericMotion(MotionEvent event) {
        final int action = event.getActionMasked();

        if (action == MotionEvent.ACTION_SCROLL) {
            nativeInput.scroll(
                    event.getAxisValue(MotionEvent.AXIS_HSCROLL),
                    event.getAxisValue(MotionEvent.AXIS_VSCROLL));
            return true;
        }

        if (event.isFromSource(InputDevice.SOURCE_MOUSE)) {
            if (action == MotionEvent.ACTION_BUTTON_PRESS || action == MotionEvent.ACTION_BUTTON_RELEASE) {
                return mouseButton(event);
            }

            if (action == MotionEvent.ACTION_HOVER_ENTER
                    || action == MotionEvent.ACTION_HOVER_MOVE
                    || action == MotionEvent.ACTION_MOVE) {
                final float x = event.getX();
                final float y = event.getY();
                // ID -1 is reserved for the mouse cursor; touch pointer IDs are non-negative.
                nativeInput.pointerMove(-1, x, y);
                if (hasMousePosition) {
                    nativeInput.mouseDelta(x - lastMouseX, y - lastMouseY);
                }
                rememberMousePosition(x, y);
                return true;
            }
        }

        return false;
    }

    public boolean mouseButton(MotionEvent event) {
        final int action = event.getActionMasked();
        final boolean down;
        if (action == MotionEvent.ACTION_DOWN || action == MotionEvent.ACTION_BUTTON_PRESS) {
            down = true;
        } else if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_BUTTON_RELEASE) {
            down = false;
        } else {
            return false;
        }

        // Make sure click coordinates reach the backend even when no hover event
        // was delivered first (common with some touchpad and remote-control devices).
        final float mouseX = event.getX();
        final float mouseY = event.getY();
        nativeInput.pointerMove(-1, mouseX, mouseY);
        if (hasMousePosition) {
            nativeInput.mouseDelta(mouseX - lastMouseX, mouseY - lastMouseY);
        }
        rememberMousePosition(mouseX, mouseY);

        int button = event.getActionButton();
        if (button == 0) button = MotionEvent.BUTTON_PRIMARY;
        nativeInput.mouseButton(button, down);
        if (down) pressedMouseButtons.add(button);
        else pressedMouseButtons.remove(button);
        return true;
    }

    public boolean key(KeyEvent event) {
        final int action = event.getAction();
        if (action != KeyEvent.ACTION_DOWN && action != KeyEvent.ACTION_UP) return false;

        // Let Android handle Back rather than swallowing it as an ordinary game key.
        if (event.getKeyCode() == KeyEvent.KEYCODE_BACK) return false;

        final int keyCode = event.getKeyCode();
        final int scanCode = event.getScanCode();
        final int modifiers = event.getMetaState();
        final boolean down = action == KeyEvent.ACTION_DOWN;
        nativeInput.key(keyCode, scanCode, down, modifiers);

        if (down) {
            pressedKeys.put(keyCode, new KeyState(scanCode, modifiers));
            final int codePoint = event.getUnicodeChar(modifiers);
            if (codePoint != 0 && Character.isValidCodePoint(codePoint)
                    && !Character.isISOControl(codePoint)) {
                nativeInput.charInput(codePoint, modifiers);
            }
        } else {
            pressedKeys.remove(keyCode);
        }
        return true;
    }

    /** Releases any tracked state when the activity loses focus or is shutting down. */
    public void releaseAll() {
        for (Map.Entry<Integer, PointerPosition> entry : activePointers.entrySet()) {
            PointerPosition position = entry.getValue();
            nativeInput.pointerUp(entry.getKey(), position.x, position.y);
        }
        activePointers.clear();
        cameraPointerId = MotionEvent.INVALID_POINTER_ID;

        for (Map.Entry<Integer, KeyState> entry : pressedKeys.entrySet()) {
            KeyState key = entry.getValue();
            nativeInput.key(entry.getKey(), key.scanCode, false, key.modifiers);
        }
        pressedKeys.clear();

        for (Integer button : pressedMouseButtons) {
            nativeInput.mouseButton(button, false);
        }
        pressedMouseButtons.clear();
        hasMousePosition = false;
    }

    private void rememberMousePosition(float x, float y) {
        lastMouseX = x;
        lastMouseY = y;
        hasMousePosition = true;
    }

    private static final class PointerPosition {
        final float x;
        final float y;
        PointerPosition(float x, float y) { this.x = x; this.y = y; }
    }

    private static final class KeyState {
        final int scanCode;
        final int modifiers;
        KeyState(int scanCode, int modifiers) {
            this.scanCode = scanCode;
            this.modifiers = modifiers;
        }
    }
}
