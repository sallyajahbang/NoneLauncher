package org.lwjgl.glfw;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import androidx.annotation.Keep;

/**
 * Android-side half of Pojav's two-VM CallbackBridge contract.
 * The patched GLFW classes used by the embedded JVM ship in assets and are not
 * part of the Android app classpath, so this class intentionally shares the FQCN.
 */
@Keep
public final class CallbackBridge {
    public static final int CLIPBOARD_COPY = 2000;
    public static final int CLIPBOARD_PASTE = 2001;
    public static final int CLIPBOARD_OPEN = 2002;

    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static volatile Context appContext;
    private static volatile GrabStateListener grabStateListener;
    public static volatile int windowWidth;
    public static volatile int windowHeight;
    public static volatile int physicalWidth;
    public static volatile int physicalHeight;
    public static volatile boolean isGrabbing;
    /** Shared virtual cursor position (Pojav keeps one mouseX/mouseY for touch, mouse and capture). */
    public static volatile float mouseX;
    public static volatile float mouseY;
    public static volatile boolean directGamepadInput;
    private static ByteBuffer gamepadButtons;
    private static ByteBuffer gamepadAxes;

    private CallbackBridge() {}

    public interface GrabStateListener {
        void onGrabStateChanged(boolean grabbing);
    }

    public static void installContext(Context context) {
        if (context != null) appContext = context.getApplicationContext();
    }

    public static void setGrabStateListener(GrabStateListener listener) {
        grabStateListener = listener;
    }

    /** Called by native code running in the Android VM. */
    @Keep
    public static String accessAndroidClipboard(int action, String copy) {
        Context context = appContext;
        if (context == null) return action == CLIPBOARD_PASTE ? "" : null;
        try {
            ClipboardManager clipboard = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
            if (clipboard == null) return action == CLIPBOARD_PASTE ? "" : null;
            if (action == CLIPBOARD_COPY) {
                clipboard.setPrimaryClip(ClipData.newPlainText("Sally Launcher", copy == null ? "" : copy));
                return null;
            }
            if (action == CLIPBOARD_PASTE) {
                if (!clipboard.hasPrimaryClip() || clipboard.getPrimaryClip() == null ||
                    clipboard.getPrimaryClip().getItemCount() == 0) return "";
                CharSequence text = clipboard.getPrimaryClip().getItemAt(0).coerceToText(context);
                return text == null ? "" : text.toString();
            }
            if (action == CLIPBOARD_OPEN && copy != null) {
                // Game-triggered external links are intentionally not launched automatically.
                return null;
            }
        } catch (RuntimeException ignored) {
            return action == CLIPBOARD_PASTE ? "" : null;
        }
        return null;
    }

    @Keep
    public static void onGrabStateChanged(boolean grabbing) {
        isGrabbing = grabbing;
        GrabStateListener listener = grabStateListener;
        if (listener != null) MAIN.post(() -> listener.onGrabStateChanged(grabbing));
    }

    @Keep
    public static void onDirectInputEnable() {
        directGamepadInput = true;
    }

    public static void initializeNativeBuffers() {
        if (gamepadButtons != null && gamepadAxes != null) return;
        synchronized (CallbackBridge.class) {
            if (gamepadButtons == null) gamepadButtons = nativeCreateGamepadButtonBuffer().order(ByteOrder.LITTLE_ENDIAN);
            if (gamepadAxes == null) {
                gamepadAxes = nativeCreateGamepadAxisBuffer().order(ByteOrder.LITTLE_ENDIAN);
                // GLFW standard gamepad trigger axes rest at -1, not 0 (which looks half pressed).
                gamepadAxes.asFloatBuffer().put(4, -1.0f);
                gamepadAxes.asFloatBuffer().put(5, -1.0f);
            }
        }
    }

    public static void sendKey(int key, int scanCode, int action, int modifiers, int character) {
        nativeSendKey(key, scanCode, action, modifiers);
        if (action != 0 && character > 0 && character <= Character.MAX_VALUE) {
            nativeSendCharMods((char) character, modifiers);
            nativeSendChar((char) character);
        }
    }

    public static void sendCursorPos(float x, float y) {
        mouseX = x;
        mouseY = y;
        nativeSendCursorPos(x, y);
    }
    public static void sendMouseButton(int button, int action, int modifiers) { nativeSendMouseButton(button, action, modifiers); }
    public static void sendScroll(double x, double y) { nativeSendScroll(x, y); }
    public static void sendScreenSize(int width, int height) {
        windowWidth = width;
        windowHeight = height;
        physicalWidth = width;
        physicalHeight = height;
        nativeSendScreenSize(width, height);
    }

    public static void sendGamepadAxis(int axis, float value) {
        initializeNativeBuffers();
        if (axis >= 0 && axis < 6 && Float.isFinite(value)) gamepadAxes.asFloatBuffer().put(axis, value);
    }

    public static void sendGamepadButton(int button, boolean down) {
        initializeNativeBuffers();
        if (button >= 0 && button < 15) gamepadButtons.put(button, (byte) (down ? 1 : 0));
    }

    @Keep public static native void nativeSetUseInputStackQueue(boolean useInputStackQueue);
    @Keep private static native boolean nativeSendChar(char codepoint);
    @Keep private static native boolean nativeSendCharMods(char codepoint, int modifiers);
    @Keep private static native void nativeSendKey(int key, int scanCode, int action, int modifiers);
    @Keep private static native void nativeSendCursorPos(float x, float y);
    @Keep private static native void nativeSendMouseButton(int button, int action, int modifiers);
    @Keep private static native void nativeSendScroll(double xOffset, double yOffset);
    @Keep private static native void nativeSendScreenSize(int width, int height);
    @Keep public static native void nativeSetWindowAttrib(int attribute, int value);
    @Keep private static native ByteBuffer nativeCreateGamepadButtonBuffer();
    @Keep private static native ByteBuffer nativeCreateGamepadAxisBuffer();
}
