package net.kdt.pojavlaunch;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;

/** Minimal host-side callback for Pojav's native fatal-exit bridge. */
public final class ExitActivity {
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    private ExitActivity() {}

    /** JNI signature is (Landroid/content/Context;IZ)V, matching stdio_is.c. */
    public static void showExitMessage(Context context, int errorCode, boolean isSignal) {
        if (context == null) return;
        final Context appContext = context.getApplicationContext();
        final String message = isSignal
                ? "Minecraft's Java runtime stopped after a fatal native signal."
                : "Minecraft's Java runtime exited with code " + errorCode + ".";
        MAIN.post(() -> Toast.makeText(appContext != null ? appContext : context, message, Toast.LENGTH_LONG).show());
    }
}
