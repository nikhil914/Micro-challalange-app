package com.microchallenge.app.games

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.microchallenge.app.ui.GameColors
import com.microchallenge.core.challenges.games.ColorRushSession
import com.microchallenge.core.challenges.games.RushBubble
import com.microchallenge.core.challenges.games.RushTapOutcome
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

private const val BURST_MS = 650L
private const val SPAWN_POP_MS = 180L
private const val SHOCK_MS = 380L
private val FIELD_TOP = Color(0xFF14132B)
private val FIELD_BOTTOM = Color(0xFF2A1B3D)

/** One spark of a burst. Velocity is in canvas fractions per second. */
private class Spark(val angle: Float, val speed: Float, val size: Float, val spin: Float)

/** A burst spawned by a tap, drawn for [BURST_MS] then dropped. */
private class Burst(
    val x: Float,
    val y: Float,
    val color: Color,
    val good: Boolean,
    val startMs: Long,
    val sparks: List<Spark>,
    val label: String,
)

/** Stars in the background, fixed per session. */
private class Star(val x: Float, val y: Float, val r: Float, val phase: Float)

/**
 * Color Rush: bubbles bloom on a dark starfield; tap only the ones matching the target
 * color. Hits explode into sparks with a shockwave and a floating "+points" label; wrong
 * taps shatter grey and shake the combo meter. Every bubble also carries its color's
 * initial letter so color is never the only cue.
 */
@Composable
fun ColorRushScreen(session: ColorRushSession, elapsedMs: Long, onInput: () -> Unit) {
    val haptics = LocalHapticFeedback.current
    val currentOnInput by rememberUpdatedState(onInput)
    val palette = remember(session) { GameColors.entries.take(session.colorCount) }
    val bursts = remember(session) { mutableStateListOf<Burst>() }
    val fx = remember(session) { Random(session.bubbles.size * 31L + session.difficulty) }
    val stars = remember(session) {
        List(40) { Star(fx.nextFloat(), fx.nextFloat(), 0.6f + fx.nextFloat() * 1.6f, fx.nextFloat() * 6.28f) }
    }
    val measurer = rememberTextMeasurer()

    val target = palette[session.targetColor()]
    val phase = session.phaseAt(elapsedMs)
    val phaseProgress = ((elapsedMs - phase.startMs).toFloat() / (phase.endMs - phase.startMs)).coerceIn(0f, 1f)
    val justSwitched = elapsedMs - phase.startMs < 350 && phase.startMs > 0

    val handleTap: (Offset, Float, Float) -> Unit = { pos, w, h ->
        val side = min(w, h)
        val ox = (w - side) / 2f
        val oy = (h - side) / 2f
        val (outcome, b) = session.tapAt((pos.x - ox) / side, (pos.y - oy) / side)
        if (b != null) {
            val good = outcome == RushTapOutcome.HIT
            if (!good) haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            else haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            val n = if (good) 22 else 10
            val now = session.elapsedMs()
            bursts.removeAll { now - it.startMs > BURST_MS }
            bursts += Burst(
                x = b.x, y = b.y,
                color = if (good) palette[b.colorIndex].color else Color(0xFF8A8A9A),
                good = good,
                startMs = session.elapsedMs(),
                sparks = List(n) {
                    Spark(
                        angle = (it.toFloat() / n) * 2f * PI.toFloat() + fx.nextFloat() * 0.4f,
                        speed = 0.25f + fx.nextFloat() * 0.45f,
                        size = 2.5f + fx.nextFloat() * 4f,
                        spin = fx.nextFloat() * 6f - 3f,
                    )
                },
                label = if (good) "+${10 * session.comboMultiplier()}" else "✕",
            )
        }
        currentOnInput()
    }
    val tapHandler by rememberUpdatedState(handleTap)

    Column(Modifier.fillMaxSize().padding(horizontal = 8.dp)) {
        // Target banner with a draining phase bar.
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .padding(horizontal = 16.dp, vertical = 10.dp)
                .semantics { liveRegion = LiveRegionMode.Polite; contentDescription = "Target color ${target.label}" },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val pulse = if (justSwitched) 1.25f else 1f
            Box(Modifier.size((28 * pulse).dp).clip(CircleShape).background(target.color))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("POP ${target.label.uppercase()}", fontWeight = FontWeight.Black, fontSize = 22.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Box(Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp))
                    .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.15f))) {
                    Box(Modifier.fillMaxWidth(1f - phaseProgress).height(4.dp).background(target.color))
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(horizontalAlignment = Alignment.End) {
                Text("${session.points}", fontWeight = FontWeight.Bold, fontSize = 22.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                val mult = session.comboMultiplier()
                Text(if (mult > 1) "COMBO x$mult" else "combo ${session.combo}",
                    fontSize = 12.sp, fontWeight = FontWeight.Bold,
                    color = if (mult > 1) target.color else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f))
            }
        }
        Spacer(Modifier.height(10.dp))

        Box(
            Modifier.weight(1f).fillMaxWidth().clip(RoundedCornerShape(24.dp))
                .background(Brush.verticalGradient(listOf(FIELD_TOP, FIELD_BOTTOM)))
                .pointerInput(session) {
                    // onPress fires on touch-down for snappy reflex play.
                    detectTapGestures(onPress = { tapHandler(it, size.width.toFloat(), size.height.toFloat()) })
                }
                .semantics { contentDescription = "Bubble field. ${session.visible().count { it.colorIndex == session.targetColor() }} ${target.label} bubbles" },
        ) {
            Canvas(Modifier.fillMaxSize()) {
                val side = min(size.width, size.height)
                val origin = Offset((size.width - side) / 2f, (size.height - side) / 2f)
                val t = elapsedMs / 1000f

                // Twinkling starfield.
                for (s in stars) {
                    val a = 0.25f + 0.35f * (0.5f + 0.5f * sin(t * 2.2f + s.phase))
                    drawCircle(Color.White.copy(alpha = a), s.r * density, Offset(s.x * size.width, s.y * size.height))
                }
                // Flash the field edge in the new target color on a switch.
                if (justSwitched) {
                    val k = 1f - (elapsedMs - phase.startMs) / 350f
                    drawRect(target.color.copy(alpha = 0.35f * k), style = Stroke(width = 10.dp.toPx()))
                }

                for (b in session.visible()) drawBubble(b, palette[b.colorIndex], elapsedMs, side, origin, measurer)

                for (burst in bursts) if (elapsedMs - burst.startMs <= BURST_MS) drawBurst(burst, elapsedMs, side, origin, measurer)
            }
        }

        Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Popped ${session.hits}", style = MaterialTheme.typography.titleSmall)
            Text("Best x${session.bestCombo}", style = MaterialTheme.typography.titleSmall)
            Text("Wrong ${session.wrongTaps}", style = MaterialTheme.typography.titleSmall,
                color = if (session.wrongTaps > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onBackground)
        }
    }
}

private fun DrawScope.drawBubble(
    b: RushBubble, c: GameColors, now: Long, side: Float, origin: Offset, measurer: TextMeasurer,
) {
    val age = now - b.spawnMs
    val left = b.despawnMs - now
    val life = (b.despawnMs - b.spawnMs).toFloat()
    // Spring-ish pop in, gentle breathing, shrink-out over the last 250 ms.
    val popIn = (age / SPAWN_POP_MS.toFloat()).coerceIn(0f, 1f)
    val overshoot = 1f + 0.18f * sin(popIn * PI.toFloat())
    val fadeOut = (left / 250f).coerceIn(0f, 1f)
    val breathe = 1f + 0.03f * sin(age / 120f)
    val scale = popIn * overshoot * breathe * (0.6f + 0.4f * fadeOut)
    val r = b.radius * side * scale
    val center = Offset(origin.x + b.x * side, origin.y + b.y * side)
    val alpha = fadeOut

    // Outer glow.
    drawCircle(
        Brush.radialGradient(listOf(c.color.copy(alpha = 0.45f * alpha), Color.Transparent), center, r * 1.7f),
        r * 1.7f, center,
    )
    // Body with a highlight for a glossy look.
    drawCircle(
        Brush.radialGradient(
            listOf(Color.White.copy(alpha = 0.9f), c.color, c.color.copy(red = c.color.red * 0.55f, green = c.color.green * 0.55f, blue = c.color.blue * 0.55f)),
            center = center + Offset(-r * 0.35f, -r * 0.4f), radius = r * 1.5f,
        ),
        r, center, alpha = alpha,
    )
    // Life ring that drains as the bubble ages.
    val sweep = 360f * (left / life).coerceIn(0f, 1f)
    drawArc(Color.White.copy(alpha = 0.75f * alpha), -90f, sweep, false,
        topLeft = center - Offset(r * 1.12f, r * 1.12f),
        size = androidx.compose.ui.geometry.Size(r * 2.24f, r * 2.24f),
        style = Stroke(width = r * 0.09f))
    // Color initial so color is not the only cue.
    if (r > 8f) {
        val layout = measurer.measure(c.label.take(1),
            TextStyle(fontSize = (r * 0.8f / density).sp, fontWeight = FontWeight.Black, color = Color.Black.copy(alpha = 0.55f * alpha)))
        drawText(layout, topLeft = center - Offset(layout.size.width / 2f, layout.size.height / 2f))
    }
}

private fun DrawScope.drawBurst(burst: Burst, now: Long, side: Float, origin: Offset, measurer: TextMeasurer) {
    val age = (now - burst.startMs).coerceAtLeast(0L)
    val k = (age / BURST_MS.toFloat()).coerceIn(0f, 1f)
    val secs = age / 1000f
    val center = Offset(origin.x + burst.x * side, origin.y + burst.y * side)
    val fade = 1f - k

    // Shockwave ring.
    if (age < SHOCK_MS) {
        val s = age / SHOCK_MS.toFloat()
        drawCircle(burst.color.copy(alpha = 0.8f * (1f - s)), side * (0.05f + 0.16f * s), center,
            style = Stroke(width = (1f - s) * 8.dp.toPx()))
    }
    // Sparks with drag and a little gravity.
    for (sp in burst.sparks) {
        val dist = sp.speed * side * (1f - (1f - secs).let { it * it * it }.coerceAtLeast(0f)) * 0.55f
        val gravity = side * 0.18f * secs * secs
        val p = center + Offset(cos(sp.angle) * dist, sin(sp.angle) * dist + gravity)
        val sz = sp.size * density * fade
        if (burst.good) {
            rotate(sp.spin * age / 10f, p) {
                drawRect(burst.color.copy(alpha = fade), topLeft = p - Offset(sz, sz / 2.5f),
                    size = androidx.compose.ui.geometry.Size(sz * 2, sz / 1.25f))
            }
            drawCircle(Color.White.copy(alpha = fade * 0.8f), sz * 0.35f, p)
        } else {
            drawCircle(burst.color.copy(alpha = fade), sz * 0.8f, p)
        }
    }
    // Floating score label.
    val layout = measurer.measure(burst.label, TextStyle(
        fontSize = 20.sp, fontWeight = FontWeight.Black,
        color = (if (burst.good) Color.White else Color(0xFFFF6B6B)).copy(alpha = fade)))
    drawText(layout, topLeft = center - Offset(layout.size.width / 2f, layout.size.height / 2f + side * 0.12f * k))
}
