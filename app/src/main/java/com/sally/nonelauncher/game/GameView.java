package com.sally.nonelauncher.game;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.view.InputDevice;
import android.view.MotionEvent;
import android.view.View;

import com.sally.nonelauncher.input.InputRouter;
import com.sally.nonelauncher.input.NativeInput;

/** Temporary rendering surface used to validate NoneLauncher's own input layer. */
public final class GameView extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final NativeInput nativeInput = new NativeInput();
    private final InputRouter input = new InputRouter(nativeInput);

    public GameView(Context context) {
        super(context);
        setFocusable(true);
        setFocusableInTouchMode(true);
        requestFocus();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        canvas.drawColor(Color.rgb(18, 18, 18));
        paint.setColor(Color.WHITE);
        paint.setTextSize(24);
        canvas.drawText("NoneLauncher", 40, 55, paint);
        paint.setTextSize(15);
        paint.setColor(Color.LTGRAY);
        canvas.drawText("Input test surface", 40, 82, paint);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if ((event.getSource() & InputDevice.SOURCE_MOUSE) != 0) {
            if (event.getActionMasked() == MotionEvent.ACTION_DOWN ||
                    event.getActionMasked() == MotionEvent.ACTION_UP) {
                return input.mouseButton(event);
            }
            return input.genericMotion(event);
        }
        return input.touch(event, true);
    }

    @Override
    public boolean onGenericMotionEvent(MotionEvent event) {
        return input.genericMotion(event) || super.onGenericMotionEvent(event);
    }

    @Override
    public boolean onKeyDown(int keyCode, android.view.KeyEvent event) {
        return input.key(event) || super.onKeyDown(keyCode, event);
    }

    @Override
    public boolean onKeyUp(int keyCode, android.view.KeyEvent event) {
        return input.key(event) || super.onKeyUp(keyCode, event);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        nativeInput.resize(w, h);
    }
}
