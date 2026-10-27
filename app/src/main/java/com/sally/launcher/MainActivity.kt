package com.sally.launcher

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.sally.launcher.core.auth.LocalAccountRepository
import com.sally.launcher.core.auth.MicrosoftLoginActivity
import com.sally.launcher.core.engine.EngineDiagnosticsActivity
import com.sally.launcher.core.engine.JliBridgeDiagnostics
import com.sally.launcher.core.engine.JliLaunchSpecBuilder
import com.sally.launcher.core.engine.JliRuntimeSmokeService
import com.sally.launcher.core.engine.LaunchProfileBuilder
import com.sally.launcher.core.engine.MinecraftAccountType
import com.sally.launcher.core.engine.MinecraftLaunchSessionStore
import com.sally.launcher.core.engine.PojavEngineDiagnosticsActivity
import com.sally.launcher.core.engine.PojavEnginePayloadInstaller
import com.sally.launcher.core.engine.PojavGameActivity
import com.sally.launcher.data.install.VersionInstaller
import com.sally.launcher.data.model.MinecraftVersion
import com.sally.launcher.data.model.VersionCatalog
import com.sally.launcher.data.runtime.RuntimeManager
import com.sally.launcher.data.skin.SkinModel
import com.sally.launcher.data.skin.SkinProvider
import com.sally.launcher.data.storage.LauncherStorage
import com.sally.launcher.data.version.VersionManifestRepository
import org.json.JSONObject
import java.io.File
import java.util.concurrent.Executors
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.asImageBitmap

class MainActivity : AppCompatActivity() {
    private val ioExecutor = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())
    private lateinit var repository: VersionManifestRepository
    private val uiState = mutableStateOf(LauncherUiState())
    private var catalogVersions: List<MinecraftVersion> = emptyList()
    private var latestReleaseId: String? = null
    private var latestSnapshotId: String? = null
    private var jvmSmokeReceiver: BroadcastReceiver? = null
    private var jvmSmokeTimeout: Runnable? = null

    private val actions by lazy {
        LauncherActions(
            navigate = { tab -> updateState { copy(tab = tab) } },
            setFilter = { filter -> updateState { copy(filter = filter) } },
            selectVersion = ::selectVersion,
            refreshCatalog = ::loadVersionCatalog,
            installVersion = ::installVersion,
            redownloadVersion = ::confirmRedownloadVersion,
            play = ::onPlayPressed,
            installRuntime = { installRuntimeForSelectedVersion(forceRedownload = false) },
            installJava = ::installJava,
            redownloadRuntime = ::confirmRedownloadRuntime,
            signInMicrosoft = { startActivity(Intent(this, MicrosoftLoginActivity::class.java)) },
            setAccountStep = { step -> updateState { copy(accountStep = step) } },
            activateAccount = ::activateAccount,
            removeAccount = ::removeAccount,
            addOfflineAccount = ::addOfflineAccount,
            prepareProfile = ::prepareLaunchProfile,
            probeJvm = ::probeJvmBridge,
            testJvm = ::runJliRuntimeSmokeTest,
            testGraphics = { startActivity(Intent(this, EngineDiagnosticsActivity::class.java)) },
            testPojav = { startActivity(Intent(this, PojavEngineDiagnosticsActivity::class.java)) },
            setThemeMode = ::setThemeMode,
            requestDeveloperMode = ::requestDeveloperMode,
            setKeepScreenOn = ::setKeepScreenOn,
            dismissDialog = { updateState { copy(dialog = null) } },
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            window.attributes.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
        hideSystemBars()
        repository = VersionManifestRepository(applicationContext)
        LocalAccountRepository.restoreSelectedSession(this)
        val prefs = getPreferences(MODE_PRIVATE)
        val settingsPrefs = getSharedPreferences(PREFS_SETTINGS, MODE_PRIVATE)
        val selected = prefs.getString(KEY_SELECTED_VERSION, "").orEmpty()
        uiState.value = uiState.value.copy(
            selectedVersionId = selected,
            themeMode = ThemeSettings.read(this).mode,
            developerMode = settingsPrefs.getBoolean(KEY_DEVELOPER_MODE, false),
            keepScreenOn = settingsPrefs.getBoolean(KEY_KEEP_SCREEN_ON, false),
            accountStatus = accountStatusSummary(),
        )
        applyKeepScreenOn(uiState.value.keepScreenOn)
        refreshAccountState()
        setContent {
            SallyLauncherRoot(state = uiState.value, actions = actions)
        }
        updateRuntimeStatus()
        loadVersionCatalog()
    }

    override fun onResume() {
        super.onResume()
        hideSystemBars()
        LocalAccountRepository.restoreSelectedSession(this)
        updateAccountStatus()
        refreshAccountState()
        refreshInstalledVersionsAsync()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemBars()
    }

    // Fullscreen: hide status and navigation bars; they reappear briefly on a swipe.
    private fun hideSystemBars() {
        WindowInsetsControllerCompat(window, window.decorView).apply {
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            hide(WindowInsetsCompat.Type.systemBars())
        }
    }

    override fun onDestroy() {
        clearJliSmokeReceiver()
        ioExecutor.shutdownNow()
        super.onDestroy()
    }

    private inline fun updateState(reducer: LauncherUiState.() -> LauncherUiState) {
        uiState.value = uiState.value.reducer()
    }

    private fun showDialog(dialog: LauncherDialog) = updateState { copy(dialog = dialog) }

    private fun showMessage(title: String, message: String, confirmText: String = "OK", onConfirm: (() -> Unit)? = null) {
        showDialog(LauncherDialog(title = title, message = message, confirmText = confirmText, onConfirm = onConfirm))
    }

    private fun loadVersionCatalog() {
        updateState { copy(loadingCatalog = true, refreshing = true, catalogStatus = "Connecting to the official version catalog…") }
        ioExecutor.execute {
            val result = runCatching { repository.fetchCatalog() }
            val existingCatalog = result.getOrNull()?.versions ?: catalogVersions
            val installed = scanInstalledVersions(existingCatalog)
            mainHandler.post {
                if (isFinishing || isDestroyed) return@post
                updateState {
                    copy(
                        loadingCatalog = false,
                        refreshing = false,
                        catalogStatus = result.fold(
                            { catalog -> "${catalog.versions.size} official versions · ${if (catalog.loadedFromCache) "saved catalog (offline)" else "catalog updated"}" },
                            { "Could not refresh the catalog. Showing installed versions; check your connection and retry." },
                        ),
                        versions = mergeVersions(existingCatalog, installed),
                        installedVersions = installed,
                    )
                }
                result.onSuccess { catalog ->
                    catalogVersions = catalog.versions
                    latestReleaseId = catalog.latestReleaseId
                    latestSnapshotId = catalog.latestSnapshotId
                    val persisted = getPreferences(MODE_PRIVATE).getString(KEY_SELECTED_VERSION, "").orEmpty()
                    val preferredInstalled = installed.firstOrNull { it.id == persisted }
                        ?: installed.firstOrNull { it.id == catalog.latestReleaseId }
                        ?: installed.firstOrNull()
                    val preferred = preferredInstalled
                        ?: catalog.versions.firstOrNull { it.id == persisted }
                        ?: catalog.versions.firstOrNull { it.id == catalog.latestReleaseId }
                        ?: catalog.versions.firstOrNull()
                    if (preferred != null) {
                        if (preferredInstalled != null) selectVersion(preferred.id)
                        else updateState { copy(selectedVersionId = preferred.id) }
                    }
                }.onFailure { error ->
                    if (installed.isNotEmpty()) {
                        updateState { copy(catalogStatus = "Offline catalog unavailable · ${installed.size} installed versions ready") }
                    }
                    Toast.makeText(this, error.message ?: "Version catalog unavailable", Toast.LENGTH_LONG).show()
                }
                updateRuntimeStatus()
            }
        }
    }

    private fun mergeVersions(catalog: List<MinecraftVersion>, installed: List<MinecraftVersion>): List<MinecraftVersion> {
        val byId = LinkedHashMap<String, MinecraftVersion>()
        catalog.forEach { byId[it.id] = it }
        installed.forEach { installedVersion -> byId.putIfAbsent(installedVersion.id, installedVersion) }
        return byId.values.toList()
    }

    private fun scanInstalledVersions(knownVersions: List<MinecraftVersion>): List<MinecraftVersion> {
        val root = LauncherStorage.minecraftRoot(applicationContext)
        val versionsDir = File(root, "versions")
        if (!versionsDir.isDirectory) return emptyList()
        val byId = knownVersions.associateBy { it.id }
        val found = versionsDir.listFiles().orEmpty().mapNotNull { directory ->
            if (!directory.isDirectory) return@mapNotNull null
            val id = directory.name
            val metadataFile = File(directory, "$id.json")
            val jar = File(directory, "$id.jar")
            if (!metadataFile.isFile || metadataFile.length() <= 0L || !jar.isFile || jar.length() <= 0L) return@mapNotNull null
            val metadata = runCatching { JSONObject(metadataFile.readText(Charsets.UTF_8)) }.getOrNull() ?: return@mapNotNull null
            if (metadata.optString("id").let { it.isNotBlank() && it != id }) return@mapNotNull null
            val official = byId[id]
            MinecraftVersion(
                id = id,
                type = official?.type ?: metadata.optString("type", "release").ifBlank { "release" },
                url = official?.url.orEmpty(),
                releaseTime = official?.releaseTime ?: metadata.optString("releaseTime", metadata.optString("time", "")),
            )
        }
        val order = knownVersions.mapIndexed { index, version -> version.id to index }.toMap()
        return found.sortedWith(compareBy<MinecraftVersion> { order[it.id] ?: Int.MAX_VALUE }.thenByDescending { it.releaseTime }.thenByDescending { it.id })
    }

    private fun refreshInstalledVersionsAsync() {
        ioExecutor.execute {
            val installed = scanInstalledVersions(catalogVersions)
            mainHandler.post {
                if (isFinishing || isDestroyed) return@post
                updateState {
                    val selectedStillInstalled = installed.any { it.id == selectedVersionId }
                    val fallback = installed.firstOrNull { it.id == latestReleaseId } ?: installed.firstOrNull()
                    val normalizedSelected = if (installed.isNotEmpty() && !selectedStillInstalled) fallback?.id.orEmpty() else selectedVersionId
                    if (normalizedSelected != selectedVersionId && normalizedSelected.isNotBlank()) {
                        getPreferences(MODE_PRIVATE).edit().putString(KEY_SELECTED_VERSION, normalizedSelected).apply()
                    }
                    copy(installedVersions = installed, versions = mergeVersions(catalogVersions, installed), selectedVersionId = normalizedSelected)
                }
                updateRuntimeStatus()
            }
        }
    }

    private fun selectVersion(versionId: String) {
        val current = uiState.value
        val version = current.versions.firstOrNull { it.id == versionId }
            ?: current.installedVersions.firstOrNull { it.id == versionId }
            ?: return
        val isInstalled = current.installedVersions.any { it.id == version.id }
        if (isInstalled) updateState { copy(selectedVersionId = version.id) }
        if (isInstalled) getPreferences(MODE_PRIVATE).edit().putString(KEY_SELECTED_VERSION, version.id).apply()
    }

    private fun selectedVersionForInstall(versionId: String): MinecraftVersion? =
        uiState.value.versions.firstOrNull { it.id == versionId }
            ?: uiState.value.installedVersions.firstOrNull { it.id == versionId }

    private fun installVersion(versionId: String) = runVersionInstall(versionId, forceRedownload = false)

    private fun confirmRedownloadVersion(versionId: String) {
        if (selectedVersionForInstall(versionId) == null) {
            showMessage("Version not found", "Refresh the official catalog and select a version again.")
            return
        }
        showDialog(
            LauncherDialog(
                title = "Redownload Minecraft $versionId?",
                message = "This downloads the version metadata, client JAR, libraries, and assets used by this version again. Existing shared assets may be downloaded again too, so this can use a lot of mobile data and storage space. Network failures will retry automatically and partial files can resume when the server supports it.",
                confirmText = "Redownload",
                onConfirm = { runVersionInstall(versionId, forceRedownload = true) },
                dismissText = "Cancel",
            )
        )
    }

    private fun runVersionInstall(versionId: String, forceRedownload: Boolean) {
        val selected = selectedVersionForInstall(versionId)
        if (selected == null) {
            showMessage("Version not found", "Refresh the official catalog and select a version again.")
            return
        }
        val actionKey = if (forceRedownload) "redownload:$versionId" else "install:$versionId"
        val startLabel = if (forceRedownload) "Preparing full redownload of Minecraft $versionId…" else "Preparing Minecraft $versionId…"
        updateState { copy(busyAction = actionKey, catalogStatus = startLabel, transferDone = 0L, transferTotal = 0L) }
        ioExecutor.execute {
            val result = runCatching {
                VersionInstaller(applicationContext).installVersion(
                    version = selected,
                    onProgress = { message ->
                        mainHandler.post {
                            if (!isFinishing && !isDestroyed) updateState { copy(catalogStatus = message) }
                        }
                    },
                    onBytes = { done, total ->
                        mainHandler.post {
                            if (!isFinishing && !isDestroyed) updateState { copy(transferDone = done, transferTotal = total) }
                        }
                    },
                    forceRedownload = forceRedownload,
                )
            }
            val newInstalled = scanInstalledVersions(catalogVersions)
            mainHandler.post {
                if (isFinishing || isDestroyed) return@post
                updateState {
                    copy(
                        busyAction = null,
                        transferDone = 0L,
                        transferTotal = 0L,
                        installedVersions = newInstalled,
                        versions = mergeVersions(catalogVersions, newInstalled),
                        filter = VersionFilter.INSTALLED,
                        catalogStatus = result.fold(
                            { "${it.versionId} installed and verified · ${it.downloadedBytes / (1024 * 1024)} MB transferred" },
                            { error -> "Download failed: ${error.message ?: "unknown error"}" },
                        ),
                    )
                }
                result.onSuccess { installed ->
                    selectVersion(installed.versionId)
                    refreshAccountState()
                    showMessage(
                        if (forceRedownload) "Redownload complete" else "Version installed",
                        "Minecraft ${installed.versionId} is installed and its downloaded files passed available integrity checks. Libraries: ${installed.libraryFiles}. Assets: ${installed.assetFiles}. Data transferred this run: ${installed.downloadedBytes / (1024 * 1024)} MB."
                    )
                }.onFailure { error ->
                    val detail = error.message ?: "Could not download Minecraft version files. Check internet, storage, and file permissions."
                    showDialog(
                        LauncherDialog(
                            title = "Download failed",
                            message = "$detail\n\nThe launcher retries temporary network errors automatically. You can retry the missing files or choose a full redownload.",
                            confirmText = "Retry",
                            onConfirm = { runVersionInstall(versionId, forceRedownload = false) },
                            dismissText = "Close",
                            neutralText = "Redownload",
                            onNeutral = { confirmRedownloadVersion(versionId) },
                        )
                    )
                }
                updateRuntimeStatus()
            }
        }
    }

    private fun selectedInstalledVersion(): MinecraftVersion? {
        val state = uiState.value
        return state.installedVersions.firstOrNull { it.id == state.selectedVersionId } ?: state.installedVersions.firstOrNull()
    }

    private fun confirmRedownloadRuntime() {
        if (selectedInstalledVersion() == null) {
            showMessage("No installed version", "Install a Minecraft version first so the launcher can choose the compatible Java runtime.")
            return
        }
        showDialog(
            LauncherDialog(
                title = "Redownload Java runtime?",
                message = "The compatible Java runtime will be downloaded and verified again before replacing the current runtime. This can use several hundred megabytes of data and needs sufficient free storage. The currently installed runtime is kept until the new one passes validation.",
                confirmText = "Redownload Java",
                onConfirm = { installRuntimeForSelectedVersion(forceRedownload = true) },
                dismissText = "Cancel",
            )
        )
    }

    private fun installRuntimeForSelectedVersion(forceRedownload: Boolean = false, resumeRedownload: Boolean = false) {
        val selected = selectedInstalledVersion()
        if (selected == null) {
            showMessage("No installed version", "Install a Minecraft version first. Open Versions and choose a release, snapshot, beta, or alpha.", "Open Versions") {
                updateState { copy(tab = LauncherTab.VERSIONS) }
            }
            return
        }
        val actionKey = if (forceRedownload) "runtime-redownload" else "runtime"
        updateState { copy(busyAction = actionKey, transferDone = 0L, transferTotal = 0L, runtimeStatus = if (forceRedownload) "Preparing to redownload a compatible Java runtime for ${selected.id}…" else "Preparing a compatible Java runtime for ${selected.id}…") }
        ioExecutor.execute {
            val result = runCatching {
                RuntimeManager(applicationContext).installForVersion(
                    version = selected,
                    forceRedownload = forceRedownload,
                    resumeRedownload = resumeRedownload,
                    onBytes = { done, total ->
                        mainHandler.post { if (!isFinishing && !isDestroyed) updateState { copy(transferDone = done, transferTotal = total) } }
                    },
                    onProgress = { message ->
                        mainHandler.post { if (!isFinishing && !isDestroyed) updateState { copy(runtimeStatus = message) } }
                    },
                )
            }
            mainHandler.post {
                if (isFinishing || isDestroyed) return@post
                updateState { copy(busyAction = null, transferDone = 0L, transferTotal = 0L) }
                updateRuntimeStatus()
                result.onSuccess { installed ->
                    updateState { copy(runtimeStatus = "${installed.displayName()} installed and verified") }
                    showMessage(
                        if (forceRedownload) "Java runtime redownloaded" else "Java runtime ready",
                        "Java ${installed.majorVersion} for Android ${installed.architecture} is installed and verified. Run the Java self-test from DEV if startup fails."
                    )
                }.onFailure { error ->
                    val detail = error.message ?: "Could not install a compatible Java runtime. Check the internet connection and free storage."
                    updateState { copy(runtimeStatus = "Runtime download failed: $detail") }
                    showDialog(
                        LauncherDialog(
                            title = "Runtime download failed",
                            message = "$detail\n\nTemporary network failures are retried automatically. You can retry the runtime installation or choose to redownload it.",
                            confirmText = "Retry",
                            onConfirm = { installRuntimeForSelectedVersion(forceRedownload = forceRedownload, resumeRedownload = forceRedownload) },
                            dismissText = "Close",
                            neutralText = "Redownload",
                            onNeutral = { confirmRedownloadRuntime() },
                        )
                    )
                }
            }
        }
    }

    private fun updateRuntimeStatus() {
        ioExecutor.execute {
            val runtimes = runCatching { RuntimeManager(applicationContext).installedRuntimes() }.getOrDefault(emptyList())
            val supported = runCatching { RuntimeManager(applicationContext).supportedJavaMajors() }.getOrDefault(listOf(8, 17))
            mainHandler.post {
                if (isFinishing || isDestroyed) return@post
                updateState {
                    copy(
                        installedJavaMajors = runtimes.map { it.majorVersion },
                        supportedJavaMajors = supported,
                        runtimeStatus = if (runtimes.isEmpty()) "No verified Java runtimes installed on this device." else "Installed: " + runtimes.joinToString(" · ") { it.displayName() })
                }
            }
        }
    }

    private fun prepareLaunchProfile() {
        val selected = selectedInstalledVersion()
        if (selected == null) {
            showMessage("No installed version", "Install Minecraft version files before preparing a launch profile.")
            return
        }
        updateState { copy(busyAction = "prepare", engineStatus = "Resolving version metadata, AWT assets, runtime, and libraries…") }
        ioExecutor.execute {
            val result = runCatching {
                val minecraftRoot = LauncherStorage.minecraftRoot(applicationContext)
                val payloadInstaller = PojavEnginePayloadInstaller(applicationContext)
                payloadInstaller.installCaciocavalloComponents(minecraftRoot)
                payloadInstaller.installPatchedGlfwClasses(minecraftRoot)
                val session = MinecraftLaunchSessionStore.current()
                val profile = LaunchProfileBuilder(applicationContext).prepare(selected, session = session)
                val launchSpec = JliLaunchSpecBuilder(applicationContext).build(profile)
                profile to launchSpec
            }
            mainHandler.post {
                if (isFinishing || isDestroyed) return@post
                updateState { copy(busyAction = null) }
                result.onSuccess { (profile, spec) ->
                    val blockers = spec.blockers.joinToString("\n") { "• $it" }.ifBlank { "• None reported by the spec builder" }
                    val summary = "Minecraft ${profile.versionId}\nRequired Java: ${profile.requiredJavaMajor}\nSelected runtime: Java ${profile.runtimeMajor}\nMain class: ${profile.mainClass}\nClasspath entries: ${profile.classpathEntries.size}\nJVM arguments: ${profile.jvmArguments.size}\nGame arguments: ${profile.gameArguments.size}\nJLI argv entries: ${spec.argv.size}\nNative library directories: ${spec.nativeLibraryDirectories.size}\nWorking directory: ${spec.workingDirectory.absolutePath}\nAssets index: ${profile.assetIndexId}\nValid account profile: ${profile.hasValidAccountSession}\nAccount type: ${profile.accountType ?: "none"}\n\nRemaining blockers:\n$blockers\n\nNo session tokens are included in diagnostics."
                    updateState { copy(engineStatus = "Launch profile checked for ${profile.versionId} · ${spec.blockers.size} blocker(s)") }
                    showMessage("Launch profile prepared", summary)
                }.onFailure { error ->
                    updateState { copy(engineStatus = "Profile check failed: ${error.message ?: "unknown error"}") }
                    showMessage("Cannot prepare launch profile", error.message ?: "The selected version is missing required launch files.")
                }
            }
        }
    }

    private fun probeJvmBridge() {
        updateState { copy(busyAction = "probe", engineStatus = "Checking installed JVM's native JLI/JVM libraries…") }
        ioExecutor.execute {
            val result = runCatching {
                val runtime = RuntimeManager(applicationContext).installedRuntimes().maxByOrNull { it.majorVersion }
                    ?: error("No verified Java runtime is installed. Install a compatible runtime first.")
                runtime to JliBridgeDiagnostics.probe(runtime.home)
            }
            mainHandler.post {
                if (isFinishing || isDestroyed) return@post
                updateState { copy(busyAction = null) }
                result.onSuccess { (runtime, probe) ->
                    updateState { copy(engineStatus = if (probe.available) "JLI loader probe passed for ${runtime.displayName()} (library availability only)." else "JLI loader probe failed for ${runtime.displayName()}.") }
                    showMessage(if (probe.available) "JVM native loader available" else "JVM native loader not ready", "Runtime: ${runtime.displayName()}\nPath: ${runtime.home.absolutePath}\n\n${probe.details}\n\nThis probe maps native libraries and checks JLI_Launch. It does not start Minecraft.")
                }.onFailure { error ->
                    updateState { copy(engineStatus = "JLI probe failed: ${error.message ?: "unknown error"}") }
                    showMessage("JVM bridge probe failed", error.message ?: "Could not inspect the installed runtime.")
                }
            }
        }
    }

    private fun runJliRuntimeSmokeTest() {
        if (jvmSmokeReceiver != null) return
        updateState { copy(busyAction = "smoke", engineStatus = "Starting a disposable process for Java -version…") }
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                if (intent.action != JliRuntimeSmokeService.ACTION_RESULT) return
                clearJliSmokeReceiver()
                val passed = intent.getBooleanExtra(JliRuntimeSmokeService.EXTRA_PASSED, false)
                val runtime = intent.getStringExtra(JliRuntimeSmokeService.EXTRA_RUNTIME).orEmpty()
                val runtimeHome = intent.getStringExtra(JliRuntimeSmokeService.EXTRA_RUNTIME_HOME).orEmpty()
                val details = intent.getStringExtra(JliRuntimeSmokeService.EXTRA_DETAILS).orEmpty()
                val logPath = intent.getStringExtra(JliRuntimeSmokeService.EXTRA_LOG_PATH).orEmpty()
                updateState {
                    copy(
                        busyAction = null,
                        engineStatus = if (passed) "JVM startup test passed for $runtime. Minecraft rendering is not tested by this check." else "JVM startup test failed for $runtime. See the diagnostic log.",
                    )
                }
                showMessage(if (passed) "Java runtime test passed" else "Java runtime test failed", "Runtime: $runtime\n${if (runtimeHome.isBlank()) "" else "Path: $runtimeHome\n"}\n$details\n\nSaved diagnostic log: $logPath\n\nThe test uses JLI_Launch with -version in a disposable Android process. It does not start Minecraft.")
            }
        }
        jvmSmokeReceiver = receiver
        val filter = IntentFilter(JliRuntimeSmokeService.ACTION_RESULT)
        try {
            if (Build.VERSION.SDK_INT >= 33) registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
            else {
                @Suppress("DEPRECATION")
                registerReceiver(receiver, filter)
            }
        } catch (error: Exception) {
            clearJliSmokeReceiver()
            updateState { copy(busyAction = null, engineStatus = "Could not listen for the JVM diagnostic result.") }
            showMessage("JVM test unavailable", error.message ?: "Could not register diagnostic receiver.")
            return
        }
        val timeout = Runnable {
            clearJliSmokeReceiver()
            updateState { copy(busyAction = null, engineStatus = "JVM test process did not return a result. It may have crashed during initialization.") }
            val diagnostics = runCatching {
                val log = File(LauncherStorage.diagnosticsRoot(applicationContext), "jli-version.log")
                val logText = if (log.isFile) log.readText().takeLast(2500) else ""
                val crashReport = File(cacheDir, "jli-version-work").listFiles { file -> file.name.startsWith("hs_err") }
                    ?.filter { System.currentTimeMillis() - it.lastModified() < 120_000L }
                    ?.maxByOrNull { it.lastModified() }?.readText()?.take(2500).orEmpty()
                buildString { if (logText.isNotBlank()) append("\n\nJVM output:\n").append(logText); if (crashReport.isNotBlank()) append("\n\nCrash report:\n").append(crashReport) }
            }.getOrDefault("")
            showMessage("JVM test timed out", "The separate JVM diagnostic process did not report a result within 45 seconds. Check Android logcat and the diagnostics folder.$diagnostics")
        }
        jvmSmokeTimeout = timeout
        mainHandler.postDelayed(timeout, 45_000L)
        try {
            startService(Intent(this, JliRuntimeSmokeService::class.java))
        } catch (error: Exception) {
            clearJliSmokeReceiver()
            updateState { copy(busyAction = null, engineStatus = "Could not start the isolated JVM diagnostic process.") }
            showMessage("JVM test unavailable", error.message ?: "Could not start the diagnostic service.")
        }
    }

    private fun clearJliSmokeReceiver() {
        jvmSmokeTimeout?.let(mainHandler::removeCallbacks)
        jvmSmokeTimeout = null
        jvmSmokeReceiver?.let { runCatching { unregisterReceiver(it) } }
        jvmSmokeReceiver = null
    }

    private fun onPlayPressed() {
        val selected = selectedInstalledVersion()
        if (selected == null) {
            showDialog(
                LauncherDialog(
                    title = "No installed version",
                    message = "Open Versions, choose the Vanilla / Release, Snapshot, Old Beta, or Old Alpha channel, and install a version first.",
                    confirmText = "Open Versions",
                    onConfirm = { updateState { copy(tab = LauncherTab.VERSIONS, filter = VersionFilter.RELEASE) } },
                    dismissText = "Cancel",
                ),
            )
            return
        }
        val session = MinecraftLaunchSessionStore.current()
        if (session == null) {
            updateState { copy(accountStep = AccountStep.PICK) }
            return
        }
        val sessionError = session.validationError()
        if (sessionError != null) {
            MinecraftLaunchSessionStore.clear()
            updateAccountStatus()
            showMessage("Minecraft account profile invalid", sessionError)
            return
        }
        startActivity(Intent(this, PojavGameActivity::class.java).apply {
            putExtra(PojavGameActivity.EXTRA_VERSION_ID, selected.id)
            putExtra(PojavGameActivity.EXTRA_VERSION_TYPE, selected.type)
            putExtra(PojavGameActivity.EXTRA_VERSION_URL, selected.url)
            putExtra(PojavGameActivity.EXTRA_RELEASE_TIME, selected.releaseTime)
        })
    }

    private fun accountStatusSummary(): String {
        val session = MinecraftLaunchSessionStore.current()
        val problem = session?.validationError()
        return when {
            session == null -> "No account selected. Choose Microsoft or local/offline account."
            problem != null -> "Account profile is invalid: $problem"
            session.accountType == MinecraftAccountType.LOCAL -> "Using ${session.username} · Local/offline account. Online-mode servers require Microsoft authentication."
            else -> "Signed in as ${session.username} · Microsoft account. Session tokens remain in memory only."
        }
    }

    private fun updateAccountStatus() {
        val session = MinecraftLaunchSessionStore.current()
        if (session?.validationError() != null) MinecraftLaunchSessionStore.clear()
        updateState { copy(accountStatus = accountStatusSummary()) }
    }

    private fun activateAccount(name: String) {
        runCatching { LocalAccountRepository.activate(this, name) }
            .onFailure { error -> showMessage("Akun tidak bisa dipilih", error.message ?: "Coba lagi.") }
        updateState { copy(accountStep = AccountStep.NONE) }
        updateAccountStatus()
        refreshAccountState()
    }

    private fun removeAccount(name: String) {
        LocalAccountRepository.remove(this, name)
        updateAccountStatus()
        refreshAccountState()
    }

    private fun addOfflineAccount(name: String, model: SkinModel) {
        runCatching { LocalAccountRepository.add(this, name) }
            .onSuccess { profile ->
                LocalAccountRepository.setSkinModel(this, profile.username, model)
                updateState { copy(accountStep = AccountStep.NONE) }
                updateAccountStatus()
                refreshAccountState()
            }
            .onFailure { error -> showMessage("Akun belum disimpan", error.message ?: "Nama tidak valid.") }
    }

    /** Rebuilds the account list, the active identity and its default skin texture for the UI. */
    private fun refreshAccountState() {
        val session = MinecraftLaunchSessionStore.current()
        val localName = session?.takeIf { it.accountType == MinecraftAccountType.LOCAL }?.username
        val model = localName?.let { LocalAccountRepository.skinModel(this, it) } ?: SkinModel.STEVE
        val savedNames = LocalAccountRepository.list(this).map { it.username }
        val microsoftName = session?.takeIf { it.accountType == MinecraftAccountType.MICROSOFT }?.username
        updateState {
            copy(
                accounts = savedNames,
                activeAccount = session?.username,
                microsoftAccount = microsoftName,
                skinModel = model,
                accountStatus = accountStatusSummary(),
            )
        }
        ioExecutor.execute {
            val image = runCatching { SkinProvider.load(applicationContext, model) }.getOrNull()?.asImageBitmap()
            mainHandler.post {
                if (!isFinishing && !isDestroyed) updateState { copy(skin = image) }
            }
        }
    }

    private fun setThemeMode(mode: ThemeMode) {
        getSharedPreferences(PREFS_SETTINGS, MODE_PRIVATE).edit().putString(ThemeSettings.KEY_THEME_MODE, mode.name).apply()
        updateState { copy(themeMode = mode) }
    }

    // design.md §7.8: enabling Developer mode shows a warning first; disabling is immediate.
    private fun requestDeveloperMode(enabled: Boolean) {
        if (!enabled) {
            setDeveloperMode(false)
            return
        }
        showDialog(
            LauncherDialog(
                title = "Aktifkan Mode Developer?",
                message = "Opsi ini untuk pengguna tingkat lanjut. Salah mengatur bisa membuat game crash.",
                confirmText = "Aktifkan",
                onConfirm = { setDeveloperMode(true) },
                dismissText = "Batal",
            )
        )
    }

    private fun setDeveloperMode(enabled: Boolean) {
        getSharedPreferences(PREFS_SETTINGS, MODE_PRIVATE).edit().putBoolean(KEY_DEVELOPER_MODE, enabled).apply()
        updateState { copy(developerMode = enabled) }
    }

    private fun installJava(major: Int) {
        updateState { copy(busyAction = "runtime", transferDone = 0L, transferTotal = 0L, runtimeStatus = "Menyiapkan Java $major…") }
        ioExecutor.execute {
            val result = runCatching {
                RuntimeManager(applicationContext).installMajor(
                    major = major,
                    onBytes = { done, total ->
                        mainHandler.post { if (!isFinishing && !isDestroyed) updateState { copy(transferDone = done, transferTotal = total) } }
                    },
                ) { message ->
                    mainHandler.post { if (!isFinishing && !isDestroyed) updateState { copy(runtimeStatus = message) } }
                }
            }
            mainHandler.post {
                if (isFinishing || isDestroyed) return@post
                updateState { copy(busyAction = null, transferDone = 0L, transferTotal = 0L) }
                updateRuntimeStatus()
                result.onFailure { error ->
                    showMessage("Install Java gagal", error.message ?: "Periksa koneksi internet dan ruang penyimpanan.")
                }
            }
        }
    }

    private fun setKeepScreenOn(enabled: Boolean) {
        getSharedPreferences(PREFS_SETTINGS, MODE_PRIVATE).edit().putBoolean(KEY_KEEP_SCREEN_ON, enabled).apply()
        applyKeepScreenOn(enabled)
        updateState { copy(keepScreenOn = enabled) }
    }

    private fun applyKeepScreenOn(enabled: Boolean) {
        if (enabled) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        else window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    private companion object {
        const val KEY_SELECTED_VERSION = "selected_minecraft_version"
        const val KEY_DEVELOPER_MODE = "developer_mode"
        const val KEY_KEEP_SCREEN_ON = "keep_screen_on"
        const val PREFS_SETTINGS = "sally_launcher_settings"
    }
}
