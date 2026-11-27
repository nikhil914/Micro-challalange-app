package com.microchallenge.app.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.microchallenge.app.ui.design.Palette
import com.microchallenge.app.ui.design.Panel
import com.microchallenge.app.ui.design.RoundIconButton
import com.microchallenge.app.ui.design.SoftButton

@Composable
fun SettingsScreen(
    hapticsOn: Boolean,
    onHaptics: (Boolean) -> Unit,
    reduceMotion: Boolean,
    onReduceMotion: (Boolean) -> Unit,
    autoStart: Boolean,
    onAutoStart: (Boolean) -> Unit,
    onReset: () -> Unit,
    onBack: () -> Unit,
) {
    var confirm by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp).verticalScroll(rememberScrollState())) {
        Row(Modifier.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            RoundIconButton("‹", "Back", onBack)
            Spacer(Modifier.width(12.dp))
            Text("Settings", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.semantics { heading() })
        }
        Spacer(Modifier.height(8.dp))
        Panel(Modifier.fillMaxWidth()) {
            ToggleRow("Vibration feedback", "Taps, countdown ticks and results", hapticsOn, onHaptics)
            HorizontalDivider(color = Palette.Outline)
            ToggleRow("Reduce motion", "Turns off confetti, glows and looping animations", reduceMotion, onReduceMotion)
            HorizontalDivider(color = Palette.Outline)
            ToggleRow("Start a challenge on open", "Skip the home screen and jump straight into a random game",
                autoStart, onAutoStart)
        }
        Spacer(Modifier.height(20.dp))
        SoftButton("Reset all progress", { confirm = true }, Modifier.fillMaxWidth())
        Spacer(Modifier.height(20.dp))
        Text("All scores stay on this device. No account, no ads, no tracking.",
            style = MaterialTheme.typography.bodySmall, color = Palette.TextFaint)
    }
    if (confirm) {
        AlertDialog(
            onDismissRequest = { confirm = false },
            containerColor = Palette.Surface2,
            title = { Text("Reset progress?") },
            text = { Text("This deletes all scores, streaks and levels. It cannot be undone.") },
            confirmButton = { TextButton(onClick = { confirm = false; onReset() }) { Text("Reset", color = Palette.Wrong) } },
            dismissButton = { TextButton(onClick = { confirm = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun ToggleRow(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().toggleable(checked, role = Role.Switch, onValueChange = onChange).padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = Palette.TextDim)
        }
        Switch(
            checked = checked, onCheckedChange = null,
            colors = SwitchDefaults.colors(
                checkedTrackColor = Palette.Primary, checkedThumbColor = Color.White,
                uncheckedTrackColor = Palette.Surface3, uncheckedThumbColor = Palette.TextDim,
                uncheckedBorderColor = Palette.Outline,
            ),
        )
    }
}
