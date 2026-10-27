package com.sally.launcher.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.view.View
import kotlin.random.Random

/** Static deep-space backdrop: gradient, soft nebula glow and a fixed starfield. No animation, so it costs nothing while idle. */
class CosmicBackgroundView(context: Context) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stars = FloatArray(STAR_COUNT * 4) // x, y (0..1), radius dp, alpha
    private var density = resources.displayMetrics.density

    init {
        val random = Random(7)
        for (i in 0 until STAR_COUNT) {
            stars[i * 4] = random.nextFloat()
            stars[i * 4 + 1] = random.nextFloat()
            stars[i * 4 + 2] = 0.4f + random.nextFloat() * 1.0f
            stars[i * 4 + 3] = 0.25f + random.nextFloat() * 0.65f
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        paint.shader = LinearGradient(
            0f, 0f, 0f, h.toFloat(),
            intArrayOf(0xFF07071A.toInt(), 0xFF0E0E2C.toInt(), 0xFF12103A.toInt()),
            floatArrayOf(0f, 0.6f, 1f),
            Shader.TileMode.CLAMP,
        )
        val radius = maxOf(w, h) * 0.55f
        glowPaint.shader = RadialGradient(
            w * 0.85f, h * 0.15f, radius,
            intArrayOf(0x405B47D6, 0x00000000),
            null,
            Shader.TileMode.CLAMP,
        )
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        canvas.drawRect(0f, 0f, w, h, paint)
        canvas.drawRect(0f, 0f, w, h, glowPaint)
        val starPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFFFFFF.toInt() }
        for (i in 0 until STAR_COUNT) {
            starPaint.alpha = (stars[i * 4 + 3] * 255).toInt()
            canvas.drawCircle(stars[i * 4] * w, stars[i * 4 + 1] * h, stars[i * 4 + 2] * density, starPaint)
        }
    }

    private companion object {
        const val STAR_COUNT = 90
    }
}
