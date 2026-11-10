package com.microchallenge.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.microchallenge.app.Screen
import kotlinx.coroutines.delay

/** Shown on every app open: what the challenge is, then a short 3-2-1 so the user is ready. */
@Composable
fun IntroScreen(
    intro: Screen.Intro,
    streak: Int,
    rankTitle: String,
    dailyDone: Boolean,
    onStart: () -> Unit,
    onSkip: () -> Unit,
    onStats: () -> Unit,
    onDaily: () -> Unit,
) {
    var counting by remember(intro) { mutableIntStateOf(if (intro.autoStart) 3 else -1) }
    LaunchedEffect(counting) {
        if (counting > 0) { delay(700); counting -= 1 }
        else if (counting == 0) onStart()
    }

    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically) {
            Column {
                Text(rankTitle.uppercase(), style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.secondary)
                Text(if (streak > 0) "🔥 $streak-day streak" else "Start a streak today",
                    style = MaterialTheme.typography.titleMedium)
            }
            TextButton(onClick = onStats) { Text("Stats") }
        }
        Spacer(Modifier.weight(1f))
        if (intro.isDaily) AssistChip(onClick = {}, label = { Text("Daily challenge") })
        Text(intro.type.title, style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center)
        Text("${intro.type.skill.name.lowercase().replaceFirstChar { it.uppercase() }} · Level ${intro.difficulty}",
            style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.secondary)
        Spacer(Modifier.height(24.dp))
        Card(Modifier.fillMaxWidth()) {
            Text(intro.type.instructions, Modifier.padding(20.dp), style = MaterialTheme.typography.titleLarge,
                textAlign = TextAlign.Center)
        }
        Spacer(Modifier.height(32.dp))
        if (counting >= 0) {
            Text(if (counting == 0) "Go!" else "$counting", fontSize = 72.sp, fontWeight = FontWeight.Black,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
        } else {
            Button(onClick = { counting = 3 }, Modifier.fillMaxWidth().height(72.dp)) {
                Text("PLAY · 10s", fontSize = 22.sp, fontWeight = FontWeight.Black)
            }
        }
        Spacer(Modifier.weight(1f))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            TextButton(onClick = onSkip, enabled = counting < 0) { Text("Reroll") }
            if (!intro.isDaily) TextButton(onClick = onDaily, enabled = counting < 0) {
                Text(if (dailyDone) "Daily done ✓" else "Daily challenge")
            }
        }
    }
}
