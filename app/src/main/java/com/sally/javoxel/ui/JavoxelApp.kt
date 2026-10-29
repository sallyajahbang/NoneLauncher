package com.sally.javoxel.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.sally.javoxel.game.requiredJavaMajor

@Composable
fun JavoxelApp() {
    var version by remember { mutableStateOf("1.16.5") }
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
            modifier = Modifier.fillMaxWidth()
        )

        Text("Butuh Java $javaMajor")

        Button(onClick = { /* TODO: hubungkan ke GameLauncher */ }, enabled = false) {
            Text("Launch (belum tersedia)")
        }
    }
}
