package com.microchallenge.app.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.microchallenge.app.Screen
import com.microchallenge.app.ui.design.AppFonts
import com.microchallenge.app.ui.design.ChunkyButton
import com.microchallenge.app.ui.design.GameIcon
import com.microchallenge.app.ui.design.LocalReduceMotion
import com.microchallenge.app.ui.design.Medal
import com.microchallenge.app.ui.design.MedalBadge
import com.microchallenge.app.ui.design.Palette
import com.microchallenge.app.ui.design.Panel
import com.microchallenge.app.ui.design.RoundIconButton
import com.microchallenge.app.ui.design.Shapes
import com.microchallenge.app.ui.design.SkillChip
import com.microchallenge.app.ui.design.SoftButton
import com.microchallenge.app.ui.design.StatTile
import com.microchallenge.app.ui.design.floating
import com.microchallenge.app.ui.design.style
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Game intro: what the game is, your best, then a 3-2-1 so you are ready. */
@Composable
fun IntroScreen(
    intro: Screen.Intro,
    best: Int,
    plays: Int,
    onStart: () -> Unit,
    onBack: () -> Unit,
    onShuffle: () -> Unit,
) {
    var counting by remember(intro) { mutableIntStateOf(if (intro.autoStart) 3 else -1) }
    val haptics = LocalHapticFeedback.current
    LaunchedEffect(counting) {
        if (counting > 0) {
            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            delay(700); counting -= 1
        } else if (counting == 0) {
            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            delay(250); onStart()
        }
    }
    val style = intro.type.skill.style()
    val medal = Medal.forScore(best)

    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            RoundIconButton("‹", "Back", onClick = { if (counting < 0) onBack() })
            Spacer(Modifier.weight(1f))
            if (intro.isDaily) {
                Text("DAILY CHALLENGE", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Black,
                    color = Palette.Xp,
                    modifier = Modifier.clip(Shapes.pill).background(Palette.Xp.copy(alpha = 0.15f))
                        .padding(horizontal = 12.dp, vertical = 6.dp))
            }
        }
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                Modifier.fillMaxWidth().height(190.dp).clip(Shapes.card)
                    .background(Brush.linearGradient(listOf(lerp(style.start, Palette.Night, 0.2f), lerp(style.end, Palette.Night, 0.5f))))
                    .border(1.dp, Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.35f), Color.Transparent)), Shapes.card),
                contentAlignment = Alignment.Center,
            ) {
                GameIcon(intro.type, Modifier.floating(8.dp), size = 112.dp)
            }
            Spacer(Modifier.height(18.dp))
            Text(intro.type.title, style = MaterialTheme.typography.displaySmall, textAlign = TextAlign.Center,
                modifier = Modifier.semantics { heading() })
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                SkillChip(style)
                Spacer(Modifier.width(8.dp))
                Text("Level ${intro.difficulty}", style = MaterialTheme.typography.labelLarge, color = Palette.TextDim)
            }
            Spacer(Modifier.height(16.dp))
            Panel(Modifier.fillMaxWidth()) {
                Text("HOW TO PLAY", style = MaterialTheme.typography.labelMedium, color = style.start, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(6.dp))
                Text(intro.type.instructions, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
            }
            Spacer(Modifier.height(12.dp))
            Panel(Modifier.fillMaxWidth()) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically) {
                    StatTile(if (plays == 0) "-" else "$best", "Best")
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        MedalBadge(medal, size = 30.dp)
                        Text(medal.label.uppercase(), style = MaterialTheme.typography.labelSmall, color = Palette.TextFaint)
                    }
                    StatTile("$plays", "Plays")
                }
            }
            Spacer(Modifier.height(16.dp))
        }
        Box(Modifier.fillMaxWidth().height(150.dp), contentAlignment = Alignment.Center) {
            if (counting >= 0) {
                Countdown(counting, style.start)
            } else {
                Column {
                    ChunkyButton("Play · 10s", { counting = 3 }, Modifier.fillMaxWidth(), height = 68.dp, fontSize = 24.sp,
                        face = style.start, edge = lerp(style.end, Color.Black, 0.45f), textColor = Color(0xFF14102B))
                    if (intro.random && !intro.isDaily) {
                        Spacer(Modifier.height(10.dp))
                        SoftButton("Try another game", onShuffle, Modifier.fillMaxWidth())
                    }
                }
            }
        }
    }
}

/** 3, 2, 1, GO: each number punches in with a ring pulse. */
@Composable
private fun Countdown(n: Int, color: Color) {
    val reduce = LocalReduceMotion.current
    val pop = remember(n) { Animatable(if (reduce) 1f else 1.8f) }
    val ring = remember(n) { Animatable(0f) }
    LaunchedEffect(n) {
        if (!reduce) {
            coroutineScope {
                launch { pop.animateTo(1f, spring(dampingRatio = 0.5f, stiffness = Spring.StiffnessMedium)) }
                launch { ring.animateTo(1f, tween(650, easing = FastOutSlowInEasing)) }
            }
        }
    }
    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(150.dp)) {
        Canvas(Modifier.size(150.dp)) {
            val r = size.minDimension / 2f * (0.45f + 0.55f * ring.value)
            drawCircle(color.copy(alpha = (1f - ring.value) * 0.8f), r, style = Stroke(6.dp.toPx()))
        }
        Text(
            if (n == 0) "GO!" else "$n",
            fontFamily = AppFonts.display, fontWeight = FontWeight.Black, fontSize = 84.sp, color = color,
            modifier = Modifier.scale(pop.value).semantics { liveRegion = LiveRegionMode.Polite },
        )
    }
}
