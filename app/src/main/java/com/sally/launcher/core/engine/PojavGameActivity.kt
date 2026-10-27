package com.sally.launcher.core.engine

import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.sally.launcher.data.model.MinecraftVersion
import java.io.IOException
import java.util.concurrent.Executors

/** Landscape game host. It starts JLI only after a bound Pojav surface and a real account session exist. */
class PojavGameActivity : AppCompatActivity() {
    private val launchExecutor = Executors.newSingleThreadExecutor { task ->
        Thread(task, "Sally-Minecraft-JLI").apply { isDaemon = false }
    }
    private val mainHandler = Handler(Looper.getMainLooper())

    private lateinit var surfaceView: PojavGameSurfaceView
    private lateinit var statusText: TextView
    private lateinit var closeButton: MaterialButton
    private var launchStarted = false
    private var launchFinished = false
    private var requestedVersion: MinecraftVersion? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_FULLSCREEN or
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            )

        requestedVersion = readVersionFromIntent()
        if (requestedVersion == null) {
            Toast.makeText(this, "The selected Minecraft version is missing.", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        val root = FrameLayout(this).apply {
            setBackgroundColor(Color.BLACK)
            keepScreenOn = true
        }
        surfaceView = PojavGameSurfaceView(this) { message ->
            mainHandler.post {
                if (::statusText.isInitialized && !isFinishing) statusText.text = message
            }
        }.apply {
            contentDescription = "Minecraft Java game surface"
            onBridgeReady = { beginLaunchWhenReady() }
        }
        root.addView(surfaceView, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT,
        ))

        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(10), dp(12), dp(10))
            setBackgroundColor(0xD91B2430.toInt())
        }
        statusText = TextView(this).apply {
            textSize = 13f
            setTextColor(Color.WHITE)
            text = "Waiting for the Android game surface…"
            contentDescription = "Minecraft launch status"
        }
        panel.addView(statusText, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        ))
        closeButton = MaterialButton(this).apply {
            text = "Back to launcher"
            isAllCaps = false
            // The user must be able to leave the host if surface binding fails or the
            // account is not signed in. Disable this only while the native game is active.
            isEnabled = true
            setOnClickListener {
                if (launchStarted && !launchFinished) {
                    Toast.makeText(this@PojavGameActivity, "Exit Minecraft from its in-game menu first.", Toast.LENGTH_LONG).show()
                } else {
                    finish()
                }
            }
        }
        panel.addView(closeButton, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        ))
        root.addView(panel, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            Gravity.TOP,
        ))
        setContentView(root)
        surfaceView.requestFocus()
    }

    override fun onResume() {
        super.onResume()
        if (::surfaceView.isInitialized && surfaceView.isBridgeReady) beginLaunchWhenReady()
    }

    override fun onBackPressed() {
        if (launchStarted && !launchFinished) {
            Toast.makeText(this, "Exit Minecraft from its in-game menu first.", Toast.LENGTH_LONG).show()
        } else {
            super.onBackPressed()
        }
    }

    override fun onDestroy() {
        // shutdown() allows an active native JLI call to return naturally; interrupting it is unsafe.
        launchExecutor.shutdown()
        super.onDestroy()
    }

    @Synchronized
    private fun beginLaunchWhenReady() {
        if (isFinishing || launchStarted || !::surfaceView.isInitialized || !surfaceView.isBridgeReady) return
        val version = requestedVersion ?: return
        val session = MinecraftLaunchSessionStore.current()
        if (session == null) {
            statusText.text = "Graphics/input surface is ready. Sign in with a Microsoft account linked to Minecraft Java Edition before launch. No offline placeholder token will be used."
            return
        }
        val sessionError = session.validationError()
        if (sessionError != null) {
            MinecraftLaunchSessionStore.clear()
            statusText.text = sessionError
            return
        }

        launchStarted = true
        closeButton.isEnabled = false
        statusText.text = "Preparing Java ${version.id}, native library paths, and authenticated launch arguments…"
        launchExecutor.execute {
            val result = runCatching {
                val profile = LaunchProfileBuilder(applicationContext).prepare(version, session = session)
                val spec = JliLaunchSpecBuilder(applicationContext).build(profile)
                if (!spec.canAttemptLaunch) {
                    throw IOException("Minecraft launch is blocked:\n${spec.blockers.joinToString("\n") { "• $it" }}")
                }
                PojavJliLaunchExecutor(applicationContext).launch(
                    profile = profile,
                    spec = spec,
                    session = session,
                    surfaceReady = surfaceView.isBridgeReady,
                )
            }
            mainHandler.post {
                if (isFinishing || isDestroyed) return@post
                launchFinished = true
                closeButton.isEnabled = true
                result.onSuccess { exitCode ->
                    statusText.text = "Minecraft Java ${version.id} exited with code $exitCode. Check the game log if it did not reach the title screen."
                }.onFailure { error ->
                    statusText.text = "Minecraft could not start: ${error.message ?: error.javaClass.simpleName}"
                }
            }
        }
    }

    private fun readVersionFromIntent(): MinecraftVersion? {
        val id = intent.getStringExtra(EXTRA_VERSION_ID)?.takeIf { it.isNotBlank() } ?: return null
        val type = intent.getStringExtra(EXTRA_VERSION_TYPE).orEmpty().ifBlank { "release" }
        val url = intent.getStringExtra(EXTRA_VERSION_URL)?.takeIf { it.startsWith("https://") } ?: return null
        val releaseTime = intent.getStringExtra(EXTRA_RELEASE_TIME).orEmpty()
        if (!id.matches(Regex("[A-Za-z0-9._-]{1,100}"))) return null
        return MinecraftVersion(id = id, type = type, url = url, releaseTime = releaseTime)
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density + 0.5f).toInt()

    companion object {
        const val EXTRA_VERSION_ID = "com.sally.launcher.extra.VERSION_ID"
        const val EXTRA_VERSION_TYPE = "com.sally.launcher.extra.VERSION_TYPE"
        const val EXTRA_VERSION_URL = "com.sally.launcher.extra.VERSION_URL"
        const val EXTRA_RELEASE_TIME = "com.sally.launcher.extra.RELEASE_TIME"
    }
}
