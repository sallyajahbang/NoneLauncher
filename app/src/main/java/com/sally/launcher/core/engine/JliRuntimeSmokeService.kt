package com.sally.launcher.core.engine

import android.app.Service
import android.content.Intent
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Process
import com.sally.launcher.data.runtime.RuntimeManager
import java.io.File
import java.util.concurrent.Executors

/**
 * Runs JLI in a disposable secondary Android process. A native JVM abort should therefore not
 * take the launcher UI process with it. The process is explicitly terminated after reporting.
 */
class JliRuntimeSmokeService : Service() {
    private val executor = Executors.newSingleThreadExecutor()

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        executor.execute {
            val response = runCatching {
                val runtime = RuntimeManager(applicationContext).installedRuntimes()
                    .maxByOrNull { it.majorVersion }
                    ?: throw IllegalStateException("No verified Java runtime is installed. Install a runtime first.")
                val diagnosticsDirectory = File(filesDir, "diagnostics")
                val workDirectory = File(cacheDir, "jli-version-work")
                val logFile = File(diagnosticsDirectory, "jli-version.log")
                val test = JliRuntimeSmokeTest.run(runtime.home, workDirectory, logFile)
                ResultPayload(
                    passed = test.passed,
                    runtime = runtime.displayName(),
                    runtimeHome = runtime.home.absolutePath,
                    details = test.details,
                    logPath = test.logFile.absolutePath,
                )
            }.getOrElse { error ->
                ResultPayload(
                    passed = false,
                    runtime = "Unavailable",
                    runtimeHome = "",
                    details = error.message ?: error.javaClass.simpleName,
                    logPath = File(filesDir, "diagnostics/jli-version.log").absolutePath,
                )
            }

            val resultIntent = Intent(ACTION_RESULT)
                .setPackage(packageName)
                .putExtra(EXTRA_PASSED, response.passed)
                .putExtra(EXTRA_RUNTIME, response.runtime)
                .putExtra(EXTRA_RUNTIME_HOME, response.runtimeHome)
                .putExtra(EXTRA_DETAILS, response.details)
                .putExtra(EXTRA_LOG_PATH, response.logPath)
            sendBroadcast(resultIntent)
            stopSelf(startId)

            // A returned JLI call can still leave process-global VM state. Keep the launcher
            // process independent and guarantee this one-shot diagnostic gets a fresh process.
            Handler(Looper.getMainLooper()).postDelayed({
                Process.killProcess(Process.myPid())
            }, PROCESS_EXIT_DELAY_MS)
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        executor.shutdownNow()
        super.onDestroy()
    }

    private data class ResultPayload(
        val passed: Boolean,
        val runtime: String,
        val runtimeHome: String,
        val details: String,
        val logPath: String,
    )

    companion object {
        const val ACTION_RESULT = "com.sally.launcher.action.JLI_RUNTIME_SMOKE_RESULT"
        const val EXTRA_PASSED = "passed"
        const val EXTRA_RUNTIME = "runtime"
        const val EXTRA_RUNTIME_HOME = "runtime_home"
        const val EXTRA_DETAILS = "details"
        const val EXTRA_LOG_PATH = "log_path"
        private const val PROCESS_EXIT_DELAY_MS = 750L
    }
}
