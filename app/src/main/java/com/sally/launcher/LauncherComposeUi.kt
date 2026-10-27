package com.sally.launcher

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.BugReport
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Gamepad
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material.icons.outlined.ListAlt
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import com.sally.launcher.data.model.MinecraftVersion

internal enum class LauncherTab(val label: String, val icon: ImageVector) {
    PLAY("Play", Icons.Outlined.Gamepad),
    VERSIONS("Versions", Icons.Outlined.ListAlt),
    ACCOUNT("Account", Icons.Outlined.AccountCircle),
    SETTINGS("Settings", Icons.Outlined.Settings),
    DEV("DEV", Icons.Outlined.BugReport),
}

internal enum class VersionFilter(val label: String) {
    INSTALLED("Installed"), RELEASE("Vanilla / Release"), SNAPSHOT("Snapshot"), OLD_BETA("Old Beta"), OLD_ALPHA("Old Alpha"), OTHER("Other")
}

internal data class LauncherDialog(
    val title: String,
    val message: String,
    val confirmText: String = "OK",
    val onConfirm: (() -> Unit)? = null,
    val dismissText: String? = null,
    val onDismiss: (() -> Unit)? = null,
    val neutralText: String? = null,
    val onNeutral: (() -> Unit)? = null,
)

internal data class LauncherUiState(
    val tab: LauncherTab = LauncherTab.PLAY,
    val versions: List<MinecraftVersion> = emptyList(),
    val installedVersions: List<MinecraftVersion> = emptyList(),
    val selectedVersionId: String = "",
    val managerSelectedVersionId: String = "",
    val filter: VersionFilter = VersionFilter.INSTALLED,
    val catalogStatus: String = "Loading official Minecraft versions…",
    val runtimeStatus: String = "Checking installed Java runtimes…",
    val engineStatus: String = "Ready for diagnostics.",
    val accountStatus: String = "Checking account…",
    val loadingCatalog: Boolean = true,
    val refreshing: Boolean = false,
    val busyAction: String? = null,
    val darkTheme: Boolean = true,
    val keepScreenOn: Boolean = true,
    val dialog: LauncherDialog? = null,
)

internal data class LauncherActions(
    val navigate: (LauncherTab) -> Unit,
    val setFilter: (VersionFilter) -> Unit,
    val selectVersion: (String) -> Unit,
    val refreshCatalog: () -> Unit,
    val installVersion: (String) -> Unit,
    val play: () -> Unit,
    val installRuntime: () -> Unit,
    val signInMicrosoft: () -> Unit,
    val manageLocalAccounts: () -> Unit,
    val prepareProfile: () -> Unit,
    val probeJvm: () -> Unit,
    val testJvm: () -> Unit,
    val testGraphics: () -> Unit,
    val testPojav: () -> Unit,
    val setDarkTheme: (Boolean) -> Unit,
    val setKeepScreenOn: (Boolean) -> Unit,
    val dismissDialog: () -> Unit,
)

@Composable
internal fun SallyLauncherRoot(state: LauncherUiState, actions: LauncherActions) {
    SallyMaterialTheme(darkTheme = state.darkTheme) {
        Scaffold(
            bottomBar = {
                NavigationBar {
                    LauncherTab.entries.forEach { tab ->
                        NavigationBarItem(
                            selected = state.tab == tab,
                            onClick = { actions.navigate(tab) },
                            icon = { Icon(tab.icon, contentDescription = tab.label) },
                            label = { Text(tab.label, maxLines = 1) },
                        )
                    }
                }
            },
        ) { padding ->
            when (state.tab) {
                LauncherTab.PLAY -> PlayScreen(state, actions, Modifier.padding(padding))
                LauncherTab.VERSIONS -> VersionsScreen(state, actions, Modifier.padding(padding))
                LauncherTab.ACCOUNT -> AccountScreen(state, actions, Modifier.padding(padding))
                LauncherTab.SETTINGS -> SettingsScreen(state, actions, Modifier.padding(padding))
                LauncherTab.DEV -> DevScreen(state, actions, Modifier.padding(padding))
            }
        }
        state.dialog?.let { dialog ->
            AlertDialog(
                onDismissRequest = actions.dismissDialog,
                icon = { Icon(Icons.Outlined.Info, contentDescription = null) },
                title = { Text(dialog.title) },
                text = { Column(Modifier.heightIn(max = 440.dp).verticalScroll(rememberScrollState())) { Text(dialog.message) } },
                confirmButton = {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.End) {
                        if (dialog.dismissText != null) {
                            TextButton(onClick = { actions.dismissDialog(); dialog.onDismiss?.invoke() }) { Text(dialog.dismissText) }
                        }
                        if (dialog.neutralText != null) {
                            TextButton(onClick = { actions.dismissDialog(); dialog.onNeutral?.invoke() }) { Text(dialog.neutralText) }
                        }
                        TextButton(onClick = {
                            actions.dismissDialog()
                            dialog.onConfirm?.invoke()
                        }) { Text(dialog.confirmText) }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun PlayScreen(state: LauncherUiState, actions: LauncherActions, modifier: Modifier = Modifier) {
    var versionPickerOpen by remember { mutableStateOf(false) }
    var versionQuery by remember { mutableStateOf("") }
    val activeVersion = state.installedVersions.firstOrNull { it.id == state.selectedVersionId }
        ?: state.installedVersions.firstOrNull()
    if (versionPickerOpen) {
        val matchingInstalled = state.installedVersions.filter {
            it.id.contains(versionQuery.trim(), ignoreCase = true) || versionFilterLabel(it.type).contains(versionQuery.trim(), ignoreCase = true)
        }
        AlertDialog(
            onDismissRequest = { versionPickerOpen = false },
            properties = DialogProperties(usePlatformDefaultWidth = false),
            modifier = Modifier.fillMaxWidth(.92f).widthIn(max = 520.dp),
            title = { Text("Choose installed version") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = versionQuery,
                        onValueChange = { versionQuery = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        label = { Text("Search installed versions") },
                        leadingIcon = { Icon(Icons.Outlined.FolderOpen, null) },
                    )
                    Text("${matchingInstalled.size} installed versions", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    LazyColumn(Modifier.fillMaxWidth().heightIn(max = 380.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        items(matchingInstalled, key = { "picker-${it.id}" }) { version ->
                            Surface(
                                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).clickable {
                                    actions.selectVersion(version.id)
                                    versionPickerOpen = false
                                    versionQuery = ""
                                },
                                color = if (version.id == activeVersion?.id) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerLow,
                                shape = RoundedCornerShape(14.dp),
                            ) {
                                Row(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Icon(if (version.id == activeVersion?.id) Icons.Outlined.CheckCircle else Icons.Outlined.Extension, null, tint = MaterialTheme.colorScheme.primary)
                                    Column(Modifier.weight(1f)) {
                                        Text(version.id, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                                        Text(versionFilterLabel(version.type), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { versionPickerOpen = false; versionQuery = "" }) { Text("Close") } },
        )
    }
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, top = 20.dp, end = 20.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            BrandHeader(eyebrow = "SALLY LAUNCHER", title = "Your Java world.", subtitle = "A clean, focused home for Minecraft: Java Edition.")
        }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer), shape = RoundedCornerShape(28.dp)) {
                Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Outlined.Gamepad, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
                        Text("READY TO PLAY", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onPrimaryContainer, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.weight(1f))
                        if (state.installedVersions.isNotEmpty()) StatusPill("${state.installedVersions.size} installed", true)
                    }
                    Text(activeVersion?.id ?: "No installed version yet", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer)
                    Text(
                        if (activeVersion != null) "Select an installed version and launch it directly. No need to browse the full catalog every time."
                        else "Install a Minecraft version first. Installed versions will appear here for quick launch.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                    if (activeVersion != null) {
                        Box {
                            OutlinedButton(onClick = { versionPickerOpen = true }, modifier = Modifier.fillMaxWidth(), border = BorderStroke(1.dp, MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = .28f)), colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onPrimaryContainer)) {
                                Icon(Icons.Outlined.PlayArrow, contentDescription = null)
                                Spacer(Modifier.width(8.dp))
                                Text("Version: ${activeVersion.id}", maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Spacer(Modifier.weight(1f))
                                Text("⌄", fontSize = 20.sp)
                            }

                        }
                    }
                    Button(onClick = actions.play, modifier = Modifier.fillMaxWidth(), enabled = activeVersion != null && state.busyAction == null, contentPadding = PaddingValues(vertical = 15.dp)) {
                        Icon(Icons.Outlined.PlayArrow, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Play", style = MaterialTheme.typography.titleMedium)
                    }
                    if (activeVersion == null) {
                        FilledTonalButton(onClick = { actions.setFilter(VersionFilter.RELEASE); actions.navigate(LauncherTab.VERSIONS) }, modifier = Modifier.fillMaxWidth()) { Text("Browse versions") }
                    }
                }
            }
        }
        item {
            SectionTitle("Runtime", "The launcher checks Java compatibility for the selected version.")
            OutlinedCard(shape = RoundedCornerShape(22.dp)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Icon(Icons.Outlined.Terminal, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Column(Modifier.weight(1f)) {
                            Text("Java runtime", fontWeight = FontWeight.SemiBold)
                            Text(state.runtimeStatus, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    FilledTonalButton(onClick = actions.installRuntime, enabled = activeVersion != null && state.busyAction == null, modifier = Modifier.fillMaxWidth()) {
                        if (state.busyAction == "runtime") CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp) else Icon(Icons.Outlined.Download, null)
                        Spacer(Modifier.width(8.dp)); Text(if (state.busyAction == "runtime") "Installing Java…" else "Install / repair compatible Java")
                    }
                }
            }
        }
        item {
            SectionTitle("Account", "Sign in with Microsoft or use a local profile for offline play where supported.")
            OutlinedCard(shape = RoundedCornerShape(22.dp)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Icon(Icons.Outlined.AccountCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Text(state.accountStatus, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                    }
                    OutlinedButton(onClick = { actions.navigate(LauncherTab.ACCOUNT) }, modifier = Modifier.fillMaxWidth()) { Text("Manage account") }
                }
            }
        }
        item { Text(state.catalogStatus, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}

@Composable
private fun VersionsScreen(state: LauncherUiState, actions: LauncherActions, modifier: Modifier = Modifier) {
    val list = when (state.filter) {
        VersionFilter.INSTALLED -> state.installedVersions
        VersionFilter.RELEASE -> state.versions.filter { it.type.equals("release", true) }
        VersionFilter.SNAPSHOT -> state.versions.filter { it.type.equals("snapshot", true) }
        VersionFilter.OLD_BETA -> state.versions.filter { it.type.equals("old_beta", true) }
        VersionFilter.OLD_ALPHA -> state.versions.filter { it.type.equals("old_alpha", true) }
        VersionFilter.OTHER -> state.versions.filter { it.type !in listOf("release", "snapshot", "old_beta", "old_alpha") }
    }
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 18.dp, top = 20.dp, end = 18.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            BrandHeader("VERSION MANAGER", "All versions, clearly sorted.", "Installed builds are separated from the official catalog. Releases, snapshots, beta, and alpha never get mixed together.")
        }
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Version library", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text(state.catalogStatus, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                OutlinedButton(onClick = actions.refreshCatalog, enabled = !state.refreshing) {
                    if (state.refreshing) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                    else Icon(Icons.Outlined.Refresh, null)
                    Spacer(Modifier.width(6.dp)); Text("Refresh")
                }
            }
        }
        item {
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                VersionFilter.entries.forEach { filter ->
                    FilterChip(selected = state.filter == filter, onClick = { actions.setFilter(filter) }, label = { Text(filter.label) })
                }
            }
        }
        item {
            Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceContainerLow) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(state.filter.label, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Text("${list.size} version${if (list.size == 1) "" else "s"}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (state.filter == VersionFilter.INSTALLED) StatusPill("Installed only", true)
                }
            }
        }
        if (state.loadingCatalog && list.isEmpty()) {
            item { Box(Modifier.fillMaxWidth().padding(28.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() } }
        } else if (list.isEmpty()) {
            item {
                EmptyState(
                    icon = if (state.filter == VersionFilter.INSTALLED) Icons.Outlined.FolderOpen else Icons.Outlined.Extension,
                    title = if (state.filter == VersionFilter.INSTALLED) "No installed versions yet" else "No versions in this channel",
                    body = if (state.filter == VersionFilter.INSTALLED) "Choose Release, Snapshot, Beta, or Alpha and install one version. Once installed it will appear in Play's quick selector." else "Refresh the official version catalog, or check your connection.",
                    buttonText = if (state.filter == VersionFilter.INSTALLED) "Browse releases" else "Refresh catalog",
                    onClick = { if (state.filter == VersionFilter.INSTALLED) actions.setFilter(VersionFilter.RELEASE) else actions.refreshCatalog() },
                )
            }
        } else if (state.filter == VersionFilter.INSTALLED) {
            val installedGroups = list.groupBy { filterForVersionType(it.type) }
            VersionFilter.entries.filter { it != VersionFilter.INSTALLED }.forEach { group ->
                val groupVersions = installedGroups[group].orEmpty()
                if (groupVersions.isNotEmpty()) {
                    item(key = "installed-group-${group.name}") {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(group.label, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Text("${groupVersions.size} installed", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        }
                    }
                    items(groupVersions, key = { "installed-${group.name}-${it.id}" }) { version ->
                        VersionRow(
                            version = version,
                            installed = true,
                            selected = state.managerSelectedVersionId == version.id || state.selectedVersionId == version.id,
                            busy = false,
                            onSelect = { actions.selectVersion(version.id) },
                            onInstall = { actions.installVersion(version.id) },
                        )
                    }
                }
            }
        } else {
            items(list, key = { "${state.filter.name}-${it.id}" }) { version ->
                VersionRow(
                    version = version,
                    installed = state.installedVersions.any { it.id == version.id },
                    selected = state.managerSelectedVersionId == version.id || state.selectedVersionId == version.id,
                    busy = state.busyAction == "install:${version.id}",
                    onSelect = { actions.selectVersion(version.id) },
                    onInstall = { actions.installVersion(version.id) },
                )
            }
        }
        item { Spacer(Modifier.height(6.dp)) }
    }
}

@Composable
private fun VersionRow(
    version: MinecraftVersion,
    installed: Boolean,
    selected: Boolean,
    busy: Boolean,
    onSelect: () -> Unit,
    onInstall: () -> Unit,
) {
    OutlinedCard(
        onClick = onSelect,
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant),
        colors = CardDefaults.outlinedCardColors(containerColor = if (selected) MaterialTheme.colorScheme.secondaryContainer.copy(alpha = .42f) else MaterialTheme.colorScheme.surface),
    ) {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surfaceContainerHighest, modifier = Modifier.size(44.dp)) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(if (installed) Icons.Outlined.CheckCircle else Icons.Outlined.Extension, null, tint = if (installed) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Column(Modifier.weight(1f)) {
                    Text(version.id, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(versionFilterLabel(version.type), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (installed) StatusPill("Installed", true)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(version.releaseTime.take(10).ifBlank { "Official catalog" }, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                if (selected) StatusPill("Selected", true)
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .6f))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (installed) {
                    Button(onClick = onSelect, modifier = Modifier.weight(1f)) { Icon(Icons.Outlined.CheckCircle, null); Spacer(Modifier.width(6.dp)); Text(if (selected) "Selected" else "Use in Play") }
                } else {
                    Button(onClick = onInstall, enabled = !busy, modifier = Modifier.weight(1f)) {
                        if (busy) CircularProgressIndicator(Modifier.size(17.dp), strokeWidth = 2.dp) else Icon(Icons.Outlined.Download, null)
                        Spacer(Modifier.width(6.dp)); Text(if (busy) "Installing…" else "Install")
                    }
                    OutlinedButton(onClick = onSelect) { Text("Select") }
                }
            }
        }
    }
}

@Composable
private fun AccountScreen(state: LauncherUiState, actions: LauncherActions, modifier: Modifier = Modifier) {
    LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item { BrandHeader("PLAYER IDENTITY", "Your account.", "Manage the identity used to launch Minecraft. Tokens are never shown in this screen.") }
        item {
            Card(shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
                Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Surface(shape = CircleShape, color = MaterialTheme.colorScheme.secondaryContainer, modifier = Modifier.size(64.dp)) {
                        Box(contentAlignment = Alignment.Center) { Icon(Icons.Outlined.AccountCircle, null, modifier = Modifier.size(38.dp), tint = MaterialTheme.colorScheme.onSecondaryContainer) }
                    }
                    Text("Active account", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text(state.accountStatus, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Button(onClick = actions.signInMicrosoft, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Outlined.AccountCircle, null); Spacer(Modifier.width(8.dp)); Text("Sign in with Microsoft")
                    }
                    OutlinedButton(onClick = actions.manageLocalAccounts, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Outlined.AccountCircle, null); Spacer(Modifier.width(8.dp)); Text("Manage local / offline profiles")
                    }
                }
            }
        }
        item {
            SectionTitle("Account types", "Choose the correct type for how you play.")
            InfoRow(Icons.Outlined.CheckCircle, "Microsoft", "Authenticated access to Minecraft Java Edition and online-mode servers.")
            InfoRow(Icons.Outlined.Info, "Local / offline", "Useful for local single-player and servers explicitly configured to allow offline identities. It is not Microsoft authentication.")
        }
    }
}

@Composable
private fun SettingsScreen(state: LauncherUiState, actions: LauncherActions, modifier: Modifier = Modifier) {
    LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item { BrandHeader("PREFERENCES", "Settings.", "Tune the launcher without touching development diagnostics.") }
        item {
            SectionTitle("Appearance", "Material Design 3")
            OutlinedCard(shape = RoundedCornerShape(20.dp)) {
                SettingSwitchRow(
                    icon = if (state.darkTheme) Icons.Outlined.DarkMode else Icons.Outlined.LightMode,
                    title = "Dark theme",
                    subtitle = if (state.darkTheme) "Dark surfaces with Sally blue accents" else "Light surfaces with Sally blue accents",
                    checked = state.darkTheme,
                    onCheckedChange = actions.setDarkTheme,
                )
            }
        }
        item {
            SectionTitle("Gameplay", "Convenience options")
            OutlinedCard(shape = RoundedCornerShape(20.dp)) {
                SettingSwitchRow(Icons.Outlined.Gamepad, "Keep screen awake", "Prevent the screen from sleeping while the launcher is open.", state.keepScreenOn, actions.setKeepScreenOn)
            }
        }
        item {
            SectionTitle("Storage", "Game data location")
            OutlinedCard(shape = RoundedCornerShape(20.dp)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Outlined.FolderOpen, tint = MaterialTheme.colorScheme.primary, contentDescription = null)
                        Text("Accessible app storage", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    }
                    Text("Android/data/com.sally.launcher/files/minecraft", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("Executable Java runtimes stay in internal app storage because Android external storage can be mounted noexec.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        item {
            SectionTitle("About", "Sally Launcher")
            InfoRow(Icons.Outlined.Info, "Sally Launcher · 0.1.0", "Material 3 interface with a Pojav-derived Android native engine.")
        }
    }
}

@Composable
private fun DevScreen(state: LauncherUiState, actions: LauncherActions, modifier: Modifier = Modifier) {
    LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item { BrandHeader("DEVELOPER TOOLS", "Diagnostics.", "Debug and engine tools live here, away from the normal Play flow.") }
        item {
            Card(shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Outlined.Terminal, tint = MaterialTheme.colorScheme.primary, contentDescription = null)
                        Text("Engine status", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    }
                    Text(state.engineStatus, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("Logs: Android/data/com.sally.launcher/files/diagnostics", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        item {
            DevActionCard(Icons.Outlined.Code, "Prepare launch profile", "Resolve version metadata, classpath, Java runtime, account, and blockers.", "prepare", state.busyAction, actions.prepareProfile)
        }
        item {
            DevActionCard(Icons.Outlined.Terminal, "Probe JVM native loader", "Check libjli / libjvm availability. This does not launch Minecraft.", "probe", state.busyAction, actions.probeJvm)
        }
        item {
            DevActionCard(Icons.Outlined.CheckCircle, "Run Java runtime self-test", "Start a disposable JLI process with -version and save diagnostics.", "smoke", state.busyAction, actions.testJvm)
        }
        item {
            DevActionCard(Icons.Outlined.BugReport, "Test graphics & input bridge", "Open the GLES and input event diagnostic surface.", null, state.busyAction, actions.testGraphics)
        }
        item {
            DevActionCard(Icons.Outlined.Extension, "Test Pojav surface bridge", "Open diagnostics for the bundled Pojav-derived surface / JNI bridge.", null, state.busyAction, actions.testPojav)
        }
        item {
            OutlinedCard(shape = RoundedCornerShape(20.dp)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Troubleshooting notes", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text("• JVM self-test confirms Java can start; it does not prove Minecraft can render.\n• Check latest.log for Java exceptions and diagnostics/jli-version.log for JLI output.\n• If a native crash generates hs_err_pid*.log, preserve the complete file.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun DevActionCard(
    icon: ImageVector,
    title: String,
    description: String,
    busyKey: String?,
    busyAction: String?,
    onClick: () -> Unit,
) {
    OutlinedCard(shape = RoundedCornerShape(20.dp)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.secondaryContainer, modifier = Modifier.size(44.dp)) {
                    Box(contentAlignment = Alignment.Center) { Icon(icon, null, tint = MaterialTheme.colorScheme.onSecondaryContainer) }
                }
                Column(Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Button(onClick = onClick, enabled = busyAction == null, modifier = Modifier.fillMaxWidth()) {
                if (busyKey != null && busyAction == busyKey) CircularProgressIndicator(Modifier.size(17.dp), strokeWidth = 2.dp) else Icon(Icons.Outlined.BugReport, null)
                Spacer(Modifier.width(8.dp)); Text(if (busyKey != null && busyAction == busyKey) "Running…" else "Run diagnostic")
            }
        }
    }
}

@Composable
private fun BrandHeader(eyebrow: String, title: String, subtitle: String) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(eyebrow, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold, letterSpacing = 1.6.sp)
        Text(title, style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold, letterSpacing = (-.8).sp)
        Text(subtitle, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun SectionTitle(title: String, subtitle: String) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun StatusPill(text: String, positive: Boolean) {
    Surface(shape = CircleShape, color = if (positive) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.surfaceContainerHighest) {
        Text(text, modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp), style = MaterialTheme.typography.labelSmall, color = if (positive) MaterialTheme.colorScheme.onTertiaryContainer else MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun EmptyState(icon: ImageVector, title: String, body: String, buttonText: String, onClick: () -> Unit) {
    OutlinedCard(shape = RoundedCornerShape(24.dp)) {
        Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(icon, null, modifier = Modifier.size(42.dp), tint = MaterialTheme.colorScheme.primary)
            Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Button(onClick = onClick) { Text(buttonText) }
        }
    }
}

@Composable
private fun InfoRow(icon: ImageVector, title: String, body: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 2.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Text(body, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun SettingSwitchRow(icon: ImageVector, title: String, subtitle: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

private fun filterForVersionType(type: String): VersionFilter = when (type.lowercase()) {
    "release" -> VersionFilter.RELEASE
    "snapshot" -> VersionFilter.SNAPSHOT
    "old_beta" -> VersionFilter.OLD_BETA
    "old_alpha" -> VersionFilter.OLD_ALPHA
    else -> VersionFilter.OTHER
}

private fun versionFilterLabel(type: String): String = when (type.lowercase()) {
    "release" -> "Vanilla · Release"
    "snapshot" -> "Snapshot"
    "old_beta" -> "Old Beta"
    "old_alpha" -> "Old Alpha"
    else -> type.replace('_', ' ').replaceFirstChar { it.uppercase() }
}
