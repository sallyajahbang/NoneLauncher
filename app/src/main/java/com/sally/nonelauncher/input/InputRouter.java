package com.sally.nonelauncher.input;

import android.view.KeyEvent;
import android.view.MotionEvent;

/**
 * NoneLauncher's Android input router.
 * Keeps a stable pointer id for camera control so multi-touch cannot silently
 * switch the active camera finger.
 */
public final class InputRouter {
    private final NativeInput nativeInput;
    private int cameraPointerId = MotionEvent.INVALID_POINTER_ID;
    private float lastX;
    private float lastY;

    public InputRouter(NativeInput input) {
        nativeInput = input;
    }

    public boolean touch(MotionEvent e, boolean cameraMode) {
        final int action = e.getActionMasked();
        final int index = e.getActionIndex();
        final int pointerId = e.getPointerId(index);

        switch (action) {
            case MotionEvent.ACTION_DOWN:
                nativeInput.pointerDown(pointerId, e.getX(index), e.getY(index));
                if (cameraMode) beginCamera(pointerId, e.getX(index), e.getY(index));
                return true;

            case MotionEvent.ACTION_POINTER_DOWN:
                nativeInput.pointerDown(pointerId, e.getX(index), e.getY(index));
                // Do not steal the camera from the existing finger.
                return true;

            case MotionEvent.ACTION_MOVE:
                if (cameraMode && cameraPointerId != MotionEvent.INVALID_POINTER_ID) {
                    final int p = e.findPointerIndex(cameraPointerId);
                    if (p >= 0) {
                        final float x = e.getX(p);
                        final float y = e.getY(p);
                        nativeInput.pointerDelta(x - lastX, y - lastY);
                        lastX = x;
                        lastY = y;
                    }
                }
                return true;

            case MotionEvent.ACTION_POINTER_UP:
                nativeInput.pointerUp(pointerId, e.getX(index), e.getY(index));
                if (pointerId == cameraPointerId) {
                    // Prefer another finger still on screen for seamless camera control.
                    cameraPointerId = MotionEvent.INVALID_POINTER_ID;
                    for (int i = 0; i < e.getPointerCount(); i++) {
                        if (i != index) {
                            int replacement = e.getPointerId(i);
                            cameraPointerId = replacement;
                            lastX = e.getX(i);
                            lastY = e.getY(i);
                            break;
                        }
                    }
                }
                return true;

            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                nativeInput.pointerUp(pointerId, e.getX(index), e.getY(index));
                cameraPointerId = MotionEvent.INVALID_POINTER_ID;
                return true;

            default:
                return true;
        }
    }

    private void beginCamera(int pointerId, float x, float y) {
        cameraPointerId = pointerId;
        lastX = x;
        lastY = y;
    }

    public boolean genericMotion(MotionEvent e) {
        final int source = e.getSource();
        final int action = e.getActionMasked();

        if (action == MotionEvent.ACTION_SCROLL) {
            nativeInput.scroll(e.getAxisValue(MotionEvent.AXIS_HSCROLL),
                    e.getAxisValue(MotionEvent.AXIS_VSCROLL));
            return true;
        }

        if ((source & android.view.InputDevice.SOURCE_MOUSE) != 0) {
            final float x = e.getX();
            final float y = e.getY();
            if (action == MotionEvent.ACTION_HOVER_MOVE) {
                nativeInput.pointerDelta(x - lastX, y - lastY);
                lastX = x;
                lastY = y;
                return true;
            }
        }
        return false;
    }

    public boolean mouseButton(MotionEvent e) {
        final int action = e.getActionMasked();
        if (action != MotionEvent.ACTION_DOWN && action != MotionEvent.ACTION_UP) return false;
        int button = e.getActionButton();
        if (button == 0) button = MotionEvent.BUTTON_PRIMARY;
        nativeInput.mouseButton(button, action == MotionEvent.ACTION_DOWN);
        return true;
    }

    public boolean key(KeyEvent e) {
        final int action = e.getAction();
        if (action == KeyEvent.ACTION_DOWN || action == KeyEvent.ACTION_UP) {
            nativeInput.key(e.getKeyCode(), e.getScanCode(),
                    action == KeyEvent.ACTION_DOWN, e.getMetaState());
            return true;
        }
        return false;
    }
}
