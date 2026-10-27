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
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.OpenInBrowser
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.VpnKey
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.Color
import com.sally.launcher.SallyLauncherTheme
import com.sally.launcher.ThemeSettings
import com.sally.launcher.launcherBackground
import com.sally.launcher.core.engine.MinecraftLaunchSessionStore
import com.sally.launcher.core.engine.MinecraftAccountType
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
    private var uiState by mutableStateOf(MicrosoftSignInUiState())
    private var currentChallenge: MinecraftMicrosoftAuthenticator.DeviceCodeChallenge? = null
    private var activeFuture: Future<*>? = null
    @Volatile private var completed = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        uiState = uiState.copy(clientId = getSharedPreferences(PREFS, MODE_PRIVATE).getString(KEY_CLIENT_ID, "").orEmpty())
        refreshAccountState()
        setContent {
            SallyLauncherTheme(settings = ThemeSettings.read(this)) {
                MicrosoftSignInScreen(
                    state = uiState,
                    onClientIdChange = { uiState = uiState.copy(clientId = it, clientIdError = null) },
                    onBegin = { beginSignIn(uiState.clientId) },
                    onOpenBrowser = ::openVerificationPage,
                    onCopyCode = ::copyUserCode,
                    onCancel = ::cancelSignInByUser,
                    onSignOut = ::signOut,
                    onBack = { setResult(Activity.RESULT_OK); finish() },
                )
            }
        }
    }

    override fun onDestroy() {
        if (!completed) cancelPendingWork()
        worker.shutdownNow()
        super.onDestroy()
    }

    private fun beginSignIn(rawClientId: String) {
        val clientId = rawClientId.trim()
        val validation = authenticator.validateClientId(clientId)
        if (validation != null) {
            uiState = uiState.copy(clientIdError = validation, status = validation)
            return
        }
        getSharedPreferences(PREFS, MODE_PRIVATE).edit().putString(KEY_CLIENT_ID, clientId).apply()
        cancelPendingWork()
        val cancellation = AtomicBoolean(false)
        currentCancellation = cancellation
        completed = false
        currentChallenge = null
        uiState = uiState.copy(
            clientId = clientId,
            clientIdError = null,
            code = "Requesting code…",
            verificationMessage = "",
            status = "Contacting Microsoft sign-in…",
            busy = true,
            browserEnabled = false,
            copyEnabled = false,
            signOutEnabled = false,
        )
        activeFuture = worker.submit {
            try {
                val challenge = authenticator.requestDeviceCode(clientId)
                currentChallenge = challenge
                mainHandler.post {
                    if (isFinishing || isDestroyed || cancellation.get()) return@post
                    uiState = uiState.copy(
                        code = challenge.userCode,
                        verificationMessage = "Open ${challenge.verificationUri} and enter the code above. Code expires in about ${challenge.expiresInSeconds / 60} minute(s).",
                        browserEnabled = true,
                        copyEnabled = true,
                        status = "Waiting for Microsoft authorization…",
                    )
                }
                val session = authenticator.completeSignIn(
                    clientId = clientId,
                    challenge = challenge,
                    isCancelled = { cancellation.get() || Thread.currentThread().isInterrupted },
                    onStatus = { message ->
                        mainHandler.post {
                            if (!isFinishing && !isDestroyed && !cancellation.get()) uiState = uiState.copy(status = message.take(500))
                        }
                    },
                )
                if (cancellation.get()) throw InterruptedException("Sign-in cancelled")
                LocalAccountRepository.clearLocalSelectionForMicrosoft(applicationContext)
                MinecraftLaunchSessionStore.installAfterAuthentication(session)
                completed = true
                mainHandler.post {
                    if (isFinishing || isDestroyed) return@post
                    uiState = uiState.copy(status = "Signed in as ${session.username}. The access token is held in memory only.", busy = false)
                    Toast.makeText(this, "Signed in as ${session.username}", Toast.LENGTH_LONG).show()
                    setResult(Activity.RESULT_OK)
                    finish()
                }
            } catch (error: InterruptedException) {
                Thread.currentThread().interrupt()
                mainHandler.post {
                    if (!isFinishing && !isDestroyed && cancellation.get()) {
                        uiState = uiState.copy(status = "Sign-in cancelled.", busy = false, browserEnabled = currentChallenge != null, copyEnabled = currentChallenge != null)
                    }
                }
            } catch (error: Exception) {
                mainHandler.post {
                    if (isFinishing || isDestroyed || cancellation.get()) return@post
                    uiState = uiState.copy(
                        status = error.message?.take(500) ?: "Sign-in failed. Check the app registration and network connection.",
                        busy = false,
                        browserEnabled = currentChallenge != null,
                        copyEnabled = currentChallenge != null,
                    )
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
        uiState = uiState.copy(status = "Sign-in cancelled.", busy = false, browserEnabled = currentChallenge != null, copyEnabled = currentChallenge != null)
    }

    private fun signOut() {
        cancelPendingWork()
        if (MinecraftLaunchSessionStore.current()?.accountType == MinecraftAccountType.MICROSOFT) MinecraftLaunchSessionStore.clear()
        currentChallenge = null
        uiState = uiState.copy(
            code = "Sign-in code will appear here",
            verificationMessage = "Open the official verification page when a code is provided.",
            browserEnabled = false,
            copyEnabled = false,
            busy = false,
        )
        refreshAccountState()
        Toast.makeText(this, "Signed out. The session was cleared from memory.", Toast.LENGTH_SHORT).show()
    }

    private fun cancelPendingWork() {
        currentCancellation.set(true)
        activeFuture?.cancel(true)
        activeFuture = null
    }

    private fun refreshAccountState() {
        val session = MinecraftLaunchSessionStore.current()
        val issue = session?.validationError()
        if (session == null || issue != null) {
            if (issue != null) MinecraftLaunchSessionStore.clear()
            uiState = uiState.copy(status = issue ?: "Not signed in to Microsoft.", signOutEnabled = false)
        } else if (session.accountType == MinecraftAccountType.LOCAL) {
            uiState = uiState.copy(status = "Local account ${session.username} is currently active. Signing in here will switch to a Microsoft account.", signOutEnabled = false)
        } else {
            uiState = uiState.copy(status = "Signed in as ${session.username} · Microsoft account. Token held in memory only.", signOutEnabled = true)
        }
    }

    private companion object {
        const val PREFS = "microsoft_auth_settings"
        const val KEY_CLIENT_ID = "microsoft_oauth_client_id"
    }
}

private data class MicrosoftSignInUiState(
    val clientId: String = "",
    val clientIdError: String? = null,
    val code: String = "Sign-in code will appear here",
    val verificationMessage: String = "Open the official verification page when a code is provided.",
    val status: String = "Not signed in to Microsoft.",
    val busy: Boolean = false,
    val browserEnabled: Boolean = false,
    val copyEnabled: Boolean = false,
    val signOutEnabled: Boolean = false,
)

@Composable
private fun MicrosoftSignInScreen(
    state: MicrosoftSignInUiState,
    onClientIdChange: (String) -> Unit,
    onBegin: () -> Unit,
    onOpenBrowser: () -> Unit,
    onCopyCode: () -> Unit,
    onCancel: () -> Unit,
    onSignOut: () -> Unit,
    onBack: () -> Unit,
) {
    Scaffold(modifier = Modifier.fillMaxSize().launcherBackground(), containerColor = Color.Transparent) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(onClick = onBack, contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp)) { Icon(Icons.Outlined.ArrowBack, "Back") }
                    Column(Modifier.weight(1f)) {
                        Text("SALLY LAUNCHER · ACCOUNT", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp)
                        Text("Microsoft sign-in", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                    }
                }
            }
            item {
                Card(shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                    Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Outlined.Security, null, modifier = Modifier.size(36.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer)
                        Text("Official device-code flow", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer)
                        Text("Sign-in happens on Microsoft's website. Sally never asks for your Microsoft password and does not save access or refresh tokens to disk.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
                    }
                }
            }
            item {
                OutlinedCard(shape = RoundedCornerShape(22.dp)) {
                    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("App registration", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Text("Enter an Application (client) ID GUID for a Microsoft Entra app configured for personal Microsoft accounts and public-client/device-code sign-in. Do not enter a client secret. Minecraft Services may require app access approval.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        OutlinedTextField(
                            value = state.clientId,
                            onValueChange = onClientIdChange,
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            enabled = !state.busy,
                            isError = state.clientIdError != null,
                            label = { Text("Application (client) ID") },
                            supportingText = { if (state.clientIdError != null) Text(state.clientIdError) else Text("Client ID only · no client secret") },
                            leadingIcon = { Icon(Icons.Outlined.VpnKey, null) },
                        )
                        Button(onClick = onBegin, enabled = !state.busy, modifier = Modifier.fillMaxWidth()) {
                            if (state.busy) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp) else Icon(Icons.Outlined.Security, null)
                            Spacer(Modifier.width(8.dp)); Text(if (state.busy) "Waiting for authorization…" else "Request sign-in code")
                        }
                    }
                }
            }
            item {
                OutlinedCard(shape = RoundedCornerShape(22.dp)) {
                    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Your device code", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Text(state.code, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        Text(state.verificationMessage, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        FilledTonalButton(onClick = onOpenBrowser, enabled = state.browserEnabled, modifier = Modifier.fillMaxWidth()) {
                            Icon(Icons.Outlined.OpenInBrowser, null); Spacer(Modifier.width(8.dp)); Text("Open Microsoft verification page")
                        }
                        OutlinedButton(onClick = onCopyCode, enabled = state.copyEnabled, modifier = Modifier.fillMaxWidth()) {
                            Icon(Icons.Outlined.ContentCopy, null); Spacer(Modifier.width(8.dp)); Text("Copy sign-in code")
                        }
                        if (state.busy) OutlinedButton(onClick = onCancel, modifier = Modifier.fillMaxWidth()) { Text("Cancel sign-in") }
                    }
                }
            }
            item {
                OutlinedCard(shape = RoundedCornerShape(22.dp)) {
                    Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Top) {
                        Icon(if (state.signOutEnabled) Icons.Outlined.CheckCircle else Icons.Outlined.AccountCircle, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 2.dp))
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Authentication status", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Text(state.status, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            OutlinedButton(onClick = onSignOut, enabled = state.signOutEnabled && !state.busy) { Text("Sign out Microsoft") }
                        }
                    }
                }
            }
        }
    }
}
