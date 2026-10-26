package com.sally.launcher

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
import com.sally.launcher.core.engine.EngineLaunchResult
import com.sally.launcher.core.engine.EngineLauncher
import com.sally.launcher.core.engine.EngineLauncherNotIntegrated
import com.sally.launcher.data.model.MinecraftVersion
import com.sally.launcher.data.model.VersionCatalog
import com.sally.launcher.data.version.VersionManifestRepository
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {
    private val ioExecutor = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())
    private val engineLauncher: EngineLauncher = EngineLauncherNotIntegrated()

    private lateinit var repository: VersionManifestRepository
    private lateinit var versionInput: MaterialAutoCompleteTextView
    private lateinit var statusText: TextView
    private lateinit var refreshButton: MaterialButton
    private lateinit var playButton: MaterialButton
    private lateinit var versions: List<MinecraftVersion>
    private var selectedVersionId: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        repository = VersionManifestRepository(applicationContext)
        selectedVersionId = getPreferences(MODE_PRIVATE).getString(KEY_SELECTED_VERSION, "").orEmpty()
        setContentView(createContentView())
        loadVersionCatalog()
    }

    override fun onDestroy() {
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
        addGap(versionContent, 12)
        statusText = textView("Loading official version catalog…", 13f, color = color(com.google.android.material.R.attr.colorOnSurfaceVariant))
        versionContent.addView(statusText)
        page.addView(versionCard, fullWidth())

        addGap(page, 18)
        val engineCard = card()
        val engineContent = cardContent(engineCard)
        engineContent.addView(sectionLabel("ENGINE STATUS"))
        addGap(engineContent, 8)
        engineContent.addView(textView("Runtime not connected yet", 19f, bold = true))
        addGap(engineContent, 6)
        engineContent.addView(textView(
            "This first build sets up the launcher UI and version catalog. Java runtime, Minecraft libraries, graphics and input bridges are the next integration step.",
            14f,
            color = color(com.google.android.material.R.attr.colorOnSurfaceVariant),
        ))
        page.addView(engineCard, fullWidth())

        addGap(page, 18)
        page.addView(textView("STAGE 1  ·  VERSION CATALOG", 11f, bold = true, color = color(com.google.android.material.R.attr.colorOnSurfaceVariant)))
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

    private fun onPlayPressed() {
        if (selectedVersionId.isBlank()) {
            Toast.makeText(this, "Load and select a Minecraft version first.", Toast.LENGTH_SHORT).show()
            return
        }
        when (val result = engineLauncher.launch(selectedVersionId)) {
            is EngineLaunchResult.Started -> Toast.makeText(this, "Starting ${result.versionId}", Toast.LENGTH_SHORT).show()
            is EngineLaunchResult.NotReady -> MaterialAlertDialogBuilder(this)
                .setTitle("Game engine not ready")
                .setMessage(result.explanation)
                .setPositiveButton("OK", null)
                .show()
            is EngineLaunchResult.Failed -> MaterialAlertDialogBuilder(this)
                .setTitle("Could not launch")
                .setMessage(result.explanation)
                .setPositiveButton("OK", null)
                .show()
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
