package com.sally.launcher

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.TypedValue
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.MaterialAutoCompleteTextView
import com.google.android.material.textfield.TextInputLayout
import com.sally.launcher.core.engine.LaunchProfileBuilder
import com.sally.launcher.core.engine.JliBridgeDiagnostics
import com.sally.launcher.core.engine.JliRuntimeSmokeService
import com.sally.launcher.core.engine.JliLaunchSpecBuilder
import com.sally.launcher.core.engine.MinecraftLaunchSessionStore
import com.sally.launcher.core.auth.MicrosoftLoginActivity
import com.sally.launcher.core.auth.LocalAccountActivity
import com.sally.launcher.core.auth.LocalAccountRepository
import com.sally.launcher.core.engine.MinecraftAccountType
import com.sally.launcher.core.engine.PojavGameActivity
import com.sally.launcher.core.engine.PojavEnginePayloadInstaller
import com.sally.launcher.data.model.MinecraftVersion
import com.sally.launcher.data.storage.LauncherStorage
import com.sally.launcher.data.install.VersionInstaller
import com.sally.launcher.data.runtime.RuntimeManager
import com.sally.launcher.data.model.VersionCatalog
import com.sally.launcher.data.version.VersionManifestRepository
import java.io.File
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {
    private val ioExecutor = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())
    private lateinit var repository: VersionManifestRepository
    private lateinit var versionInput: MaterialAutoCompleteTextView
    private lateinit var statusText: TextView
    private lateinit var refreshButton: MaterialButton
    private lateinit var playButton: MaterialButton
    private lateinit var installButton: MaterialButton
    private lateinit var runtimeButton: MaterialButton
    private lateinit var runtimeStatusText: TextView
    private lateinit var prepareLaunchButton: MaterialButton
    private lateinit var engineDiagnosticsButton: MaterialButton
    private lateinit var pojavBridgeDiagnosticsButton: MaterialButton
    private lateinit var jvmBridgeButton: MaterialButton
    private lateinit var jvmSmokeButton: MaterialButton
    private lateinit var engineStatusText: TextView
    private lateinit var accountStatusText: TextView
    private lateinit var signInButton: MaterialButton
    private lateinit var localAccountButton: MaterialButton
    private var jvmSmokeReceiver: BroadcastReceiver? = null
    private var jvmSmokeTimeout: Runnable? = null
    private lateinit var versions: List<MinecraftVersion>
    private var selectedVersionId: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        repository = VersionManifestRepository(applicationContext)
        LocalAccountRepository.restoreSelectedSession(this)
        selectedVersionId = getPreferences(MODE_PRIVATE).getString(KEY_SELECTED_VERSION, "").orEmpty()
        setContentView(createContentView())
        updateRuntimeStatus()
        updateAccountStatus()
        loadVersionCatalog()
    }

    override fun onResume() {
        super.onResume()
        if (::accountStatusText.isInitialized) updateAccountStatus()
    }

    override fun onDestroy() {
        clearJliSmokeReceiver()
        ioExecutor.shutdownNow()
        super.onDestroy()
    }

    private fun createContentView(): View {
        val scroll = ScrollView(this).apply {
            isFillViewport = true
            clipToPadding = false
            setPadding(dp(20), dp(22), dp(20), dp(28))
        }
        val page = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        }
        scroll.addView(page)

        page.addView(textView("SALLY LAUNCHER", 12f, bold = true, color = color(com.google.android.material.R.attr.colorPrimary)).apply {
            letterSpacing = 0.14f
        })
        addGap(page, 10)
        page.addView(textView("Your Java world.", 32f, bold = true))
        addGap(page, 8)
        page.addView(textView("A clean starting point for Minecraft: Java Edition.", 15f, color = color(com.google.android.material.R.attr.colorOnSurfaceVariant)))
        addGap(page, 26)

        val versionCard = card()
        val versionContent = cardContent(versionCard)
        versionContent.addView(sectionLabel("VERSION CATALOG"))
        addGap(versionContent, 8)
        versionContent.addView(textView("Choose a Minecraft version", 20f, bold = true))
        addGap(versionContent, 6)
        versionContent.addView(textView("Browse official releases and snapshots.", 14f, color = color(com.google.android.material.R.attr.colorOnSurfaceVariant)))
        addGap(versionContent, 18)

        val textInputLayout = TextInputLayout(this).apply {
            hint = "Minecraft version"
            boxBackgroundMode = TextInputLayout.BOX_BACKGROUND_OUTLINE
            endIconMode = TextInputLayout.END_ICON_DROPDOWN_MENU
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        }
        versionInput = MaterialAutoCompleteTextView(this).apply {
            threshold = 0
            keyListener = null
            setTextColor(color(com.google.android.material.R.attr.colorOnSurface))
            setOnClickListener { showDropDown() }
            setOnFocusChangeListener { _, hasFocus -> if (hasFocus && adapter != null && adapter!!.count > 0) showDropDown() }
            setText(selectedVersionId.ifBlank { "Loading version catalog…" }, false)
        }
        textInputLayout.addView(versionInput)
        versionContent.addView(textInputLayout)
        versionInput.setOnItemClickListener(AdapterView.OnItemClickListener { _, _, position, _ ->
            versions.getOrNull(position)?.let { selectVersion(it) }
        })

        addGap(versionContent, 14)
        val actionRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
        }
        refreshButton = MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
            text = "Refresh"
            isAllCaps = false
            contentDescription = "Refresh official Minecraft versions"
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginEnd = dp(8)
            }
            setOnClickListener { loadVersionCatalog() }
        }
        playButton = MaterialButton(this).apply {
            text = "Play"
            isAllCaps = false
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            setOnClickListener { onPlayPressed() }
        }
        actionRow.addView(refreshButton)
        actionRow.addView(playButton)
        versionContent.addView(actionRow)
        addGap(versionContent, 8)
        installButton = MaterialButton(this).apply {
            text = "Install version files"
            isAllCaps = false
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            setOnClickListener { installSelectedVersion() }
        }
        versionContent.addView(installButton)
        addGap(versionContent, 12)
        statusText = textView("Loading official version catalog…", 13f, color = color(com.google.android.material.R.attr.colorOnSurfaceVariant))
        versionContent.addView(statusText)
        page.addView(versionCard, fullWidth())

        addGap(page, 18)
        val engineCard = card()
        val engineContent = cardContent(engineCard)
        engineContent.addView(sectionLabel("ENGINE STATUS"))
        addGap(engineContent, 8)
        engineContent.addView(textView("NATIVE ENGINE · Pojav / JLI", 19f, bold = true))
        addGap(engineContent, 6)
        engineContent.addView(textView(
            "The Pojav-derived Android GLFW/LWJGL bridge and JNI JLI launch adapter are bundled. Use Microsoft sign-in for authenticated online play, or create a local/offline profile for single-player and servers configured to allow offline identities.",
            14f,
            color = color(com.google.android.material.R.attr.colorOnSurfaceVariant),
        ))
        addGap(engineContent, 10)
        accountStatusText = textView(
            "Checking active account…",
            13f,
            color = color(com.google.android.material.R.attr.colorOnSurfaceVariant),
        )
        engineContent.addView(accountStatusText)
        addGap(engineContent, 8)
        signInButton = MaterialButton(this).apply {
            text = "Sign in with Microsoft"
            isAllCaps = false
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            setOnClickListener { startActivity(Intent(this@MainActivity, MicrosoftLoginActivity::class.java)) }
        }
        engineContent.addView(signInButton)
        addGap(engineContent, 8)
        localAccountButton = MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
            text = "Manage local accounts"
            isAllCaps = false
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            setOnClickListener { startActivity(Intent(this@MainActivity, LocalAccountActivity::class.java)) }
        }
        engineContent.addView(localAccountButton)
        addGap(engineContent, 12)
        prepareLaunchButton = MaterialButton(this).apply {
            text = "Prepare launch profile"
            isAllCaps = false
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            setOnClickListener { prepareLaunchProfile() }
        }
        engineContent.addView(prepareLaunchButton)
        addGap(engineContent, 8)
        jvmBridgeButton = MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
            text = "Probe installed JVM bridge"
            isAllCaps = false
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            setOnClickListener { probeJvmBridge() }
        }
        engineContent.addView(jvmBridgeButton)
        addGap(engineContent, 8)
        jvmSmokeButton = MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
            text = "Run Java runtime self-test"
            isAllCaps = false
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            setOnClickListener { runJliRuntimeSmokeTest() }
        }
        engineContent.addView(jvmSmokeButton)
        addGap(engineContent, 8)
        engineDiagnosticsButton = MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
            text = "Test graphics & input bridge"
            isAllCaps = false
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            setOnClickListener {
                startActivity(Intent(this@MainActivity, com.sally.launcher.core.engine.EngineDiagnosticsActivity::class.java))
            }
        }
        engineContent.addView(engineDiagnosticsButton)
        addGap(engineContent, 8)
        pojavBridgeDiagnosticsButton = MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
            text = "Test Pojav surface bridge"
            isAllCaps = false
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            setOnClickListener {
                startActivity(Intent(this@MainActivity, com.sally.launcher.core.engine.PojavEngineDiagnosticsActivity::class.java))
            }
        }
        engineContent.addView(pojavBridgeDiagnosticsButton)
        addGap(engineContent, 8)
        engineStatusText = textView(
            "Profile not prepared. Install version files and a compatible Java runtime first.",
            13f,
            color = color(com.google.android.material.R.attr.colorOnSurfaceVariant),
        )
        engineContent.addView(engineStatusText)
        page.addView(engineCard, fullWidth())

        addGap(page, 18)
        val runtimeCard = card()
        val runtimeContent = cardContent(runtimeCard)
        runtimeContent.addView(sectionLabel("JAVA RUNTIME"))
        addGap(runtimeContent, 8)
        runtimeContent.addView(textView("Install a compatible runtime", 20f, bold = true))
        addGap(runtimeContent, 6)
        runtimeContent.addView(textView(
            "Reads the selected Minecraft version's Java requirement, chooses an Android ABI-matched runtime, verifies its SHA-256 digest, and installs it in app-private storage.",
            14f,
            color = color(com.google.android.material.R.attr.colorOnSurfaceVariant),
        ))
        addGap(runtimeContent, 14)
        runtimeButton = MaterialButton(this).apply {
            text = "Install compatible Java runtime"
            isAllCaps = false
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            setOnClickListener { installRuntimeForSelectedVersion() }
        }
        runtimeContent.addView(runtimeButton)
        addGap(runtimeContent, 10)
        runtimeStatusText = textView("Checking installed runtimes…", 13f, color = color(com.google.android.material.R.attr.colorOnSurfaceVariant))
        runtimeContent.addView(runtimeStatusText)
        page.addView(runtimeCard, fullWidth())

        addGap(page, 18)
        page.addView(textView("STAGE 9  ·  JVM STARTUP SELF-TEST", 11f, bold = true, color = color(com.google.android.material.R.attr.colorOnSurfaceVariant)))
        return scroll
    }

    private fun loadVersionCatalog() {
        refreshButton.isEnabled = false
        refreshButton.text = "Loading…"
        statusText.text = "Connecting to the official version catalog…"
        ioExecutor.execute {
            val result = runCatching { repository.fetchCatalog() }
            mainHandler.post {
                if (isFinishing || isDestroyed) return@post
                refreshButton.isEnabled = true
                refreshButton.text = "Refresh"
                result.onSuccess(::showCatalog).onFailure { error ->
                    statusText.text = "Could not load versions. Check your connection and retry."
                    Toast.makeText(this, error.message ?: "Version catalog unavailable", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun showCatalog(catalog: VersionCatalog) {
        versions = catalog.versions
        val adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, versions.map { it.displayLabel() })
        versionInput.setAdapter(adapter)
        versionInput.setOnClickListener { if (adapter.count > 0) versionInput.showDropDown() }

        val preferred = versions.firstOrNull { it.id == selectedVersionId }
            ?: versions.firstOrNull { it.id == catalog.latestReleaseId }
            ?: versions.firstOrNull()
        if (preferred != null) selectVersion(preferred)

        statusText.text = if (catalog.loadedFromCache) {
            "${versions.size} versions · showing saved catalog (offline)"
        } else {
            "${versions.size} versions available · official catalog updated"
        }
    }

    private fun selectVersion(version: MinecraftVersion) {
        selectedVersionId = version.id
        versionInput.setText(version.displayLabel(), false)
        getPreferences(MODE_PRIVATE).edit().putString(KEY_SELECTED_VERSION, version.id).apply()
    }

    private fun installSelectedVersion() {
        if (!::versions.isInitialized) {
            Toast.makeText(this, "Wait for the version catalog to finish loading.", Toast.LENGTH_SHORT).show()
            return
        }
        val selected = versions.firstOrNull { it.id == selectedVersionId }
        if (selected == null) {
            Toast.makeText(this, "Load and select a Minecraft version first.", Toast.LENGTH_SHORT).show()
            return
        }
        installButton.isEnabled = false
        refreshButton.isEnabled = false
        playButton.isEnabled = false
        runtimeButton.isEnabled = false
        installButton.text = "Installing…"
        statusText.text = "Preparing ${selected.id}…"
        ioExecutor.execute {
            val result = runCatching {
                VersionInstaller(applicationContext).installVersion(selected) { message ->
                    mainHandler.post {
                        if (!isFinishing && !isDestroyed) statusText.text = message
                    }
                }
            }
            mainHandler.post {
                if (isFinishing || isDestroyed) return@post
                installButton.isEnabled = true
                refreshButton.isEnabled = true
                playButton.isEnabled = true
                runtimeButton.isEnabled = true
                installButton.text = "Install version files"
                result.onSuccess { installed ->
                    statusText.text = "${installed.versionId}: ${installed.libraryFiles} library files and ${installed.assetFiles} assets available. SHA-1 is checked when official metadata provides it; install a compatible Java runtime separately, and the game engine is still pending."
                    MaterialAlertDialogBuilder(this)
                        .setTitle("Version files installed")
                        .setMessage("Downloaded the Minecraft client, common Java library artifacts, and asset objects for ${installed.versionId}. SHA-1 is checked wherever official metadata provides a hash. Bytes downloaded this run: ${installed.downloadedBytes / (1024 * 1024)} MB. A separate Android Java runtime installer is available below; the JVM/JNI launch bridge, renderer, and input integration are still pending.")
                        .setPositiveButton("OK", null)
                        .show()
                }.onFailure { error ->
                    statusText.text = "Install failed. Check your connection/storage and retry."
                    MaterialAlertDialogBuilder(this)
                        .setTitle("Download failed")
                        .setMessage(error.message ?: "Could not install Minecraft version files.")
                        .setPositiveButton("OK", null)
                        .show()
                }
            }
        }
    }

    private fun installRuntimeForSelectedVersion() {
        if (!::versions.isInitialized) {
            Toast.makeText(this, "Wait for the version catalog to finish loading.", Toast.LENGTH_SHORT).show()
            return
        }
        val selected = versions.firstOrNull { it.id == selectedVersionId }
        if (selected == null) {
            Toast.makeText(this, "Load and select a Minecraft version first.", Toast.LENGTH_SHORT).show()
            return
        }

        runtimeButton.isEnabled = false
        installButton.isEnabled = false
        refreshButton.isEnabled = false
        playButton.isEnabled = false
        runtimeButton.text = "Installing runtime…"
        runtimeStatusText.text = "Preparing a compatible Java runtime for ${selected.id}…"
        ioExecutor.execute {
            val result = runCatching {
                RuntimeManager(applicationContext).installForVersion(selected) { message ->
                    mainHandler.post {
                        if (!isFinishing && !isDestroyed) runtimeStatusText.text = message
                    }
                }
            }
            mainHandler.post {
                if (isFinishing || isDestroyed) return@post
                runtimeButton.isEnabled = true
                installButton.isEnabled = true
                refreshButton.isEnabled = true
                playButton.isEnabled = true
                runtimeButton.text = "Install compatible Java runtime"
                updateRuntimeStatus()
                result.onSuccess { installed ->
                    runtimeStatusText.text = "${installed.displayName()} installed and verified. JVM/renderer bridge still pending."
                    MaterialAlertDialogBuilder(this)
                        .setTitle("Java runtime ready")
                        .setMessage(
                            "Java ${installed.majorVersion} for Android ${installed.architecture} was downloaded, SHA-256 verified, and unpacked to app-private storage for Minecraft ${selected.id}.\n\nThis does not launch the game yet: the JVM/JNI bridge, renderer, and input path still need integration."
                        )
                        .setPositiveButton("OK", null)
                        .show()
                }.onFailure { error ->
                    runtimeStatusText.text = "Runtime installation failed. Check connection and free storage."
                    MaterialAlertDialogBuilder(this)
                        .setTitle("Runtime installation failed")
                        .setMessage(error.message ?: "Could not install a compatible Java runtime.")
                        .setPositiveButton("OK", null)
                        .show()
                }
            }
        }
    }

    private fun updateRuntimeStatus() {
        if (!::runtimeStatusText.isInitialized) return
        val runtimes = runCatching { RuntimeManager(applicationContext).installedRuntimes() }
            .getOrDefault(emptyList())
        runtimeStatusText.text = if (runtimes.isEmpty()) {
            "No verified Java runtimes installed on this device."
        } else {
            "Installed: " + runtimes.joinToString(" · ") { it.displayName() }
        }
    }

    private fun prepareLaunchProfile() {
        if (!::versions.isInitialized) {
            Toast.makeText(this, "Wait for the version catalog to finish loading.", Toast.LENGTH_SHORT).show()
            return
        }
        val selected = versions.firstOrNull { it.id == selectedVersionId }
        if (selected == null) {
            Toast.makeText(this, "Load and select a Minecraft version first.", Toast.LENGTH_SHORT).show()
            return
        }

        prepareLaunchButton.isEnabled = false
        prepareLaunchButton.text = "Resolving profile…"
        engineStatusText.text = "Reading version metadata, checking runtime and resolving libraries…"
        installButton.isEnabled = false
        runtimeButton.isEnabled = false
        refreshButton.isEnabled = false
        playButton.isEnabled = false
        ioExecutor.execute {
            val result = runCatching {
                // This button may be used before any game Surface exists. Install the bundled
                // runtime assets before inspecting them so a clean first launch does not report
                // Caciocavallo/GLFW as missing merely because the game activity has not opened.
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
                prepareLaunchButton.isEnabled = true
                prepareLaunchButton.text = "Prepare launch profile"
                installButton.isEnabled = true
                runtimeButton.isEnabled = true
                refreshButton.isEnabled = true
                playButton.isEnabled = true
                result.onSuccess { (profile, launchSpec) ->
                    engineStatusText.text = "Native launch request assembled: ${launchSpec.argv.size} argv entries, ${launchSpec.environment.size} environment values, ${launchSpec.blockers.size} engine blockers remain."
                    val blockers = launchSpec.blockers.joinToString("\n") { "• $it" }
                    MaterialAlertDialogBuilder(this)
                        .setTitle("Native launch request prepared")
                        .setMessage(
                            "Minecraft ${profile.versionId}\n" +
                                "Required Java: ${profile.requiredJavaMajor}\n" +
                                "Selected runtime: Java ${profile.runtimeMajor}\n" +
                                "Main class: ${profile.mainClass}\n" +
                                "Classpath entries: ${profile.classpathEntries.size}\n" +
                                "JVM arguments: ${profile.jvmArguments.size}\n" +
                                "Game arguments: ${profile.gameArguments.size}\n" +
                                "JLI argv entries: ${launchSpec.argv.size}\n" +
                                "Native library directories: ${launchSpec.nativeLibraryDirectories.size}\n" +
                                "Working directory: ${launchSpec.workingDirectory.absolutePath}\n" +
                                "Assets index: ${profile.assetIndexId}\n" +
                                "Valid account profile: ${profile.hasValidAccountSession}\n" +
                                "Account type: ${profile.accountType ?: "none"}\n\n" +
                                "Remaining blockers:\n$blockers\n\n" +
                                "Session tokens are never displayed in diagnostics. The game activity invokes native JLI only when all checks pass and the Pojav surface is bound."
                        )
                        .setPositiveButton("OK", null)
                        .show()
                }.onFailure { error ->
                    engineStatusText.text = "Profile check failed. Install version files/runtime, then retry."
                    MaterialAlertDialogBuilder(this)
                        .setTitle("Cannot prepare launch profile")
                        .setMessage(error.message ?: "The selected version is missing required launch files.")
                        .setPositiveButton("OK", null)
                        .show()
                }
            }
        }
    }

    private fun probeJvmBridge() {
        if (!::jvmBridgeButton.isInitialized) return
        jvmBridgeButton.isEnabled = false
        jvmBridgeButton.text = "Checking JVM libraries…"
        engineStatusText.text = "Finding an installed runtime and checking its native JLI/JVM libraries…"
        ioExecutor.execute {
            val result = runCatching {
                val runtime = RuntimeManager(applicationContext).installedRuntimes()
                    .maxByOrNull { it.majorVersion }
                    ?: throw IllegalStateException("No verified Java runtime is installed. Install a compatible runtime first.")
                runtime to JliBridgeDiagnostics.probe(runtime.home)
            }
            mainHandler.post {
                if (isFinishing || isDestroyed) return@post
                jvmBridgeButton.isEnabled = true
                jvmBridgeButton.text = "Probe installed JVM bridge"
                result.onSuccess { (runtime, probe) ->
                    engineStatusText.text = if (probe.available) {
                        "JLI loader probe passed for ${runtime.displayName()}. This verifies library availability only; actual launch still requires a valid account profile, a bound surface, and device-tested renderer compatibility."
                    } else {
                        "JLI loader probe failed for ${runtime.displayName()}. See diagnostic details."
                    }
                    MaterialAlertDialogBuilder(this)
                        .setTitle(if (probe.available) "JVM native loader available" else "JVM native loader not ready")
                        .setMessage(
                            "Runtime: ${runtime.displayName()}\n" +
                                "Path: ${runtime.home.absolutePath}\n\n" +
                                probe.details +
                                "\n\nThis probe only maps native runtime libraries and checks JLI_Launch. It does not start a JVM or launch Minecraft."
                        )
                        .setPositiveButton("OK", null)
                        .show()
                }.onFailure { error ->
                    engineStatusText.text = "JLI loader probe could not run. Install and verify a Java runtime first."
                    MaterialAlertDialogBuilder(this)
                        .setTitle("JVM bridge probe failed")
                        .setMessage(error.message ?: "Could not inspect the installed runtime.")
                        .setPositiveButton("OK", null)
                        .show()
                }
            }
        }
    }

    private fun runJliRuntimeSmokeTest() {
        if (!::jvmSmokeButton.isInitialized) return
        if (jvmSmokeReceiver != null) return
        jvmSmokeButton.isEnabled = false
        jvmSmokeButton.text = "Starting isolated JVM test…"
        engineStatusText.text = "Starting a disposable process for Java -version…"

        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                if (intent.action != JliRuntimeSmokeService.ACTION_RESULT) return
                clearJliSmokeReceiver()
                jvmSmokeButton.isEnabled = true
                jvmSmokeButton.text = "Run Java runtime self-test"
                val passed = intent.getBooleanExtra(JliRuntimeSmokeService.EXTRA_PASSED, false)
                val runtime = intent.getStringExtra(JliRuntimeSmokeService.EXTRA_RUNTIME).orEmpty()
                val runtimeHome = intent.getStringExtra(JliRuntimeSmokeService.EXTRA_RUNTIME_HOME).orEmpty()
                val details = intent.getStringExtra(JliRuntimeSmokeService.EXTRA_DETAILS).orEmpty()
                val logPath = intent.getStringExtra(JliRuntimeSmokeService.EXTRA_LOG_PATH).orEmpty()
                engineStatusText.text = if (passed) {
                    "JVM startup test passed for $runtime. This does not prove Minecraft will render; actual launch also requires a valid Microsoft or local account profile and a successful device-side game-surface test."
                } else {
                    "JVM startup test failed or is unavailable for $runtime. See the diagnostic log for details."
                }
                MaterialAlertDialogBuilder(this@MainActivity)
                    .setTitle(if (passed) "Java runtime test passed" else "Java runtime test failed")
                    .setMessage(
                        "Runtime: $runtime\n" +
                            (if (runtimeHome.isBlank()) "" else "Path: $runtimeHome\n") +
                            "\n$details\n\nSaved diagnostic log: $logPath\n\n" +
                            "The test runs `JLI_Launch` with only `-version` in a disposable Android process. It does not start Minecraft."
                    )
                    .setPositiveButton("OK", null)
                    .show()
            }
        }
        jvmSmokeReceiver = receiver
        val filter = IntentFilter(JliRuntimeSmokeService.ACTION_RESULT)
        try {
            if (Build.VERSION.SDK_INT >= 33) {
                registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
            } else {
                @Suppress("DEPRECATION")
                registerReceiver(receiver, filter)
            }
        } catch (error: Exception) {
            clearJliSmokeReceiver()
            jvmSmokeButton.isEnabled = true
            jvmSmokeButton.text = "Run Java runtime self-test"
            engineStatusText.text = "Could not listen for the isolated JVM diagnostic result."
            Toast.makeText(this, error.message ?: "Could not register JVM test receiver", Toast.LENGTH_LONG).show()
            return
        }

        val timeout = Runnable {
            clearJliSmokeReceiver()
            jvmSmokeButton.isEnabled = true
            jvmSmokeButton.text = "Run Java runtime self-test"
            engineStatusText.text = "JVM test process did not return a result. It may have crashed during JVM initialization."
            // Show whatever the JVM managed to print (and a fresh hs_err crash report) in the dialog.
            val diagnostics = runCatching {
                val log = java.io.File(LauncherStorage.diagnosticsRoot(applicationContext), "jli-version.log")
                val logText = if (log.isFile) log.readText().takeLast(2500) else ""
                val crashReport = java.io.File(cacheDir, "jli-version-work")
                    .listFiles { file -> file.name.startsWith("hs_err") }
                    ?.filter { System.currentTimeMillis() - it.lastModified() < 120_000L }
                    ?.maxByOrNull { it.lastModified() }
                    ?.readText()?.take(2500).orEmpty()
                buildString {
                    if (logText.isNotBlank()) append("\n\nJVM output:\n").append(logText)
                    if (crashReport.isNotBlank()) append("\n\nCrash report (hs_err):\n").append(crashReport)
                }
            }.getOrDefault("")
            MaterialAlertDialogBuilder(this)
                .setTitle("JVM test timed out")
                .setMessage(
                    "The separate JVM diagnostic process did not report a result within 45 seconds. " +
                        "Sally's main UI process is isolated from that native test. Check Android logcat and the app-private diagnostics folder." +
                        diagnostics
                )
                .setPositiveButton("OK", null)
                .show()
        }
        jvmSmokeTimeout = timeout
        mainHandler.postDelayed(timeout, 45_000L)
        try {
            startService(Intent(this, JliRuntimeSmokeService::class.java))
        } catch (error: Exception) {
            clearJliSmokeReceiver()
            jvmSmokeButton.isEnabled = true
            jvmSmokeButton.text = "Run Java runtime self-test"
            engineStatusText.text = "Could not start the isolated JVM diagnostic process."
            MaterialAlertDialogBuilder(this)
                .setTitle("JVM test unavailable")
                .setMessage(error.message ?: "Could not start the diagnostic service.")
                .setPositiveButton("OK", null)
                .show()
        }
    }

    private fun clearJliSmokeReceiver() {
        jvmSmokeTimeout?.let(mainHandler::removeCallbacks)
        jvmSmokeTimeout = null
        jvmSmokeReceiver?.let { receiver ->
            runCatching { unregisterReceiver(receiver) }
        }
        jvmSmokeReceiver = null
    }

    private fun onPlayPressed() {
        if (!::versions.isInitialized || selectedVersionId.isBlank()) {
            Toast.makeText(this, "Load and select a Minecraft version first.", Toast.LENGTH_SHORT).show()
            return
        }
        val selected = versions.firstOrNull { it.id == selectedVersionId }
        if (selected == null) {
            Toast.makeText(this, "The selected Minecraft version is no longer in the loaded catalog.", Toast.LENGTH_LONG).show()
            return
        }
        val session = MinecraftLaunchSessionStore.current()
        if (session == null) {
            MaterialAlertDialogBuilder(this)
                .setTitle("Choose an account")
                .setMessage("Sign in with Microsoft for an authenticated Minecraft Java profile, or use a local/offline profile for single-player and servers that explicitly allow offline mode.")
                .setNegativeButton("Cancel", null)
                .setNeutralButton("Local account") { _, _ -> startActivity(Intent(this, LocalAccountActivity::class.java)) }
                .setPositiveButton("Microsoft sign-in") { _, _ -> startActivity(Intent(this, MicrosoftLoginActivity::class.java)) }
                .show()
            return
        }
        val sessionError = session.validationError()
        if (sessionError != null) {
            MinecraftLaunchSessionStore.clear()
            MaterialAlertDialogBuilder(this)
                .setTitle("Minecraft account profile invalid")
                .setMessage(sessionError)
                .setPositiveButton("OK", null)
                .show()
            return
        }
        startActivity(Intent(this, PojavGameActivity::class.java).apply {
            putExtra(PojavGameActivity.EXTRA_VERSION_ID, selected.id)
            putExtra(PojavGameActivity.EXTRA_VERSION_TYPE, selected.type)
            putExtra(PojavGameActivity.EXTRA_VERSION_URL, selected.url)
            putExtra(PojavGameActivity.EXTRA_RELEASE_TIME, selected.releaseTime)
        })
    }

    private fun updateAccountStatus() {
        if (!::accountStatusText.isInitialized) return
        val session = MinecraftLaunchSessionStore.current()
        val problem = session?.validationError()
        if (problem != null) MinecraftLaunchSessionStore.clear()
        accountStatusText.text = when {
            session == null -> "Account: none selected. Choose Microsoft or local/offline account."
            problem != null -> "Account profile is invalid: $problem"
            session.accountType == MinecraftAccountType.LOCAL -> "Using ${session.username} · Local/offline account. Online-mode servers require Microsoft authentication."
            else -> "Signed in as ${session.username} · Microsoft account (token in memory only)."
        }
        if (::signInButton.isInitialized) {
            signInButton.text = if (session?.accountType == MinecraftAccountType.MICROSOFT && problem == null) "Manage / sign out Microsoft account" else "Sign in with Microsoft"
        }
    }

    private fun card(): MaterialCardView = MaterialCardView(this).apply {
        radius = dp(22).toFloat()
        cardElevation = 0f
        strokeWidth = dp(1)
        strokeColor = color(com.google.android.material.R.attr.colorOutlineVariant)
        setCardBackgroundColor(color(com.google.android.material.R.attr.colorSurface))
    }

    private fun cardContent(card: MaterialCardView): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(18), dp(18), dp(18), dp(18))
        card.addView(this, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
    }

    private fun sectionLabel(text: String): TextView = textView(
        text,
        11f,
        bold = true,
        color = color(com.google.android.material.R.attr.colorPrimary),
    ).apply { letterSpacing = 0.12f }

    private fun textView(text: String, sizeSp: Float, bold: Boolean = false, color: Int = color(com.google.android.material.R.attr.colorOnSurface)): TextView = TextView(this).apply {
        this.text = text
        textSize = sizeSp
        setTextColor(color)
        if (bold) setTypeface(typeface, android.graphics.Typeface.BOLD)
        includeFontPadding = true
    }

    private fun addGap(parent: LinearLayout, heightDp: Int) {
        parent.addView(View(this), LinearLayout.LayoutParams(1, dp(heightDp)))
    }

    private fun fullWidth(): LinearLayout.LayoutParams = LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.WRAP_CONTENT,
    )

    private fun color(attribute: Int): Int {
        val value = TypedValue()
        return if (theme.resolveAttribute(attribute, value, true)) value.data else Color.BLACK
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private companion object {
        const val KEY_SELECTED_VERSION = "selected_minecraft_version"
    }
}
