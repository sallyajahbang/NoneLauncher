package com.sally.launcher

import android.content.BroadcastReceiver
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Configuration
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.InputFilter
import android.text.InputType
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.BaseAdapter
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ListPopupWindow
import android.widget.PopupMenu
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.sally.launcher.core.auth.LocalAccountRepository
import com.sally.launcher.core.auth.MicrosoftLoginActivity
import com.sally.launcher.core.engine.EngineDiagnosticsActivity
import com.sally.launcher.core.engine.JliRuntimeSmokeService
import com.sally.launcher.core.engine.MinecraftAccountType
import com.sally.launcher.core.engine.MinecraftLaunchSessionStore
import com.sally.launcher.core.engine.OfflineAccountProfile
import com.sally.launcher.core.engine.PojavEngineDiagnosticsActivity
import com.sally.launcher.core.engine.PojavEnginePayloadInstaller
import com.sally.launcher.core.engine.PojavGameActivity
import com.sally.launcher.data.install.VersionInstaller
import com.sally.launcher.data.model.MinecraftVersion
import com.sally.launcher.data.model.VersionCatalog
import com.sally.launcher.data.runtime.RuntimeManager
import com.sally.launcher.data.storage.LauncherStorage
import com.sally.launcher.data.version.VersionManifestRepository
import com.sally.launcher.ui.CosmicBackgroundView
import java.io.File
import java.util.concurrent.Executors

/**
 * Single-screen Home: pick an account and a version from two dropdowns, press Play.
 * Play downloads whatever is missing (version files + Java runtime) and then starts the game.
 */
class MainActivity : AppCompatActivity() {
    private val ioExecutor = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())
    private lateinit var repository: VersionManifestRepository

    private lateinit var column: LinearLayout
    private lateinit var accountChip: Chip
    private lateinit var versionChip: Chip
    private lateinit var playButton: MaterialButton
    private lateinit var progress: ProgressBar
    private lateinit var statusText: TextView

    private var versions: List<MinecraftVersion>? = null
    private var selectedVersionId: String = ""
    private var showAllVersions = false
    private var busy = false
    private var catalogLoading = false

    private var smokeReceiver: BroadcastReceiver? = null
    private var smokeTimeout: Runnable? = null

    // ---------------------------------------------------------------- lifecycle

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        repository = VersionManifestRepository(applicationContext)
        LocalAccountRepository.restoreSelectedSession(this)
        val prefs = getPreferences(MODE_PRIVATE)
        selectedVersionId = prefs.getString(KEY_SELECTED_VERSION, "").orEmpty()
        showAllVersions = prefs.getBoolean(KEY_SHOW_ALL, false)

        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        setContentView(buildContent())
        refreshAccountChip()
        refreshVersionChip()
        loadCatalog()
    }

    override fun onResume() {
        super.onResume()
        if (::accountChip.isInitialized) refreshAccountChip()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        if (::column.isInitialized) column.layoutParams = column.layoutParams.apply { width = columnWidth() }
    }

    override fun onDestroy() {
        clearSmokeReceiver()
        ioExecutor.shutdown()
        super.onDestroy()
    }

    // ---------------------------------------------------------------- layout

    private class Chip(val card: MaterialCardView, val icon: TextView, val primary: TextView, val secondary: TextView)

    private fun buildContent(): View {
        val root = FrameLayout(this)
        root.addView(CosmicBackgroundView(this), ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)

        val scroll = ScrollView(this).apply {
            isFillViewport = true
            isVerticalScrollBarEnabled = false
        }
        val page = FrameLayout(this)
        scroll.addView(page, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        // Top bar: brand + menu
        val topBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        topBar.addView(TextView(this).apply {
            text = "SALLY"
            textSize = 13f
            letterSpacing = 0.35f
            setTextColor(res(R.color.cosmic_text_dim))
            setTypeface(typeface, Typeface.BOLD)
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        topBar.addView(TextView(this).apply {
            text = "⋮"
            textSize = 24f
            gravity = Gravity.CENTER
            setTextColor(res(R.color.cosmic_text))
            contentDescription = "Menu"
            val ripple = TypedValue()
            if (theme.resolveAttribute(android.R.attr.selectableItemBackgroundBorderless, ripple, true)) {
                setBackgroundResource(ripple.resourceId)
            }
            setOnClickListener { showToolsMenu(it) }
        }, LinearLayout.LayoutParams(dp(44), dp(44)))
        page.addView(topBar, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP).apply {
            setMargins(dp(20), dp(12), dp(12), 0)
        })

        // Centered column
        column = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }

        column.addView(TextView(this).apply {
            text = "Minecraft"
            textSize = 34f
            setTextColor(res(R.color.cosmic_text))
            typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)
        })
        column.addView(TextView(this).apply {
            text = "Java Edition"
            textSize = 14f
            letterSpacing = 0.15f
            setTextColor(res(R.color.cosmic_cyan))
        })
        column.addView(space(32))

        accountChip = makeChip("Account")
        accountChip.card.setOnClickListener { if (!busy) onAccountChipClicked() }
        column.addView(accountChip.card, matchWrap())
        column.addView(space(12))

        versionChip = makeChip("Version")
        versionChip.card.setOnClickListener { if (!busy) onVersionChipClicked() }
        column.addView(versionChip.card, matchWrap())
        column.addView(space(24))

        playButton = MaterialButton(this).apply {
            text = "Play"
            textSize = 18f
            isAllCaps = false
            letterSpacing = 0.1f
            cornerRadius = dp(20)
            setTypeface(typeface, Typeface.BOLD)
            setBackgroundColor(res(R.color.cosmic_violet))
            setTextColor(res(R.color.cosmic_void))
            setOnClickListener { onPlayClicked() }
        }
        column.addView(playButton, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(60)))

        progress = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            isIndeterminate = true
            visibility = View.INVISIBLE
            indeterminateTintList = android.content.res.ColorStateList.valueOf(res(R.color.cosmic_cyan))
        }
        column.addView(progress, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(4)).apply { topMargin = dp(14) })

        statusText = TextView(this).apply {
            textSize = 13f
            gravity = Gravity.CENTER
            setTextColor(res(R.color.cosmic_text_dim))
        }
        column.addView(statusText, matchWrap().apply { topMargin = dp(10) })

        page.addView(column, FrameLayout.LayoutParams(columnWidth(), ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER).apply {
            topMargin = dp(56)
            bottomMargin = dp(24)
        })

        root.addView(scroll, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.ime())
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }
        return root
    }

    private fun makeChip(label: String): Chip {
        val card = MaterialCardView(this).apply {
            radius = dp(18).toFloat()
            cardElevation = 0f
            strokeWidth = dp(1)
            strokeColor = res(R.color.cosmic_outline)
            setCardBackgroundColor(res(R.color.cosmic_surface))
            isClickable = true
            isFocusable = true
            contentDescription = label
        }
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(14), dp(12), dp(16), dp(12))
        }
        val icon = TextView(this).apply {
            gravity = Gravity.CENTER
            textSize = 16f
            setTextColor(res(R.color.cosmic_text))
            setTypeface(typeface, Typeface.BOLD)
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(res(R.color.cosmic_violet_dark))
            }
        }
        row.addView(icon, LinearLayout.LayoutParams(dp(40), dp(40)).apply { marginEnd = dp(14) })
        val texts = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val primary = TextView(this).apply {
            textSize = 17f
            setTextColor(res(R.color.cosmic_text))
            setTypeface(typeface, Typeface.BOLD)
            maxLines = 1
        }
        val secondary = TextView(this).apply {
            textSize = 12f
            setTextColor(res(R.color.cosmic_text_dim))
            maxLines = 1
        }
        texts.addView(primary)
        texts.addView(secondary)
        row.addView(texts, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        row.addView(TextView(this).apply {
            text = "▾"
            textSize = 16f
            setTextColor(res(R.color.cosmic_text_dim))
        })
        card.addView(row, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        return Chip(card, icon, primary, secondary)
    }

    private fun columnWidth(): Int {
        val available = resources.displayMetrics.widthPixels - dp(48)
        return minOf(available, dp(400))
    }

    // ---------------------------------------------------------------- account

    private fun refreshAccountChip() {
        val session = MinecraftLaunchSessionStore.current()
        if (session == null || session.validationError() != null) {
            if (session != null) MinecraftLaunchSessionStore.clear()
            accountChip.icon.text = "+"
            accountChip.primary.text = "Add account"
            accountChip.secondary.text = "Tap to choose"
        } else {
            accountChip.icon.text = session.username.first().uppercaseChar().toString()
            accountChip.primary.text = session.username
            accountChip.secondary.text =
                if (session.accountType == MinecraftAccountType.LOCAL) "Offline account" else "Microsoft account"
        }
    }

    private sealed class AccountRow {
        data class Local(val profile: OfflineAccountProfile, val active: Boolean) : AccountRow()
        data class Microsoft(val username: String) : AccountRow()
        object AddOffline : AccountRow()
        object SignInMicrosoft : AccountRow()
    }

    private fun onAccountChipClicked() {
        val hasAny = LocalAccountRepository.list(this).isNotEmpty() || MinecraftLaunchSessionStore.current() != null
        if (hasAny) showAccountMenu() else showAddOfflineDialog()
    }

    private fun showAccountMenu() {
        val session = MinecraftLaunchSessionStore.current()
        val rows = ArrayList<AccountRow>()
        if (session != null && session.accountType == MinecraftAccountType.MICROSOFT) rows += AccountRow.Microsoft(session.username)
        LocalAccountRepository.list(this).forEach { profile ->
            rows += AccountRow.Local(
                profile,
                active = session != null && session.accountType == MinecraftAccountType.LOCAL && session.username.equals(profile.username, true),
            )
        }
        rows += AccountRow.AddOffline
        rows += AccountRow.SignInMicrosoft

        var popup: ListPopupWindow? = null
        val adapter = RowAdapter(rows) { row ->
            when (row) {
                is AccountRow.Local -> menuRow(
                    title = row.profile.username,
                    subtitle = "Offline",
                    selected = row.active,
                    trailing = "✕",
                    onTrailing = {
                        popup?.dismiss()
                        confirmRemove(row.profile.username)
                    },
                )
                is AccountRow.Microsoft -> menuRow(row.username, "Microsoft", selected = true)
                AccountRow.AddOffline -> menuRow("＋  Offline account", null, accent = true)
                AccountRow.SignInMicrosoft -> menuRow("Microsoft sign-in", null, accent = true)
            }
        }
        popup = showPopup(accountChip.card, adapter, rows.size) { position ->
            when (val row = rows[position]) {
                is AccountRow.Local -> runCatching { LocalAccountRepository.activate(this, row.profile.username) }
                    .onFailure { Toast.makeText(this, it.message, Toast.LENGTH_SHORT).show() }
                is AccountRow.Microsoft -> startActivity(Intent(this, MicrosoftLoginActivity::class.java))
                AccountRow.AddOffline -> showAddOfflineDialog()
                AccountRow.SignInMicrosoft -> startActivity(Intent(this, MicrosoftLoginActivity::class.java))
            }
            refreshAccountChip()
        }
    }

    private fun showAddOfflineDialog() {
        val input = TextInputEditText(this).apply {
            setSingleLine()
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            filters = arrayOf(InputFilter.LengthFilter(16))
        }
        val layout = TextInputLayout(this).apply {
            hint = "Username"
            helperText = "3–16 letters, numbers or _"
            boxBackgroundMode = TextInputLayout.BOX_BACKGROUND_OUTLINE
            addView(input)
        }
        val container = FrameLayout(this).apply {
            setPadding(dp(24), dp(8), dp(24), 0)
            addView(layout)
        }
        val dialog = MaterialAlertDialogBuilder(this)
            .setTitle("Add offline account")
            .setView(container)
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Add", null)
            .create()
        dialog.setOnShowListener {
            dialog.getButton(DialogInterface.BUTTON_POSITIVE).setOnClickListener {
                try {
                    LocalAccountRepository.add(this, input.text?.toString().orEmpty())
                    dialog.dismiss()
                    refreshAccountChip()
                } catch (error: IllegalArgumentException) {
                    layout.error = error.message
                }
            }
        }
        dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE)
        dialog.show()
        input.requestFocus()
    }

    private fun confirmRemove(username: String) {
        MaterialAlertDialogBuilder(this)
            .setTitle("Remove $username?")
            .setMessage("This offline account will be deleted from the launcher.")
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Remove") { _, _ ->
                LocalAccountRepository.remove(this, username)
                refreshAccountChip()
            }
            .show()
    }

    // ---------------------------------------------------------------- versions

    private sealed class VersionRow {
        data class Item(val version: MinecraftVersion) : VersionRow()
        data class Toggle(val showingAll: Boolean) : VersionRow()
    }

    private fun loadCatalog() {
        if (catalogLoading) return
        catalogLoading = true
        setStatus("Loading versions…")
        ioExecutor.execute {
            val result = runCatching { repository.fetchCatalog() }
            mainHandler.post {
                catalogLoading = false
                if (isFinishing || isDestroyed) return@post
                result.onSuccess(::onCatalogLoaded).onFailure {
                    setStatus("Could not load versions. Tap the version to retry.")
                }
            }
        }
    }

    private fun onCatalogLoaded(catalog: VersionCatalog) {
        versions = catalog.versions
        val preferred = catalog.versions.firstOrNull { it.id == selectedVersionId }
            ?: catalog.versions.firstOrNull { it.id == catalog.latestReleaseId }
            ?: catalog.versions.firstOrNull()
        if (preferred != null) selectVersion(preferred)
        setStatus(if (catalog.loadedFromCache) "Offline — using saved version list" else "")
    }

    private fun selectVersion(version: MinecraftVersion) {
        selectedVersionId = version.id
        getPreferences(MODE_PRIVATE).edit().putString(KEY_SELECTED_VERSION, version.id).apply()
        refreshVersionChip()
    }

    private fun refreshVersionChip() {
        val selected = versions?.firstOrNull { it.id == selectedVersionId }
        if (selectedVersionId.isBlank()) {
            versionChip.icon.text = "…"
            versionChip.primary.text = "Loading…"
            versionChip.secondary.text = "Fetching official versions"
            return
        }
        versionChip.icon.text = "⛏"
        versionChip.primary.text = selectedVersionId
        val type = selected?.type?.replaceFirstChar { it.uppercase() } ?: "Version"
        versionChip.secondary.text = if (isInstalled(selectedVersionId)) "$type · Ready" else "$type · Downloads on play"
    }

    private fun onVersionChipClicked() {
        val list = versions
        if (list == null) {
            loadCatalog()
            return
        }
        val rows = ArrayList<VersionRow>()
        list.filter { showAllVersions || it.type == "release" || it.id == selectedVersionId }
            .forEach { rows += VersionRow.Item(it) }
        rows += VersionRow.Toggle(showAllVersions)

        var popup: ListPopupWindow? = null
        val adapter = RowAdapter(rows) { row ->
            when (row) {
                is VersionRow.Item -> menuRow(
                    title = row.version.id,
                    subtitle = row.version.type.replaceFirstChar { it.uppercase() } +
                        if (isInstalled(row.version.id)) " · Ready" else "",
                    selected = row.version.id == selectedVersionId,
                )
                is VersionRow.Toggle -> menuRow(
                    if (row.showingAll) "Show releases only" else "Show snapshots & old versions",
                    null,
                    accent = true,
                )
            }
        }
        popup = showPopup(versionChip.card, adapter, rows.size) { position ->
            when (val row = rows[position]) {
                is VersionRow.Item -> selectVersion(row.version)
                is VersionRow.Toggle -> {
                    showAllVersions = !row.showingAll
                    getPreferences(MODE_PRIVATE).edit().putBoolean(KEY_SHOW_ALL, showAllVersions).apply()
                    versionChip.card.post { onVersionChipClicked() }
                }
            }
        }
        // Scroll the list to the current selection.
        val selectedIndex = rows.indexOfFirst { it is VersionRow.Item && it.version.id == selectedVersionId }
        if (selectedIndex > 3) popup?.listView?.post { popup?.setSelection(selectedIndex - 2) }
    }

    private fun versionDir(id: String) = File(File(LauncherStorage.minecraftRoot(applicationContext), "versions"), id)

    private fun isInstalled(id: String): Boolean = runCatching { File(versionDir(id), INSTALLED_MARKER).isFile }.getOrDefault(false)

    // ---------------------------------------------------------------- play

    private fun onPlayClicked() {
        if (busy) return
        val selected = versions?.firstOrNull { it.id == selectedVersionId }
        if (selected == null) {
            Toast.makeText(this, "Versions are still loading.", Toast.LENGTH_SHORT).show()
            if (versions == null) loadCatalog()
            return
        }
        val session = MinecraftLaunchSessionStore.current()
        if (session == null) {
            onAccountChipClicked()
            return
        }
        val problem = session.validationError()
        if (problem != null) {
            MinecraftLaunchSessionStore.clear()
            refreshAccountChip()
            showMessage("Account problem", problem)
            return
        }

        setBusy(true, "Preparing…")
        ioExecutor.execute {
            val result = runCatching {
                if (!isInstalled(selected.id)) {
                    VersionInstaller(applicationContext).installVersion(selected) { message -> postStatus(message) }
                    File(versionDir(selected.id), INSTALLED_MARKER).writeText("ok")
                }
                postStatus("Preparing Java…")
                RuntimeManager(applicationContext).installForVersion(selected) { message -> postStatus(message) }
                postStatus("Preparing engine…")
                val minecraftRoot = LauncherStorage.minecraftRoot(applicationContext)
                val payload = PojavEnginePayloadInstaller(applicationContext)
                payload.installCaciocavalloComponents(minecraftRoot)
                payload.installPatchedGlfwClasses(minecraftRoot)
            }
            mainHandler.post {
                if (isFinishing || isDestroyed) return@post
                setBusy(false, "")
                refreshVersionChip()
                result.onSuccess { launchGame(selected) }.onFailure { error ->
                    setStatus("Failed. Check connection and storage, then retry.")
                    showMessage("Could not prepare ${selected.id}", error.message ?: error.javaClass.simpleName)
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

    private fun setBusy(value: Boolean, message: String) {
        busy = value
        playButton.isEnabled = !value
        playButton.text = if (value) "Preparing…" else "Play"
        accountChip.card.alpha = if (value) 0.5f else 1f
        versionChip.card.alpha = if (value) 0.5f else 1f
        progress.visibility = if (value) View.VISIBLE else View.INVISIBLE
        setStatus(message)
    }

    private fun setStatus(message: String) {
        if (::statusText.isInitialized) statusText.text = message
    }

    private fun postStatus(message: String) {
        mainHandler.post { if (!isFinishing && !isDestroyed) setStatus(message) }
    }

    // ---------------------------------------------------------------- tools menu

    private fun showToolsMenu(anchor: View) {
        PopupMenu(this, anchor).apply {
            menu.add(0, 1, 0, "Refresh versions")
            menu.add(0, 2, 1, "Java self-test")
            menu.add(0, 3, 2, "Graphics & input test")
            menu.add(0, 4, 3, "Pojav bridge test")
            setOnMenuItemClickListener { item ->
                when (item.itemId) {
                    1 -> loadCatalog()
                    2 -> runJavaSelfTest()
                    3 -> startActivity(Intent(this@MainActivity, EngineDiagnosticsActivity::class.java))
                    4 -> startActivity(Intent(this@MainActivity, PojavEngineDiagnosticsActivity::class.java))
                }
                true
            }
            show()
        }
    }

    private fun runJavaSelfTest() {
        if (smokeReceiver != null) {
            Toast.makeText(this, "Self-test already running…", Toast.LENGTH_SHORT).show()
            return
        }
        setStatus("Running Java self-test…")
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                if (intent.action != JliRuntimeSmokeService.ACTION_RESULT) return
                clearSmokeReceiver()
                setStatus("")
                val passed = intent.getBooleanExtra(JliRuntimeSmokeService.EXTRA_PASSED, false)
                val runtime = intent.getStringExtra(JliRuntimeSmokeService.EXTRA_RUNTIME).orEmpty()
                val details = intent.getStringExtra(JliRuntimeSmokeService.EXTRA_DETAILS).orEmpty()
                val logPath = intent.getStringExtra(JliRuntimeSmokeService.EXTRA_LOG_PATH).orEmpty()
                showMessage(
                    if (passed) "Java self-test passed" else "Java self-test failed",
                    "Runtime: $runtime\n\n$details\n\nLog: $logPath",
                )
            }
        }
        smokeReceiver = receiver
        val filter = IntentFilter(JliRuntimeSmokeService.ACTION_RESULT)
        try {
            if (Build.VERSION.SDK_INT >= 33) {
                registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
            } else {
                @Suppress("DEPRECATION")
                registerReceiver(receiver, filter)
            }
            val timeout = Runnable {
                clearSmokeReceiver()
                setStatus("")
                showMessage("Java self-test timed out", "No result within 45 seconds. Check the diagnostics folder and logcat.")
            }
            smokeTimeout = timeout
            mainHandler.postDelayed(timeout, 45_000L)
            startService(Intent(this, JliRuntimeSmokeService::class.java))
        } catch (error: Exception) {
            clearSmokeReceiver()
            setStatus("")
            showMessage("Self-test unavailable", error.message ?: "Could not start the diagnostic service.")
        }
    }

    private fun clearSmokeReceiver() {
        smokeTimeout?.let(mainHandler::removeCallbacks)
        smokeTimeout = null
        smokeReceiver?.let { runCatching { unregisterReceiver(it) } }
        smokeReceiver = null
    }

    // ---------------------------------------------------------------- shared UI helpers

    private class RowAdapter<T>(private val items: List<T>, private val create: (T) -> View) : BaseAdapter() {
        override fun getCount() = items.size
        override fun getItem(position: Int) = items[position]
        override fun getItemId(position: Int) = position.toLong()
        override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View = create(items[position])
    }

    private fun showPopup(anchor: View, adapter: BaseAdapter, rowCount: Int, onPick: (Int) -> Unit): ListPopupWindow {
        val popup = ListPopupWindow(this)
        popup.anchorView = anchor
        popup.width = anchor.width.coerceAtLeast(dp(240))
        popup.height = minOf(dp(8) + rowCount * dp(56), dp(340))
        popup.isModal = true
        popup.verticalOffset = dp(6)
        popup.setBackgroundDrawable(GradientDrawable().apply {
            setColor(res(R.color.cosmic_surface_high))
            cornerRadius = dp(18).toFloat()
            setStroke(dp(1), res(R.color.cosmic_outline))
        })
        popup.setAdapter(adapter)
        popup.setOnItemClickListener { _, _, position, _ ->
            popup.dismiss()
            onPick(position)
        }
        popup.show()
        popup.listView?.apply {
            divider = null
            setPadding(0, dp(4), 0, dp(4))
            clipToPadding = false
        }
        return popup
    }

    private fun menuRow(
        title: String,
        subtitle: String?,
        selected: Boolean = false,
        accent: Boolean = false,
        trailing: String? = null,
        onTrailing: (() -> Unit)? = null,
    ): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16), dp(8), dp(8), dp(8))
            minimumHeight = dp(52)
        }
        val texts = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        texts.addView(TextView(this).apply {
            text = title
            textSize = 16f
            maxLines = 1
            setTextColor(res(if (accent || selected) R.color.cosmic_violet else R.color.cosmic_text))
            if (selected) setTypeface(typeface, Typeface.BOLD)
        })
        if (subtitle != null) {
            texts.addView(TextView(this).apply {
                text = subtitle
                textSize = 12f
                setTextColor(res(R.color.cosmic_text_dim))
            })
        }
        row.addView(texts, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        if (selected && trailing == null) {
            row.addView(TextView(this).apply {
                text = "✓"
                textSize = 16f
                setTextColor(res(R.color.cosmic_cyan))
                setPadding(dp(8), 0, dp(12), 0)
            })
        }
        if (trailing != null) {
            row.addView(TextView(this).apply {
                text = trailing
                textSize = 15f
                gravity = Gravity.CENTER
                setTextColor(res(R.color.cosmic_text_dim))
                contentDescription = "Remove"
                setOnClickListener { onTrailing?.invoke() }
            }, LinearLayout.LayoutParams(dp(44), dp(44)))
        }
        return row
    }

    private fun showMessage(title: String, message: String) {
        MaterialAlertDialogBuilder(this).setTitle(title).setMessage(message).setPositiveButton("OK", null).show()
    }

    private fun space(heightDp: Int) = View(this).apply {
        layoutParams = LinearLayout.LayoutParams(1, dp(heightDp))
    }

    private fun matchWrap() = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)

    private fun res(id: Int): Int = ContextCompat.getColor(this, id)

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density + 0.5f).toInt()

    private companion object {
        const val KEY_SELECTED_VERSION = "selected_minecraft_version"
        const val KEY_SHOW_ALL = "show_all_versions"
        const val INSTALLED_MARKER = ".sally-installed"
    }
}
