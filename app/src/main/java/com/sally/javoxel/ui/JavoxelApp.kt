package com.sally.javoxel.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.sally.javoxel.data.MinecraftInstaller
import com.sally.javoxel.game.GameLauncher
import com.sally.javoxel.game.requiredJavaMajor
import com.sally.javoxel.runtime.JreInstaller
import com.sally.javoxel.util.AppLog
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun JavoxelApp() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var version by remember { mutableStateOf("1.16.5") }
    var busy by remember { mutableStateOf(false) }
    var showLog by remember { mutableStateOf(false) }
    val logLines = remember { mutableStateListOf<String>() }
    // Tebakan untuk tampilan saja. Saat aksi dijalankan, versi lokal dibaca dulu.
    val guessedJava = requiredJavaMajor(version)

    val minecraftDir = File(context.filesDir, "minecraft")
    val runtimeDir = File(context.filesDir, "runtime")
    val nativeLibDir = File(context.applicationInfo.nativeLibraryDir)

    // Pesan masuk ke file log (bisa dibuka di layar Log) dan ke daftar singkat di layar ini.
    fun log(msg: String) {
        AppLog.i(msg)
        scope.launch { logLines.add(msg) }
    }

    if (showLog) {
        LogViewer(onClose = { showLog = false })
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "Javoxel",
            style = MaterialTheme.typography.headlineMedium
        )

        OutlinedTextField(
            value = version,
            onValueChange = { version = it },
            label = { Text("Versi Minecraft") },
            singleLine = true,
            enabled = !busy,
            modifier = Modifier.fillMaxWidth()
        )

        Text("Butuh Java $guessedJava")

        BevelButton(
            text = if (busy) "Mengunduh..." else "Unduh versi",
            enabled = !busy && version.isNotBlank(),
            onClick = {
                busy = true
                val target = version.trim()
                scope.launch {
                    try {
                        withContext(Dispatchers.IO) {
                            MinecraftInstaller(minecraftDir) { msg -> log(msg) }
                                .install(target)
                        }
                    } catch (e: Exception) {
                        log("Gagal: ${e.message}")
                        AppLog.e(e.stackTraceToString())
                    } finally {
                        busy = false
                    }
                }
            }
        )

        BevelButton(
            text = "Siapkan Java",
            enabled = !busy,
            onClick = {
                busy = true
                val id = version.trim()
                scope.launch {
                    try {
                        withContext(Dispatchers.IO) {
                            val major = GameLauncher.javaMajorFor(minecraftDir, id)
                            JreInstaller(runtimeDir, context.assets, nativeLibDir) { msg -> log(msg) }
                                .ensureJre(major)
                        }
                    } catch (e: Throwable) {
                        log("Gagal JRE: ${e.message}")
                        AppLog.e(e.stackTraceToString())
                    } finally {
                        busy = false
                    }
                }
            }
        )

        BevelButton(
            text = "Launch",
            enabled = !busy,
            onClick = {
                busy = true
                val id = version.trim()
                log("Launch $id ditekan")
                scope.launch {
                    try {
                        withContext(Dispatchers.IO) {
                            val major = GameLauncher.javaMajorFor(minecraftDir, id)
                            val javaHome = JreInstaller(runtimeDir, context.assets, nativeLibDir) { msg -> log(msg) }
                                .ensureJre(major)
                            GameLauncher.launch(
                                baseDir = minecraftDir,
                                javaHome = javaHome,
                                javaMajor = major,
                                versionId = id,
                                playerName = "Player",
                                memoryMb = 1024,
                                log = { msg -> log(msg) },
                            )
                        }
                    } catch (e: Throwable) {
                        log("Gagal launch: ${e.message}")
                        AppLog.e(e.stackTraceToString())
                    } finally {
                        busy = false
                    }
                }
            }
        )

        BevelButton(
            text = "Buka log lengkap",
            onClick = { showLog = true }
        )

        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            items(logLines) { line ->
                Text(line, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
