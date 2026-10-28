// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 Buntuk

package com.sally.nonelauncher

import android.graphics.Typeface
import android.os.Bundle
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.sally.nonelauncher.core.launcher.LaunchOrchestrator
import com.sally.nonelauncher.runtime.jre.LocalJavaRuntimeProvider
import java.io.File

class MainActivity : AppCompatActivity() {

    private lateinit var logView: TextView
    private lateinit var launchButton: Button

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

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, pad)
            addView(launchButton, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            addView(logScroll, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        }
        setContentView(content)
    }

    private fun startLaunch() {
        launchButton.isEnabled = false
        logView.text = ""

        Thread {
            val orchestrator = LaunchOrchestrator(
                root = filesDir,
                javaProvider = LocalJavaRuntimeProvider(File(filesDir, "runtime")),
                log = { msg -> runOnUiThread { appendLog(msg) } },
            )
            try {
                orchestrator.launch(versionId = "1.16.5", username = "Player")
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
}
