@file:OptIn(ExperimentalMaterial3Api::class)

package com.sally.launcher

import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.ArrowDropDown
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.foundation.clickable
import com.sally.launcher.data.model.MinecraftVersion

// ------------------------------------------------------------------ state

internal enum class Screen { HOME, SETTINGS }

internal enum class VersionFilter(val label: String) {
    INSTALLED("Terpasang"), RELEASE("Release"), SNAPSHOT("Snapshot"), OLD("Lawas")
}

internal data class AccountItem(val name: String, val microsoft: Boolean)

internal data class LauncherDialog(
    val title: String,
    val message: String,
    val confirmText: String = "OK",
    val onConfirm: (() -> Unit)? = null,
    val dismissText: String? = null,
)

internal data class LauncherUiState(
    val screen: Screen = Screen.HOME,
    val versions: List<MinecraftVersion> = emptyList(),
    val installedIds: Set<String> = emptySet(),
    val selectedVersionId: String = "",
    val loadingCatalog: Boolean = true,
    val busy: Boolean = false,
    val status: String = "",
    val javaMajors: List<Int> = emptyList(),
    val accounts: List<AccountItem> = emptyList(),
    val activeAccount: AccountItem? = null,
    val accountDialog: Boolean = false,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val amoledBlack: Boolean = false,
    val dynamicColor: Boolean = false,
    val keepScreenOn: Boolean = false,
    val developerMode: Boolean = false,
    val engineStatus: String = "",
    val dialog: LauncherDialog? = null,
)

internal data class LauncherActions(
    val navigate: (Screen) -> Unit,
    val play: () -> Unit,
    val selectVersion: (String) -> Unit,
    val refreshCatalog: () -> Unit,
    val openAccounts: (Boolean) -> Unit,
    val selectAccount: (AccountItem) -> Unit,
    /** Returns an error message, or null when the account was added. */
    val addOfflineAccount: (String) -> String?,
    val removeAccount: (AccountItem) -> Unit,
    val signInMicrosoft: () -> Unit,
    val repairGame: () -> Unit,
    val reinstallJava: () -> Unit,
    val setThemeMode: (ThemeMode) -> Unit,
    val setAmoledBlack: (Boolean) -> Unit,
    val setDynamicColor: (Boolean) -> Unit,
    val setKeepScreenOn: (Boolean) -> Unit,
    val requestDeveloperMode: (Boolean) -> Unit,
    val devPrepare: () -> Unit,
    val devProbe: () -> Unit,
    val devJavaTest: () -> Unit,
    val devGraphics: () -> Unit,
    val devPojav: () -> Unit,
    val dismissDialog: () -> Unit,
)

// ------------------------------------------------------------------ root

@Composable
internal fun SallyLauncherRoot(state: LauncherUiState, actions: LauncherActions) {
    SallyLauncherTheme(
        settings = ThemeSettings(mode = state.themeMode, amoled = state.amoledBlack, dynamicColor = state.dynamicColor),
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background,
            contentColor = MaterialTheme.colorScheme.onBackground,
        ) {
            when (state.screen) {
                Screen.HOME -> HomeScreen(state, actions)
                Screen.SETTINGS -> SettingsScreen(state, actions)
            }
            if (state.accountDialog) AccountDialog(state, actions)
            state.dialog?.let { dialog ->
                AlertDialog(
                    onDismissRequest = actions.dismissDialog,
                    title = { Text(dialog.title) },
                    text = { Column(Modifier.heightIn(max = 320.dp).verticalScroll(rememberScrollState())) { Text(dialog.message) } },
                    confirmButton = {
                        TextButton(onClick = { actions.dismissDialog(); dialog.onConfirm?.invoke() }) { Text(dialog.confirmText) }
                    },
                    dismissButton = if (dialog.dismissText != null) {
                        { TextButton(onClick = actions.dismissDialog) { Text(dialog.dismissText) } }
                    } else null,
                )
            }
        }
    }
}

// ------------------------------------------------------------------ home

@Composable
private fun HomeScreen(state: LauncherUiState, actions: LauncherActions) {
    val wide = LocalConfiguration.current.screenWidthDp >= 600
    var versionDialog by remember { mutableStateOf(false) }
    val selected = state.versions.firstOrNull { it.id == state.selectedVersionId }
    val installed = selected != null && selected.id in state.installedIds

    val hero: @Composable ColumnScope.() -> Unit = {
        Text("Minecraft Java", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        Text(
            selected?.id ?: if (state.loadingCatalog) "…" else "Pilih versi",
            style = MaterialTheme.typography.displayMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            when {
                selected == null -> if (state.loadingCatalog) "Memuat daftar versi…" else "Belum ada versi dipilih"
                installed -> "Terpasang · siap dimainkan"
                else -> "Akan diunduh otomatis saat Main"
            },
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }

    val controls: @Composable ColumnScope.() -> Unit = {
        SelectorRow(
            leading = state.activeAccount?.name?.take(1)?.uppercase() ?: "+",
            title = state.activeAccount?.name ?: "Pilih akun",
            subtitle = when {
                state.activeAccount == null -> "Belum ada akun"
                state.activeAccount.microsoft -> "Microsoft"
                else -> "Offline"
            },
            enabled = !state.busy,
            onClick = { actions.openAccounts(true) },
        )
        Spacer(Modifier.height(10.dp))
        SelectorRow(
            leading = "MC",
            title = selected?.id ?: "Pilih versi",
            subtitle = if (selected == null) "Ketuk untuk memilih" else versionTypeLabel(selected.type),
            enabled = !state.busy,
            onClick = { versionDialog = true },
        )
        Spacer(Modifier.height(20.dp))
        Button(
            onClick = actions.play,
            enabled = !state.busy && selected != null,
            shape = CircleShape,
            modifier = Modifier.fillMaxWidth().height(64.dp),
        ) {
            if (state.busy) {
                CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp, color = LocalContentColor.current)
                Spacer(Modifier.width(10.dp))
                Text("Menyiapkan…", style = MaterialTheme.typography.titleMedium)
            } else {
                Icon(Icons.Outlined.PlayArrow, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Main", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            }
        }
        if (state.busy) {
            LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 14.dp))
        }
        if (state.status.isNotBlank()) {
            Text(
                state.status,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            )
        }
    }

    Box(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
        if (wide) {
            Row(
                Modifier.fillMaxSize().padding(horizontal = 40.dp, vertical = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(56.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f), content = hero)
                Column(Modifier.weight(1f).widthIn(max = 380.dp), content = controls)
            }
        } else {
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, content = hero)
                Spacer(Modifier.height(32.dp))
                Column(Modifier.widthIn(max = 420.dp), content = controls)
            }
        }
        IconButton(
            onClick = { actions.navigate(Screen.SETTINGS) },
            modifier = Modifier.align(Alignment.TopEnd).padding(8.dp),
        ) { Icon(Icons.Outlined.Settings, contentDescription = "Pengaturan") }
    }

    if (versionDialog) {
        VersionDialog(state, actions, onDismiss = { versionDialog = false })
    }
}

@Composable
private fun SelectorRow(leading: String, title: String, subtitle: String, enabled: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier.padding(start = 14.dp, end = 10.dp, top = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Box(
                Modifier.size(40.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Text(leading, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onPrimaryContainer, fontWeight = FontWeight.Bold)
            }
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(Icons.Outlined.ArrowDropDown, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

// ------------------------------------------------------------------ pickers (dialogs sized for landscape)

@Composable
private fun PickerDialog(title: String, onDismiss: () -> Unit, actionsRow: @Composable (() -> Unit)? = null, content: @Composable ColumnScope.() -> Unit) {
    val maxHeight = (LocalConfiguration.current.screenHeightDp * 0.92f).dp
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier.fillMaxWidth(0.9f).widthIn(max = 480.dp).heightIn(max = maxHeight),
        ) {
            Column(Modifier.padding(vertical = 20.dp)) {
                Row(Modifier.padding(start = 24.dp, end = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                    actionsRow?.invoke()
                    IconButton(onClick = onDismiss) { Icon(Icons.Outlined.Close, contentDescription = "Tutup") }
                }
                content()
            }
        }
    }
}

@Composable
private fun VersionDialog(state: LauncherUiState, actions: LauncherActions, onDismiss: () -> Unit) {
    var query by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf(if (state.installedIds.isNotEmpty()) VersionFilter.INSTALLED else VersionFilter.RELEASE) }
    val list = remember(state.versions, state.installedIds, filter, query) {
        state.versions.filter { version ->
            val inFilter = when (filter) {
                VersionFilter.INSTALLED -> version.id in state.installedIds
                VersionFilter.RELEASE -> version.type.equals("release", true)
                VersionFilter.SNAPSHOT -> version.type.equals("snapshot", true)
                VersionFilter.OLD -> version.type.startsWith("old_", true)
            }
            inFilter && (query.isBlank() || version.id.contains(query.trim(), ignoreCase = true))
        }
    }
    PickerDialog(
        title = "Pilih versi",
        onDismiss = onDismiss,
        actionsRow = {
            IconButton(onClick = actions.refreshCatalog, enabled = !state.loadingCatalog) {
                if (state.loadingCatalog) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                else Icon(Icons.Outlined.Refresh, contentDescription = "Segarkan daftar")
            }
        },
    ) {
        Column(Modifier.padding(horizontal = 24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                singleLine = true,
                placeholder = { Text("Cari versi") },
                leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                modifier = Modifier.fillMaxWidth(),
            )
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                VersionFilter.entries.forEach { item ->
                    FilterChip(selected = filter == item, onClick = { filter = item }, label = { Text(item.label) })
                }
            }
        }
        if (list.isEmpty()) {
            Text(
                if (state.loadingCatalog) "Memuat…" else if (filter == VersionFilter.INSTALLED) "Belum ada versi terpasang. Pilih Release." else "Tidak ada versi.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(24.dp),
            )
        } else {
            LazyColumn(Modifier.weight(1f, fill = false).padding(top = 8.dp)) {
                items(list, key = { it.id }) { version ->
                    val isInstalled = version.id in state.installedIds
                    ListItem(
                        headlineContent = { Text(version.id) },
                        supportingContent = {
                            Text(
                                versionTypeLabel(version.type) + version.releaseTime.take(10).takeIf { it.isNotBlank() }?.let { " · $it" }.orEmpty() +
                                    if (isInstalled) "" else " · belum diunduh",
                            )
                        },
                        trailingContent = {
                            if (version.id == state.selectedVersionId) Icon(Icons.Outlined.Check, contentDescription = "Dipilih", tint = MaterialTheme.colorScheme.primary)
                        },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        modifier = Modifier.clickable {
                            actions.selectVersion(version.id)
                            onDismiss()
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun AccountDialog(state: LauncherUiState, actions: LauncherActions) {
    var addOpen by remember { mutableStateOf(state.accounts.isEmpty()) }
    var removeTarget by remember { mutableStateOf<AccountItem?>(null) }
    val close = { actions.openAccounts(false) }

    PickerDialog(title = "Akun", onDismiss = close) {
        if (state.accounts.isEmpty()) {
            Text(
                "Belum ada akun. Tambah akun offline untuk mulai bermain.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
            )
        } else {
            LazyColumn(Modifier.weight(1f, fill = false)) {
                items(state.accounts, key = { (if (it.microsoft) "ms:" else "off:") + it.name }) { account ->
                    ListItem(
                        headlineContent = { Text(account.name) },
                        supportingContent = { Text(if (account.microsoft) "Microsoft" else "Offline") },
                        leadingContent = { RadioButton(selected = account == state.activeAccount, onClick = null) },
                        trailingContent = if (account.microsoft) null else {
                            { IconButton(onClick = { removeTarget = account }) { Icon(Icons.Outlined.Delete, contentDescription = "Hapus ${account.name}") } }
                        },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        modifier = Modifier.clickable { actions.selectAccount(account) },
                    )
                }
            }
        }
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(top = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilledTonalButton(onClick = { addOpen = true }, modifier = Modifier.weight(1f)) { Text("Akun offline") }
            OutlinedButton(onClick = actions.signInMicrosoft, modifier = Modifier.weight(1f)) {
                Icon(Icons.Outlined.AccountCircle, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Microsoft")
            }
        }
    }

    if (addOpen) {
        var name by remember { mutableStateOf("") }
        var error by remember { mutableStateOf<String?>(null) }
        val submit = {
            val result = actions.addOfflineAccount(name)
            if (result == null) addOpen = false else error = result
        }
        AlertDialog(
            onDismissRequest = { addOpen = false },
            title = { Text("Akun offline") },
            text = {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(16); error = null },
                    singleLine = true,
                    label = { Text("Username") },
                    isError = error != null,
                    supportingText = { Text(error ?: "3–16 huruf, angka, atau _") },
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = { TextButton(onClick = submit) { Text("Tambah") } },
            dismissButton = { TextButton(onClick = { addOpen = false }) { Text("Batal") } },
        )
    }
    removeTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { removeTarget = null },
            title = { Text("Hapus ${target.name}?") },
            text = { Text("Akun offline ini akan dihapus dari launcher.") },
            confirmButton = { TextButton(onClick = { actions.removeAccount(target); removeTarget = null }) { Text("Hapus") } },
            dismissButton = { TextButton(onClick = { removeTarget = null }) { Text("Batal") } },
        )
    }
}

// ------------------------------------------------------------------ settings

@Composable
private fun SettingsScreen(state: LauncherUiState, actions: LauncherActions) {
    BackHandler { actions.navigate(Screen.HOME) }
    var creditsOpen by remember { mutableStateOf(false) }
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Pengaturan") },
                navigationIcon = {
                    IconButton(onClick = { actions.navigate(Screen.HOME) }) { Icon(Icons.Outlined.ArrowBack, contentDescription = "Kembali") }
                },
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.TopCenter) {
            Column(Modifier.widthIn(max = 640.dp).fillMaxHeight().verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
                SectionLabel("Tampilan")
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
                    ThemeMode.entries.forEachIndexed { index, mode ->
                        SegmentedButton(
                            selected = state.themeMode == mode,
                            onClick = { actions.setThemeMode(mode) },
                            shape = SegmentedButtonDefaults.itemShape(index, ThemeMode.entries.size),
                            label = { Text(themeModeLabel(mode)) },
                        )
                    }
                }
                SwitchRow("AMOLED hitam", "Latar hitam pekat di tema gelap", state.amoledBlack, actions.setAmoledBlack)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    SwitchRow("Warna dinamis", "Ikuti warna wallpaper", state.dynamicColor, actions.setDynamicColor)
                }
                SwitchRow("Layar tetap menyala", "Saat launcher terbuka", state.keepScreenOn, actions.setKeepScreenOn)

                SectionLabel("Game")
                ActionRow("Perbaiki file game", "Unduh ulang dan verifikasi ${state.selectedVersionId.ifBlank { "versi terpilih" }}", enabled = !state.busy && state.selectedVersionId.isNotBlank(), onClick = actions.repairGame)
                ActionRow(
                    "Pasang ulang Java",
                    if (state.javaMajors.isEmpty()) "Belum ada Java" else "Terpasang: " + state.javaMajors.joinToString { "Java $it" } + " (dipasang otomatis saat Main)",
                    enabled = !state.busy && state.selectedVersionId.isNotBlank(),
                    onClick = actions.reinstallJava,
                )

                SectionLabel("Lanjutan")
                SwitchRow("Mode developer", "Alat diagnostik untuk pengguna tingkat lanjut", state.developerMode, actions.requestDeveloperMode)
                if (state.developerMode) {
                    if (state.engineStatus.isNotBlank()) {
                        Text(state.engineStatus, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
                    }
                    ActionRow("Siapkan profil launch", "Cek metadata, classpath, runtime, dan penghalang", !state.busy, actions.devPrepare)
                    ActionRow("Probe loader JVM", "Cek libjli dan libjvm", !state.busy, actions.devProbe)
                    ActionRow("Tes runtime Java", "Jalankan java -version di proses terpisah", !state.busy, actions.devJavaTest)
                    ActionRow("Tes grafis & input", "Buka diagnostik GLES dan input", !state.busy, actions.devGraphics)
                    ActionRow("Tes jembatan Pojav", "Buka diagnostik surface dan JNI", !state.busy, actions.devPojav)
                }

                SectionLabel("Tentang")
                ActionRow("Sally Launcher 0.1.0", "Data game: Android/data/com.sally.launcher/files/minecraft", true) {}
                ActionRow("Kredit & lisensi", "Pojav / Amethyst (LGPL-3.0)", true) { creditsOpen = true }
            }
        }
    }
    if (creditsOpen) {
        AlertDialog(
            onDismissRequest = { creditsOpen = false },
            title = { Text("Kredit & lisensi") },
            text = {
                Text(
                    "Terinspirasi oleh Amethyst-Android (AngelAuraMC) dan PojavLauncher (PojavLauncherTeam), keduanya berlisensi LGPL-3.0. " +
                        "Tampilan didesain ulang dengan Material 3 tanpa menyalin kode UI keduanya. Lisensi dan notice engine ada di assets/engine/pojav.",
                )
            },
            confirmButton = { TextButton(onClick = { creditsOpen = false }) { Text("OK") } },
        )
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 4.dp),
    )
}

@Composable
private fun SwitchRow(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(subtitle) },
        trailingContent = { Switch(checked = checked, onCheckedChange = onChange) },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier.clickable { onChange(!checked) },
    )
}

@Composable
private fun ActionRow(title: String, subtitle: String, enabled: Boolean, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(subtitle) },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier.clickable(enabled = enabled, onClick = onClick),
    )
}

// ------------------------------------------------------------------ labels

private fun themeModeLabel(mode: ThemeMode): String = when (mode) {
    ThemeMode.SYSTEM -> "Sistem"
    ThemeMode.LIGHT -> "Terang"
    ThemeMode.DARK -> "Gelap"
}

private fun versionTypeLabel(type: String): String = when (type.lowercase()) {
    "release" -> "Release"
    "snapshot" -> "Snapshot"
    "old_beta" -> "Beta lawas"
    "old_alpha" -> "Alpha lawas"
    else -> type.replaceFirstChar { it.uppercase() }
}
