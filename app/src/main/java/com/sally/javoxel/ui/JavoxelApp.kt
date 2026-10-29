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
import com.sally.javoxel.game.requiredJavaMajor
import com.sally.javoxel.runtime.JreInstaller
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
    val logLines = remember { mutableStateListOf<String>() }
    val javaMajor = requiredJavaMajor(version)

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

        Text("Butuh Java $javaMajor")

        BevelButton(
            text = if (busy) "Mengunduh..." else "Unduh versi",
            enabled = !busy && version.isNotBlank(),
            onClick = {
                busy = true
                val target = version.trim()
                scope.launch {
                    try {
                        withContext(Dispatchers.IO) {
                            MinecraftInstaller(File(context.filesDir, "minecraft")) { msg ->
                                scope.launch { logLines.add(msg) }
                            }.install(target)
                        }
                    } catch (e: Exception) {
                        logLines.add("Gagal: ${e.message}")
                    } finally {
                        busy = false
                    }
                }
            }
        )

        BevelButton(
            text = "Siapkan Java $javaMajor",
            enabled = !busy,
            onClick = {
                busy = true
                val major = requiredJavaMajor(version.trim())
                scope.launch {
                    try {
                        withContext(Dispatchers.IO) {
                            JreInstaller(File(context.filesDir, "runtime"), context.assets) { msg ->
                                scope.launch { logLines.add(msg) }
                            }.ensureJre(major)
                        }
                    } catch (e: Exception) {
                        logLines.add("Gagal JRE: ${e.message}")
                    } finally {
                        busy = false
                    }
                }
            }
        )

        BevelButton(
            text = "Launch (belum tersedia)",
            enabled = false,
            onClick = { /* TODO: hubungkan ke GameLauncher */ }
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
