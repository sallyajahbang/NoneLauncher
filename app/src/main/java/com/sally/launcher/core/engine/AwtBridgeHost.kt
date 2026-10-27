package com.sally.launcher.core.engine

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.Log
import net.kdt.pojavlaunch.AWTInputBridge
import java.io.File

/** Android-side callbacks consumed by the Pojav/Caciocavallo native bridge. */
object AwtBridgeHost {
    private const val TAG = "SallyAwtBridge"
    private val mainHandler = Handler(Looper.getMainLooper())

    @Volatile
    private var applicationContext: Context? = null

    @JvmStatic
    fun install(context: Context) {
        applicationContext = context.applicationContext
    }

    @JvmStatic
    fun openLink(link: String?) {
        val context = applicationContext ?: return
        if (link.isNullOrBlank()) return
        mainHandler.post {
            try {
                val uri = if (link.startsWith("file:", ignoreCase = true)) {
                    val path = Uri.parse(link).path.orEmpty()
                    Uri.fromFile(File(path))
                } else {
                    Uri.parse(link)
                }
                context.startActivity(Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            } catch (error: Throwable) {
                Log.w(TAG, "Could not open AWT link", error)
            }
        }
    }

    @JvmStatic
    fun openPath(path: String?) {
        if (path.isNullOrBlank()) return
        // Avoid exposing file:// URIs to unrelated applications. The host does not currently
        // provide a FileProvider-backed browser; keep this best-effort action non-fatal.
        openLink(Uri.fromFile(File(path)).toString())
    }

    @JvmStatic
    fun querySystemClipboard() {
        val context = applicationContext ?: return
        mainHandler.post {
            try {
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                val clip = clipboard?.primaryClip
                val text = if (clip != null && clip.itemCount > 0) clip.getItemAt(0).coerceToText(context)?.toString() else null
                AWTInputBridge.nativeClipboardReceived(text, if (text == null) null else "plain")
            } catch (error: Throwable) {
                Log.w(TAG, "Could not read Android clipboard", error)
                runCatching { AWTInputBridge.nativeClipboardReceived(null, null) }
            }
        }
    }

    @JvmStatic
    fun putClipboardData(data: String?, mimeType: String?) {
        val context = applicationContext ?: return
        mainHandler.post {
            try {
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return@post
                val value = data.orEmpty()
                val clip = when (mimeType?.lowercase()) {
                    "text/html", "html" -> ClipData.newHtmlText("Minecraft", value, value)
                    "text/plain", "plain", null, "" -> ClipData.newPlainText("Minecraft", value)
                    else -> ClipData.newPlainText("Minecraft", value)
                }
                clipboard.setPrimaryClip(clip)
            } catch (error: Throwable) {
                Log.w(TAG, "Could not write Android clipboard", error)
            }
        }
    }
}
