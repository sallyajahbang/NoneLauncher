package com.sally.launcher

import android.os.Build
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import androidx.compose.ui.window.DialogProperties
import com.sally.launcher.data.model.MinecraftVersion

// design.md §5.2 — NavigationRail items: Home, Versi, Java, Setting. Developer lives inside Setting.
internal enum class LauncherTab(val label: String, val icon: ImageVector) {
    HOME("Home", Icons.Outlined.Home),
    VERSIONS("Versi", Icons.Outlined.ListAlt),
    JAVA("Java", Icons.Outlined.Terminal),
    SETTINGS("Setting", Icons.Outlined.Settings),
}

// design.md §7.3 filter chips: Terinstall, Release, Snapshot, Old, Lainnya.
internal enum class VersionFilter(val label: String) {
    INSTALLED("Terinstall"), RELEASE("Release"), SNAPSHOT("Snapshot"), OLD("Old"), OTHER("Lainnya")
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
    val tab: LauncherTab = LauncherTab.HOME,
    val versions: List<MinecraftVersion> = emptyList(),
    val installedVersions: List<MinecraftVersion> = emptyList(),
    val selectedVersionId: String = "",
    val managerSelectedVersionId: String = "",
    val filter: VersionFilter = VersionFilter.INSTALLED,
    val catalogStatus: String = "Memuat katalog versi resmi…",
    val runtimeStatus: String = "Memeriksa runtime Java…",
    val engineStatus: String = "Siap untuk diagnostik.",
    val accountStatus: String = "Memeriksa akun…",
    val loadingCatalog: Boolean = true,
    val refreshing: Boolean = false,
    val busyAction: String? = null,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val amoledBlack: Boolean = false,
    val dynamicColor: Boolean = false,
    val developerMode: Boolean = false,
    val keepScreenOn: Boolean = false,
    val installedJavaMajors: List<Int> = emptyList(),
    val supportedJavaMajors: List<Int> = listOf(8, 17),
    val dialog: LauncherDialog? = null,
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
    val manageLocalAccounts: () -> Unit,
    val prepareProfile: () -> Unit,
    val probeJvm: () -> Unit,
    val testJvm: () -> Unit,
    val testGraphics: () -> Unit,
    val testPojav: () -> Unit,
    val setThemeMode: (ThemeMode) -> Unit,
    val setAmoledBlack: (Boolean) -> Unit,
    val setDynamicColor: (Boolean) -> Unit,
    val requestDeveloperMode: (Boolean) -> Unit,
    val setKeepScreenOn: (Boolean) -> Unit,
    val dismissDialog: () -> Unit,
)

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
        var accountOpen by remember { mutableStateOf(false) }
        val openAccount: () -> Unit = { accountOpen = true }

        if (isWideScreen()) {
            // design.md §5.1: landscape / expanded → NavigationRail on the left.
            Row(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).windowInsetsPadding(WindowInsets.displayCutout)) {
                NavigationRail(containerColor = MaterialTheme.colorScheme.surfaceContainer) {
                    Spacer(Modifier.height(12.dp))
                    LauncherTab.entries.forEach { tab ->
                        NavigationRailItem(
                            selected = state.tab == tab,
                            onClick = { actions.navigate(tab) },
                            icon = { Icon(tab.icon, contentDescription = tab.label) },
                            label = { Text(tab.label, maxLines = 1) },
                        )
                    }
                }
                LauncherTabContent(state, actions, openAccount, Modifier.weight(1f).fillMaxHeight())
            }
        } else {
            // design.md §5.1: portrait / compact → NavigationBar at the bottom.
            Scaffold(
                containerColor = MaterialTheme.colorScheme.background,
                bottomBar = {
                    NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainer) {
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
                LauncherTabContent(state, actions, openAccount, Modifier.padding(padding))
            }
        }

        if (accountOpen) {
            AccountSheet(state = state, actions = actions, onDismiss = { accountOpen = false })
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
}

@Composable
private fun LauncherTabContent(state: LauncherUiState, actions: LauncherActions, onOpenAccount: () -> Unit, modifier: Modifier) {
    when (state.tab) {
        LauncherTab.HOME -> HomeScreen(state, actions, onOpenAccount, modifier)
        LauncherTab.VERSIONS -> VersionsScreen(state, actions, onOpenAccount, modifier)
        LauncherTab.JAVA -> JavaScreen(state, actions, onOpenAccount, modifier)
        LauncherTab.SETTINGS -> SettingsScreen(state, actions, onOpenAccount, modifier)
    }
}

@Composable
private fun isWideScreen(): Boolean = LocalConfiguration.current.screenWidthDp >= 600

private fun activeVersion(state: LauncherUiState): MinecraftVersion? =
    state.installedVersions.firstOrNull { it.id == state.selectedVersionId } ?: state.installedVersions.firstOrNull()

// ---------------------------------------------------------------- Home (design.md §7.1)

@Composable
private fun HomeScreen(state: LauncherUiState, actions: LauncherActions, onOpenAccount: () -> Unit, modifier: Modifier) {
    val active = activeVersion(state)
    val javaReady = state.installedJavaMajors.isNotEmpty()
    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        ScreenHeader(
            title = "Home",
            subtitle = state.accountStatus,
            onOpenAccount = onOpenAccount,
            trailing = {
                IconButton(onClick = { actions.navigate(LauncherTab.SETTINGS) }) {
                    Icon(Icons.Outlined.Settings, contentDescription = "Setting")
                }
            },
        )
        if (isWideScreen()) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.Top) {
                HeroCard(active, Modifier.weight(1f))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    HomeInfoCards(state, actions, active, javaReady)
                }
            }
        } else {
            HeroCard(active, Modifier.fillMaxWidth())
            HomeInfoCards(state, actions, active, javaReady)
        }
        // design.md §1: Play always visible; "Main" pill per wireframe §7.1.
        Button(
            onClick = actions.play,
            enabled = active != null && state.busyAction == null,
            shape = RoundedCornerShape(50),
            modifier = Modifier.fillMaxWidth().height(64.dp),
        ) {
            Icon(Icons.Outlined.PlayArrow, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("MAIN", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        }
        state.busyAction?.let {
            Text(state.runtimeStatus, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun HeroCard(active: MinecraftVersion?, modifier: Modifier) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
    ) {
        Column(
            Modifier.fillMaxWidth().heightIn(min = 200.dp).padding(24.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                "MINECRAFT: JAVA EDITION",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.4.sp,
            )
            Column {
                Text(
                    active?.id ?: "Belum ada versi",
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    if (active != null) versionFilterLabel(active.type) else "Pasang satu versi di tab Versi",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = .8f),
                )
            }
        }
    }
}

@Composable
private fun HomeInfoCards(state: LauncherUiState, actions: LauncherActions, active: MinecraftVersion?, javaReady: Boolean) {
    val busy = state.busyAction != null
    SimpleCard(shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(Icons.Outlined.Extension, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Column(Modifier.weight(1f)) {
                Text("Versi", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(active?.id ?: "Belum dipilih", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            }
            TextButton(onClick = { actions.navigate(LauncherTab.VERSIONS) }) { Text(if (active == null) "Pilih" else "Ganti") }
        }
    }
    SimpleCard(shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(Icons.Outlined.Terminal, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Column(Modifier.weight(1f)) {
                Text("Java", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    if (javaReady) "Java ${state.installedJavaMajors.joinToString(", ")} terpasang" else "Java belum terpasang",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            if (javaReady) {
                StatusPill("Java ✓", true)
            } else {
                FilledTonalButton(onClick = actions.installRuntime, enabled = active != null && !busy) { Text("Perbaiki") }
            }
        }
    }
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StatusChip(if (javaReady) "Java ✓" else "⚠ Perlu diunduh", javaReady)
        StatusChip(if (active != null) "Versi terpasang ✓" else "⚠ Belum ada versi", active != null)
    }
    OutlinedButton(
        onClick = { active?.let { actions.installVersion(it.id) } },
        enabled = active != null && !busy,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Icon(Icons.Outlined.Build, contentDescription = null)
        Spacer(Modifier.width(8.dp))
        Text("Perbaiki otomatis")
    }
}

@Composable
private fun StatusChip(text: String, ok: Boolean) {
    AssistChip(
        onClick = {},
        label = { Text(text) },
        colors = AssistChipDefaults.assistChipColors(
            containerColor = if (ok) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.errorContainer,
            labelColor = if (ok) MaterialTheme.colorScheme.onTertiaryContainer else MaterialTheme.colorScheme.onErrorContainer,
        ),
    )
}

// ---------------------------------------------------------------- Versi (design.md §7.3)

@Composable
private fun VersionsScreen(state: LauncherUiState, actions: LauncherActions, onOpenAccount: () -> Unit, modifier: Modifier) {
    var query by remember { mutableStateOf("") }
    var actionVersionId by remember { mutableStateOf<String?>(null) }
    val baseList = when (state.filter) {
        VersionFilter.INSTALLED -> state.installedVersions
        VersionFilter.RELEASE -> state.versions.filter { it.type.equals("release", true) }
        VersionFilter.SNAPSHOT -> state.versions.filter { it.type.equals("snapshot", true) }
        VersionFilter.OLD -> state.versions.filter { it.type.equals("old_beta", true) || it.type.equals("old_alpha", true) }
        VersionFilter.OTHER -> state.versions.filter { filterForVersionType(it.type) == VersionFilter.OTHER }
    }
    val list = baseList.filter { query.isBlank() || it.id.contains(query.trim(), ignoreCase = true) }
    val selected = (state.versions + state.installedVersions).firstOrNull { it.id == state.managerSelectedVersionId }
        ?: state.installedVersions.firstOrNull { it.id == state.selectedVersionId }

    val header: @Composable () -> Unit = {
        ScreenHeader(
            title = "Versi",
            subtitle = if (state.versions.isEmpty()) "Memuat daftar versi…" else "${state.versions.size} versi resmi",
            onOpenAccount = onOpenAccount,
            trailing = {
                IconButton(onClick = actions.refreshCatalog, enabled = !state.refreshing) {
                    if (state.refreshing) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    else Icon(Icons.Outlined.Refresh, contentDescription = "Segarkan")
                }
            },
        )
    }
    val controls: @Composable () -> Unit = {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            placeholder = { Text("Cari versi…") },
            leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
        )
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            VersionFilter.entries.forEach { filter ->
                FilterChip(selected = state.filter == filter, onClick = { actions.setFilter(filter) }, label = { Text(filter.label) })
            }
        }
    }

    if (isWideScreen()) {
        Row(modifier.fillMaxSize().padding(20.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Column(Modifier.weight(.55f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                header()
                controls()
                VersionList(list, state, actions, onItemClick = { actions.selectVersion(it) }, modifier = Modifier.weight(1f))
            }
            VersionDetailCard(selected, state, actions, Modifier.weight(.45f).fillMaxHeight())
        }
    } else {
        Column(modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            header()
            controls()
            VersionList(list, state, actions, onItemClick = { actionVersionId = it }, modifier = Modifier.weight(1f))
        }
        actionVersionId?.let { id ->
            (state.versions + state.installedVersions).firstOrNull { it.id == id }?.let { version ->
                VersionActionDialog(version, state, actions, onDismiss = { actionVersionId = null })
            }
        }
    }
}

@Composable
private fun VersionList(
    list: List<MinecraftVersion>,
    state: LauncherUiState,
    actions: LauncherActions,
    onItemClick: (String) -> Unit,
    modifier: Modifier,
) {
    LazyColumn(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(bottom = 24.dp)) {
        when {
            state.loadingCatalog && list.isEmpty() && state.filter != VersionFilter.INSTALLED -> item {
                Box(Modifier.fillMaxWidth().padding(28.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            }
            list.isEmpty() -> item {
                EmptyState(
                    icon = if (state.filter == VersionFilter.INSTALLED) Icons.Outlined.FolderOpen else Icons.Outlined.Extension,
                    title = if (state.filter == VersionFilter.INSTALLED) "Belum ada versi terpasang" else "Tidak ada versi di kategori ini",
                    body = if (state.filter == VersionFilter.INSTALLED) "Pilih Release lalu pasang satu versi." else "Segarkan daftar atau periksa koneksi.",
                    buttonText = if (state.filter == VersionFilter.INSTALLED) "Lihat Release" else "Segarkan",
                    onClick = { if (state.filter == VersionFilter.INSTALLED) actions.setFilter(VersionFilter.RELEASE) else actions.refreshCatalog() },
                )
            }
            else -> items(list, key = { "${state.filter.name}-${it.id}" }) { version ->
                val installed = state.installedVersions.any { it.id == version.id }
                val busy = state.busyAction == "install:${version.id}" || state.busyAction == "redownload:${version.id}"
                VersionListItem(
                    version = version,
                    installed = installed,
                    selected = state.managerSelectedVersionId == version.id || state.selectedVersionId == version.id,
                    busy = busy,
                    onClick = { onItemClick(version.id) },
                    onUse = { onItemClick(version.id) },
                    onRedownload = { onItemClick(version.id) },
                    onInstall = { onItemClick(version.id) },
                )
            }
        }
    }
}

@Composable
private fun VersionActionDialog(version: MinecraftVersion, state: LauncherUiState, actions: LauncherActions, onDismiss: () -> Unit) {
    val installed = state.installedVersions.any { it.id == version.id }
    val busy = state.busyAction == "install:${version.id}" || state.busyAction == "redownload:${version.id}"
    val inUse = state.selectedVersionId == version.id
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(version.id) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "${versionFilterLabel(version.type)} · ${version.releaseTime.take(10).ifBlank { "-" }}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                StatusPill(if (installed) "Terpasang" else "Belum terpasang", installed)
                if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                if (installed) {
                    Button(
                        onClick = { onDismiss(); actions.selectVersion(version.id) },
                        enabled = !busy && !inUse,
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(if (inUse) "Sedang dipakai" else "Pakai di Home") }
                    OutlinedButton(
                        onClick = { onDismiss(); actions.redownloadVersion(version.id) },
                        enabled = !busy,
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Redownload") }
                } else {
                    Button(
                        onClick = { onDismiss(); actions.installVersion(version.id) },
                        enabled = !busy,
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(if (busy) "Memasang…" else "Pasang") }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Tutup") } },
    )
}

@Composable
private fun VersionListItem(
    version: MinecraftVersion,
    installed: Boolean,
    selected: Boolean,
    busy: Boolean,
    onClick: () -> Unit,
    onUse: () -> Unit,
    onRedownload: () -> Unit,
    onInstall: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(start = 14.dp, top = 6.dp, bottom = 6.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                if (installed) Icons.Outlined.CheckCircle else Icons.Outlined.Extension,
                contentDescription = null,
                tint = if (installed) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Column(Modifier.weight(1f)) {
                Text(version.id, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text(versionFilterLabel(version.type), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (installed) StatusPill("Terpasang", true)
            Box {
                IconButton(onClick = { menuOpen = true }) { Icon(Icons.Outlined.MoreVert, contentDescription = "Aksi") }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    if (installed) {
                        DropdownMenuItem(text = { Text("Pakai di Home") }, onClick = { menuOpen = false; onUse() }, enabled = !busy)
                        DropdownMenuItem(text = { Text("Redownload") }, onClick = { menuOpen = false; onRedownload() }, enabled = !busy)
                    } else {
                        DropdownMenuItem(text = { Text("Pasang") }, onClick = { menuOpen = false; onInstall() }, enabled = !busy)
                    }
                }
            }
        }
    }
}

@Composable
private fun VersionDetailCard(version: MinecraftVersion?, state: LauncherUiState, actions: LauncherActions, modifier: Modifier) {
    SimpleCard(modifier = modifier, shape = RoundedCornerShape(24.dp)) {
        if (version == null) {
            Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                Text("Pilih versi di daftar untuk melihat detail.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            val installed = state.installedVersions.any { it.id == version.id }
            val busy = state.busyAction == "install:${version.id}" || state.busyAction == "redownload:${version.id}"
            val inUse = state.selectedVersionId == version.id
            Column(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(version.id, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text(versionFilterLabel(version.type), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("Dirilis: ${version.releaseTime.take(10).ifBlank { "-" }}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                StatusPill(if (installed) "Terpasang" else "Belum terpasang", installed)
                if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                Spacer(Modifier.weight(1f))
                if (installed) {
                    Button(onClick = { actions.selectVersion(version.id) }, enabled = !busy && !inUse, modifier = Modifier.fillMaxWidth()) {
                        Text(if (inUse) "Sedang dipakai" else "Pakai di Home")
                    }
                    FilledTonalButton(onClick = { actions.redownloadVersion(version.id) }, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Outlined.Refresh, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Redownload")
                    }
                } else {
                    Button(onClick = { actions.installVersion(version.id) }, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Outlined.Download, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(if (busy) "Memasang…" else "Pasang")
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------- Java (design.md §7.5, §7.6)

@Composable
private fun JavaScreen(state: LauncherUiState, actions: LauncherActions, onOpenAccount: () -> Unit, modifier: Modifier) {
    var installOpen by remember { mutableStateOf(false) }
    var chosenMajor by remember(state.supportedJavaMajors) { mutableIntStateOf(state.supportedJavaMajors.firstOrNull() ?: 17) }
    val javaBusy = state.busyAction == "runtime" || state.busyAction == "runtime-redownload"
    LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item { ScreenHeader("Java", "Runtime yang dipakai untuk menjalankan Minecraft.", onOpenAccount) }
        item {
            SectionTitle("Terpasang", "Runtime yang sudah diverifikasi di perangkat ini.")
            SimpleCard(shape = RoundedCornerShape(20.dp)) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (state.installedJavaMajors.isEmpty()) {
                        Text("Belum ada Java terpasang.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        state.installedJavaMajors.forEach { major ->
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Icon(Icons.Outlined.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                Text("Java $major", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                                StatusPill("Siap", true)
                            }
                        }
                    }
                    Text(state.runtimeStatus, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Button(onClick = { installOpen = true }, enabled = !javaBusy && state.busyAction == null, modifier = Modifier.fillMaxWidth()) {
                        if (javaBusy) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp) else Icon(Icons.Outlined.Download, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(if (javaBusy) "Memasang Java…" else "Install Java")
                    }
                    OutlinedButton(onClick = actions.redownloadRuntime, enabled = !javaBusy && state.busyAction == null, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Outlined.Refresh, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Redownload Java untuk versi terpilih")
                    }
                }
            }
        }
        item {
            SectionTitle("Pemetaan versi game", "Java yang disarankan berdasarkan versi Minecraft.")
            SimpleCard(shape = RoundedCornerShape(20.dp)) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    JavaMappingRow("1.16.5 ke bawah", "Java 8", supported = true)
                    JavaMappingRow("1.17 – 1.20.4", "Java 17", supported = true)
                    JavaMappingRow("1.20.5 ke atas", "Java 21", supported = false)
                }
            }
        }
    }

    if (installOpen) {
        AlertDialog(
            onDismissRequest = { installOpen = false },
            icon = { Icon(Icons.Outlined.Download, contentDescription = null) },
            title = { Text("Install Java") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Pilih versi Java yang ingin dipasang.", style = MaterialTheme.typography.bodyMedium)
                    state.supportedJavaMajors.forEachIndexed { index, major ->
                        val installed = major in state.installedJavaMajors
                        Row(
                            Modifier.fillMaxWidth().clickable { chosenMajor = major }.padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            RadioButton(selected = chosenMajor == major, onClick = { chosenMajor = major })
                            Text("Java $major", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                            if (installed) StatusPill("Terpasang", true)
                            else if (index == 0) StatusPill("Disarankan", false)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { installOpen = false; actions.installJava(chosenMajor) }) { Text("Install") }
            },
            dismissButton = { TextButton(onClick = { installOpen = false }) { Text("Batal") } },
        )
    }
}

@Composable
private fun JavaMappingRow(versions: String, java: String, supported: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(versions, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Text(java, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        if (!supported) StatusPill("Belum didukung", false)
    }
}

// ---------------------------------------------------------------- Setting (design.md §7.7, §7.8)

@Composable
private fun SettingsScreen(state: LauncherUiState, actions: LauncherActions, onOpenAccount: () -> Unit, modifier: Modifier) {
    var versionTaps by remember { mutableIntStateOf(0) }
    LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item { ScreenHeader("Setting", "Atur launcher tanpa menyentuh diagnostik.", onOpenAccount) }

        item {
            SettingsSection("Umum", "Tampilan dan perilaku dasar") {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Tema", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
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
                SettingSwitchRow(
                    icon = Icons.Outlined.DarkMode,
                    title = "AMOLED hitam",
                    subtitle = "Latar hitam pekat saat tema gelap.",
                    checked = state.amoledBlack,
                    onCheckedChange = actions.setAmoledBlack,
                )
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    SettingSwitchRow(
                        icon = Icons.Outlined.Palette,
                        title = "Warna dinamis",
                        subtitle = "Ikuti warna wallpaper (Android 12+). Mati: palet hijau Minecraft.",
                        checked = state.dynamicColor,
                        onCheckedChange = actions.setDynamicColor,
                    )
                }
                SettingSwitchRow(
                    icon = Icons.Outlined.BugReport,
                    title = "Mode Developer",
                    subtitle = "Tampilkan opsi diagnostik untuk pengguna tingkat lanjut.",
                    checked = state.developerMode,
                    onCheckedChange = actions.requestDeveloperMode,
                )
            }
        }

        item {
            SettingsSection("Game", "Kenyamanan saat bermain") {
                SettingSwitchRow(
                    icon = Icons.Outlined.Gamepad,
                    title = "Layar tetap menyala",
                    subtitle = "Cegah layar mati saat launcher terbuka.",
                    checked = state.keepScreenOn,
                    onCheckedChange = actions.setKeepScreenOn,
                )
            }
        }

        item {
            SettingsSection("Java & Runtime", "Kelola runtime Java") {
                SettingLinkRow(icon = Icons.Outlined.Terminal, title = "Kelola Java", subtitle = "Pasang atau perbaiki runtime Java.") {
                    actions.navigate(LauncherTab.JAVA)
                }
            }
        }

        item {
            SettingsSection("Akun", "Microsoft atau profil lokal") {
                SettingLinkRow(icon = Icons.Outlined.AccountCircle, title = "Akun", subtitle = state.accountStatus, onClick = onOpenAccount)
            }
        }

        item {
            SettingsSection("Penyimpanan", "Lokasi data game") {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Android/data/com.sally.launcher/files/minecraft", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        "Runtime Java tetap di penyimpanan internal aplikasi karena penyimpanan eksternal bisa dipasang sebagai noexec.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        if (state.developerMode) {
            item {
                SettingsSection("Developer", "Hanya tampil saat Mode Developer aktif") {
                    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(state.engineStatus, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("Log: Android/data/com.sally.launcher/files/diagnostics", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    DevOption(Icons.Outlined.Code, "Siapkan profil launch", "Cek metadata versi, classpath, runtime, akun, dan penghalang.", "prepare", state.busyAction, actions.prepareProfile)
                    DevOption(Icons.Outlined.Terminal, "Probe loader JVM", "Cek libjli dan libjvm. Tidak menjalankan Minecraft.", "probe", state.busyAction, actions.probeJvm)
                    DevOption(Icons.Outlined.CheckCircle, "Tes runtime Java", "Jalankan proses JLI sekali pakai dengan -version.", "smoke", state.busyAction, actions.testJvm)
                    DevOption(Icons.Outlined.BugReport, "Tes grafis & input", "Buka permukaan diagnostik GLES dan input.", null, state.busyAction, actions.testGraphics)
                    DevOption(Icons.Outlined.Extension, "Tes jembatan Pojav", "Buka diagnostik jembatan surface / JNI Pojav.", null, state.busyAction, actions.testPojav)
                    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                        OutlinedButton(onClick = { actions.requestDeveloperMode(false) }, modifier = Modifier.fillMaxWidth()) {
                            Text("Matikan Mode Developer")
                        }
                    }
                }
            }
        }

        item {
            SettingsSection("Tentang", "Sally Launcher") {
                Row(
                    Modifier.fillMaxWidth()
                        .clickable {
                            versionTaps++
                            if (versionTaps >= 7) {
                                versionTaps = 0
                                actions.requestDeveloperMode(!state.developerMode)
                            }
                        }
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Icon(Icons.Outlined.Info, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Column(Modifier.weight(1f)) {
                        Text("Versi app · 0.1.0", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Text("Antarmuka Material 3 dengan mesin native turunan Pojav.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsSection(title: String, subtitle: String, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 4.dp))
        SimpleCard(shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth()) {
            Column(content = content)
        }
    }
}

@Composable
private fun SettingLinkRow(icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun SettingSwitchRow(icon: ImageVector, title: String, subtitle: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun DevOption(
    icon: ImageVector,
    title: String,
    description: String,
    busyKey: String?,
    busyAction: String?,
    onClick: () -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        val running = busyKey != null && busyAction == busyKey
        Button(onClick = onClick, enabled = busyAction == null, modifier = Modifier.fillMaxWidth()) {
            if (running) CircularProgressIndicator(Modifier.size(17.dp), strokeWidth = 2.dp) else Icon(Icons.Outlined.BugReport, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text(if (running) "Berjalan…" else "Jalankan")
        }
    }
}

// ---------------------------------------------------------------- Account sheet (design.md §7.2)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AccountSheet(state: LauncherUiState, actions: LauncherActions, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text("Akun", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(state.accountStatus, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Button(onClick = { onDismiss(); actions.signInMicrosoft() }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Outlined.AccountCircle, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Masuk dengan Microsoft")
            }
            OutlinedButton(onClick = { onDismiss(); actions.manageLocalAccounts() }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Outlined.AccountCircle, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Profil lokal / offline")
            }
        }
    }
}

// ---------------------------------------------------------------- Shared pieces

@Composable
private fun ScreenHeader(
    title: String,
    subtitle: String,
    onOpenAccount: () -> Unit,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        trailing?.invoke()
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.secondaryContainer,
            modifier = Modifier.size(44.dp).clickable(onClick = onOpenAccount),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(Icons.Outlined.AccountCircle, contentDescription = "Akun", tint = MaterialTheme.colorScheme.onSecondaryContainer)
            }
        }
    }
}

@Composable
private fun SectionTitle(title: String, subtitle: String) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Flat tonal surface, no border: keeps the UI minimal and readable in both themes. */
@Composable
private fun SimpleCard(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(20.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(modifier = modifier, shape = shape, color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(content = content)
    }
}

@Composable
private fun StatusPill(text: String, positive: Boolean) {
    Surface(shape = CircleShape, color = if (positive) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.surfaceContainerHighest) {
        Text(
            text,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            style = MaterialTheme.typography.labelSmall,
            color = if (positive) MaterialTheme.colorScheme.onTertiaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun EmptyState(icon: ImageVector, title: String, body: String, buttonText: String, onClick: () -> Unit) {
    SimpleCard(shape = RoundedCornerShape(24.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(42.dp), tint = MaterialTheme.colorScheme.primary)
            Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Button(onClick = onClick) { Text(buttonText) }
        }
    }
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
