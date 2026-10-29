package com.sally.nonelauncher

import android.app.Activity
import android.os.Bundle
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import com.sally.nonelauncher.core.NativeBridge
import org.json.JSONObject
import kotlin.concurrent.thread

/**
 * Minimal UI to check that the Rust core loads and can reach Mojang.
 * The real launcher UI (version list, accounts, play button) goes here later.
 */
class MainActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val status = TextView(this).apply {
            text = "Siap."
            setPadding(32, 32, 32, 32)
        }

        val button = Button(this).apply {
            text = "Tes koneksi Mojang"
            setOnClickListener {
                status.text = "Mengambil manifest..."
                thread {
                    val message = try {
                        val json = JSONObject(NativeBridge.fetchManifest())
                        if (json.has("error")) {
                            "Gagal: ${json.getString("error")}"
                        } else {
                            val release = json.getJSONObject("latest").getString("release")
                            "Rilis terbaru: $release\nUUID offline Steve: " +
                                NativeBridge.offlineUuid("Steve")
                        }
                    } catch (e: Throwable) {
                        "Error: ${e.message}"
                    }
                    runOnUiThread { status.text = message }
                }
            }
        }

        setContentView(
            LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(32, 32, 32, 32)
                addView(button, ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                addView(status)
            }
        )
    }
}
