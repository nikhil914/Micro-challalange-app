package com.microchallenge.app.games

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.microchallenge.core.challenges.games.MathBlitzSession
import com.microchallenge.core.challenges.games.MathBlitzSession.Effect
import com.microchallenge.core.challenges.games.MathBlitzSession.TapResult
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/** Neon colors per lane: top and bottom of each bubble's gradient. */
private val LaneColors = listOf(
    Color(0xFF7CF3FF) to Color(0xFF3A7BFF),
    Color(0xFFFF9BF5) to Color(0xFF9B4DFF),
    Color(0xFFFFE57C) to Color(0xFFFF8A3A),
)
private val SkyTop = Color(0xFF1B1140)
private val SkyBottom = Color(0xFF060814)
private val PopGreen = Color(0xFF6BFFB0)
private val DangerRed = Color(0xFFFF4D6D)

private const val BUBBLE_HEIGHT_DP = 54
private const val BANNER_MS = 800L

private data class BlitzStar(val x: Float, val y: Float, val r: Float, val speed: Float, val phase: Float)

@Composable
fun MathBlitzScreen(session: MathBlitzSession, elapsedMs: Long, onInput: () -> Unit) {
    val haptics = LocalHapticFeedback.current
    val currentOnInput by rememberUpdatedState(onInput)

    // elapsedMs ticks every frame; reading it keeps bubbles moving smoothly.
    @Suppress("UNUSED_VARIABLE") val tick = elapsedMs
    val now = session.nowMs()
    val bubbles = session.bubbles()
    val effects = session.effects()

    // Field shake on any mistake or landed match.
    val shake = remember(session) { Animatable(0f) }
    val hurtKey = session.mistakes + session.misses
    LaunchedEffect(hurtKey) {
        if (hurtKey > 0) for (x in listOf(14f, -11f, 8f, -4f, 0f)) shake.animateTo(x, tween(40))
    }

    // Combo badge pops each time the combo grows.
    val comboPop = remember(session) { Animatable(1f) }
    LaunchedEffect(session.combo) {
        if (session.combo > 0) {
            comboPop.snapTo(1.45f)
            comboPop.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium))
        }
    }

    // Target pulses gently; a new wave makes it spin in.
    val targetIn = remember(session) { Animatable(1f) }
    LaunchedEffect(session.wave) {
        if (session.wave > 1) {
            targetIn.snapTo(0f)
            targetIn.animateTo(1f, spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessMediumLow))
        }
    }

    val stars = remember(session) {
        val r = Random(42)
        List(46) { BlitzStar(r.nextFloat(), r.nextFloat(), 0.6f + r.nextFloat() * 1.8f, 0.01f + r.nextFloat() * 0.04f, r.nextFloat() * 6.28f) }
    }

    fun onBubbleTap(id: Int) {
        when (session.tap(id)) {
            TapResult.HIT, TapResult.WAVE_CLEARED -> haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            TapResult.WRONG -> haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            TapResult.IGNORED -> Unit
        }
        currentOnInput()
    }

    Column(Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 4.dp)) {
        // ---------------------------------------------------------------- HUD
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Wave ${session.wave}", style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("${session.points}", fontSize = 26.sp, fontWeight = FontWeight.Black,
                    modifier = Modifier.semantics { contentDescription = "${session.points} points" })
                WaveDots(session.hitsInWave)
            }
            TargetOrb(
                target = session.target,
                pulse = 1f + 0.05f * sin(now / 160f),
                enter = targetIn.value,
            )
            Box(Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) {
                ComboBadge(session.combo, session.multiplier(), comboPop.value)
            }
        }
        Spacer(Modifier.height(10.dp))

        // ---------------------------------------------------------------- play field
        BoxWithConstraints(
            Modifier
                .fillMaxWidth()
                .weight(1f)
                .graphicsLayer { translationX = shake.value }
                .clip(RoundedCornerShape(24.dp))
                .background(Brush.verticalGradient(listOf(SkyTop, SkyBottom)))
                .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(24.dp)),
        ) {
            val fieldW = maxWidth
            val fieldH = maxHeight
            val laneW = fieldW / MathBlitzSession.LANES
            val bubbleH = BUBBLE_HEIGHT_DP.dp
            val travel = fieldH - bubbleH - 18.dp

            // Background: drifting stars, lane guides, danger floor, effect bursts.
            Canvas(Modifier.fillMaxSize()) {
                val t = now / 1000f
                for (s in stars) {
                    val y = ((s.y + t * s.speed) % 1f) * size.height
                    val twinkle = 0.35f + 0.65f * ((sin(t * 3f + s.phase) + 1f) / 2f)
                    drawCircle(Color.White.copy(alpha = 0.55f * twinkle), s.r.dp.toPx(), Offset(s.x * size.width, y))
                }
                val lanePx = size.width / MathBlitzSession.LANES
                for (i in 1 until MathBlitzSession.LANES) {
                    drawLine(
                        Brush.verticalGradient(listOf(Color.Transparent, Color.White.copy(alpha = 0.10f), Color.Transparent)),
                        Offset(lanePx * i, 0f), Offset(lanePx * i, size.height), strokeWidth = 1.5f,
                    )
                }
                // Danger floor glow: brighter when a matching bubble is about to land.
                val danger = bubbles.filter { it.value == session.target }.maxOfOrNull { it.progress(now) } ?: 0f
                val dangerAlpha = 0.18f + 0.5f * ((danger - 0.6f) / 0.4f).coerceIn(0f, 1f)
                drawRect(
                    Brush.verticalGradient(listOf(Color.Transparent, DangerRed.copy(alpha = dangerAlpha)),
                        startY = size.height - 60.dp.toPx(), endY = size.height),
                    topLeft = Offset(0f, size.height - 60.dp.toPx()),
                )
                drawLine(DangerRed.copy(alpha = 0.4f + dangerAlpha), Offset(0f, size.height - 2f),
                    Offset(size.width, size.height - 2f), strokeWidth = 3f)

                val travelPx = travel.toPx()
                val bubbleHPx = bubbleH.toPx()
                for (e in effects) {
                    val age = ((now - e.atMs).toFloat() / MathBlitzSession.EFFECT_MS).coerceIn(0f, 1f)
                    val cx = lanePx * (e.lane + 0.5f)
                    val cy = e.progress * travelPx + bubbleHPx / 2f
                    when (e.kind) {
                        Effect.Kind.POP -> {
                            val (c1, c2) = LaneColors[e.lane]
                            drawCircle(c1.copy(alpha = (1f - age) * 0.8f), radius = 20f + age * lanePx * 0.55f,
                                center = Offset(cx, cy), style = Stroke(width = 6f * (1f - age) + 1f))
                            for (k in 0 until 12) {
                                val a = (k / 12f) * 2f * PI.toFloat() + e.lane
                                val dist = 18f + age * lanePx * 0.6f * (if (k % 2 == 0) 1f else 0.7f)
                                val p = Offset(cx + cos(a) * dist, cy + sin(a) * dist)
                                drawCircle(if (k % 2 == 0) c1 else c2, radius = (1f - age) * 7f + 1f, center = p,
                                    alpha = 1f - age)
                            }
                            drawCircle(Color.White.copy(alpha = (1f - age) * 0.6f), radius = 26f * (1f - age), center = Offset(cx, cy))
                        }
                        Effect.Kind.WRONG -> {
                            val r = 22f + age * 30f
                            drawCircle(DangerRed.copy(alpha = 1f - age), radius = r, center = Offset(cx, cy),
                                style = Stroke(width = 5f))
                            val d = r * 0.5f
                            drawLine(DangerRed.copy(alpha = 1f - age), Offset(cx - d, cy - d), Offset(cx + d, cy + d), 6f, StrokeCap.Round)
                            drawLine(DangerRed.copy(alpha = 1f - age), Offset(cx + d, cy - d), Offset(cx - d, cy + d), 6f, StrokeCap.Round)
                        }
                        Effect.Kind.MISS -> {
                            drawRect(
                                Brush.verticalGradient(listOf(Color.Transparent, DangerRed.copy(alpha = (1f - age) * 0.7f))),
                                topLeft = Offset(lanePx * e.lane, size.height * 0.55f),
                                size = Size(lanePx, size.height * 0.45f),
                            )
                        }
                    }
                }
            }

            // Falling equation bubbles.
            for (b in bubbles) {
                key(b.id) {
                    val p = b.progress(now)
                    EquationBubble(
                        expression = b.expression,
                        lane = b.lane,
                        progress = p,
                        width = laneW * 0.88f,
                        modifier = Modifier.offset(
                            x = laneW * b.lane + laneW * 0.06f,
                            y = travel * p + 6.dp,
                        ),
                        onTap = { onBubbleTap(b.id) },
                    )
                }
            }

            // Floating "+points" and "miss" labels.
            for (e in effects) {
                val age = ((now - e.atMs).toFloat() / MathBlitzSession.EFFECT_MS).coerceIn(0f, 1f)
                val label = when (e.kind) {
                    Effect.Kind.POP -> "+${e.points}"
                    Effect.Kind.WRONG -> "✗"
                    Effect.Kind.MISS -> "MISS"
                }
                val color = when (e.kind) {
                    Effect.Kind.POP -> PopGreen
                    else -> DangerRed
                }
                Text(
                    label,
                    color = color.copy(alpha = 1f - age),
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Black,
                    modifier = Modifier
                        .offset(
                            x = laneW * e.lane + laneW * 0.3f,
                            y = (travel * e.progress - 36.dp * age).coerceAtLeast(0.dp),
                        )
                        .graphicsLayer { scaleX = 1f + age * 0.3f; scaleY = 1f + age * 0.3f }
                        .clearAndSetSemantics { },
                )
            }

            // Wave banner.
            val sinceWave = now - session.waveStartMs
            if (session.wave > 1 && sinceWave < BANNER_MS) {
                val a = sinceWave.toFloat() / BANNER_MS
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        "WAVE ${session.wave}",
                        fontSize = 44.sp,
                        fontWeight = FontWeight.Black,
                        color = Color.White.copy(alpha = 1f - a),
                        modifier = Modifier
                            .graphicsLayer { scaleX = 0.8f + a * 0.6f; scaleY = 0.8f + a * 0.6f }
                            .semantics { liveRegion = LiveRegionMode.Polite },
                    )
                }
            }
        }
    }
}

@Composable
private fun EquationBubble(
    expression: String,
    lane: Int,
    progress: Float,
    width: Dp,
    modifier: Modifier,
    onTap: () -> Unit,
) {
    val (top, bottom) = LaneColors[lane]
    val shape = RoundedCornerShape(BUBBLE_HEIGHT_DP.dp / 2)
    val currentOnTap by rememberUpdatedState(onTap)
    // Slight wobble so bubbles feel alive.
    val wobble = sin(progress * 14f + lane) * 2.5f
    // Spawn pop-in during the first few percent of the fall.
    val appear = (progress / 0.06f).coerceIn(0f, 1f)
    Box(
        modifier = modifier
            .width(width)
            .height(BUBBLE_HEIGHT_DP.dp)
            .graphicsLayer {
                rotationZ = wobble
                scaleX = 0.6f + 0.4f * appear
                scaleY = 0.6f + 0.4f * appear
                alpha = appear
            }
            .shadow(16.dp, shape, ambientColor = top, spotColor = bottom)
            .clip(shape)
            .background(Brush.verticalGradient(listOf(top, bottom)))
            .border(1.5.dp, Color.White.copy(alpha = 0.6f), shape)
            .pointerInput(Unit) { detectTapGestures(onPress = { currentOnTap() }) }
            .clearAndSetSemantics {
                role = Role.Button
                contentDescription = expression.replace("−", "minus").replace("×", "times").replace("÷", "divided by")
                onClick(label = "Pop") { currentOnTap(); true }
            },
        contentAlignment = Alignment.Center,
    ) {
        // Glassy highlight.
        Canvas(Modifier.fillMaxSize()) {
            drawCircle(
                Brush.radialGradient(listOf(Color.White.copy(alpha = 0.55f), Color.Transparent),
                    center = Offset(size.width * 0.25f, size.height * 0.2f), radius = size.height),
                radius = size.height,
                center = Offset(size.width * 0.25f, size.height * 0.2f),
            )
        }
        Text(expression, color = Color(0xFF10102A), fontSize = 19.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1)
    }
}

@Composable
private fun TargetOrb(target: Int, pulse: Float, enter: Float) {
    Box(
        Modifier
            .size(84.dp)
            .graphicsLayer {
                val s = pulse * (0.4f + 0.6f * enter)
                scaleX = s; scaleY = s
                rotationZ = (1f - enter) * -180f
            }
            .clearAndSetSemantics {
                contentDescription = "Target $target"
                liveRegion = LiveRegionMode.Polite
            },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize()) {
            drawCircle(Brush.radialGradient(listOf(PopGreen.copy(alpha = 0.55f), Color.Transparent)), radius = size.minDimension / 2f)
            drawCircle(
                Brush.sweepGradient(listOf(Color(0xFF7CF3FF), Color(0xFFFF9BF5), Color(0xFFFFE57C), Color(0xFF7CF3FF))),
                radius = size.minDimension * 0.4f,
                style = Stroke(width = 4.dp.toPx()),
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("TARGET", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("$target", fontSize = 28.sp, fontWeight = FontWeight.Black)
        }
    }
}

@Composable
private fun ComboBadge(combo: Int, multiplier: Double, pop: Float) {
    val hot = combo >= 3
    val brush = if (hot) Brush.linearGradient(listOf(Color(0xFFFFE57C), Color(0xFFFF4D6D)))
    else Brush.linearGradient(listOf(Color(0xFF3A3F66), Color(0xFF23264A)))
    Column(
        Modifier
            .graphicsLayer { scaleX = pop; scaleY = pop }
            .shadow(if (hot) 14.dp else 0.dp, RoundedCornerShape(14.dp), ambientColor = Color(0xFFFF8A3A), spotColor = Color(0xFFFF8A3A))
            .clip(RoundedCornerShape(14.dp))
            .background(brush)
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .semantics { contentDescription = "Combo $combo" },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(if (hot) "🔥 x$combo" else "x$combo", fontSize = 20.sp, fontWeight = FontWeight.Black,
            color = if (hot) Color(0xFF2A0A12) else Color.White)
        Text(String.format(java.util.Locale.US, "%.2f×", multiplier), fontSize = 11.sp,
            color = if (hot) Color(0xFF2A0A12) else Color.White.copy(alpha = 0.7f))
    }
}

@Composable
private fun WaveDots(hits: Int) {
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.clearAndSetSemantics { }) {
        repeat(MathBlitzSession.WAVE_SIZE) { i ->
            Box(
                Modifier.size(8.dp).clip(CircleShape)
                    .background(if (i < hits) PopGreen else Color.White.copy(alpha = 0.18f)),
            )
        }
    }
}
