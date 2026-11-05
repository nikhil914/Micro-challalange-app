package com.microchallenge.app.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.microchallenge.app.Screen
import com.microchallenge.core.engine.Progression

private fun verdict(score: Int) = when {
    score >= 900 -> "UNREAL"
    score >= 700 -> "Clutch"
    score >= 450 -> "Solid run"
    score >= 200 -> "Warming up"
    else -> "Run it back"
}

@Composable
fun ResultScreen(r: Screen.Result, onAgain: () -> Unit, onStats: () -> Unit) {
    val anim = remember(r) { Animatable(0f) }
    LaunchedEffect(r) { anim.animateTo(r.result.score.toFloat(), tween(900)) }

    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.weight(1f))
        Text(r.result.type.title, style = MaterialTheme.typography.titleLarge)
        Text(
            "${anim.value.toInt()}",
            fontSize = 88.sp, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.semantics { contentDescription = "Score ${r.result.score} out of 1000" },
        )
        Text(verdict(r.result.score), style = MaterialTheme.typography.headlineSmall)
        if (r.result.detail.isNotBlank()) {
            Text(r.result.detail, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.secondary)
        }
        Spacer(Modifier.height(24.dp))
        Card(Modifier.fillMaxWidth()) {
            Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                Stat("Best", if (r.isBest) "${r.result.score} 🎉" else "${r.previousBest}")
                Stat("Streak", "🔥 ${r.streak}")
                Stat("Level", "${r.result.difficulty}")
            }
        }
        Spacer(Modifier.height(12.dp))
        XpPanel(r)
        if (r.isBest && r.previousBest > 0) {
            Spacer(Modifier.height(8.dp))
            Text("New personal best! +${r.result.score - r.previousBest}", color = MaterialTheme.colorScheme.tertiary,
                fontWeight = FontWeight.Bold)
        }
        if (r.isDaily) Text("Daily challenge complete. Come back tomorrow!", Modifier.padding(top = 8.dp))
        Spacer(Modifier.weight(1f))
        Button(onClick = onAgain, Modifier.fillMaxWidth().height(64.dp)) { Text("Next round", fontSize = 20.sp) }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(onClick = onStats, Modifier.fillMaxWidth().height(56.dp)) { Text("My stats") }
    }
}

@Composable
private fun Stat(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text(label, style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
private fun XpPanel(r: Screen.Result) {
    val g = r.xpGain
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(r.rank.title.uppercase(), style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.secondary, modifier = Modifier.weight(1f))
                Text("+${g.total} XP", style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.primary)
            }
            Spacer(Modifier.height(8.dp))
            LinearProgressIndicator(progress = { Progression.rankProgress(r.xpTotal) }, Modifier.fillMaxWidth())
            val next = Progression.nextRank(r.xpTotal)
            Text(
                if (next == null) "Max rank reached" else "${next.minXp - r.xpTotal} XP to ${next.title}",
                style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 4.dp),
            )
            val extras = buildList {
                if (g.streakBonus > 0) add("streak +${g.streakBonus}")
                if (g.comboBonus > 0) add("hot run x${r.combo} +${g.comboBonus}")
                if (g.bestBonus > 0) add("new best +${g.bestBonus}")
            }
            if (extras.isNotEmpty()) Text(extras.joinToString(" · "), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (r.rankedUp) Text("RANK UP: ${r.rank.title}!", style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.tertiary,
                modifier = Modifier.padding(top = 6.dp))
        }
    }
}
