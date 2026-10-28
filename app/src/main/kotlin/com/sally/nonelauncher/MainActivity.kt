package com.sally.nonelauncher

import android.app.Activity
import android.os.Build
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
import com.sally.nonelauncher.core.runtime.RuntimeInstaller
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.tukaani.xz.XZInputStream
import java.io.File

/**
 * Plain functional screen: pick a version, install it, start it.
 * Layout is deliberately unstyled for now.
 */
class MainActivity : Activity() {

    companion object {
        /** The only Java runtime bundled in the APK for now. */
        private const val BUNDLED_JAVA = 21
    }

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
        val testJvmBtn = Button(this).apply {
            text = "Test JVM"
            setOnClickListener { testJvm() }
        }

        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.START
            addView(versionInput)
            addView(nameInput)
            addView(installBtn)
            addView(launchBtn)
            addView(testJvmBtn)
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
                return@launch
            }

            // Runtime is separate from the game files, so a missing runtime does not undo the install.
            try {
                val major = withContext(Dispatchers.IO) { core.loadInstalled(id).meta.javaMajorVersion }
                val home = withContext(Dispatchers.IO) { ensureRuntime(major) }
                append("Runtime Java $major siap: ${home.path}")
            } catch (e: Throwable) {
                append("Runtime gagal: ${e.message}")
            }
        }
    }

    private fun launch() {
        val id = versionInput.text.toString().trim()
        val name = nameInput.text.toString().trim()
        scope.launch {
            try {
                val installed = withContext(Dispatchers.IO) { core.loadInstalled(id) }
                withContext(Dispatchers.IO) { ensureRuntime(installed.meta.javaMajorVersion) }
                val command = core.buildCommand(installed, OfflineAccount(name), instance = id)
                withContext(Dispatchers.IO) { backend.start(command) }
            } catch (e: Throwable) {
                append("Launch gagal: ${e.message}\n${e.stackTraceToString()}")
            }
        }
    }

    /**
     * Installs the bundled Java runtime for [major] if it is not present yet.
     * Only [BUNDLED_JAVA] is available; other versions need their own bundle.
     */
    private fun ensureRuntime(major: Int): File {
        require(major == BUNDLED_JAVA) {
            "Java $major belum dibundel di aplikasi ini (baru Java $BUNDLED_JAVA)"
        }
        val abi = when (Build.SUPPORTED_ABIS.firstOrNull()) {
            "arm64-v8a" -> "arm64"
            "x86_64" -> "x86_64"
            else -> throw IllegalStateException("arsitektur ${Build.SUPPORTED_ABIS.firstOrNull()} belum didukung")
        }
        val dir = "runtimes/java-$BUNDLED_JAVA"
        val version = assets.open("$dir/version").bufferedReader().use { it.readText().trim() }
        return RuntimeInstaller.install(major, version, core.paths) {
            XZInputStream(assets.open("$dir/bin-$abi.tar.xz"))
        }
    }

    /**
     * Step 2 of the JVM bridge: installs the runtime and checks that libjli.so loads.
     *
     * launchJVM is NOT called here. If the JVM starts successfully it exits the whole
     * process when it finishes, which would close the app. The real launch comes next.
     */
    private fun testJvm() {
        scope.launch {
            try {
                withContext(Dispatchers.IO) { System.loadLibrary("nonelauncher_jvm") }
                append("Library native nonelauncher_jvm berhasil dimuat.")
                val home = withContext(Dispatchers.IO) { ensureRuntime(BUNDLED_JAVA) }
                append("Runtime siap: ${home.path}")
                val jli = File(home, "lib/libjli.so")
                withContext(Dispatchers.IO) { System.load(jli.absolutePath) }
                append("libjli.so berhasil dimuat.")
            } catch (e: Throwable) {
                append("Test JVM gagal: ${e.message}\n${e.stackTraceToString()}")
            }
        }
    }

    private fun append(line: String) {
        runOnUiThread { log.append(line + "\n") }
    }
}
