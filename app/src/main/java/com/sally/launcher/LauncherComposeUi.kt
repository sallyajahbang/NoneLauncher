package com.sally.launcher

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.sally.launcher.core.engine.OfflineAccountProfile
import com.sally.launcher.data.model.MinecraftVersion
import com.sally.launcher.data.skin.SkinModel
import java.util.Locale
import kotlin.math.roundToInt

// Three destinations only. Java and account live inside Pengaturan (dialog and sheet).
internal enum class LauncherTab(val label: String, val icon: ImageVector) {
    HOME("Beranda", Icons.Outlined.Home),
    VERSIONS("Versi", Icons.Outlined.Apps),
    SETTINGS("Pengaturan", Icons.Outlined.Settings),
}

// Filters used by the Versi screen.
internal enum class VersionFilter(val label: String) {
    INSTALLED("Terpasang"), RELEASE("Release"), SNAPSHOT("Snapshot"), OLD("Lama"), OTHER("Lainnya")
}

/** Which account dialog is on screen. NONE means no account dialog is open. */
internal enum class AccountStep { NONE, PICK, ADD, OFFLINE_NAME }

internal data class LauncherDialog(
    val title: String,
    val message: String,
    /** When set, the dialog offers a Salin button that copies this full text, for sharing debug output. */
    val copyText: String? = null,
    val confirmText: String = "OK",
    val onConfirm: (() -> Unit)? = null,
    val dismissText: String? = null,
    val onDismiss: (() -> Unit)? = null,
    val neutralText: String? = null,
    val onNeutral: (() -> Unit)? = null,
)

internal data class LauncherUiState(
    val tab: LauncherTab = LauncherTab.HOME,
    val versions: List<MinecraftVersion> = emptyList(),
    val installedVersions: List<MinecraftVersion> = emptyList(),
    val selectedVersionId: String = "",
    val filter: VersionFilter = VersionFilter.INSTALLED,
    val catalogStatus: String = "Memuat katalog versi resmi…",
    val runtimeStatus: String = "Memeriksa runtime Java…",
    val engineStatus: String = "Siap untuk diagnostik.",
    val accountStatus: String = "Memeriksa akun…",
    val loadingCatalog: Boolean = true,
    val refreshing: Boolean = false,
    val busyAction: String? = null,
    /** Bytes moved so far by the active download, and the planned total. Drives the progress bar. */
    val transferDone: Long = 0L,
    val transferTotal: Long = 0L,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val developerMode: Boolean = false,
    val keepScreenOn: Boolean = false,
    val installedJavaMajors: List<Int> = emptyList(),
    val supportedJavaMajors: List<Int> = listOf(8, 17),
    val dialog: LauncherDialog? = null,
    /** Which account dialog is open. */
    val accountStep: AccountStep = AccountStep.NONE,
    /** Offline profile names saved on this device. */
    val accounts: List<String> = emptyList(),
    /** Name of the identity Play will use, or null when no account is selected. */
    val activeAccount: String? = null,
    /** Microsoft account name when signed in with Microsoft, otherwise null. */
    val microsoftAccount: String? = null,
    /** Skin model (Steve or Alex) of the active account. */
    val skinModel: SkinModel = SkinModel.STEVE,
    /** Default skin texture of the active account. Null until a Minecraft client JAR is installed. */
    val skin: ImageBitmap? = null,
)

internal data class LauncherActions(
    val navigate: (LauncherTab) -> Unit,
    val setFilter: (VersionFilter) -> Unit,
    val selectVersion: (String) -> Unit,
    val refreshCatalog: () -> Unit,
    val installVersion: (String) -> Unit,
    val redownloadVersion: (String) -> Unit,
    val play: () -> Unit,
    val installRuntime: () -> Unit,
    val redownloadRuntime: () -> Unit,
    val installJava: (Int) -> Unit,
    val signInMicrosoft: () -> Unit,
    val setAccountStep: (AccountStep) -> Unit,
    val activateAccount: (String) -> Unit,
    val removeAccount: (String) -> Unit,
    val addOfflineAccount: (String, SkinModel) -> Unit,
    val prepareProfile: () -> Unit,
    val probeJvm: () -> Unit,
    val testJvm: () -> Unit,
    val showLaunchArgs: () -> Unit,
    val testGraphics: () -> Unit,
    val testPojav: () -> Unit,
    val setThemeMode: (ThemeMode) -> Unit,
    val requestDeveloperMode: (Boolean) -> Unit,
    val setKeepScreenOn: (Boolean) -> Unit,
    val dismissDialog: () -> Unit,
)

@Composable
internal fun SallyLauncherRoot(state: LauncherUiState, actions: LauncherActions) {
    SallyLauncherTheme(settings = ThemeSettings(mode = state.themeMode)) {
        val wide = LocalConfiguration.current.screenWidthDp >= 600
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = MaterialTheme.colorScheme.background,
            contentColor = MaterialTheme.colorScheme.onBackground,
            // Fullscreen: the system bars are hidden, so do not reserve space for them.
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            bottomBar = { if (!wide) LauncherBottomBar(state, actions) },
        ) { padding ->
            Row(Modifier.fillMaxSize().padding(padding)) {
                if (wide) LauncherRail(state, actions)
                LauncherContent(
                    state = state,
                    actions = actions,
                    onOpenAccount = { actions.setAccountStep(AccountStep.PICK) },
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                )
            }
        }
        AccountDialogs(state = state, actions = actions)
        state.dialog?.let { dialog -> LauncherAlert(dialog = dialog, actions = actions) }
    }
}

@Composable
private fun LauncherBottomBar(state: LauncherUiState, actions: LauncherActions) {
    NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainer) {
        LauncherTab.entries.forEach { tab ->
            NavigationBarItem(
                selected = state.tab == tab,
                onClick = { actions.navigate(tab) },
                icon = { Icon(tab.icon, contentDescription = tab.label) },
                label = { Text(tab.label) },
            )
        }
    }
}

@Composable
private fun LauncherRail(state: LauncherUiState, actions: LauncherActions) {
    NavigationRail(
        modifier = Modifier.fillMaxHeight(),
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        LauncherTab.entries.forEach { tab ->
            NavigationRailItem(
                selected = state.tab == tab,
                onClick = { actions.navigate(tab) },
                icon = { Icon(tab.icon, contentDescription = tab.label) },
                label = { Text(tab.label) },
            )
        }
    }
}

@Composable
private fun LauncherContent(state: LauncherUiState, actions: LauncherActions, onOpenAccount: () -> Unit, modifier: Modifier) {
    when (state.tab) {
        LauncherTab.HOME -> HomeScreen(state, actions, onOpenAccount, modifier)
        LauncherTab.VERSIONS -> VersionsScreen(state, actions, modifier)
        LauncherTab.SETTINGS -> SettingsScreen(state, actions, onOpenAccount, modifier)
    }
}

// ---------------------------------------------------------------- Dialog helpers

/** Keeps the status and navigation bars hidden inside dialog windows, which otherwise show them. */
@Composable
private fun DialogWindowEffect() {
    val view = LocalView.current
    SideEffect {
        (view.parent as? DialogWindowProvider)?.window?.let { window ->
            WindowInsetsControllerCompat(window, window.decorView).apply {
                systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                hide(WindowInsetsCompat.Type.systemBars())
            }
        }
    }
}

/** Caps dialog body height and makes long content scrollable, so dialogs stay compact. */
@Composable
private fun dialogScrollModifier(): Modifier =
    Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState())

@Composable
private fun LauncherAlert(dialog: LauncherDialog, actions: LauncherActions) {
    val clipboard = LocalClipboardManager.current
    AlertDialog(
        onDismissRequest = actions.dismissDialog,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        titleContentColor = MaterialTheme.colorScheme.onSurface,
        textContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        title = { Text(dialog.title) },
        text = {
            DialogWindowEffect()
            Column(dialogScrollModifier()) {
                SelectionContainer {
                    Text(dialog.message, style = MaterialTheme.typography.bodyMedium)
                }
            }
        },
        confirmButton = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.End) {
                val copy = dialog.copyText
                if (copy != null) {
                    TextButton(onClick = { clipboard.setText(AnnotatedString(copy)) }) { Text("Salin") }
                }
                if (dialog.dismissText != null) {
                    TextButton(onClick = { actions.dismissDialog(); dialog.onDismiss?.invoke() }) { Text(dialog.dismissText) }
                }
                if (dialog.neutralText != null) {
                    TextButton(onClick = { actions.dismissDialog(); dialog.onNeutral?.invoke() }) { Text(dialog.neutralText) }
                }
                TextButton(onClick = { actions.dismissDialog(); dialog.onConfirm?.invoke() }) { Text(dialog.confirmText) }
            }
        },
    )
}

// ---------------------------------------------------------------- Akun (dialog)

@Composable
private fun AccountDialogs(state: LauncherUiState, actions: LauncherActions) {
    when (state.accountStep) {
        AccountStep.NONE -> Unit
        AccountStep.PICK -> AccountPickDialog(state, actions)
        AccountStep.ADD -> AccountAddDialog(actions)
        AccountStep.OFFLINE_NAME -> OfflineNameDialog(actions)
    }
}

@Composable
private fun AccountPickDialog(state: LauncherUiState, actions: LauncherActions) {
    val close = { actions.setAccountStep(AccountStep.NONE) }
    AlertDialog(
        onDismissRequest = close,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        titleContentColor = MaterialTheme.colorScheme.onSurface,
        textContentColor = MaterialTheme.colorScheme.onSurface,
        title = { Text("Pilih akun") },
        text = {
            DialogWindowEffect()
            Column(dialogScrollModifier(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                state.microsoftAccount?.let { name ->
                    AccountRow(name = name, detail = "Microsoft", selected = true, onClick = close)
                }
                state.accounts.forEach { name ->
                    val selected = state.microsoftAccount == null &&
                        name.equals(state.activeAccount, ignoreCase = true)
                    AccountRow(
                        name = name,
                        detail = "Offline",
                        selected = selected,
                        onClick = { actions.activateAccount(name) },
                        onRemove = { actions.removeAccount(name) },
                    )
                }
                if (state.microsoftAccount == null && state.accounts.isEmpty()) {
                    Text(
                        "Belum ada akun. Tambahkan akun Microsoft atau offline.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { actions.setAccountStep(AccountStep.ADD) }) { Text("Tambah akun") }
        },
        dismissButton = { TextButton(onClick = close) { Text("Tutup") } },
    )
}

@Composable
private fun AccountRow(name: String, detail: String, selected: Boolean, onClick: () -> Unit, onRemove: (() -> Unit)? = null) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(start = 12.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                name,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            )
            Text(
                detail,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (selected) {
            Icon(Icons.Outlined.Check, contentDescription = "Dipakai", tint = MaterialTheme.colorScheme.primary)
        }
        if (onRemove != null) {
            IconButton(onClick = onRemove) {
                Icon(Icons.Outlined.Delete, contentDescription = "Hapus akun", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun AccountAddDialog(actions: LauncherActions) {
    val close = { actions.setAccountStep(AccountStep.NONE) }
    AlertDialog(
        onDismissRequest = close,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        titleContentColor = MaterialTheme.colorScheme.onSurface,
        textContentColor = MaterialTheme.colorScheme.onSurface,
        title = { Text("Tambah akun") },
        text = {
            DialogWindowEffect()
            Column(dialogScrollModifier(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "Pilih jenis akun yang akan ditambahkan.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Button(
                    onClick = { close(); actions.signInMicrosoft() },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Microsoft") }
                OutlinedButton(
                    onClick = { actions.setAccountStep(AccountStep.OFFLINE_NAME) },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Offline") }
            }
        },
        confirmButton = { TextButton(onClick = close) { Text("Batal") } },
    )
}

@Composable
private fun OfflineNameDialog(actions: LauncherActions) {
    var name by remember { mutableStateOf("") }
    var model by remember { mutableStateOf(SkinModel.STEVE) }
    val error: String? = if (name.isBlank()) null else runCatching { OfflineAccountProfile.create(name) }.exceptionOrNull()?.message
    val close = { actions.setAccountStep(AccountStep.NONE) }
    AlertDialog(
        onDismissRequest = close,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        titleContentColor = MaterialTheme.colorScheme.onSurface,
        textContentColor = MaterialTheme.colorScheme.onSurface,
        title = { Text("Akun offline") },
        text = {
            DialogWindowEffect()
            Column(dialogScrollModifier(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("Nama pemain") },
                    isError = error != null,
                    supportingText = { Text(error ?: "3–16 karakter: huruf, angka, atau _") },
                )
                Text("Skin", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    SkinModel.entries.forEachIndexed { index, option ->
                        SegmentedButton(
                            selected = model == option,
                            onClick = { model = option },
                            shape = SegmentedButtonDefaults.itemShape(index = index, count = SkinModel.entries.size),
                            label = { Text(option.label) },
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { actions.addOfflineAccount(name.trim(), model) },
                enabled = name.isNotBlank() && error == null,
            ) { Text("Simpan") }
        },
        dismissButton = { TextButton(onClick = close) { Text("Batal") } },
    )
}

// ---------------------------------------------------------------- Beranda

@Composable
private fun HomeScreen(state: LauncherUiState, actions: LauncherActions, onOpenAccount: () -> Unit, modifier: Modifier) {
    val active = activeVersion(state)
    val busy = state.busyAction != null
    val javaReady = state.installedJavaMajors.isNotEmpty()
    Column(modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 16.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    "Sally Launcher",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    homeStatusText(state, hasActive = active != null, javaReady = javaReady, busy = busy),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = onOpenAccount) {
                SkinAvatar(skin = state.skin, avatarSize = 40.dp)
            }
        }

        // Centre: the 3D skin preview of the active account.
        Column(
            Modifier.weight(1f).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            SkinPreview(
                skin = state.skin,
                slim = state.skinModel.slim,
                modifier = Modifier.fillMaxWidth().weight(1f),
            )
            Spacer(Modifier.height(8.dp))
            Text(
                state.activeAccount ?: "Belum ada akun",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
            )
            Text(
                active?.let { "Minecraft ${it.id}" } ?: "Belum ada versi terpasang",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }

        // Bottom: progress on the left, version selector and Play stacked on the right.
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Column(Modifier.weight(1f)) {
                if (busy) {
                    TransferCard(status = busyStatus(state), done = state.transferDone, total = state.transferTotal)
                } else if (active != null) {
                    Row {
                        if (!javaReady) TextButton(onClick = actions.installRuntime) { Text("Pasang Java") }
                        TextButton(onClick = { actions.installVersion(active.id) }) { Text("Perbaiki file") }
                    }
                }
            }
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                VersionSelector(state = state, actions = actions)
                Button(
                    onClick = actions.play,
                    enabled = active != null && !busy,
                    shape = RoundedCornerShape(28.dp),
                    modifier = Modifier.width(200.dp).height(56.dp),
                ) {
                    Icon(Icons.Outlined.PlayArrow, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Main", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

private fun homeStatusText(state: LauncherUiState, hasActive: Boolean, javaReady: Boolean, busy: Boolean): String = when {
    busy -> "Sedang memproses…"
    !hasActive -> "Pasang satu versi di tab Versi untuk mulai."
    javaReady -> "Siap dimainkan · Java ${state.installedJavaMajors.joinToString(", ")}"
    else -> "Java belum terpasang."
}

/** Dropdown-style version picker: a field that opens a menu of installed versions. */
@Composable
private fun VersionSelector(state: LauncherUiState, actions: LauncherActions) {
    var open by remember { mutableStateOf(false) }
    val active = activeVersion(state)
    val enabled = state.installedVersions.isNotEmpty() && state.busyAction == null
    Box(Modifier.width(200.dp)) {
        Surface(
            onClick = { open = true },
            enabled = enabled,
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        "Versi",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        active?.id ?: "Pilih versi",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Icon(Icons.Outlined.ArrowDropDown, contentDescription = null)
            }
        }
        DropdownMenu(
            expanded = open,
            onDismissRequest = { open = false },
            modifier = Modifier.width(200.dp),
        ) {
            state.installedVersions.forEach { version ->
                val current = version.id == active?.id
                DropdownMenuItem(
                    text = { Text(version.id, fontWeight = if (current) FontWeight.SemiBold else FontWeight.Normal) },
                    onClick = {
                        open = false
                        actions.selectVersion(version.id)
                    },
                    trailingIcon = {
                        if (current) Icon(Icons.Outlined.Check, contentDescription = null)
                    },
                )
            }
        }
    }
}

// ---------------------------------------------------------------- Versi

@Composable
private fun VersionsScreen(state: LauncherUiState, actions: LauncherActions, modifier: Modifier) {
    var query by remember { mutableStateOf("") }
    var sheetVersionId by remember { mutableStateOf<String?>(null) }
    val baseList = when (state.filter) {
        VersionFilter.INSTALLED -> state.installedVersions
        VersionFilter.RELEASE -> state.versions.filter { it.type.equals("release", true) }
        VersionFilter.SNAPSHOT -> state.versions.filter { it.type.equals("snapshot", true) }
        VersionFilter.OLD -> state.versions.filter { it.type.equals("old_beta", true) || it.type.equals("old_alpha", true) }
        VersionFilter.OTHER -> state.versions.filter { filterForVersionType(it.type) == VersionFilter.OTHER }
    }
    val list = baseList.filter { query.isBlank() || it.id.contains(query.trim(), ignoreCase = true) }
    val busy = state.busyAction != null

    LazyColumn(
        modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        item {
            Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Versi", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                    Text(
                        if (state.versions.isEmpty()) "Memuat daftar versi…"
                        else "${state.installedVersions.size} terpasang dari ${state.versions.size} versi",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = actions.refreshCatalog, enabled = !state.refreshing) {
                    if (state.refreshing) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    else Icon(Icons.Outlined.Refresh, contentDescription = "Segarkan")
                }
            }
        }
        item {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                placeholder = { Text("Cari versi") },
                leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                shape = RoundedCornerShape(28.dp),
            )
        }
        item {
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                VersionFilter.entries.forEach { filter ->
                    FilterChip(
                        selected = state.filter == filter,
                        onClick = { actions.setFilter(filter) },
                        label = { Text(filter.label) },
                    )
                }
            }
        }
        if (busy) {
            item {
                TransferCard(
                    status = busyStatus(state),
                    done = state.transferDone,
                    total = state.transferTotal,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
            }
        }
        when {
            state.loadingCatalog && list.isEmpty() && state.filter != VersionFilter.INSTALLED -> item {
                Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            }
            list.isEmpty() -> item {
                EmptyVersions(state = state, actions = actions)
            }
            else -> items(list, key = { it.id }) { version ->
                VersionRow(
                    version = version,
                    installed = state.installedVersions.any { it.id == version.id },
                    inUse = state.selectedVersionId == version.id,
                    onClick = { sheetVersionId = version.id },
                )
            }
        }
    }

    sheetVersionId?.let { id ->
        (state.versions + state.installedVersions).firstOrNull { it.id == id }?.let { version ->
            VersionSheet(version = version, state = state, actions = actions, onDismiss = { sheetVersionId = null })
        }
    }
}

@Composable
private fun EmptyVersions(state: LauncherUiState, actions: LauncherActions) {
    val installedFilter = state.filter == VersionFilter.INSTALLED
    Column(
        Modifier.fillMaxWidth().padding(vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            if (installedFilter) "Belum ada versi terpasang" else "Tidak ada versi di kategori ini",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            if (installedFilter) "Pilih Release lalu pasang satu versi." else "Segarkan daftar atau periksa koneksi.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        TextButton(onClick = {
            if (installedFilter) actions.setFilter(VersionFilter.RELEASE) else actions.refreshCatalog()
        }) {
            Text(if (installedFilter) "Lihat Release" else "Segarkan")
        }
    }
}

@Composable
private fun VersionRow(version: MinecraftVersion, installed: Boolean, inUse: Boolean, onClick: () -> Unit) {
    ListItem(
        headlineContent = {
            Text(version.id, fontWeight = if (inUse) FontWeight.Bold else FontWeight.Normal)
        },
        supportingContent = {
            Text("${versionFilterLabel(version.type)} · ${version.releaseTime.take(10).ifBlank { "-" }}")
        },
        trailingContent = {
            when {
                inUse -> Text("Dipakai", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                installed -> Text("Terpasang", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                else -> Unit
            }
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).clickable(onClick = onClick),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun VersionSheet(version: MinecraftVersion, state: LauncherUiState, actions: LauncherActions, onDismiss: () -> Unit) {
    val installed = state.installedVersions.any { it.id == version.id }
    val busy = state.busyAction == "install:${version.id}" || state.busyAction == "redownload:${version.id}"
    val inUse = state.selectedVersionId == version.id
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(version.id, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text(
                "${versionFilterLabel(version.type)} · ${version.releaseTime.take(10).ifBlank { "-" }}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (busy) {
                TransferCard(status = state.catalogStatus, done = state.transferDone, total = state.transferTotal)
            }
            if (installed) {
                Button(
                    onClick = { onDismiss(); actions.selectVersion(version.id) },
                    enabled = !busy && !inUse,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(if (inUse) "Sedang dipakai" else "Pakai di Beranda") }
                OutlinedButton(
                    onClick = { onDismiss(); actions.redownloadVersion(version.id) },
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Unduh ulang") }
            } else {
                Button(
                    onClick = { onDismiss(); actions.installVersion(version.id) },
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(if (busy) "Sedang dipasang…" else "Pasang") }
            }
        }
    }
}

// ---------------------------------------------------------------- Pengaturan

@Composable
private fun SettingsScreen(state: LauncherUiState, actions: LauncherActions, onOpenAccount: () -> Unit, modifier: Modifier) {
    var javaOpen by remember { mutableStateOf(false) }
    var versionTaps by remember { mutableIntStateOf(0) }
    LazyColumn(
        modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        item {
            Text("Pengaturan", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        }
        item {
            SettingsGroup("Tampilan") {
                Column(Modifier.fillMaxWidth().padding(16.dp)) {
                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                        ThemeMode.entries.forEachIndexed { index, mode ->
                            SegmentedButton(
                                selected = state.themeMode == mode,
                                onClick = { actions.setThemeMode(mode) },
                                shape = SegmentedButtonDefaults.itemShape(index = index, count = ThemeMode.entries.size),
                                label = { Text(themeModeLabel(mode)) },
                            )
                        }
                    }
                }
            }
        }
        item {
            SettingsGroup("Akun") {
                SettingRow(title = "Akun", supporting = state.accountStatus, onClick = onOpenAccount)
            }
        }
        item {
            SettingsGroup("Java") {
                SettingRow(title = "Runtime Java", supporting = state.runtimeStatus, onClick = { javaOpen = true })
            }
        }
        item {
            SettingsGroup("Umum") {
                SwitchRow(
                    title = "Layar tetap menyala",
                    supporting = "Cegah layar mati saat launcher terbuka.",
                    checked = state.keepScreenOn,
                    onCheckedChange = actions.setKeepScreenOn,
                )
            }
        }
        if (state.developerMode) {
            item {
                SettingsGroup(DeveloperOptionsText.GROUP_TITLE) {
                    val devCallbacks = mapOf(
                        "prepare" to actions.prepareProfile,
                        "probe" to actions.probeJvm,
                        "smoke" to actions.testJvm,
                        "graphics" to actions.testGraphics,
                        "argv" to actions.showLaunchArgs,
                        "pojav" to actions.testPojav,
                    )
                    DeveloperOptionsText.actions.forEach { action ->
                        DevRow(
                            title = action.title,
                            description = action.description,
                            busyKey = action.busyKey,
                            busyAction = state.busyAction,
                            onClick = devCallbacks.getValue(action.id),
                        )
                    }
                    TextButton(
                        onClick = { actions.requestDeveloperMode(false) },
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    ) { Text(DeveloperOptionsText.DISABLE_LABEL) }
                }
            }
        }
        item {
            SettingsGroup("Tentang") {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clickable {
                            versionTaps++
                            if (versionTaps >= 7) {
                                versionTaps = 0
                                actions.requestDeveloperMode(!state.developerMode)
                            }
                        }
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text("Sally Launcher 0.1.0", style = MaterialTheme.typography.titleMedium)
                    Text(
                        DeveloperOptionsText.ENABLE_HINT,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        "Terinspirasi Amethyst-Android dan PojavLauncher (LGPL-3.0).",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }

    if (javaOpen) JavaDialog(state = state, actions = actions, onDismiss = { javaOpen = false })
}

@Composable
private fun JavaDialog(state: LauncherUiState, actions: LauncherActions, onDismiss: () -> Unit) {
    var chosenMajor by remember(state.supportedJavaMajors) {
        mutableIntStateOf(state.supportedJavaMajors.firstOrNull() ?: 17)
    }
    val javaBusy = state.busyAction == "runtime" || state.busyAction == "runtime-redownload"
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        titleContentColor = MaterialTheme.colorScheme.onSurface,
        textContentColor = MaterialTheme.colorScheme.onSurface,
        title = { Text("Runtime Java") },
        text = {
            DialogWindowEffect()
            Column(dialogScrollModifier(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (javaBusy) {
                    TransferCard(status = state.runtimeStatus, done = state.transferDone, total = state.transferTotal)
                } else {
                    Text(
                        if (state.installedJavaMajors.isEmpty()) "Belum ada Java terpasang." else "Terpasang: " + state.installedJavaMajors.joinToString(", ") { "Java $it" },
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                state.supportedJavaMajors.forEachIndexed { index, major ->
                    val installed = major in state.installedJavaMajors
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable(enabled = !javaBusy) { chosenMajor = major }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = chosenMajor == major, onClick = { chosenMajor = major }, enabled = !javaBusy)
                        Text("Java $major", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                        when {
                            installed -> Text("Terpasang", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                            index == 0 -> Text("Disarankan", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            else -> Unit
                        }
                    }
                }
                Text(
                    "1.16.5 ke bawah → Java 8 · 1.17–1.20.4 → Java 17 · 1.20.5 ke atas → Java 21 (belum didukung).",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                TextButton(
                    onClick = { onDismiss(); actions.redownloadRuntime() },
                    enabled = !javaBusy && state.busyAction == null,
                ) { Text("Unduh ulang Java untuk versi terpilih") }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { actions.installJava(chosenMajor) },
                enabled = !javaBusy && state.busyAction == null,
            ) { Text(if (javaBusy) "Memasang…" else "Pasang") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Tutup") } },
    )
}

// ---------------------------------------------------------------- Shared pieces

/** Progress for a download or install. The bar moves with bytes, and the label shows MB done / MB total. */
@Composable
private fun TransferCard(status: String, done: Long, total: Long, modifier: Modifier = Modifier) {
    val known = total > 0L
    val fraction = if (known) (done.toFloat() / total.toFloat()).coerceIn(0f, 1f) else 0f
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                status,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (known) {
                Text(
                    "${(fraction * 100).roundToInt()}%",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
        if (known) {
            LinearProgressIndicator(progress = { fraction }, modifier = Modifier.fillMaxWidth())
            Text(
                "${formatBytes(done.coerceAtMost(total))} dari ${formatBytes(total)}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun SettingsGroup(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            title,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 4.dp),
        )
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(content = content)
        }
    }
}

@Composable
private fun SettingRow(title: String, supporting: String?, onClick: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        if (supporting != null) {
            Text(
                supporting,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun SwitchRow(title: String, supporting: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(supporting, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun DevRow(
    title: String,
    description: String,
    busyKey: String?,
    busyAction: String?,
    onClick: () -> Unit,
) {
    val running = busyKey != null && busyAction == busyKey
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        FilledTonalButton(
            onClick = onClick,
            enabled = busyAction == null,
            modifier = Modifier.align(Alignment.End),
        ) {
            Text(if (running) "Berjalan…" else "Jalankan")
        }
    }
}

private fun activeVersion(state: LauncherUiState): MinecraftVersion? =
    state.installedVersions.firstOrNull { it.id == state.selectedVersionId } ?: state.installedVersions.firstOrNull()

/** Which operation is running decides which status line is the most useful to show. */
private fun busyStatus(state: LauncherUiState): String {
    val action = state.busyAction.orEmpty()
    return when {
        action.startsWith("runtime") -> state.runtimeStatus
        action.startsWith("install") || action.startsWith("redownload") -> state.catalogStatus
        else -> state.engineStatus
    }
}

private const val KIB = 1024L
private const val MIB = KIB * 1024L
private const val GIB = MIB * 1024L

internal fun formatBytes(bytes: Long): String = when {
    bytes >= GIB -> String.format(Locale.US, "%.2f GB", bytes.toDouble() / GIB)
    bytes >= MIB -> String.format(Locale.US, "%.1f MB", bytes.toDouble() / MIB)
    bytes >= KIB -> String.format(Locale.US, "%.0f KB", bytes.toDouble() / KIB)
    else -> "$bytes B"
}

private fun themeModeLabel(mode: ThemeMode): String = when (mode) {
    ThemeMode.SYSTEM -> "Sistem"
    ThemeMode.LIGHT -> "Terang"
    ThemeMode.DARK -> "Gelap"
}

private fun filterForVersionType(type: String): VersionFilter = when (type.lowercase()) {
    "release" -> VersionFilter.RELEASE
    "snapshot" -> VersionFilter.SNAPSHOT
    "old_beta", "old_alpha" -> VersionFilter.OLD
    else -> VersionFilter.OTHER
}

private fun versionFilterLabel(type: String): String = when (type.lowercase()) {
    "release" -> "Vanilla · Release"
    "snapshot" -> "Snapshot"
    "old_beta" -> "Old Beta"
    "old_alpha" -> "Old Alpha"
    else -> type.replace('_', ' ').replaceFirstChar { it.uppercase() }
}
