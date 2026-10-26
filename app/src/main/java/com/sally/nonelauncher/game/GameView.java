package com.sally.nonelauncher.game;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;

import com.sally.nonelauncher.input.InputRouter;
import com.sally.nonelauncher.input.NativeInput;

import java.util.ArrayDeque;
import java.util.Locale;

/** On-device diagnostics surface for checking the Android-to-native input path. */
public final class GameView extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final NativeInput nativeInput = new NativeInput();
    private final InputRouter input = new InputRouter(nativeInput);
    private final ArrayDeque<String> recentEvents = new ArrayDeque<>();
    private final float density;

    private long receivedEventCount;
    private long droppedEventCount;

    public GameView(Context context) {
        super(context);
        density = getResources().getDisplayMetrics().density;
        setFocusable(true);
        setFocusableInTouchMode(true);
        requestFocus();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        canvas.drawColor(Color.rgb(13, 17, 23));

        final float width = getWidth();
        final float height = getHeight();
        final float margin = dp(22);

        paint.setColor(Color.WHITE);
        paint.setTextSize(dp(25));
        paint.setFakeBoldText(true);
        canvas.drawText("NoneLauncher", margin, dp(42), paint);

        paint.setFakeBoldText(false);
        paint.setColor(Color.rgb(151, 165, 181));
        paint.setTextSize(dp(13));
        canvas.drawText("Native input diagnostics", margin, dp(65), paint);

        // Summary panel adapts to narrow screens instead of relying on fixed widths.
        float panelTop = dp(87);
        float panelHeight = dp(74);
        RectF panel = new RectF(margin, panelTop, width - margin, panelTop + panelHeight);
        paint.setColor(Color.rgb(25, 33, 44));
        canvas.drawRoundRect(panel, dp(12), dp(12), paint);

        paint.setColor(Color.rgb(117, 185, 255));
        paint.setTextSize(dp(12));
        canvas.drawText("EVENTS RECEIVED", margin + dp(14), panelTop + dp(24), paint);
        paint.setColor(Color.WHITE);
        paint.setTextSize(dp(22));
        paint.setFakeBoldText(true);
        canvas.drawText(String.valueOf(receivedEventCount), margin + dp(14), panelTop + dp(51), paint);

        float secondColumn = Math.min(width - margin - dp(130), margin + dp(160));
        paint.setFakeBoldText(false);
        paint.setColor(Color.rgb(117, 185, 255));
        paint.setTextSize(dp(12));
        canvas.drawText("DROPPED", secondColumn, panelTop + dp(24), paint);
        paint.setColor(droppedEventCount == 0 ? Color.WHITE : Color.rgb(255, 145, 145));
        paint.setTextSize(dp(22));
        paint.setFakeBoldText(true);
        canvas.drawText(String.valueOf(droppedEventCount), secondColumn, panelTop + dp(51), paint);

        paint.setFakeBoldText(false);
        paint.setColor(Color.rgb(205, 215, 228));
        paint.setTextSize(dp(12));
        canvas.drawText("Touch, multi-touch, mouse, wheel and keyboard events appear below.",
                margin, panelTop + panelHeight + dp(26), paint);

        float listTop = panelTop + panelHeight + dp(56);
        paint.setColor(Color.WHITE);
        paint.setTextSize(dp(15));
        paint.setFakeBoldText(true);
        canvas.drawText("RECENT NATIVE EVENTS", margin, listTop, paint);
        paint.setFakeBoldText(false);

        float lineY = listTop + dp(25);
        final float lineHeight = dp(22);
        final float bottom = height - dp(18);
        if (recentEvents.isEmpty()) {
            paint.setColor(Color.rgb(130, 144, 160));
            canvas.drawText("No events yet. Touch the screen or connect a mouse/keyboard.",
                    margin, lineY, paint);
        } else {
            for (String line : recentEvents) {
                if (lineY > bottom) break;
                paint.setColor(Color.rgb(185, 202, 221));
                canvas.drawText(line, margin, lineY, paint);
                lineY += lineHeight;
            }
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        final boolean isMouse = event.isFromSource(InputDevice.SOURCE_MOUSE);
        final int action = event.getActionMasked();
        boolean handled;

        if (isMouse) {
            if (action == MotionEvent.ACTION_DOWN || action == MotionEvent.ACTION_UP
                    || action == MotionEvent.ACTION_BUTTON_PRESS
                    || action == MotionEvent.ACTION_BUTTON_RELEASE) {
                handled = input.mouseButton(event);
            } else {
                handled = input.genericMotion(event);
            }
        } else {
            handled = input.touch(event, true);
        }

        syncDiagnostics();
        return handled || super.onTouchEvent(event);
    }

    @Override
    public boolean onGenericMotionEvent(MotionEvent event) {
        boolean handled = input.genericMotion(event);
        if (handled) syncDiagnostics();
        return handled || super.onGenericMotionEvent(event);
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        boolean handled = input.key(event);
        if (handled) syncDiagnostics();
        return handled || super.onKeyDown(keyCode, event);
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        boolean handled = input.key(event);
        if (handled) syncDiagnostics();
        return handled || super.onKeyUp(keyCode, event);
    }

    @Override
    public void onWindowFocusChanged(boolean hasWindowFocus) {
        super.onWindowFocusChanged(hasWindowFocus);
        if (!hasWindowFocus) {
            input.releaseAll();
            syncDiagnostics();
        }
    }

    @Override
    protected void onSizeChanged(int width, int height, int oldWidth, int oldHeight) {
        nativeInput.resize(width, height);
        syncDiagnostics();
    }

    private void syncDiagnostics() {
        float[] events = nativeInput.drainEvents();
        if (events != null) {
            final int stride = NativeInput.EVENT_STRIDE;
            for (int i = 0; i + stride <= events.length; i += stride) {
                recentEvents.addFirst(describeEvent(events, i));
                receivedEventCount++;
            }
        }

        while (recentEvents.size() > 9) recentEvents.removeLast();
        droppedEventCount = nativeInput.droppedEventCount();
        invalidate();
    }

    private String describeEvent(float[] values, int offset) {
        int type = (int) values[offset];
        int code = (int) values[offset + 1];
        float x = values[offset + 2];
        float y = values[offset + 3];
        float value = values[offset + 4];
        int modifiers = (int) values[offset + 5];

        switch (type) {
            case NativeInput.EVENT_POINTER_DOWN:
                return String.format(Locale.US, "TOUCH DOWN  id=%d  x=%.1f y=%.1f", code, x, y);
            case NativeInput.EVENT_POINTER_UP:
                return String.format(Locale.US, "TOUCH UP    id=%d  x=%.1f y=%.1f", code, x, y);
            case NativeInput.EVENT_POINTER_MOVE:
                return String.format(Locale.US, "%s MOVE  id=%d  x=%.1f y=%.1f",
                        code == -1 ? "MOUSE" : "TOUCH", code, x, y);
            case NativeInput.EVENT_POINTER_DELTA:
                return String.format(Locale.US, "RELATIVE    dx=%.1f dy=%.1f", x, y);
            case NativeInput.EVENT_MOUSE_BUTTON:
                return String.format(Locale.US, "MOUSE BUTTON %d  %s", code,
                        value == 1.0f ? "DOWN" : "UP");
            case NativeInput.EVENT_SCROLL:
                return String.format(Locale.US, "SCROLL      x=%.1f y=%.1f", x, y);
            case NativeInput.EVENT_KEY:
                return String.format(Locale.US, "KEY         code=%d scan=%.0f %s mods=%d",
                        code, x, y == 1.0f ? "DOWN" : "UP", modifiers);
            case NativeInput.EVENT_CHAR:
                return "CHAR        " + printableCodePoint(code) + "  (" + code + ")";
            case NativeInput.EVENT_RESIZE:
                return String.format(Locale.US, "RESIZE      %.0f x %.0f", x, y);
            case NativeInput.EVENT_GRAB:
                return "POINTER     " + (value == 1.0f ? "GRABBED" : "RELEASED");
            case NativeInput.EVENT_MOUSE_DELTA:
                return String.format(Locale.US, "MOUSE REL   dx=%.1f dy=%.1f", x, y);
            default:
                return "UNKNOWN     type=" + type;
        }
    }

    private static String printableCodePoint(int codePoint) {
        if (!Character.isValidCodePoint(codePoint)) return "?";
        return new String(Character.toChars(codePoint));
    }

    private float dp(float value) {
        return value * density;
    }
}
