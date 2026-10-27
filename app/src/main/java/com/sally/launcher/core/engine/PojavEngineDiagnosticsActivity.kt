package com.sally.launcher.core.engine

import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

/** Device-side smoke test for the bundled Pojav JNI bridge and Android surface binding. */
class PojavEngineDiagnosticsActivity : AppCompatActivity() {
    private lateinit var statusText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.rgb(12, 18, 28)
        window.navigationBarColor = Color.rgb(12, 18, 28)

        val root = FrameLayout(this).apply {
            setBackgroundColor(Color.BLACK)
            keepScreenOn = true
        }
        val surface = PojavGameSurfaceView(this) { message ->
            runOnUiThread {
                if (::statusText.isInitialized) statusText.text = message
            }
        }.apply {
            contentDescription = "Pojav Android surface and input bridge test surface"
        }
        root.addView(surface, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT,
        ))

        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(12), dp(14), dp(12))
            setBackgroundColor(0xD91B2430.toInt())
        }
        statusText = TextView(this).apply {
            textSize = 14f
            setTextColor(Color.WHITE)
            text = "Preparing Pojav JNI bridge…"
            contentDescription = "Pojav native bridge status"
        }
        panel.addView(statusText, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        ))
        val explanation = TextView(this).apply {
            textSize = 12f
            setTextColor(0xFFD3DCE7.toInt())
            text = "This tests library loading, JNI callbacks, and surface binding only. No Minecraft JVM or world is started. Tap Back to exit."
            setPadding(0, dp(6), 0, 0)
        }
        panel.addView(explanation, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        ))
        val close = Button(this).apply {
            text = "Close diagnostics"
            isAllCaps = false
            setOnClickListener { finish() }
        }
        panel.addView(close, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        ).apply { topMargin = dp(6) })

        root.addView(panel, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            Gravity.TOP,
        ))
        setContentView(root)
        surface.requestFocus()
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density + 0.5f).toInt()
}
