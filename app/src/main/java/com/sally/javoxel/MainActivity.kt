package com.sally.javoxel

import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import com.sally.javoxel.ui.JavoxelApp
import com.sally.javoxel.util.AppLog

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        AppLog.init(filesDir)
        installJavaCrashLogger()
        AppLog.i(
            "App mulai. Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT}), " +
                "ABI ${Build.SUPPORTED_ABIS.joinToString()}, ${Build.MANUFACTURER} ${Build.MODEL}"
        )

        setContent {
            // Background dan teks mengikuti tema sistem (gelap / terang).
            val colors = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()
            MaterialTheme(colorScheme = colors) {
                Surface(color = colors.background) {
                    JavoxelApp()
                }
            }
        }
    }

    /** Crash Java dicatat ke log app sebelum proses berhenti. */
    private fun installJavaCrashLogger() {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            AppLog.e("CRASH Java di thread ${thread.name}:\n${error.stackTraceToString()}")
            previous?.uncaughtException(thread, error)
        }
    }
}
