package com.sally.javoxel.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.sally.javoxel.util.AppLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Layar log di dalam app, supaya tidak perlu logcat.
 * Bisa melihat sesi sekarang atau sesi sebelumnya (berguna setelah crash).
 */
@Composable
fun LogViewer(onClose: () -> Unit) {
    val clipboard = LocalClipboardManager.current
    var session by remember { mutableStateOf(AppLog.Session.CURRENT) }
    var reload by remember { mutableIntStateOf(0) }
    var text by remember { mutableStateOf("Memuat...") }
    val scroll = rememberScrollState()

    LaunchedEffect(session, reload) {
        text = withContext(Dispatchers.IO) { AppLog.readTail(session) }
    }
    // Tampilkan baris terakhir (paling baru) saat log dimuat.
    LaunchedEffect(text) {
        scroll.scrollTo(scroll.maxValue)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text("Log", style = MaterialTheme.typography.headlineMedium)

        BevelButton(
            text = if (session == AppLog.Session.CURRENT) "Lihat sesi sebelumnya" else "Lihat sesi ini",
            onClick = {
                session = if (session == AppLog.Session.CURRENT) {
                    AppLog.Session.PREVIOUS
                } else {
                    AppLog.Session.CURRENT
                }
            }
        )

        BevelButton(
            text = "Muat ulang",
            onClick = { reload++ }
        )

        BevelButton(
            text = "Salin log",
            onClick = { clipboard.setText(AnnotatedString(text)) }
        )

        BevelButton(
            text = "Tutup",
            onClick = onClose
        )

        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(scroll)
        )
    }
}
