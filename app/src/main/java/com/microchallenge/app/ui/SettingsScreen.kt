package com.microchallenge.app.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun SettingsScreen(hapticsOn: Boolean, onHaptics: (Boolean) -> Unit, onReset: () -> Unit, onBack: () -> Unit) {
    var confirm by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text("‹ Stats") }
            Text("Settings", style = MaterialTheme.typography.headlineSmall)
        }
        Row(Modifier.fillMaxWidth().padding(vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Vibration feedback", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
            Switch(checked = hapticsOn, onCheckedChange = onHaptics)
        }
        Spacer(Modifier.height(24.dp))
        OutlinedButton(onClick = { confirm = true }, Modifier.fillMaxWidth()) { Text("Reset all progress") }
        Spacer(Modifier.height(24.dp))
        Text("All scores stay on this device. No account, no ads, no tracking.",
            style = MaterialTheme.typography.bodySmall)
    }
    if (confirm) {
        AlertDialog(
            onDismissRequest = { confirm = false },
            title = { Text("Reset progress?") },
            text = { Text("This deletes all scores, streaks and levels. It cannot be undone.") },
            confirmButton = { TextButton(onClick = { confirm = false; onReset() }) { Text("Reset") } },
            dismissButton = { TextButton(onClick = { confirm = false }) { Text("Cancel") } },
        )
    }
}
