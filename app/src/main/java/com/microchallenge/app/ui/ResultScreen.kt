package com.microchallenge.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.microchallenge.app.Screen
import com.microchallenge.app.ui.design.AppFonts
import com.microchallenge.app.ui.design.ChunkyButton
import com.microchallenge.app.ui.design.ConfettiBurst
import com.microchallenge.app.ui.design.Flame
import com.microchallenge.app.ui.design.GameIcon
import com.microchallenge.app.ui.design.GradientBar
import com.microchallenge.app.ui.design.LocalReduceMotion
import com.microchallenge.app.ui.design.Palette
import com.microchallenge.app.ui.design.Panel
import com.microchallenge.app.ui.design.RankBadge
import com.microchallenge.app.ui.design.RoundIconButton
import com.microchallenge.app.ui.design.Shapes
import com.microchallenge.app.ui.design.SoftButton
import com.microchallenge.app.ui.design.StarRow
import com.microchallenge.app.ui.design.StatTile
import com.microchallenge.app.ui.design.rememberShareText
import com.microchallenge.app.ui.design.starsFor
import com.microchallenge.app.ui.design.style
import com.microchallenge.core.engine.Progression
import kotlinx.coroutines.delay

private fun verdict(score: Int) = when {
    score >= 900 -> "UNREAL"
    score >= 700 -> "Clutch"
    score >= 450 -> "Solid run"
    score >= 200 -> "Warming up"
    else -> "Run it back"
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ResultScreen(r: Screen.Result, onAgain: () -> Unit, onRetry: () -> Unit, onHome: () -> Unit) {
    val res = r.result
    val style = res.type.skill.style()
    val reduce = LocalReduceMotion.current
    val haptics = LocalHapticFeedback.current
    val share = rememberShareText()

    // Score counts up with a haptic tick every 100 points, then lands.
    val count = remember(r) { Animatable(if (reduce) res.score.toFloat() else 0f) }
    LaunchedEffect(r) {
        var lastTick = 0
        count.animateTo(res.score.toFloat(), tween(if (reduce) 0 else 1000, easing = FastOutSlowInEasing)) {
            val hundreds = value.toInt() / 100
            if (hundreds > lastTick) { lastTick = hundreds; haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove) }
        }
        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
    }
    val celebrate = (r.isBest && r.previousBest > 0) || r.rankedUp || r.streakMilestone > 0
    var showRankUp by remember(r) { mutableStateOf(false) }
    LaunchedEffect(r) { if (r.rankedUp) { delay(1_300); showRankUp = true } }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                RoundIconButton("✕", "Home", onHome)
                Spacer(Modifier.weight(1f))
                if (r.isDaily) {
                    Text("DAILY DONE", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Black,
                        color = Palette.Xp, modifier = Modifier.clip(Shapes.pill).background(Palette.Xp.copy(alpha = 0.15f))
                            .padding(horizontal = 12.dp, vertical = 6.dp))
                }
            }
            Column(
                Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    GameIcon(res.type, size = 36.dp)
                    Spacer(Modifier.width(10.dp))
                    Text(res.type.title, style = MaterialTheme.typography.titleLarge)
                }
                Spacer(Modifier.height(14.dp))
                StarRow(starsFor(res.score), size = 44.dp)
                Text(
                    "${count.value.toInt()}",
                    style = TextStyle(
                        brush = Brush.verticalGradient(listOf(Color.White, lerp(style.start, Color.White, 0.3f))),
                        fontFamily = AppFonts.display, fontWeight = FontWeight.Black, fontSize = 96.sp, lineHeight = 100.sp,
                    ),
                    modifier = Modifier.semantics { contentDescription = "Score ${res.score} out of 1000" },
                )
                Text(verdict(res.score), style = MaterialTheme.typography.headlineSmall, color = style.start)
                if (res.detail.isNotBlank()) {
                    Text(res.detail, style = MaterialTheme.typography.bodyLarge, color = Palette.TextDim,
                        textAlign = TextAlign.Center, modifier = Modifier.padding(top = 4.dp))
                }
                if (r.isBest && r.previousBest > 0) {
                    Spacer(Modifier.height(10.dp))
                    NewBestPill(res.score - r.previousBest)
                }
                Spacer(Modifier.height(18.dp))
                Panel(Modifier.fillMaxWidth()) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically) {
                        StatTile(if (r.isBest) "${res.score}" else "${r.previousBest}", "Best",
                            color = if (r.isBest) Palette.Xp else Palette.Text)
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Flame(size = 24.dp)
                                Text(" ${r.streak}", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black,
                                    color = Palette.Streak)
                            }
                            Text("STREAK", style = MaterialTheme.typography.labelSmall, color = Palette.TextFaint)
                        }
                        StatTile("${res.difficulty}", "Level")
                    }
                }
                Spacer(Modifier.height(12.dp))
                XpPanel(r)
                if (r.streakMilestone > 0) {
                    Spacer(Modifier.height(12.dp))
                    StreakMilestone(r.streakMilestone) {
                        share("I just hit a ${r.streakMilestone}-day streak in MicroMind!")
                    }
                }
                if (r.isDaily) {
                    Text("Daily challenge complete. A new one unlocks at midnight.",
                        style = MaterialTheme.typography.bodyMedium, color = Palette.TextDim,
                        textAlign = TextAlign.Center, modifier = Modifier.padding(top = 12.dp))
                }
                Spacer(Modifier.height(12.dp))
            }
            ChunkyButton("Next game", onAgain, Modifier.fillMaxWidth(), height = 64.dp, fontSize = 22.sp)
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SoftButton("Retry", onRetry, Modifier.weight(1f))
                SoftButton("Share", {
                    share("I scored ${res.score} in ${res.type.title} on MicroMind. Can you beat it?")
                }, Modifier.weight(1f))
            }
        }
        if (celebrate) ConfettiBurst(r)
        AnimatedVisibility(showRankUp, enter = fadeIn() + scaleIn(initialScale = 0.8f), exit = fadeOut()) {
            RankUpOverlay(r.rank.title) { showRankUp = false }
        }
    }
}

@Composable
private fun NewBestPill(gain: Int) {
    val reduce = LocalReduceMotion.current
    val glow = if (!reduce) {
        val t = rememberInfiniteTransition(label = "best")
        t.animateFloat(0.96f, 1.04f, infiniteRepeatable(tween(700), RepeatMode.Reverse), label = "glow").value
    } else 1f
    Text(
        "NEW BEST  +$gain",
        Modifier.scale(glow).clip(Shapes.pill)
            .background(Brush.horizontalGradient(listOf(Palette.Xp, Palette.Streak)))
            .padding(horizontal = 16.dp, vertical = 8.dp),
        color = Color(0xFF2A1C00), fontFamily = AppFonts.display, fontWeight = FontWeight.Black, fontSize = 18.sp,
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun XpPanel(r: Screen.Result) {
    val g = r.xpGain
    val before = r.xpTotal - g.total
    val next = Progression.nextRank(r.xpTotal)
    // If this play ranked up, the bar starts from empty for the new rank.
    val from = if (r.rankedUp) 0f else Progression.rankProgress(before)
    Panel(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            RankBadge(r.rank.title, Progression.rankProgress(r.xpTotal), size = 40.dp)
            Spacer(Modifier.width(10.dp))
            Text(r.rank.title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            Text("+${g.total} XP", style = MaterialTheme.typography.titleLarge, color = Palette.Xp, fontWeight = FontWeight.Black)
        }
        Spacer(Modifier.height(10.dp))
        GradientBar(Progression.rankProgress(r.xpTotal), animateFrom = from, durationMs = 1_100)
        Text(
            if (next == null) "Max rank reached" else "${next.minXp - r.xpTotal} XP to ${next.title}",
            style = MaterialTheme.typography.labelMedium, color = Palette.TextDim, modifier = Modifier.padding(top = 6.dp),
        )
        val extras = buildList {
            if (g.streakBonus > 0) add("Streak +${g.streakBonus}")
            if (g.comboBonus > 0) add("Hot run x${r.combo} +${g.comboBonus}")
            if (g.bestBonus > 0) add("New best +${g.bestBonus}")
        }
        if (extras.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                extras.forEach {
                    Text(it, Modifier.clip(Shapes.pill).background(Palette.Xp.copy(alpha = 0.14f))
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelMedium, color = Palette.Xp)
                }
            }
        }
    }
}

@Composable
private fun StreakMilestone(days: Int, onShare: () -> Unit) {
    Panel(
        Modifier.fillMaxWidth().border(1.dp, Palette.Streak.copy(alpha = 0.6f), Shapes.card),
        color = lerp(Palette.Surface1, Palette.StreakHot, 0.18f),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Flame(size = 52.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("$days-day streak!", style = MaterialTheme.typography.headlineSmall, color = Palette.Streak)
                Text("You showed up $days days in a row.", style = MaterialTheme.typography.bodyMedium, color = Palette.TextDim)
            }
        }
        Spacer(Modifier.height(12.dp))
        ChunkyButton("Share streak", onShare, Modifier.fillMaxWidth(), face = Palette.Streak, edge = Color(0xFFB34A12),
            height = 48.dp, fontSize = 16.sp)
    }
}

@Composable
private fun RankUpOverlay(title: String, onDismiss: () -> Unit) {
    Box(
        Modifier.fillMaxSize().background(Palette.Night.copy(alpha = 0.92f))
            .clickable(remember { MutableInteractionSource() }, indication = null, onClick = onDismiss),
        contentAlignment = Alignment.Center,
    ) {
        ConfettiBurst("rank_$title")
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(24.dp)) {
            Text("RANK UP", style = MaterialTheme.typography.titleMedium, color = Palette.Xp, fontWeight = FontWeight.Black,
                letterSpacing = 4.sp)
            Spacer(Modifier.height(18.dp))
            RankBadge(title, 1f, size = 150.dp)
            Spacer(Modifier.height(18.dp))
            Text(title, style = MaterialTheme.typography.displayMedium)
            Text("New rank unlocked. Keep the streak going.", style = MaterialTheme.typography.bodyLarge,
                color = Palette.TextDim, textAlign = TextAlign.Center)
            Spacer(Modifier.height(28.dp))
            ChunkyButton("Nice!", onDismiss, Modifier.width(220.dp), face = Palette.Xp, edge = Palette.XpEdge,
                textColor = Color(0xFF2A1C00))
        }
    }
}
