package com.microchallenge.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.microchallenge.app.ui.design.GameIcon
import com.microchallenge.app.ui.design.GradientBar
import com.microchallenge.app.ui.design.Medal
import com.microchallenge.app.ui.design.MedalBadge
import com.microchallenge.app.ui.design.Palette
import com.microchallenge.app.ui.design.Panel
import com.microchallenge.app.ui.design.RankBadge
import com.microchallenge.app.ui.design.RoundIconButton
import com.microchallenge.app.ui.design.SectionTitle
import com.microchallenge.app.ui.design.Shapes
import com.microchallenge.app.ui.design.StatTile
import com.microchallenge.app.ui.design.staggerIn
import com.microchallenge.app.ui.design.style
import com.microchallenge.core.engine.Progression
import com.microchallenge.core.model.ChallengeType
import com.microchallenge.core.model.Skill
import com.microchallenge.core.store.ProgressRepository
import java.time.LocalDate

/** Progress tab: rank, streak calendar, skill bars, score trend and every game's medal. */
@Composable
fun StatsScreen(repo: ProgressRepository, streak: Int, today: Long, onSettings: () -> Unit) {
    val stats = remember { ChallengeType.entries.map { repo.stats(it) } }
    val history = remember { repo.history() }
    val streakState = remember { repo.streak() }
    val xp = remember { repo.xp() }
    val rank = Progression.rankFor(xp)
    val next = Progression.nextRank(xp)
    val golds = stats.count { Medal.forScore(it.best) == Medal.GOLD }
    val playedDays = remember(history) { history.map { it.day }.toSet() + listOfNotNull(streakState.lastPlayedDay.takeIf { it > 0 }) }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(key = "title") {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Progress", style = MaterialTheme.typography.headlineMedium,
                    modifier = Modifier.weight(1f).semantics { heading() })
                RoundIconButton("⚙", "Settings", onSettings)
            }
        }
        item(key = "hero") {
            Panel(Modifier.fillMaxWidth().staggerIn(0)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RankBadge(rank.title, Progression.rankProgress(xp), size = 76.dp)
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text("RANK", style = MaterialTheme.typography.labelSmall, color = Palette.TextFaint)
                        Text(rank.title, style = MaterialTheme.typography.headlineMedium)
                        Text(if (next == null) "$xp XP · max rank" else "$xp XP · ${next.minXp - xp} to ${next.title}",
                            style = MaterialTheme.typography.bodyMedium, color = Palette.TextDim)
                    }
                }
                Spacer(Modifier.height(14.dp))
                GradientBar(Progression.rankProgress(xp))
                Spacer(Modifier.height(16.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    StatTile("${repo.totalPlays()}", "Plays")
                    StatTile("$streak", "Streak", color = Palette.Streak)
                    StatTile("${streakState.best}", "Best streak")
                    StatTile("$golds", "Golds", color = Palette.Gold)
                }
            }
        }
        item(key = "calendar") { StreakCalendar(playedDays, today, Modifier.staggerIn(1)) }
        item(key = "skills") { SkillBars(stats, Modifier.staggerIn(2)) }
        if (history.size >= 2) item(key = "trend") { Trend(history.map { it.score }, Modifier.staggerIn(3)) }
        Skill.entries.forEach { skill ->
            val group = stats.filter { it.type.skill == skill }
            if (group.isEmpty()) return@forEach
            item(key = "h_${skill.name}") { SectionTitle(skill.style().label) }
            items(group, key = { it.type.name }) { s -> GameRow(s.type, s.best, s.plays, s.average) }
        }
        item(key = "note") {
            Text(
                "MicroMind is a fun daily game. It is not a medical tool and makes no claim to improve memory, " +
                    "IQ or other everyday abilities.",
                style = MaterialTheme.typography.bodySmall, color = Palette.TextFaint,
                modifier = Modifier.padding(vertical = 12.dp),
            )
        }
    }
}

@Composable
private fun StreakCalendar(played: Set<Long>, today: Long, modifier: Modifier = Modifier) {
    val weeks = 5
    val dow = LocalDate.ofEpochDay(today).dayOfWeek.value - 1 // Monday = 0
    val start = today - dow - (weeks - 1) * 7
    val count = (start..today).count { it in played }
    Panel(modifier.fillMaxWidth().semantics(mergeDescendants = true) {
        contentDescription = "Played on $count of the last ${today - start + 1} days"
    }) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Streak calendar", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            Text("$count days played", style = MaterialTheme.typography.labelMedium, color = Palette.Streak)
        }
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            listOf("M", "T", "W", "T", "F", "S", "S").forEach {
                Text(it, Modifier.weight(1f), style = MaterialTheme.typography.labelSmall, color = Palette.TextFaint,
                    textAlign = TextAlign.Center)
            }
        }
        Spacer(Modifier.height(6.dp))
        for (w in 0 until weeks) {
            Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                for (d in 0 until 7) {
                    val day = start + w * 7 + d
                    Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        val isToday = day == today
                        val future = day > today
                        val on = day in played
                        Box(
                            Modifier.width(30.dp).aspectRatio(1f).clip(CircleShape)
                                .background(
                                    when {
                                        on -> Brush.linearGradient(listOf(Color(0xFFFFC14D), Palette.StreakHot))
                                        else -> Brush.linearGradient(listOf(Palette.Surface2, Palette.Surface2))
                                    },
                                )
                                .then(if (isToday) Modifier.border(2.dp, Palette.Text, CircleShape) else Modifier)
                                .then(if (future) Modifier.background(Palette.Surface1) else Modifier),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SkillBars(stats: List<com.microchallenge.core.store.TypeStats>, modifier: Modifier = Modifier) {
    Panel(modifier.fillMaxWidth()) {
        Text("Skills", style = MaterialTheme.typography.titleMedium)
        Text("Average best score of the games you've played", style = MaterialTheme.typography.bodySmall,
            color = Palette.TextFaint)
        Skill.entries.forEach { skill ->
            val played = stats.filter { it.type.skill == skill && it.plays > 0 }
            val avg = if (played.isEmpty()) 0 else played.sumOf { it.best } / played.size
            val style = skill.style()
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(style.label, style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
                Text(if (played.isEmpty()) "not played" else "$avg", style = MaterialTheme.typography.labelLarge,
                    color = if (played.isEmpty()) Palette.TextFaint else style.start)
            }
            Spacer(Modifier.height(6.dp))
            GradientBar(avg / 1000f, brush = style.horizontal, height = 10.dp, animateFrom = 0f)
        }
    }
}

@Composable
private fun Trend(scores: List<Int>, modifier: Modifier = Modifier) {
    val line = Palette.Accent
    Panel(modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Last ${scores.size} scores", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            Text("avg ${scores.average().toInt()}", style = MaterialTheme.typography.labelMedium, color = Palette.TextDim)
        }
        Spacer(Modifier.height(10.dp))
        Canvas(
            Modifier.fillMaxWidth().height(110.dp)
                .semantics { contentDescription = "Score trend, latest ${scores.last()}" },
        ) {
            val step = size.width / (scores.size - 1)
            fun pt(i: Int) = Offset(i * step, size.height * (1 - scores[i] / 1000f) * 0.92f + size.height * 0.04f)
            val path = Path().apply {
                moveTo(pt(0).x, pt(0).y)
                for (i in 1 until scores.size) lineTo(pt(i).x, pt(i).y)
            }
            val fill = Path().apply {
                addPath(path)
                lineTo(size.width, size.height); lineTo(0f, size.height); close()
            }
            drawPath(fill, Brush.verticalGradient(listOf(line.copy(alpha = 0.35f), Color.Transparent)))
            drawPath(path, Brush.horizontalGradient(listOf(Palette.Primary, line)),
                style = Stroke(3.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
            drawCircle(Color.White, 4.dp.toPx(), pt(scores.size - 1))
        }
    }
}

@Composable
private fun GameRow(type: ChallengeType, best: Int, plays: Int, average: Int) {
    Row(
        Modifier.fillMaxWidth().clip(Shapes.tile).background(Palette.Surface1).padding(12.dp)
            .semantics(mergeDescendants = true) {},
        verticalAlignment = Alignment.CenterVertically,
    ) {
        GameIcon(type, size = 44.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(type.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(if (plays == 0) "Not played yet" else "Best $best · Avg $average · $plays plays",
                style = MaterialTheme.typography.bodySmall, color = Palette.TextDim)
        }
        MedalBadge(Medal.forScore(best), size = 26.dp)
    }
}
