package com.sally.launcher.core.auth

import android.app.Activity
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.sally.launcher.core.engine.MinecraftAccountType
import com.sally.launcher.core.engine.MinecraftLaunchSessionStore
import com.sally.launcher.core.engine.OfflineAccountProfile

/** Manages Pojav-style local/offline identities without claiming that they are authenticated accounts. */
class LocalAccountActivity : AppCompatActivity() {
    private lateinit var nameInput: TextInputEditText
    private lateinit var statusText: TextView
    private lateinit var accountList: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(buildContent())
        refreshAccounts()
    }

    private fun buildContent(): View {
        val scroll = ScrollView(this).apply {
            isFillViewport = true
            clipToPadding = false
            setPadding(dp(20), dp(20), dp(20), dp(28))
        }
        val page = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        scroll.addView(page)
        page.addView(label("SALLY LAUNCHER · ACCOUNTS", 12f, true, color(com.google.android.material.R.attr.colorPrimary)))
        gap(page, 8)
        page.addView(label("Local account", 28f, true, color(com.google.android.material.R.attr.colorOnSurface)))
        gap(page, 8)
        page.addView(label(
            "Create a local offline profile using a Minecraft username. No Microsoft login or token is used. This is for single-player/local play and servers that explicitly allow offline-mode identities; online-mode servers require an official account and may reject this profile.",
            14f, false, color(com.google.android.material.R.attr.colorOnSurfaceVariant),
        ))
        gap(page, 18)

        val card = MaterialCardView(this).apply {
            radius = dp(20).toFloat()
            cardElevation = 0f
            strokeWidth = dp(1)
            strokeColor = color(com.google.android.material.R.attr.colorOutlineVariant)
            setCardBackgroundColor(color(com.google.android.material.R.attr.colorSurface))
        }
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(16))
        }
        card.addView(content, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        content.addView(label("CREATE PROFILE", 11f, true, color(com.google.android.material.R.attr.colorPrimary)))
        gap(content, 8)
        val inputLayout = TextInputLayout(this).apply {
            hint = "Minecraft username (3–16 chars)"
            isHintEnabled = true
        }
        nameInput = TextInputEditText(inputLayout.context).apply {
            setSingleLine(true)
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            filters = arrayOf(android.text.InputFilter.LengthFilter(16))
        }
        inputLayout.addView(nameInput)
        content.addView(inputLayout)
        gap(content, 10)
        content.addView(MaterialButton(this).apply {
            text = "Create and use local account"
            isAllCaps = false
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            setOnClickListener { createAccount() }
        })
        gap(page, 14)
        page.addView(card)
        gap(page, 14)
        statusText = label("No local account selected.", 13f, false, color(com.google.android.material.R.attr.colorOnSurfaceVariant))
        page.addView(statusText)
        gap(page, 10)
        page.addView(label("SAVED LOCAL ACCOUNTS", 11f, true, color(com.google.android.material.R.attr.colorPrimary)))
        gap(page, 8)
        accountList = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        page.addView(accountList)
        gap(page, 16)
        page.addView(MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
            text = "Back to launcher"
            isAllCaps = false
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            setOnClickListener { setResult(Activity.RESULT_OK); finish() }
        })
        return scroll
    }

    private fun createAccount() {
        val rawName = nameInput.text?.toString().orEmpty()
        val profile = runCatching { OfflineAccountProfile.create(rawName) }.getOrElse { error ->
            nameInput.error = error.message ?: "Enter a valid username."
            return
        }
        nameInput.error = null
        runCatching { LocalAccountRepository.add(this, profile.username) }
            .onSuccess { account ->
                statusText.text = "Active local account: ${account.username}. No online authentication token is used."
                Toast.makeText(this, "Local account ${account.username} selected", Toast.LENGTH_SHORT).show()
                nameInput.setText("")
                setResult(Activity.RESULT_OK)
                refreshAccounts()
            }
            .onFailure { error -> Toast.makeText(this, error.message ?: "Could not save local account.", Toast.LENGTH_LONG).show() }
    }

    private fun refreshAccounts() {
        if (!::accountList.isInitialized) return
        accountList.removeAllViews()
        val active = MinecraftLaunchSessionStore.current()
        statusText.text = when {
            active?.accountType == MinecraftAccountType.LOCAL -> "Active local account: ${active.username}"
            active?.accountType == MinecraftAccountType.MICROSOFT -> "Active Microsoft account: ${active.username}"
            else -> "No account selected."
        }
        val accounts = LocalAccountRepository.list(this)
        if (accounts.isEmpty()) {
            accountList.addView(label("No saved local accounts yet.", 14f, false, color(com.google.android.material.R.attr.colorOnSurfaceVariant)))
            return
        }
        accounts.forEach { account ->
            val rowCard = MaterialCardView(this).apply {
                radius = dp(16).toFloat()
                cardElevation = 0f
                strokeWidth = dp(1)
                strokeColor = color(com.google.android.material.R.attr.colorOutlineVariant)
                setCardBackgroundColor(color(com.google.android.material.R.attr.colorSurface))
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { bottomMargin = dp(8) }
            }
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(14), dp(12), dp(14), dp(12))
            }
            row.addView(label(account.username, 17f, true, color(com.google.android.material.R.attr.colorOnSurface)))
            gap(row, 4)
            row.addView(label("Offline UUID: ${account.uuid}", 11f, false, color(com.google.android.material.R.attr.colorOnSurfaceVariant)).apply { setTextIsSelectable(true) })
            gap(row, 8)
            val actions = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            actions.addView(MaterialButton(this@LocalAccountActivity).apply {
                text = if (active?.accountType == MinecraftAccountType.LOCAL && active.username.equals(account.username, true)) "Selected" else "Use account"
                isAllCaps = false
                isEnabled = !(active?.accountType == MinecraftAccountType.LOCAL && active.username.equals(account.username, true))
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { marginEnd = dp(8) }
                setOnClickListener {
                    runCatching { LocalAccountRepository.activate(this@LocalAccountActivity, account.username) }
                        .onSuccess { setResult(Activity.RESULT_OK); refreshAccounts(); Toast.makeText(this@LocalAccountActivity, "Using ${it.username}", Toast.LENGTH_SHORT).show() }
                        .onFailure { Toast.makeText(this@LocalAccountActivity, it.message ?: "Could not activate account.", Toast.LENGTH_LONG).show() }
                }
            })
            actions.addView(MaterialButton(this@LocalAccountActivity, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
                text = "Delete"
                isAllCaps = false
                setOnClickListener {
                    MaterialAlertDialogBuilder(this@LocalAccountActivity)
                        .setTitle("Delete local account?")
                        .setMessage("Remove ${account.username} from this launcher? This does not delete any Minecraft worlds.")
                        .setNegativeButton("Cancel", null)
                        .setPositiveButton("Delete") { _, _ ->
                            LocalAccountRepository.remove(this@LocalAccountActivity, account.username)
                            setResult(Activity.RESULT_OK)
                            refreshAccounts()
                        }
                        .show()
                }
            })
            row.addView(actions)
            rowCard.addView(row)
            accountList.addView(rowCard)
        }
    }

    private fun label(text: String, size: Float, bold: Boolean, foreground: Int): TextView = TextView(this).apply {
        this.text = text
        textSize = size
        setTextColor(foreground)
        if (bold) setTypeface(typeface, android.graphics.Typeface.BOLD)
        includeFontPadding = true
    }

    private fun gap(parent: LinearLayout, height: Int) { parent.addView(View(this), LinearLayout.LayoutParams(1, dp(height))) }
    private fun color(attribute: Int): Int = android.util.TypedValue().let { value ->
        if (theme.resolveAttribute(attribute, value, true)) value.data else android.graphics.Color.BLACK
    }
    private fun dp(value: Int): Int = (value * resources.displayMetrics.density + 0.5f).toInt()
}
