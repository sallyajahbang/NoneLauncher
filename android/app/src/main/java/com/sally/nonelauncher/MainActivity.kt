package com.sally.nonelauncher

import android.app.Activity
import android.os.Bundle
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import com.sally.nonelauncher.core.GameInstaller
import com.sally.nonelauncher.core.NativeBridge
import org.json.JSONObject
import java.io.File
import kotlin.concurrent.thread

/**
 * Basic UI: test connection, install the latest release, and launch.
 *
 * Launching needs a JRE in [runtimeDir]/libjvm.so (the Amethyst runtime).
 */
class MainActivity : Activity() {

    private lateinit var status: TextView
    private lateinit var installer: GameInstaller
    private var installed: GameInstaller.Installed? = null

    private val runtimeDir by lazy { File(filesDir, "runtime") }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        installer = GameInstaller(filesDir)

        status = TextView(this).apply {
            text = "Siap."
            setPadding(32, 32, 32, 32)
        }

        val testBtn = button("Tes koneksi Mojang") {
            setStatus("Mengambil manifest...")
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
                setStatus(message)
            }
        }

        val installBtn = button("Install game terbaru") {
            setStatus("Memulai install...")
            thread {
                try {
                    installed = installer.install(::setStatus)
                    setStatus("Install selesai: ${installed?.versionId}")
                } catch (e: Throwable) {
                    setStatus("Gagal install: ${e.message}")
                }
            }
        }

        val launchBtn = button("Jalankan") {
            thread { launch() }
        }

        setContentView(
            LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(32, 32, 32, 32)
                addView(testBtn, wrap())
                addView(installBtn, wrap())
                addView(launchBtn, wrap())
                addView(status)
            }
        )
    }

    private fun launch() {
        val game = installed ?: run {
            setStatus("Install game dulu.")
            return
        }
        val libjvm = File(runtimeDir, "libjvm.so")
        if (!libjvm.exists()) {
            setStatus("Runtime belum ada. Taruh libjvm.so di ${libjvm.path}")
            return
        }
        try {
            setStatus("Menyiapkan argumen...")
            val args = installer.buildArgs(game)
            setStatus("Menjalankan Minecraft ${game.versionId}...")
            val result = JSONObject(
                NativeBridge.launchJvm(libjvm.path, game.mainClass, args.toString())
            )
            setStatus(
                if (result.has("error")) "Error: ${result.getString("error")}"
                else "Game berhenti."
            )
        } catch (e: Throwable) {
            setStatus("Gagal: ${e.message}")
        }
    }

    private fun setStatus(message: String) {
        runOnUiThread { status.text = message }
    }

    private fun button(label: String, onClick: () -> Unit): Button =
        Button(this).apply {
            text = label
            setOnClickListener { onClick() }
        }

    private fun wrap() = ViewGroup.LayoutParams(
        ViewGroup.LayoutParams.WRAP_CONTENT,
        ViewGroup.LayoutParams.WRAP_CONTENT,
    )
}
