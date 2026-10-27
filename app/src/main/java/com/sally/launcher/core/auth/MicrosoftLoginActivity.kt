package com.sally.launcher.core.auth

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.sally.launcher.core.engine.MinecraftLaunchSessionStore
import com.sally.launcher.core.engine.MinecraftAccountType
import java.io.IOException
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.atomic.AtomicBoolean

/** Microsoft device-code sign-in. Access and refresh tokens are kept only in the process memory. */
class MicrosoftLoginActivity : AppCompatActivity() {
    private val worker: ExecutorService = Executors.newSingleThreadExecutor { task ->
        Thread(task, "Sally-Microsoft-Auth").apply { isDaemon = true }
    }
    private val mainHandler = Handler(Looper.getMainLooper())
    @Volatile private var currentCancellation = AtomicBoolean(false)
    private val authenticator = MinecraftMicrosoftAuthenticator()

    private lateinit var clientIdInput: TextInputEditText
    private lateinit var codeText: TextView
    private lateinit var urlText: TextView
    private lateinit var statusText: TextView
    private lateinit var startButton: MaterialButton
    private lateinit var openBrowserButton: MaterialButton
    private lateinit var copyCodeButton: MaterialButton
    private lateinit var cancelButton: MaterialButton
    private lateinit var signOutButton: MaterialButton
    private var currentChallenge: MinecraftMicrosoftAuthenticator.DeviceCodeChallenge? = null
    private var activeFuture: Future<*>? = null
    @Volatile private var completed = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(buildContent())
        clientIdInput.setText(getSharedPreferences(PREFS, MODE_PRIVATE).getString(KEY_CLIENT_ID, "").orEmpty())
        refreshAccountState()
    }

    override fun onDestroy() {
        if (!completed) cancelPendingWork()
        worker.shutdownNow()
        super.onDestroy()
    }

    private fun buildContent(): View {
        val scroll = ScrollView(this).apply {
            isFillViewport = true
            clipToPadding = false
            setPadding(dp(20), dp(20), dp(20), dp(28))
        }
        val page = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        scroll.addView(page)
        page.addView(label("SALLY LAUNCHER · ACCOUNT", 12f, true, color(com.google.android.material.R.attr.colorPrimary)))
        gap(page, 8)
        page.addView(label("Sign in with Microsoft", 28f, true, color(com.google.android.material.R.attr.colorOnSurface)))
        gap(page, 8)
        page.addView(label(
            "Use the official device-code flow. Sign-in happens on Microsoft’s website; Sally never asks for your Microsoft password and does not store access or refresh tokens on disk.",
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
        content.addView(label("MICROSOFT APP REGISTRATION", 11f, true, color(com.google.android.material.R.attr.colorPrimary)))
        gap(content, 6)
        content.addView(label(
            "Enter an Application (client) ID GUID for a Microsoft Entra app configured for personal Microsoft accounts and public-client/device-code sign-in. Do not enter a client secret. Minecraft Services may require app access approval.",
            13f, false, color(com.google.android.material.R.attr.colorOnSurfaceVariant),
        ))
        gap(content, 12)
        val inputLayout = TextInputLayout(this).apply {
            hint = "Application (client) ID"
            isHintEnabled = true
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        }
        clientIdInput = TextInputEditText(inputLayout.context).apply {
            setSingleLine(true)
            inputType = android.text.InputType.TYPE_CLASS_TEXT
            setTextIsSelectable(true)
        }
        inputLayout.addView(clientIdInput)
        content.addView(inputLayout)
        gap(content, 10)
        startButton = MaterialButton(this).apply {
            text = "Request sign-in code"
            isAllCaps = false
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            setOnClickListener { beginSignIn() }
        }
        content.addView(startButton)
        gap(content, 8)
        codeText = label("Sign-in code will appear here", 26f, true, color(com.google.android.material.R.attr.colorOnSurface))
        codeText.setTextIsSelectable(true)
        content.addView(codeText)
        gap(content, 4)
        urlText = label("Open the official verification page when a code is provided.", 13f, false, color(com.google.android.material.R.attr.colorOnSurfaceVariant))
        urlText.setTextIsSelectable(true)
        content.addView(urlText)
        gap(content, 10)
        openBrowserButton = MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
            text = "Open Microsoft verification page"
            isAllCaps = false
            isEnabled = false
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            setOnClickListener { openVerificationPage() }
        }
        content.addView(openBrowserButton)
        gap(content, 4)
        copyCodeButton = MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
            text = "Copy sign-in code"
            isAllCaps = false
            isEnabled = false
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            setOnClickListener { copyUserCode() }
        }
        content.addView(copyCodeButton)
        gap(content, 4)
        cancelButton = MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
            text = "Cancel sign-in"
            isAllCaps = false
            isEnabled = false
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            setOnClickListener { cancelSignInByUser() }
        }
        content.addView(cancelButton)
        gap(content, 12)
        statusText = label("Not signed in.", 13f, false, color(com.google.android.material.R.attr.colorOnSurfaceVariant))
        content.addView(statusText)
        gap(content, 14)
        signOutButton = MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
            text = "Sign out"
            isAllCaps = false
            isEnabled = false
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            setOnClickListener {
                cancelPendingWork()
                if (MinecraftLaunchSessionStore.current()?.accountType == MinecraftAccountType.MICROSOFT) {
                    MinecraftLaunchSessionStore.clear()
                }
                currentChallenge = null
                codeText.text = "Sign-in code will appear here"
                urlText.text = "Open the official verification page when a code is provided."
                openBrowserButton.isEnabled = false
                copyCodeButton.isEnabled = false
                refreshAccountState()
                Toast.makeText(this@MicrosoftLoginActivity, "Signed out. The session was cleared from memory.", Toast.LENGTH_SHORT).show()
            }
        }
        content.addView(signOutButton)
        page.addView(card, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        return scroll
    }

    private fun beginSignIn() {
        val clientId = clientIdInput.text?.toString()?.trim().orEmpty()
        val validation = authenticator.validateClientId(clientId)
        if (validation != null) {
            clientIdInput.error = validation
            return
        }
        clientIdInput.error = null
        getSharedPreferences(PREFS, MODE_PRIVATE).edit().putString(KEY_CLIENT_ID, clientId).apply()
        cancelPendingWork()
        val cancellation = AtomicBoolean(false)
        currentCancellation = cancellation
        completed = false
        currentChallenge = null
        codeText.text = "Requesting code…"
        urlText.text = ""
        statusText.text = "Contacting Microsoft sign-in…"
        setBusy(true)
        activeFuture = worker.submit {
            try {
                val challenge = authenticator.requestDeviceCode(clientId)
                currentChallenge = challenge
                mainHandler.post {
                    if (isFinishing || isDestroyed || cancellation.get()) return@post
                    codeText.text = challenge.userCode
                    urlText.text = "Open ${challenge.verificationUri} and enter the code above. Code expires in about ${challenge.expiresInSeconds / 60} minute(s)."
                    openBrowserButton.isEnabled = true
                    copyCodeButton.isEnabled = true
                    statusText.text = "Waiting for Microsoft authorization…"
                }
                val session = authenticator.completeSignIn(
                    clientId = clientId,
                    challenge = challenge,
                    isCancelled = { cancellation.get() || Thread.currentThread().isInterrupted },
                    onStatus = { message ->
                        mainHandler.post {
                            if (!isFinishing && !isDestroyed && !cancellation.get()) statusText.text = message
                        }
                    },
                )
                if (cancellation.get()) throw InterruptedException("Sign-in cancelled")
                LocalAccountRepository.clearLocalSelectionForMicrosoft(applicationContext)
                MinecraftLaunchSessionStore.installAfterAuthentication(session)
                completed = true
                mainHandler.post {
                    if (isFinishing || isDestroyed) return@post
                    statusText.text = "Signed in as ${session.username}. The access token is held in memory only."
                    Toast.makeText(this, "Signed in as ${session.username}", Toast.LENGTH_LONG).show()
                    setResult(Activity.RESULT_OK)
                    finish()
                }
            } catch (error: InterruptedException) {
                Thread.currentThread().interrupt()
                mainHandler.post {
                    if (!isFinishing && !isDestroyed && cancellation.get()) {
                        statusText.text = "Sign-in cancelled."
                        setBusy(false)
                    }
                }
            } catch (error: Exception) {
                mainHandler.post {
                    if (isFinishing || isDestroyed || cancellation.get()) return@post
                    statusText.text = error.message?.take(500) ?: "Sign-in failed. Check the app registration and network connection."
                    setBusy(false)
                    openBrowserButton.isEnabled = currentChallenge != null
                    copyCodeButton.isEnabled = currentChallenge != null
                }
            }
        }
    }

    private fun openVerificationPage() {
        val challenge = currentChallenge ?: return
        val uri = runCatching { Uri.parse(challenge.verificationUri) }.getOrNull()
        if (uri == null || uri.scheme != "https") {
            Toast.makeText(this, "The verification link is invalid.", Toast.LENGTH_LONG).show()
            return
        }
        runCatching { startActivity(Intent(Intent.ACTION_VIEW, uri)) }
            .onFailure { Toast.makeText(this, "No browser app could open the Microsoft verification page.", Toast.LENGTH_LONG).show() }
    }

    private fun copyUserCode() {
        val challenge = currentChallenge ?: return
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("Microsoft sign-in code", challenge.userCode))
        Toast.makeText(this, "Sign-in code copied.", Toast.LENGTH_SHORT).show()
    }

    private fun cancelSignInByUser() {
        cancelPendingWork()
        statusText.text = "Sign-in cancelled."
        setBusy(false)
        openBrowserButton.isEnabled = currentChallenge != null
        copyCodeButton.isEnabled = currentChallenge != null
    }

    private fun cancelPendingWork() {
        currentCancellation.set(true)
        activeFuture?.cancel(true)
        activeFuture = null
    }

    private fun setBusy(busy: Boolean) {
        startButton.isEnabled = !busy
        clientIdInput.isEnabled = !busy
        cancelButton.isEnabled = busy
        signOutButton.isEnabled = !busy && MinecraftLaunchSessionStore.current()?.accountType == MinecraftAccountType.MICROSOFT
    }

    private fun refreshAccountState() {
        val session = MinecraftLaunchSessionStore.current()
        val issue = session?.validationError()
        if (session == null || issue != null) {
            if (issue != null) MinecraftLaunchSessionStore.clear()
            statusText.text = issue ?: "Not signed in to Microsoft."
            signOutButton.isEnabled = false
        } else if (session.accountType == MinecraftAccountType.LOCAL) {
            statusText.text = "Local account ${session.username} is currently active. Signing in here will switch to a Microsoft account."
            signOutButton.isEnabled = false
        } else {
            statusText.text = "Signed in as ${session.username} · Microsoft account. Token held in memory only."
            signOutButton.isEnabled = true
        }
    }

    private fun label(text: String, size: Float, bold: Boolean, foreground: Int): TextView = TextView(this).apply {
        this.text = text
        textSize = size
        setTextColor(foreground)
        if (bold) setTypeface(typeface, android.graphics.Typeface.BOLD)
        includeFontPadding = true
    }

    private fun gap(parent: LinearLayout, height: Int) {
        parent.addView(View(this), LinearLayout.LayoutParams(1, dp(height)))
    }

    private fun color(attribute: Int): Int = android.util.TypedValue().let { value ->
        if (theme.resolveAttribute(attribute, value, true)) value.data else android.graphics.Color.BLACK
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density + 0.5f).toInt()

    private companion object {
        const val PREFS = "microsoft_auth_settings"
        const val KEY_CLIENT_ID = "microsoft_oauth_client_id"
    }
}
