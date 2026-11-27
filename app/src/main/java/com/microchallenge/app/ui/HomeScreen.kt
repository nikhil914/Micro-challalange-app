package com.microchallenge.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.microchallenge.app.ui.design.ChunkyButton
import com.microchallenge.app.ui.design.GameIcon
import com.microchallenge.app.ui.design.MiniGameCard
import com.microchallenge.app.ui.design.Palette
import com.microchallenge.app.ui.design.Panel
import com.microchallenge.app.ui.design.ProgressRing
import com.microchallenge.app.ui.design.RankBadge
import com.microchallenge.app.ui.design.SectionTitle
import com.microchallenge.app.ui.design.Shapes
import com.microchallenge.app.ui.design.StreakPill
import com.microchallenge.app.ui.design.floating
import com.microchallenge.app.ui.design.staggerIn
import com.microchallenge.app.ui.design.style
import com.microchallenge.core.model.ChallengeType
import com.microchallenge.core.model.Skill
import kotlinx.coroutines.delay
import java.time.Duration
import java.time.LocalDateTime
import java.time.LocalTime

/** Everything the Play tab shows, read once each time the tab opens. */
data class HomeState(
    val rankTitle: String,
    val rankProgress: Float,
    val xp: Int,
    val xpToNext: Int?,
    val streak: Int,
    val playsToday: Int,
    val dailyGoal: Int,
    val dailyType: ChallengeType,
    val dailyScore: Int?,
    val recent: List<ChallengeType>,
    val bests: Map<ChallengeType, Int>,
)

@Composable
fun HomeScreen(
    state: HomeState,
    onDaily: () -> Unit,
    onQuickPlay: () -> Unit,
    onGame: (ChallengeType) -> Unit,
    onSeeAll: (Skill?) -> Unit,
) {
    val skillRows = Skill.entries.map { s -> s to ChallengeType.entries.filter { it.skill == s } }.filter { it.second.isNotEmpty() }
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 16.dp),
    ) {
        item(key = "header") { Header(state, Modifier.staggerIn(0)) }
        item(key = "daily") { DailyHero(state, onDaily, Modifier.padding(top = 16.dp).staggerIn(1)) }
        item(key = "goal") { GoalAndQuickPlay(state, onQuickPlay, Modifier.padding(top = 14.dp).staggerIn(2)) }
        if (state.recent.isNotEmpty()) {
            item(key = "recent") {
                Column(Modifier.staggerIn(3)) {
                    SectionTitle("Jump back in")
                    GameRow(state.recent, state.bests, onGame)
                }
            }
        }
        skillRows.forEachIndexed { n, (skill, games) ->
            item(key = "skill_${skill.name}") {
                Column(Modifier.staggerIn(4 + n)) {
                    SkillTitle(skill, games.size, onSeeAll = { onSeeAll(skill) })
                    GameRow(games, state.bests, onGame)
                }
            }
        }
    }
}

@Composable
private fun Header(state: HomeState, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        RankBadge(state.rankTitle, state.rankProgress, size = 52.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(state.rankTitle.uppercase(), style = MaterialTheme.typography.labelMedium, color = Palette.Xp,
                fontWeight = FontWeight.Bold)
            Text("${state.xp} XP", style = MaterialTheme.typography.titleLarge)
            if (state.xpToNext != null) {
                Text("${state.xpToNext} XP to next rank", style = MaterialTheme.typography.labelSmall,
                    color = Palette.TextFaint)
            }
        }
        StreakPill(state.streak, playedToday = state.playsToday > 0)
    }
}

/** "Resets in 5h 12m" until local midnight, refreshed every 30 seconds. */
@Composable
private fun rememberResetLabel(): String {
    var now by remember { mutableStateOf(LocalDateTime.now()) }
    LaunchedEffect(Unit) { while (true) { delay(30_000); now = LocalDateTime.now() } }
    val left = Duration.between(now, now.toLocalDate().plusDays(1).atTime(LocalTime.MIDNIGHT))
    val h = left.toHours()
    val m = left.toMinutes() % 60
    return if (h > 0) "Resets in ${h}h ${m}m" else "Resets in ${m}m"
}

@Composable
private fun DailyHero(state: HomeState, onDaily: () -> Unit, modifier: Modifier = Modifier) {
    val style = state.dailyType.skill.style()
    val done = state.dailyScore != null
    Box(
        modifier.fillMaxWidth().clip(Shapes.card)
            .background(Brush.linearGradient(listOf(lerp(style.start, Palette.Night, 0.15f), lerp(style.end, Palette.Night, 0.35f))))
            .border(1.dp, Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.4f), Color.Transparent)), Shapes.card),
    ) {
        GameIcon(
            state.dailyType,
            Modifier.align(Alignment.TopEnd).padding(top = 18.dp, end = 18.dp).rotate(8f).floating(),
            size = 92.dp,
        )
        Column(Modifier.padding(20.dp)) {
            Text(
                "DAILY CHALLENGE", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Black,
                color = Color.White,
                modifier = Modifier.clip(Shapes.pill).background(Color.Black.copy(alpha = 0.22f))
                    .padding(horizontal = 10.dp, vertical = 4.dp),
            )
            Spacer(Modifier.height(12.dp))
            Text(state.dailyType.title, style = MaterialTheme.typography.headlineMedium, color = Color.White,
                modifier = Modifier.padding(end = 100.dp).semantics { heading() })
            Text(
                if (done) "Done today: ${state.dailyScore} points" else "${style.label} · ${rememberResetLabel()}",
                style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.85f),
            )
            Spacer(Modifier.height(18.dp))
            if (done) {
                Row(
                    Modifier.fillMaxWidth().clip(Shapes.button).background(Color.Black.copy(alpha = 0.22f))
                        .padding(vertical = 16.dp),
                    horizontalArrangement = Arrangement.Center,
                ) {
                    Text("✓  Come back tomorrow", style = MaterialTheme.typography.titleMedium, color = Color.White)
                }
            } else {
                ChunkyButton("Play daily", onDaily, Modifier.fillMaxWidth(), face = Color.White,
                    edge = lerp(style.end, Color.Black, 0.35f), textColor = Color(0xFF14102B))
            }
        }
    }
}

@Composable
private fun GoalAndQuickPlay(state: HomeState, onQuickPlay: () -> Unit, modifier: Modifier = Modifier) {
    val goalDone = state.playsToday >= state.dailyGoal
    Panel(modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.semantics(mergeDescendants = true) {
                contentDescription = "Daily goal, ${state.playsToday.coerceAtMost(state.dailyGoal)} of ${state.dailyGoal} rounds"
            }) {
            ProgressRing(
                state.playsToday.toFloat() / state.dailyGoal, size = 58.dp, stroke = 7.dp,
                colors = if (goalDone) listOf(Palette.Correct, Palette.Accent) else listOf(Palette.Xp, Palette.Streak),
            ) {
                Text("${state.playsToday.coerceAtMost(state.dailyGoal)}/${state.dailyGoal}",
                    style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Black)
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(if (goalDone) "Daily goal complete" else "Daily goal", style = MaterialTheme.typography.titleMedium)
                Text(
                    if (goalDone) "Nice. Every extra round still earns XP."
                    else "Play ${state.dailyGoal - state.playsToday} more round${if (state.dailyGoal - state.playsToday == 1) "" else "s"} today",
                    style = MaterialTheme.typography.bodyMedium, color = Palette.TextDim,
                )
            }
        }
        Spacer(Modifier.height(14.dp))
        ChunkyButton("Quick play · 10s", onQuickPlay, Modifier.fillMaxWidth(), face = Palette.Accent,
            edge = Color(0xFF0E8AA0), textColor = Color(0xFF00262D))
    }
}

@Composable
private fun SkillTitle(skill: Skill, count: Int, onSeeAll: () -> Unit) {
    val style = skill.style()
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.padding(top = 8.dp).width(4.dp).height(22.dp).clip(Shapes.pill).background(style.brush))
        Spacer(Modifier.width(10.dp))
        SectionTitle("${style.label}  ·  $count", Modifier.weight(1f), action = "See all", onAction = onSeeAll)
    }
}

@Composable
private fun GameRow(games: List<ChallengeType>, bests: Map<ChallengeType, Int>, onGame: (ChallengeType) -> Unit) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        items(games, key = { it.name }) { t -> MiniGameCard(t, bests[t] ?: 0, onClick = { onGame(t) }) }
    }
}
