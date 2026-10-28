package com.sally.nonelauncher

import android.app.Activity
import android.os.Bundle
import android.text.method.ScrollingMovementMethod
import android.view.Gravity
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.sally.nonelauncher.core.LauncherCore
import com.sally.nonelauncher.core.LauncherPaths
import com.sally.nonelauncher.core.auth.OfflineAccount
import com.sally.nonelauncher.core.download.Downloader
import com.sally.nonelauncher.core.launch.GameBackend
import com.sally.nonelauncher.core.model.RuntimeContext
import com.sally.nonelauncher.core.mojang.VersionManifest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Plain functional screen: pick a version, install it, start it.
 * Layout is deliberately unstyled for now.
 */
class MainActivity : Activity() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private lateinit var log: TextView
    private lateinit var versionInput: EditText
    private lateinit var nameInput: EditText

    private val core by lazy {
        LauncherCore(
            paths = LauncherPaths(File(filesDir, "nonelauncher")),
            platform = RuntimeContext.current(),
            downloader = Downloader(),
            log = ::append,
        )
    }
    private val backend: GameBackend = AndroidGameBackend()

    private val crashFile by lazy { File(filesDir, "last_crash.txt") }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Save any uncaught crash to a file, so the next start can show it on screen.
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching { crashFile.writeText(error.stackTraceToString()) }
            previous?.uncaughtException(thread, error)
        }

        log = TextView(this).apply {
            movementMethod = ScrollingMovementMethod()
        }
        if (crashFile.exists()) {
            log.append("=== Crash sebelumnya ===\n" + crashFile.readText() + "\n")
            crashFile.delete()
        }
        versionInput = EditText(this).apply { hint = "Versi, contoh 1.21.1"; setText("1.21.1") }
        nameInput = EditText(this).apply { hint = "Nama pemain (offline)"; setText("Player") }

        val installBtn = Button(this).apply {
            text = "Install"
            setOnClickListener { install() }
        }
        val launchBtn = Button(this).apply {
            text = "Launch"
            setOnClickListener { launch() }
        }

        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.START
            addView(versionInput)
            addView(nameInput)
            addView(installBtn)
            addView(launchBtn)
            addView(ScrollView(this@MainActivity).apply { addView(log) })
        }
        setContentView(column)
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun install() {
        val id = versionInput.text.toString().trim()
        scope.launch {
            try {
                append("Mengambil manifest...")
                val manifest: VersionManifest = withContext(Dispatchers.IO) { core.fetchManifest() }
                val entry = manifest.versions.firstOrNull { it.id == id }
                    ?: throw IllegalArgumentException("versi $id tidak ditemukan")
                withContext(Dispatchers.IO) { core.install(entry) }
                append("Selesai install $id")
            } catch (e: Throwable) {
                append("Install gagal: ${e.message}\n${e.stackTraceToString()}")
            }
        }
    }

    private fun launch() {
        val id = versionInput.text.toString().trim()
        val name = nameInput.text.toString().trim()
        scope.launch {
            try {
                val installed = withContext(Dispatchers.IO) { core.loadInstalled(id) }
                val command = core.buildCommand(installed, OfflineAccount(name), instance = id)
                withContext(Dispatchers.IO) { backend.start(command) }
            } catch (e: Throwable) {
                append("Launch gagal: ${e.message}\n${e.stackTraceToString()}")
            }
        }
    }

    private fun append(line: String) {
        runOnUiThread { log.append(line + "\n") }
    }
}
