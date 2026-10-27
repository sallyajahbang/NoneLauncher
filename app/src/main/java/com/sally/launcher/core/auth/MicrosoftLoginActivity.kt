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
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.sally.launcher.R
import com.sally.launcher.ui.CosmicBackgroundView
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
        val dim = androidx.core.content.ContextCompat.getColor(this, R.color.cosmic_text_dim)
        val textColor = androidx.core.content.ContextCompat.getColor(this, R.color.cosmic_text)
        val root = FrameLayout(this)
        root.addView(CosmicBackgroundView(this), ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        val scroll = ScrollView(this).apply {
            isFillViewport = true
            isVerticalScrollBarEnabled = false
        }
        val page = FrameLayout(this)
        scroll.addView(page, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        val content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val width = minOf(resources.displayMetrics.widthPixels - dp(48), dp(400))
        page.addView(content, FrameLayout.LayoutParams(width, ViewGroup.LayoutParams.WRAP_CONTENT, android.view.Gravity.CENTER).apply {
            topMargin = dp(24)
            bottomMargin = dp(24)
        })

        content.addView(label("Microsoft", 32f, false, textColor).apply {
            typeface = android.graphics.Typeface.create("sans-serif-light", android.graphics.Typeface.NORMAL)
        })
        content.addView(label("Sign in to play online", 14f, false, androidx.core.content.ContextCompat.getColor(this, R.color.cosmic_cyan)))
        gap(content, 24)

        val inputLayout = TextInputLayout(this).apply {
            hint = "Application (client) ID"
            boxBackgroundMode = TextInputLayout.BOX_BACKGROUND_OUTLINE
            helperText = "From your Microsoft Entra app (public client, device code)"
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        }
        clientIdInput = TextInputEditText(inputLayout.context).apply {
            setSingleLine(true)
            inputType = android.text.InputType.TYPE_CLASS_TEXT
        }
        inputLayout.addView(clientIdInput)
        content.addView(inputLayout)
        gap(content, 16)

        startButton = MaterialButton(this).apply {
            text = "Get sign-in code"
            isAllCaps = false
            textSize = 16f
            cornerRadius = dp(18)
            setBackgroundColor(androidx.core.content.ContextCompat.getColor(this@MicrosoftLoginActivity, R.color.cosmic_violet))
            setTextColor(androidx.core.content.ContextCompat.getColor(this@MicrosoftLoginActivity, R.color.cosmic_void))
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(56))
            setOnClickListener { beginSignIn() }
        }
        content.addView(startButton)
        gap(content, 20)

        val card = MaterialCardView(this).apply {
            radius = dp(18).toFloat()
            cardElevation = 0f
            strokeWidth = dp(1)
            strokeColor = androidx.core.content.ContextCompat.getColor(this@MicrosoftLoginActivity, R.color.cosmic_outline)
            setCardBackgroundColor(androidx.core.content.ContextCompat.getColor(this@MicrosoftLoginActivity, R.color.cosmic_surface))
        }
        val inner = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(12))
        }
        card.addView(inner, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        codeText = label("Code appears here", 26f, true, textColor).apply {
            letterSpacing = 0.1f
            setTextIsSelectable(true)
        }
        inner.addView(codeText)
        gap(inner, 4)
        urlText = label("Then open the verification page and enter it.", 13f, false, dim).apply { setTextIsSelectable(true) }
        inner.addView(urlText)
        gap(inner, 12)

        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        openBrowserButton = MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
            text = "Open page"
            isAllCaps = false
            isEnabled = false
            setOnClickListener { openVerificationPage() }
        }
        copyCodeButton = MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
            text = "Copy code"
            isAllCaps = false
            isEnabled = false
            setOnClickListener { copyUserCode() }
        }
        row.addView(openBrowserButton, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { marginEnd = dp(8) })
        row.addView(copyCodeButton, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        inner.addView(row)
        cancelButton = MaterialButton(this, null, com.google.android.material.R.attr.borderlessButtonStyle).apply {
            text = "Cancel"
            isAllCaps = false
            isEnabled = false
            setOnClickListener { cancelSignInByUser() }
        }
        inner.addView(cancelButton, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        content.addView(card, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        gap(content, 14)

        statusText = label("Not signed in.", 13f, false, dim).apply { gravity = android.view.Gravity.CENTER }
        content.addView(statusText, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        gap(content, 8)
        signOutButton = MaterialButton(this, null, com.google.android.material.R.attr.borderlessButtonStyle).apply {
            text = "Sign out"
            isAllCaps = false
            isEnabled = false
            setOnClickListener {
                cancelPendingWork()
                if (MinecraftLaunchSessionStore.current()?.accountType == MinecraftAccountType.MICROSOFT) {
                    MinecraftLaunchSessionStore.clear()
                }
                currentChallenge = null
                codeText.text = "Code appears here"
                urlText.text = "Then open the verification page and enter it."
                openBrowserButton.isEnabled = false
                copyCodeButton.isEnabled = false
                refreshAccountState()
                Toast.makeText(this@MicrosoftLoginActivity, "Signed out.", Toast.LENGTH_SHORT).show()
            }
        }
        content.addView(signOutButton, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        root.addView(scroll, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        androidx.core.view.WindowCompat.setDecorFitsSystemWindows(window, false)
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val bars = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars() or androidx.core.view.WindowInsetsCompat.Type.ime())
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }
        return root
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
                    urlText.text = "Open ${challenge.verificationUri} and enter the code. Expires in ~${challenge.expiresInSeconds / 60} min."
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
                    statusText.text = "Signed in as ${session.username}"
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
            statusText.text = "Offline account ${session.username} is active. Signing in will switch to Microsoft."
            signOutButton.isEnabled = false
        } else {
            statusText.text = "Signed in as ${session.username}"
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
