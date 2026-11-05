package com.microchallenge.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.microchallenge.core.model.ChallengeType
import com.microchallenge.core.store.ProgressRepository

@Composable
fun StatsScreen(repo: ProgressRepository, streak: Int, onBack: () -> Unit, onSettings: () -> Unit) {
    val stats = remember { ChallengeType.entries.map { repo.stats(it) } }
    val history = remember { repo.history() }
    val streakState = remember { repo.streak() }
    val color = MaterialTheme.colorScheme.primary

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text("‹ Play") }
            Text("Your stats", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
            TextButton(onClick = onSettings) { Text("Settings") }
        }
        Card(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
            Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                Big(com.microchallenge.core.engine.Progression.rankFor(repo.xp()).title, "${repo.xp()} XP")
                Big("${repo.totalPlays()}", "Plays")
                Big("🔥 $streak", "Streak")
                Big("${streakState.best}", "Best streak")
            }
        }
        if (history.size >= 2) {
            Text("Last ${history.size} scores", style = MaterialTheme.typography.titleMedium)
            Canvas(
                Modifier.fillMaxWidth().height(96.dp).padding(vertical = 8.dp)
                    .semantics { contentDescription = "Score trend, latest ${history.last().score}" },
            ) {
                val step = size.width / (history.size - 1)
                for (i in 1 until history.size) {
                    drawLine(
                        color,
                        Offset((i - 1) * step, size.height * (1 - history[i - 1].score / 1000f)),
                        Offset(i * step, size.height * (1 - history[i].score / 1000f)),
                        strokeWidth = 3.dp.toPx(), cap = StrokeCap.Round,
                    )
                }
            }
        }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(stats, key = { it.type }) { s ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(s.type.title, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                            Text("Lv ${"%.1f".format(s.rating)}", style = MaterialTheme.typography.labelLarge)
                        }
                        Text("Best ${s.best} · Avg ${s.average} · ${s.plays} plays",
                            style = MaterialTheme.typography.bodyMedium)
                        Spacer(Modifier.height(6.dp))
                        LinearProgressIndicator(progress = { s.best / 1000f }, Modifier.fillMaxWidth())
                    }
                }
            }
            item {
                Text(
                    "MicroMind is a fun daily game. It is not a medical tool and makes no claim to improve memory, " +
                        "IQ or other everyday abilities.",
                    style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(vertical = 16.dp),
                )
            }
        }
    }
}

@Composable
private fun Big(value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text(label, style = MaterialTheme.typography.labelSmall)
    }
}
