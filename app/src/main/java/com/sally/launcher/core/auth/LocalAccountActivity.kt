package com.sally.launcher.core.auth

import android.app.Activity
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sally.launcher.SallyLauncherTheme
import com.sally.launcher.ThemeSettings
import com.sally.launcher.core.engine.MinecraftAccountType
import com.sally.launcher.core.engine.MinecraftLaunchSessionStore
import com.sally.launcher.core.engine.OfflineAccountProfile

/** Manages Pojav-style local/offline identities without claiming that they are authenticated accounts. */
class LocalAccountActivity : AppCompatActivity() {
    private var accounts by mutableStateOf<List<OfflineAccountProfile>>(emptyList())
    private var username by mutableStateOf("")
    private var status by mutableStateOf("No account selected.")
    private var activeName by mutableStateOf<String?>(null)
    private var activeType by mutableStateOf<MinecraftAccountType?>(null)
    private var deleteTarget by mutableStateOf<OfflineAccountProfile?>(null)
    private var inputError by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        refreshAccounts()
        setContent {
            SallyLauncherTheme(settings = ThemeSettings.read(this)) {
                LocalAccountsScreen(
                    accounts = accounts,
                    username = username,
                    status = status,
                    activeName = activeName,
                    activeType = activeType,
                    deleteTarget = deleteTarget,
                    inputError = inputError,
                    onUsernameChange = { username = it; inputError = null },
                    onCreate = ::createAccount,
                    onActivate = ::activateAccount,
                    onDeleteRequest = { deleteTarget = it },
                    onDeleteConfirm = { target ->
                        LocalAccountRepository.remove(this@LocalAccountActivity, target.username)
                        deleteTarget = null
                        setResult(Activity.RESULT_OK)
                        refreshAccounts()
                    },
                    onDismissDelete = { deleteTarget = null },
                    onBack = { setResult(Activity.RESULT_OK); finish() },
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        refreshAccounts()
    }

    private fun createAccount() {
        val profile = runCatching { OfflineAccountProfile.create(username) }.getOrElse { error ->
            inputError = error.message ?: "Enter a valid username."
            return
        }
        runCatching { LocalAccountRepository.add(this, profile.username) }
            .onSuccess { account ->
                username = ""
                inputError = null
                status = "Active local account: ${account.username}. No online authentication token is used."
                setResult(Activity.RESULT_OK)
                refreshAccounts()
            }
            .onFailure { error -> status = error.message ?: "Could not save local account." }
    }

    private fun activateAccount(account: OfflineAccountProfile) {
        runCatching { LocalAccountRepository.activate(this, account.username) }
            .onSuccess {
                status = "Using ${it.username}. This is a local/offline profile."
                setResult(Activity.RESULT_OK)
                refreshAccounts()
            }
            .onFailure { status = it.message ?: "Could not activate account." }
    }

    private fun refreshAccounts() {
        val active = MinecraftLaunchSessionStore.current()
        activeName = active?.username
        activeType = active?.accountType
        status = when {
            active?.accountType == MinecraftAccountType.LOCAL -> "Active local account: ${active.username}"
            active?.accountType == MinecraftAccountType.MICROSOFT -> "Active Microsoft account: ${active.username}"
            else -> "No account selected."
        }
        accounts = LocalAccountRepository.list(this)
    }
}

@Composable
private fun LocalAccountsScreen(
    accounts: List<OfflineAccountProfile>,
    username: String,
    status: String,
    activeName: String?,
    activeType: MinecraftAccountType?,
    deleteTarget: OfflineAccountProfile?,
    inputError: String?,
    onUsernameChange: (String) -> Unit,
    onCreate: () -> Unit,
    onActivate: (OfflineAccountProfile) -> Unit,
    onDeleteRequest: (OfflineAccountProfile) -> Unit,
    onDeleteConfirm: (OfflineAccountProfile) -> Unit,
    onDismissDelete: () -> Unit,
    onBack: () -> Unit,
) {
    Scaffold { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(onClick = onBack, contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp)) {
                        Icon(Icons.Outlined.ArrowBack, contentDescription = "Back")
                    }
                    Column(Modifier.weight(1f)) {
                        Text("SALLY LAUNCHER · ACCOUNT", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp)
                        Text("Local profiles", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                    }
                }
            }
            item {
                Card(shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                    Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Outlined.AccountCircle, contentDescription = null, modifier = Modifier.size(36.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer)
                        Text("Offline identity", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer)
                        Text("Create a local profile using a Minecraft username. No Microsoft login or token is used. This is for single-player/local play and servers explicitly configured to allow offline-mode identities; online-mode servers require an official account.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
                    }
                }
            }
            item {
                OutlinedCard(shape = RoundedCornerShape(22.dp)) {
                    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("Create profile", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        OutlinedTextField(
                            value = username,
                            onValueChange = onUsernameChange,
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            isError = inputError != null,
                            label = { Text("Minecraft username") },
                            supportingText = { Text(inputError ?: "3–16 letters, numbers, or underscores") },
                            leadingIcon = { Icon(Icons.Outlined.AccountCircle, null) },
                        )
                        Button(onClick = onCreate, enabled = username.isNotBlank(), modifier = Modifier.fillMaxWidth()) {
                            Icon(Icons.Outlined.AccountCircle, null); Spacer(Modifier.width(8.dp)); Text("Create and use profile")
                        }
                    }
                }
            }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Saved accounts", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text(status, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (accounts.isEmpty()) {
                item {
                    OutlinedCard(shape = RoundedCornerShape(20.dp)) {
                        Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Outlined.AccountCircle, null, modifier = Modifier.size(38.dp), tint = MaterialTheme.colorScheme.primary)
                            Text("No saved local accounts", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                            Text("Create a profile above to use an offline identity.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
            items(accounts, key = { it.username.lowercase() }) { account ->
                OutlinedCard(shape = RoundedCornerShape(20.dp)) {
                    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Icon(Icons.Outlined.AccountCircle, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(32.dp))
                            Column(Modifier.weight(1f)) {
                                Text(account.username, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                Text("Local / offline profile", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            if (activeType == MinecraftAccountType.LOCAL && activeName.equals(account.username, true)) {
                                Icon(Icons.Outlined.CheckCircle, contentDescription = "Selected", tint = MaterialTheme.colorScheme.primary)
                            }
                        }
                        Text("UUID: ${account.uuid}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = { onActivate(account) },
                                enabled = !(activeType == MinecraftAccountType.LOCAL && activeName.equals(account.username, true)),
                                modifier = Modifier.weight(1f),
                            ) { Text(if (activeType == MinecraftAccountType.LOCAL && activeName.equals(account.username, true)) "Selected" else "Use account") }
                            OutlinedButton(onClick = { onDeleteRequest(account) }) {
                                Icon(Icons.Outlined.DeleteOutline, null); Spacer(Modifier.width(4.dp)); Text("Delete")
                            }
                        }
                    }
                }
            }
        }
    }
    deleteTarget?.let { account ->
        AlertDialog(
            onDismissRequest = onDismissDelete,
            title = { Text("Delete local account?") },
            text = { Text("Remove ${account.username} from this launcher? This does not delete Minecraft worlds.") },
            confirmButton = { TextButton(onClick = { onDeleteConfirm(account) }) { Text("Delete") } },
            dismissButton = { TextButton(onClick = onDismissDelete) { Text("Cancel") } },
        )
    }
}
