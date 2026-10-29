package com.sally.unilauncher

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.sally.unilauncher.core.instance.Instance
import com.sally.unilauncher.core.instance.InstanceScanner
import com.sally.unilauncher.ui.home.HomeScreen
import com.sally.unilauncher.ui.theme.UniLauncherTheme
import java.io.File

class MainActivity : ComponentActivity() {

    /** Folder game. Sementara di folder aplikasi; nanti bisa dipilih pengguna. */
    private val gameDir: File by lazy { File(getExternalFilesDir(null), ".minecraft") }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            UniLauncherTheme {
                var instances by remember { mutableStateOf(loadInstances()) }
                var selectedId by remember { mutableStateOf<String?>(null) }

                HomeScreen(
                    instances = instances,
                    selectedId = selectedId ?: instances.firstOrNull()?.id,
                    onSelect = { selectedId = it.id },
                    onRefresh = { instances = loadInstances() },
                    onPlay = { /* Tahap peluncuran belum diimplementasikan */ },
                )
            }
        }
    }

    private fun loadInstances(): List<Instance> = InstanceScanner(gameDir).scan()
}
