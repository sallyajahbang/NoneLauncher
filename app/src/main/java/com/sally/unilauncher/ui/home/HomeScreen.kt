package com.sally.unilauncher.ui.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.sally.unilauncher.core.instance.Instance

@Composable
fun HomeScreen(
    instances: List<Instance>,
    selectedId: String?,
    onSelect: (Instance) -> Unit,
    onRefresh: () -> Unit,
    onPlay: (Instance) -> Unit,
) {
    val selected = instances.firstOrNull { it.id == selectedId }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Uni Launcher") }) },
        bottomBar = {
            Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Button(onClick = onRefresh) { Text("Muat ulang") }
                Button(enabled = selected != null, onClick = { selected?.let(onPlay) }) {
                    Text("Play")
                }
            }
        },
    ) { padding ->
        if (instances.isEmpty()) {
            Column(
                modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("Belum ada versi terpasang.", style = MaterialTheme.typography.bodyLarge)
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(instances, key = { it.id }) { instance ->
                    InstanceCard(
                        instance = instance,
                        isSelected = instance.id == selectedId,
                        onClick = { onSelect(instance) },
                    )
                }
            }
        }
    }
}

@Composable
private fun InstanceCard(instance: Instance, isSelected: Boolean, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(instance.id, style = MaterialTheme.typography.titleMedium)
            instance.type?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
        }
    }
}
