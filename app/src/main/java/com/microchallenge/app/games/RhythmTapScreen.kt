package com.microchallenge.app.games

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.microchallenge.core.challenges.games.RhythmTapSession
import com.microchallenge.core.challenges.games.RhythmTapSession.Grade
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

private val RhythmBgTop = Color(0xFF1B1036)
private val RhythmBgBottom = Color(0xFF070A1F)
private val RhythmPerfect = Color(0xFFFFD740)
private val RhythmGood = Color(0xFF64FFDA)
private val RhythmMiss = Color(0xFFFF5370)

/** Ring colors, cycled per beat so consecutive rings are easy to tell apart. */
private val RhythmRingColors = listOf(
    Color(0xFF00E5FF), Color(0xFFB388FF), Color(0xFFFF80AB), Color(0xFF69F0AE),
)

/** How long hit feedback (burst, label, pulse) stays on screen. */
private const val RHYTHM_FEEDBACK_MS = 650L

private fun gradeColor(grade: Grade): Color = when (grade) {
    Grade.PERFECT -> RhythmPerfect
    Grade.GOOD -> RhythmGood
    Grade.MISS -> RhythmMiss
}

/**
 * Rhythm Tap: neon rings shrink onto a glowing target on a steady beat. The whole area is
 * one tap pad that fires on touch-down, so release timing never matters. Hits burst into
 * sparks, the target pulses, and a floating label says PERFECT / GOOD / MISS.
 */
@Composable
fun RhythmTapScreen(session: RhythmTapSession, elapsedMs: Long, onInput: () -> Unit) {
    val haptics = LocalHapticFeedback.current
    val currentOnInput by rememberUpdatedState(onInput)

    // elapsedMs ticks every frame; the session's own clock gives the exact time for drawing.
    @Suppress("UNUSED_VARIABLE") val tick = elapsedMs
    val now = session.elapsedMs()
    val hits = session.hits
    val combo = session.currentCombo
    val perfect = session.count(Grade.PERFECT)
    val good = session.count(Grade.GOOD)
    val missed = session.count(Grade.MISS)
    val last = hits.lastOrNull()
    val lastAge = if (last == null) Long.MAX_VALUE else now - last.atMs
    val showLabel = last != null && lastAge in 0 until RHYTHM_FEEDBACK_MS

    val handleTap: () -> Unit = {
        val hit = session.tap()
        if (hit != null) {
            haptics.performHapticFeedback(
                if (hit.grade == Grade.MISS) HapticFeedbackType.LongPress else HapticFeedbackType.TextHandleMove,
            )
        }
        currentOnInput()
    }
    val tapHandler by rememberUpdatedState(handleTap)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .clip(RoundedCornerShape(24.dp))
            .pointerInput(session) { detectTapGestures(onPress = { tapHandler() }) }
            .semantics {
                role = Role.Button
                contentDescription = "Rhythm pad. Tap when each ring reaches the circle."
                onClick(label = "Tap beat") { tapHandler(); true }
            },
    ) {
        Canvas(Modifier.fillMaxSize()) {
            drawRhythmScene(session, now, hits, combo)
        }

        Column(Modifier.fillMaxSize().padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth().clearAndSetSemantics {
                    contentDescription = "$perfect perfect, $good good, $missed missed, combo $combo"
                },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("★ $perfect  ● $good  ✕ $missed", color = Color.White.copy(alpha = 0.85f), fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold)
                Text(
                    text = if (combo >= 2) "Combo ×$combo" else " ",
                    color = lerp(RhythmGood, RhythmPerfect, (combo / 10f).coerceIn(0f, 1f)),
                    fontSize = (18 + min(combo, 10)).sp,
                    fontWeight = FontWeight.Black,
                )
            }
        }

        // Floating judgement label: rises and fades just above the target.
        val labelProgress = if (showLabel) lastAge.toFloat() / RHYTHM_FEEDBACK_MS else 1f
        Text(
            text = when {
                !showLabel || last == null -> " "
                last.grade == Grade.PERFECT -> "PERFECT"
                last.grade == Grade.GOOD -> if ((last.offsetMs ?: 0L) < 0) "GOOD · early" else "GOOD · late"
                last.beatIndex < 0 -> "MISS · off beat"
                else -> "MISS"
            },
            color = if (last != null) gradeColor(last.grade) else Color.Transparent,
            fontSize = 30.sp,
            fontWeight = FontWeight.Black,
            modifier = Modifier
                .align(Alignment.Center)
                .graphicsLayer {
                    val pop = easeOutBack((labelProgress * 4f).coerceAtMost(1f))
                    scaleX = 0.6f + 0.4f * pop
                    scaleY = scaleX
                    translationY = -size.height * 2.6f - labelProgress * 40.dp.toPx()
                    alpha = 1f - labelProgress * labelProgress
                }
                .semantics { liveRegion = LiveRegionMode.Polite },
        )

        Text(
            text = "Tap as each ring lands",
            color = Color.White.copy(alpha = 0.6f),
            fontSize = 14.sp,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 40.dp),
        )
    }
}

private fun DrawScope.drawRhythmScene(
    session: RhythmTapSession,
    now: Long,
    hits: List<RhythmTapSession.Hit>,
    combo: Int,
) {
    val beats = session.beats
    val center = Offset(size.width / 2f, size.height * 0.48f)
    val target = min(size.width, size.height) * 0.17f
    val heat = (combo / 12f).coerceIn(0f, 1f)

    // Phase within the current beat: 1 right on a beat, decaying to 0 before the next.
    val beatPulse = run {
        val lastBeat = beats.lastOrNull { it <= now } ?: return@run 0f
        (1f - (now - lastBeat).toFloat() / session.beatIntervalMs).coerceIn(0f, 1f)
    }

    // Background: deep gradient plus a glow that throbs on the beat and warms up with the combo.
    drawRect(Brush.verticalGradient(listOf(RhythmBgTop, RhythmBgBottom)))
    val glowColor = lerp(Color(0xFF7C4DFF), Color(0xFFFF4081), heat)
    drawGlow(center, size.maxDimension * (0.45f + 0.08f * beatPulse), glowColor, 0.22f + 0.18f * beatPulse + 0.15f * heat)

    // Speaker-cone rings radiating outward to the beat.
    for (k in 0 until 4) {
        val wave = ((now % session.beatIntervalMs).toFloat() / session.beatIntervalMs + k / 4f) % 1f
        drawCircle(
            color = Color.White.copy(alpha = 0.06f * (1f - wave)),
            radius = target * (1.4f + wave * 3.2f),
            center = center,
            style = Stroke(width = 2.dp.toPx()),
        )
    }

    // Orbiting sparkles that speed up with the combo.
    val orbit = now / (900f - 500f * heat)
    for (i in 0 until 10) {
        val a = orbit + i * (2 * PI / 10).toFloat()
        val r = target * (2.1f + 0.25f * sin(a * 1.7f))
        val p = Offset(center.x + cos(a) * r, center.y + sin(a) * r)
        drawCircle(lerp(Color(0xFFB388FF), RhythmPerfect, heat).copy(alpha = 0.35f + 0.3f * heat), 2.5.dp.toPx(), p)
    }

    // Target: glossy disc with a bright rim that pulses on every landed beat.
    val recent = hits.lastOrNull { it.beatIndex >= 0 && it.grade != Grade.MISS && now - it.atMs in 0 until RHYTHM_FEEDBACK_MS }
    val hitPulse = if (recent == null) 0f else 1f - (now - recent.atMs).toFloat() / RHYTHM_FEEDBACK_MS
    val targetR = target * (1f + 0.06f * beatPulse + 0.14f * hitPulse * hitPulse)
    val rimColor = recent?.let { gradeColor(it.grade) } ?: Color.White
    drawGlow(center, targetR * 1.9f, rimColor, 0.25f + 0.45f * hitPulse)
    drawCircle(
        brush = Brush.radialGradient(
            listOf(Color(0xFF3D2C8D), Color(0xFF1A1446)),
            center = Offset(center.x - targetR * 0.3f, center.y - targetR * 0.3f),
            radius = targetR * 1.4f,
        ),
        radius = targetR,
        center = center,
    )
    drawCircle(Color.White.copy(alpha = 0.10f), targetR * 0.55f, Offset(center.x - targetR * 0.25f, center.y - targetR * 0.3f))
    drawCircle(lerp(Color.White, rimColor, hitPulse), targetR, center, style = Stroke(width = 5.dp.toPx()))
    // Perfect-window guide: thin halo just outside the rim.
    drawCircle(Color.White.copy(alpha = 0.18f), targetR * 1.12f, center, style = Stroke(width = 1.5.dp.toPx()))

    // Approaching rings for every beat not yet judged.
    for (i in beats.indices) {
        if (session.gradeOf(i) != null) continue
        val untilLand = beats[i] - now
        if (untilLand > session.approachMs || untilLand < -session.goodWindowMs) continue
        val p = (untilLand.toFloat() / session.approachMs).coerceAtLeast(0f)
        val color = RhythmRingColors[i % RhythmRingColors.size]
        val radius = target * (1f + 2.4f * p)
        val alpha = (1f - p * 0.75f).coerceIn(0f, 1f)
        drawCircle(color.copy(alpha = alpha * 0.35f), radius, center, style = Stroke(width = 14.dp.toPx() * (1f - 0.5f * p)))
        drawCircle(lerp(color, Color.White, 0.3f).copy(alpha = alpha), radius, center,
            style = Stroke(width = 4.dp.toPx()))
    }

    // Hit and miss feedback: bursts and expanding shock rings.
    for (hit in hits) {
        val age = now - hit.atMs
        if (age !in 0 until RHYTHM_FEEDBACK_MS) continue
        val t = age.toFloat() / RHYTHM_FEEDBACK_MS
        val color = gradeColor(hit.grade)
        when (hit.grade) {
            Grade.PERFECT -> {
                drawBurst(center, t, color, target * 1.5f, sparks = 18)
                drawBurst(center, (t * 1.3f).coerceAtMost(1f), Color.White, target * 1.1f, sparks = 10)
                drawCircle(color.copy(alpha = 0.5f * (1f - t)), target * (1f + 2f * t), center,
                    style = Stroke(width = 3.dp.toPx()))
            }
            Grade.GOOD -> drawBurst(center, t, color, target * 1.25f, sparks = 12)
            Grade.MISS -> {
                // Crack: short red slashes flashing over the rim.
                val fade = 1f - t
                for (k in 0 until 6) {
                    val a = k * (PI / 3).toFloat() + 0.4f
                    val from = Offset(center.x + cos(a) * target * 0.9f, center.y + sin(a) * target * 0.9f)
                    val to = Offset(center.x + cos(a) * target * 1.25f, center.y + sin(a) * target * 1.25f)
                    drawLine(color.copy(alpha = fade), from, to, strokeWidth = 4.dp.toPx())
                }
            }
        }
    }

    // Beat track along the bottom: one dot per beat, filled as it is judged.
    val dotR = 4.dp.toPx()
    val gap = min(14.dp.toPx(), (size.width - 48.dp.toPx()) / beats.size.coerceAtLeast(1))
    val startX = center.x - gap * (beats.size - 1) / 2f
    val y = size.height - 18.dp.toPx()
    for (i in beats.indices) {
        val x = Offset(startX + i * gap, y)
        when (val g = session.gradeOf(i)) {
            null -> drawCircle(Color.White.copy(alpha = 0.25f), dotR, x, style = Stroke(width = 1.5.dp.toPx()))
            Grade.MISS -> drawCircle(RhythmMiss.copy(alpha = 0.6f), dotR * 0.6f, x)
            else -> drawCircle(gradeColor(g), dotR, x)
        }
    }
}
