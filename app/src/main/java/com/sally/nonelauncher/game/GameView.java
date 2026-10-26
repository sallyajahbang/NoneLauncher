package com.sally.nonelauncher.game;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.view.MotionEvent;
import android.view.View;
import com.sally.nonelauncher.input.InputRouter;
import com.sally.nonelauncher.input.NativeInput;

/** Placeholder game surface. Later this becomes the actual LWJGL/Minecraft surface. */
public final class GameView extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final InputRouter input = new InputRouter(new NativeInput());
    public GameView(Context c) { super(c); setFocusableInTouchMode(true); requestFocus(); }
    @Override protected void onDraw(Canvas c) {
        c.drawColor(Color.rgb(18,18,18)); paint.setColor(Color.WHITE); paint.setTextSize(24); c.drawText("NoneLauncher Game Surface", 40, 55, paint);
        paint.setTextSize(15); paint.setColor(Color.LTGRAY); c.drawText("Touch = pointer  •  drag = camera delta  •  native input queue",40,82,paint);
    }
    @Override public boolean onTouchEvent(MotionEvent e) { return input.touch(e, true); }
    @Override public boolean onKeyDown(int k, android.view.KeyEvent e) { return input.key(e) || super.onKeyDown(k,e); }
    @Override public boolean onKeyUp(int k, android.view.KeyEvent e) { return input.key(e) || super.onKeyUp(k,e); }
}
