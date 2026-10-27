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
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.mutableStateOf
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
import com.sally.launcher.data.runtime.RuntimeManager
import com.sally.launcher.data.storage.LauncherStorage
import com.sally.launcher.data.version.VersionManifestRepository
import org.json.JSONObject
import java.io.File
import java.util.concurrent.Executors

/**
 * Two screens only: Home (account + version dropdowns, Play) and Settings.
 * Play installs whatever is missing (version files, Java runtime, engine payload) and then starts the game.
 */
class MainActivity : AppCompatActivity() {
    private val ioExecutor = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())
    private lateinit var repository: VersionManifestRepository
    private val uiState = mutableStateOf(LauncherUiState())
    private var catalogVersions: List<MinecraftVersion> = emptyList()
    private var smokeReceiver: BroadcastReceiver? = null
    private var smokeTimeout: Runnable? = null

    private val settingsPrefs by lazy { getSharedPreferences(PREFS_SETTINGS, MODE_PRIVATE) }

    private val actions by lazy {
        LauncherActions(
            navigate = { screen -> updateState { copy(screen = screen) } },
            play = ::onPlay,
            selectVersion = ::selectVersion,
            refreshCatalog = ::loadCatalog,
            openAccounts = { open -> updateState { copy(accountDialog = open) } },
            selectAccount = ::selectAccount,
            addOfflineAccount = ::addOfflineAccount,
            removeAccount = ::removeAccount,
            signInMicrosoft = {
                updateState { copy(accountDialog = false) }
                startActivity(Intent(this, MicrosoftLoginActivity::class.java))
            },
            repairGame = ::confirmRepairGame,
            reinstallJava = ::confirmReinstallJava,
            setThemeMode = { mode -> saveSetting { putString(ThemeSettings.KEY_THEME_MODE, mode.name) }; updateState { copy(themeMode = mode) } },
            setAmoledBlack = { on -> saveSetting { putBoolean(ThemeSettings.KEY_AMOLED, on) }; updateState { copy(amoledBlack = on) } },
            setDynamicColor = { on -> saveSetting { putBoolean(ThemeSettings.KEY_DYNAMIC_COLOR, on) }; updateState { copy(dynamicColor = on) } },
            setKeepScreenOn = ::setKeepScreenOn,
            requestDeveloperMode = ::requestDeveloperMode,
            devPrepare = ::devPrepareProfile,
            devProbe = ::devProbeJvm,
            devJavaTest = ::devJavaSelfTest,
            devGraphics = { startActivity(Intent(this, EngineDiagnosticsActivity::class.java)) },
            devPojav = { startActivity(Intent(this, PojavEngineDiagnosticsActivity::class.java)) },
            dismissDialog = { updateState { copy(dialog = null) } },
        )
    }

    // ---------------------------------------------------------------- lifecycle

    override fun onCreate(savedInstanceState: Bundle?) {
        // Leave the splash (launch) theme; the system splash/windowBackground has done its job.
        setTheme(R.style.Theme_SallyLauncher)
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        hideSystemBars()
        repository = VersionManifestRepository(applicationContext)
        LocalAccountRepository.restoreSelectedSession(this)

        val themeSettings = ThemeSettings.read(this)
        val keepOn = settingsPrefs.getBoolean(KEY_KEEP_SCREEN_ON, false)
        updateState {
            copy(
                selectedVersionId = getPreferences(MODE_PRIVATE).getString(KEY_SELECTED_VERSION, "").orEmpty(),
                themeMode = themeSettings.mode,
                amoledBlack = themeSettings.amoled,
                dynamicColor = themeSettings.dynamicColor,
                keepScreenOn = keepOn,
                developerMode = settingsPrefs.getBoolean(KEY_DEVELOPER_MODE, false),
            )
        }
        applyKeepScreenOn(keepOn)
        refreshAccounts()
        setContent { SallyLauncherRoot(state = uiState.value, actions = actions) }
        loadCatalog()
    }

    override fun onResume() {
        super.onResume()
        hideSystemBars()
        LocalAccountRepository.restoreSelectedSession(this)
        refreshAccounts()
        refreshInstalled()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemBars()
    }

    private fun hideSystemBars() {
        WindowInsetsControllerCompat(window, window.decorView).apply {
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            hide(WindowInsetsCompat.Type.systemBars())
        }
    }

    override fun onDestroy() {
        clearSmokeReceiver()
        ioExecutor.shutdown()
        super.onDestroy()
    }

    // ---------------------------------------------------------------- helpers

    private inline fun updateState(reducer: LauncherUiState.() -> LauncherUiState) {
        uiState.value = uiState.value.reducer()
    }

    private fun alive() = !isFinishing && !isDestroyed

    private fun postUi(block: () -> Unit) {
        mainHandler.post { if (alive()) block() }
    }

    private fun postStatus(message: String) {
        postUi { updateState { copy(status = message) } }
    }

    private fun showMessage(title: String, message: String, confirm: String = "OK", dismiss: String? = null, onConfirm: (() -> Unit)? = null) {
        updateState { copy(dialog = LauncherDialog(title, message, confirm, onConfirm, dismiss)) }
    }

    private inline fun saveSetting(edit: android.content.SharedPreferences.Editor.() -> Unit) {
        settingsPrefs.edit().apply(edit).apply()
    }

    private fun versionDir(id: String) = File(File(LauncherStorage.minecraftRoot(applicationContext), "versions"), id)

    // ---------------------------------------------------------------- accounts

    private fun refreshAccounts() {
        val session = MinecraftLaunchSessionStore.current()
        val valid = session?.takeIf { it.validationError() == null }
        if (session != null && valid == null) MinecraftLaunchSessionStore.clear()
        val active = valid?.let { AccountItem(it.username, it.accountType == MinecraftAccountType.MICROSOFT) }
        val locals = LocalAccountRepository.list(this).map { AccountItem(it.username, false) }
        val list = buildList {
            if (active != null && active.microsoft) add(active)
            addAll(locals)
        }
        updateState { copy(accounts = list, activeAccount = active) }
    }

    private fun selectAccount(account: AccountItem) {
        if (!account.microsoft) {
            runCatching { LocalAccountRepository.activate(this, account.name) }
                .onFailure { showMessage("Akun", it.message ?: "Tidak bisa memakai akun ini.") }
        }
        refreshAccounts()
        updateState { copy(accountDialog = false) }
    }

    private fun addOfflineAccount(name: String): String? {
        if (!Regex("[A-Za-z0-9_]{3,16}").matches(name.trim())) return "Username harus 3–16 karakter: huruf, angka, atau _"
        return try {
            LocalAccountRepository.add(this, name)
            refreshAccounts()
            updateState { copy(accountDialog = false) }
            null
        } catch (error: IllegalArgumentException) {
            error.message ?: "Username tidak valid."
        }
    }

    private fun removeAccount(account: AccountItem) {
        LocalAccountRepository.remove(this, account.name)
        refreshAccounts()
    }

    // ---------------------------------------------------------------- versions

    private fun loadCatalog() {
        updateState { copy(loadingCatalog = true) }
        ioExecutor.execute {
            val result = runCatching { repository.fetchCatalog() }
            val official = result.getOrNull()?.versions ?: catalogVersions
            val installed = scanInstalledVersions(official)
            postUi {
                result.onSuccess { catalogVersions = it.versions }
                val merged = mergeVersions(official, installed)
                val ids = installed.map { it.id }.toSet()
                val persisted = getPreferences(MODE_PRIVATE).getString(KEY_SELECTED_VERSION, "").orEmpty()
                val chosen = listOfNotNull(
                    persisted.takeIf { id -> merged.any { it.id == id } },
                    installed.firstOrNull()?.id,
                    result.getOrNull()?.latestReleaseId?.takeIf { id -> merged.any { it.id == id } },
                    merged.firstOrNull()?.id,
                ).firstOrNull().orEmpty()
                updateState {
                    copy(
                        loadingCatalog = false,
                        versions = merged,
                        installedIds = ids,
                        selectedVersionId = chosen,
                        status = when {
                            busy -> status
                            result.isFailure && merged.isEmpty() -> "Tidak bisa memuat daftar versi. Periksa koneksi lalu segarkan."
                            result.isFailure -> "Offline · memakai daftar tersimpan"
                            else -> ""
                        },
                    )
                }
                updateJavaMajors()
            }
        }
    }

    private fun refreshInstalled() {
        ioExecutor.execute {
            val installed = scanInstalledVersions(catalogVersions)
            postUi {
                updateState { copy(installedIds = installed.map { it.id }.toSet(), versions = mergeVersions(catalogVersions, installed)) }
                updateJavaMajors()
            }
        }
    }

    private fun updateJavaMajors() {
        ioExecutor.execute {
            val majors = runCatching { RuntimeManager(applicationContext).installedRuntimes().map { it.majorVersion } }.getOrDefault(emptyList())
            postUi { updateState { copy(javaMajors = majors) } }
        }
    }

    private fun mergeVersions(catalog: List<MinecraftVersion>, installed: List<MinecraftVersion>): List<MinecraftVersion> {
        val byId = LinkedHashMap<String, MinecraftVersion>()
        catalog.forEach { byId[it.id] = it }
        installed.forEach { byId.putIfAbsent(it.id, it) }
        return byId.values.toList()
    }

    private fun scanInstalledVersions(known: List<MinecraftVersion>): List<MinecraftVersion> {
        val versionsDir = File(LauncherStorage.minecraftRoot(applicationContext), "versions")
        if (!versionsDir.isDirectory) return emptyList()
        val byId = known.associateBy { it.id }
        val found = versionsDir.listFiles().orEmpty().mapNotNull { dir ->
            if (!dir.isDirectory) return@mapNotNull null
            val id = dir.name
            val json = File(dir, "$id.json")
            val jar = File(dir, "$id.jar")
            if (!json.isFile || json.length() <= 0L || !jar.isFile || jar.length() <= 0L) return@mapNotNull null
            val meta = runCatching { JSONObject(json.readText(Charsets.UTF_8)) }.getOrNull() ?: return@mapNotNull null
            if (meta.optString("id").let { it.isNotBlank() && it != id }) return@mapNotNull null
            val official = byId[id]
            MinecraftVersion(
                id = id,
                type = official?.type ?: meta.optString("type", "release").ifBlank { "release" },
                url = official?.url.orEmpty(),
                releaseTime = official?.releaseTime ?: meta.optString("releaseTime", meta.optString("time", "")),
            )
        }
        val order = known.mapIndexed { index, version -> version.id to index }.toMap()
        return found.sortedWith(compareBy<MinecraftVersion> { order[it.id] ?: Int.MAX_VALUE }.thenByDescending { it.releaseTime })
    }

    private fun selectVersion(id: String) {
        getPreferences(MODE_PRIVATE).edit().putString(KEY_SELECTED_VERSION, id).apply()
        updateState { copy(selectedVersionId = id) }
    }

    private fun selectedVersion(): MinecraftVersion? = uiState.value.let { s -> s.versions.firstOrNull { it.id == s.selectedVersionId } }

    // ---------------------------------------------------------------- play / repair pipeline

    private fun onPlay() {
        val state = uiState.value
        if (state.busy) return
        val version = selectedVersion() ?: return showMessage("Pilih versi", "Pilih versi Minecraft dulu.")
        val session = MinecraftLaunchSessionStore.current()
        if (session == null) {
            updateState { copy(accountDialog = true) }
            return
        }
        val problem = session.validationError()
        if (problem != null) {
            MinecraftLaunchSessionStore.clear()
            refreshAccounts()
            return showMessage("Akun bermasalah", problem)
        }
        runPipeline(version, forceFiles = false, launchAfter = true)
    }

    /** Install missing version files, Java, and engine payload; optionally start the game when done. */
    private fun runPipeline(version: MinecraftVersion, forceFiles: Boolean, forceJava: Boolean = false, launchAfter: Boolean) {
        updateState { copy(busy = true, status = "Menyiapkan…") }
        ioExecutor.execute {
            val result = runCatching {
                val marker = File(versionDir(version.id), COMPLETE_MARKER)
                if (forceFiles || !marker.isFile) {
                    val usable = !forceFiles && version.id in uiState.value.installedIds
                    try {
                        require(version.url.startsWith("https://")) { "Alamat unduhan versi tidak tersedia. Segarkan daftar versi." }
                        VersionInstaller(applicationContext).installVersion(
                            version = version,
                            onProgress = ::postStatus,
                            forceRedownload = forceFiles,
                        )
                        marker.writeText("ok")
                    } catch (error: Exception) {
                        // Files from an older install are still usable when the network is unavailable.
                        if (!usable) throw error
                    }
                }
                postStatus("Menyiapkan Java…")
                RuntimeManager(applicationContext).installForVersion(
                    version = version,
                    forceRedownload = forceJava,
                    resumeRedownload = false,
                    onProgress = ::postStatus,
                )
                postStatus("Menyiapkan mesin game…")
                val root = LauncherStorage.minecraftRoot(applicationContext)
                PojavEnginePayloadInstaller(applicationContext).apply {
                    installCaciocavalloComponents(root)
                    installPatchedGlfwClasses(root)
                }
            }
            val installed = scanInstalledVersions(catalogVersions)
            postUi {
                updateState {
                    copy(
                        busy = false,
                        status = "",
                        installedIds = installed.map { it.id }.toSet(),
                        versions = mergeVersions(catalogVersions, installed),
                    )
                }
                updateJavaMajors()
                result.onSuccess {
                    if (launchAfter) launchGame(version) else showMessage("Selesai", "Minecraft ${version.id} siap dimainkan.")
                }.onFailure { error ->
                    updateState { copy(status = "Gagal menyiapkan ${version.id}") }
                    showMessage(
                        "Gagal menyiapkan ${version.id}",
                        (error.message ?: error.javaClass.simpleName) + "\n\nPeriksa koneksi internet dan ruang penyimpanan, lalu coba lagi.",
                        confirm = "Coba lagi",
                        dismiss = "Tutup",
                        onConfirm = { runPipeline(version, forceFiles, forceJava, launchAfter) },
                    )
                }
            }
        }
    }

    private fun launchGame(version: MinecraftVersion) {
        startActivity(Intent(this, PojavGameActivity::class.java).apply {
            putExtra(PojavGameActivity.EXTRA_VERSION_ID, version.id)
            putExtra(PojavGameActivity.EXTRA_VERSION_TYPE, version.type)
            putExtra(PojavGameActivity.EXTRA_VERSION_URL, version.url)
            putExtra(PojavGameActivity.EXTRA_RELEASE_TIME, version.releaseTime)
        })
    }

    private fun confirmRepairGame() {
        val version = selectedVersion() ?: return
        showMessage(
            "Perbaiki ${version.id}?",
            "File game akan diunduh ulang dan diverifikasi. Ini bisa memakai banyak data seluler.",
            confirm = "Perbaiki",
            dismiss = "Batal",
        ) { runPipeline(version, forceFiles = true, launchAfter = false) }
    }

    private fun confirmReinstallJava() {
        val version = selectedVersion() ?: return
        showMessage(
            "Pasang ulang Java?",
            "Runtime Java yang cocok untuk ${version.id} akan diunduh dan diverifikasi ulang.",
            confirm = "Pasang ulang",
            dismiss = "Batal",
        ) { runPipeline(version, forceFiles = false, forceJava = true, launchAfter = false) }
    }

    // ---------------------------------------------------------------- settings

    private fun setKeepScreenOn(enabled: Boolean) {
        saveSetting { putBoolean(KEY_KEEP_SCREEN_ON, enabled) }
        applyKeepScreenOn(enabled)
        updateState { copy(keepScreenOn = enabled) }
    }

    private fun applyKeepScreenOn(enabled: Boolean) {
        if (enabled) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        else window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    private fun requestDeveloperMode(enabled: Boolean) {
        if (!enabled) return setDeveloperMode(false)
        showMessage(
            "Aktifkan mode developer?",
            "Untuk pengguna tingkat lanjut. Salah mengatur bisa membuat game crash.",
            confirm = "Aktifkan",
            dismiss = "Batal",
        ) { setDeveloperMode(true) }
    }

    private fun setDeveloperMode(enabled: Boolean) {
        saveSetting { putBoolean(KEY_DEVELOPER_MODE, enabled) }
        updateState { copy(developerMode = enabled) }
    }

    // ---------------------------------------------------------------- developer tools

    private fun devPrepareProfile() {
        val version = selectedVersion() ?: return showMessage("Developer", "Pilih versi dulu.")
        updateState { copy(busy = true, engineStatus = "Memeriksa profil launch…") }
        ioExecutor.execute {
            val result = runCatching {
                val root = LauncherStorage.minecraftRoot(applicationContext)
                PojavEnginePayloadInstaller(applicationContext).apply {
                    installCaciocavalloComponents(root)
                    installPatchedGlfwClasses(root)
                }
                val profile = LaunchProfileBuilder(applicationContext).prepare(version, session = MinecraftLaunchSessionStore.current())
                profile to JliLaunchSpecBuilder(applicationContext).build(profile)
            }
            postUi {
                updateState { copy(busy = false) }
                result.onSuccess { (profile, spec) ->
                    updateState { copy(engineStatus = "Profil ${profile.versionId} · ${spec.blockers.size} penghalang") }
                    showMessage(
                        "Profil launch",
                        "Minecraft ${profile.versionId}\nJava dibutuhkan: ${profile.requiredJavaMajor}\nRuntime: Java ${profile.runtimeMajor}\n" +
                            "Main class: ${profile.mainClass}\nClasspath: ${profile.classpathEntries.size}\nJLI argv: ${spec.argv.size}\n" +
                            "Folder kerja: ${spec.workingDirectory.absolutePath}\nAkun valid: ${profile.hasValidAccountSession}\n\n" +
                            "Penghalang:\n" + spec.blockers.joinToString("\n") { "• $it" }.ifBlank { "• Tidak ada" },
                    )
                }.onFailure { error ->
                    updateState { copy(engineStatus = "Profil gagal: ${error.message}") }
                    showMessage("Profil gagal", error.message ?: "File versi belum lengkap.")
                }
            }
        }
    }

    private fun devProbeJvm() {
        updateState { copy(busy = true, engineStatus = "Memeriksa loader JVM…") }
        ioExecutor.execute {
            val result = runCatching {
                val runtime = RuntimeManager(applicationContext).installedRuntimes().maxByOrNull { it.majorVersion }
                    ?: error("Belum ada runtime Java. Tekan Main sekali untuk memasangnya.")
                runtime to JliBridgeDiagnostics.probe(runtime.home)
            }
            postUi {
                updateState { copy(busy = false) }
                result.onSuccess { (runtime, probe) ->
                    updateState { copy(engineStatus = if (probe.available) "Loader JVM siap (${runtime.displayName()})" else "Loader JVM belum siap") }
                    showMessage(if (probe.available) "Loader JVM siap" else "Loader JVM belum siap", "${runtime.displayName()}\n${runtime.home.absolutePath}\n\n${probe.details}")
                }.onFailure { error ->
                    updateState { copy(engineStatus = "Probe gagal") }
                    showMessage("Probe gagal", error.message ?: "Tidak bisa memeriksa runtime.")
                }
            }
        }
    }

    private fun devJavaSelfTest() {
        if (smokeReceiver != null) return
        updateState { copy(busy = true, engineStatus = "Menjalankan java -version…") }
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                if (intent.action != JliRuntimeSmokeService.ACTION_RESULT) return
                clearSmokeReceiver()
                val passed = intent.getBooleanExtra(JliRuntimeSmokeService.EXTRA_PASSED, false)
                val runtime = intent.getStringExtra(JliRuntimeSmokeService.EXTRA_RUNTIME).orEmpty()
                val details = intent.getStringExtra(JliRuntimeSmokeService.EXTRA_DETAILS).orEmpty()
                val log = intent.getStringExtra(JliRuntimeSmokeService.EXTRA_LOG_PATH).orEmpty()
                updateState { copy(busy = false, engineStatus = if (passed) "Tes Java lulus ($runtime)" else "Tes Java gagal ($runtime)") }
                showMessage(if (passed) "Tes Java lulus" else "Tes Java gagal", "$runtime\n\n$details\n\nLog: $log")
            }
        }
        smokeReceiver = receiver
        val filter = IntentFilter(JliRuntimeSmokeService.ACTION_RESULT)
        try {
            if (Build.VERSION.SDK_INT >= 33) registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
            else {
                @Suppress("DEPRECATION")
                registerReceiver(receiver, filter)
            }
            val timeout = Runnable {
                clearSmokeReceiver()
                updateState { copy(busy = false, engineStatus = "Tes Java tidak merespons") }
                showMessage("Tes Java timeout", "Tidak ada hasil dalam 45 detik. Periksa folder diagnostik dan logcat.")
            }
            smokeTimeout = timeout
            mainHandler.postDelayed(timeout, 45_000L)
            startService(Intent(this, JliRuntimeSmokeService::class.java))
        } catch (error: Exception) {
            clearSmokeReceiver()
            updateState { copy(busy = false, engineStatus = "Tes Java tidak tersedia") }
            showMessage("Tes Java tidak tersedia", error.message ?: "Tidak bisa memulai layanan diagnostik.")
        }
    }

    private fun clearSmokeReceiver() {
        smokeTimeout?.let(mainHandler::removeCallbacks)
        smokeTimeout = null
        smokeReceiver?.let { runCatching { unregisterReceiver(it) } }
        smokeReceiver = null
    }

    private companion object {
        const val KEY_SELECTED_VERSION = "selected_minecraft_version"
        const val KEY_DEVELOPER_MODE = "developer_mode"
        const val KEY_KEEP_SCREEN_ON = "keep_screen_on"
        const val PREFS_SETTINGS = "sally_launcher_settings"
        const val COMPLETE_MARKER = ".sally-complete"
    }
}
