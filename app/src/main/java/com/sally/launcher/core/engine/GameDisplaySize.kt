package com.sally.launcher.core.engine

import android.content.Context
import org.lwjgl.glfw.CallbackBridge

/** Landscape game resolution: the bound surface size when known, otherwise the display size. */
object GameDisplaySize {
    fun resolve(context: Context): Pair<Int, Int> {
        val surfaceWidth = CallbackBridge.windowWidth
        val surfaceHeight = CallbackBridge.windowHeight
        if (surfaceWidth > 0 && surfaceHeight > 0) return surfaceWidth to surfaceHeight
        val metrics = context.resources.displayMetrics
        return maxOf(metrics.widthPixels, metrics.heightPixels) to minOf(metrics.widthPixels, metrics.heightPixels)
    }
}
