package com.sally.nonelauncher.input;

import android.view.KeyEvent;
import android.view.MotionEvent;

/** Own input pipeline. Converts Android events into game-space mouse/key events. */
public final class InputRouter {
    private final NativeInput nativeInput;
    private float lastX, lastY;
    private boolean tracking;

    public InputRouter(NativeInput input) { nativeInput = input; }

    public boolean touch(MotionEvent e, boolean cameraMode) {
        final int action = e.getActionMasked();
        final int index = e.getActionIndex();
        if (action == MotionEvent.ACTION_DOWN || action == MotionEvent.ACTION_POINTER_DOWN) {
            nativeInput.pointerDown(e.getPointerId(index), e.getX(index), e.getY(index));
            if (cameraMode) { lastX = e.getX(index); lastY = e.getY(index); tracking = true; }
            return true;
        }
        if (action == MotionEvent.ACTION_MOVE) {
            if (cameraMode && tracking) {
                int p = e.findPointerIndex(e.getPointerId(e.getPointerId(0)));
                if (p >= 0) {
                    float x=e.getX(p), y=e.getY(p); nativeInput.pointerDelta(x-lastX,y-lastY); lastX=x; lastY=y;
                }
            }
            return true;
        }
        if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_POINTER_UP || action == MotionEvent.ACTION_CANCEL) {
            nativeInput.pointerUp(e.getPointerId(index), e.getX(index), e.getY(index)); tracking=false; return true;
        }
        return true;
    }

    public boolean key(KeyEvent e) {
        int action=e.getAction();
        if(action==KeyEvent.ACTION_DOWN || action==KeyEvent.ACTION_UP) {
            nativeInput.key(e.getKeyCode(), e.getScanCode(), action==KeyEvent.ACTION_DOWN, e.getMetaState());
            return true;
        }
        return false;
    }
}
