package com.sally.launcher.ui

import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.sally.launcher.MainActivity
import com.sally.launcher.R

/** Short cosmic splash: logo + name fade in, then hands over to the Home screen. */
class SplashActivity : AppCompatActivity() {
    private val handler = Handler(Looper.getMainLooper())
    private val openHome = Runnable {
        if (isFinishing) return@Runnable
        startActivity(Intent(this, MainActivity::class.java))
        @Suppress("DEPRECATION")
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
        finish()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val density = resources.displayMetrics.density
        val root = FrameLayout(this)
        root.addView(CosmicBackgroundView(this), ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)

        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            alpha = 0f
        }
        column.addView(ImageView(this).apply { setImageResource(R.drawable.ic_star) },
            LinearLayout.LayoutParams((96 * density).toInt(), (96 * density).toInt()))
        column.addView(TextView(this).apply {
            text = "SALLY"
            textSize = 28f
            setTextColor(Color.WHITE)
            typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)
            letterSpacing = 0.35f
            gravity = Gravity.CENTER
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            topMargin = (16 * density).toInt()
        })
        column.addView(TextView(this).apply {
            text = "LAUNCHER"
            textSize = 11f
            setTextColor(0xFF9A9AC4.toInt())
            letterSpacing = 0.5f
            gravity = Gravity.CENTER
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            topMargin = (4 * density).toInt()
        })
        root.addView(column, FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER))
        setContentView(root)

        column.animate().alpha(1f).setDuration(600).start()
        handler.postDelayed(openHome, 1400)
    }

    override fun onDestroy() {
        handler.removeCallbacks(openHome)
        super.onDestroy()
    }
}
