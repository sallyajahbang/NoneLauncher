// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 Buntuk

package com.sally.nonelauncher

import android.graphics.Typeface
import android.os.Bundle
import android.view.SurfaceView
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.sally.nonelauncher.core.launcher.LaunchOrchestrator
import com.sally.nonelauncher.runtime.jre.JreDownloader
import com.sally.nonelauncher.runtime.jre.LocalJavaRuntimeProvider
import java.io.File

class MainActivity : AppCompatActivity() {

    private lateinit var logView: TextView
    private lateinit var launchButton: Button
    private lateinit var surfaceView: SurfaceView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val pad = 32
        launchButton = Button(this).apply {
            text = "Mulai 1.16.5"
            setOnClickListener { startLaunch() }
        }
        logView = TextView(this).apply {
            textSize = 12f
            typeface = Typeface.MONOSPACE
        }
        val logScroll = ScrollView(this).apply { addView(logView) }
        surfaceView = SurfaceView(this)

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, pad)
            addView(launchButton, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            addView(logScroll, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
            addView(surfaceView, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 2f))
        }
        setContentView(content)
        startJreInstall()
    }

    /** Unduh JRE 8 di latar belakang saat app dibuka, jika belum ada. */
    private fun startJreInstall() {
        Thread {
            try {
                JreDownloader.ensureInstalled(8, runtimeDir()) { msg -> runOnUiThread { appendLog(msg) } }
            } catch (e: Throwable) {
                runOnUiThread { appendLog("JRE gagal: ${e.message ?: e.javaClass.simpleName}") }
            }
        }.start()
    }

    private fun startLaunch() {
        val surface = surfaceView.holder.surface
        if (!surface.isValid) {
            appendLog("Surface belum siap. Coba lagi sebentar.")
            return
        }

        launchButton.isEnabled = false
        logView.text = ""

        Thread {
            val orchestrator = LaunchOrchestrator(
                root = filesDir,
                javaProvider = LocalJavaRuntimeProvider(javaRoots()),
                log = { msg -> runOnUiThread { appendLog(msg) } },
            )
            try {
                orchestrator.launch(versionId = "1.16.5", username = "Player", surface = surface)
            } catch (e: Throwable) {
                runOnUiThread { appendLog("GAGAL: ${e.message ?: e.javaClass.simpleName}") }
            } finally {
                runOnUiThread { launchButton.isEnabled = true }
            }
        }.start()
    }

    private fun appendLog(msg: String) {
        logView.append(msg + "\n")
    }

    /**
     * Folder tempat JRE dicari. Folder eksternal bisa diisi lewat adb tanpa run-as:
     * Android/data/com.sally.nonelauncher/files/runtime/java8
     */
    private fun javaRoots(): List<File> = listOf(runtimeDir(), File(filesDir, "runtime")).distinct()

    /** Folder JRE di penyimpanan eksternal app. Bisa dilihat lewat file manager atau adb. */
    private fun runtimeDir(): File =
        getExternalFilesDir(null)?.let { File(it, "runtime") } ?: File(filesDir, "runtime")
}
