package com.microchallenge.app.games

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
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
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.microchallenge.app.ui.GameColors
import com.microchallenge.core.challenges.games.GlowGridSession
import com.microchallenge.core.challenges.games.GlowGridSession.Phase
import com.microchallenge.core.challenges.games.GlowGridSession.TapResult

private enum class GlowLook { HIDDEN, GLOW, FOUND, WRONG, MISSED }

private val GlowTop = Color(0xFF7CF3FF)
private val GlowBottom = Color(0xFF3A7BFF)
private val BackTop = Color(0xFF2A2F4A)
private val BackBottom = Color(0xFF181B2E)

@Composable
fun GlowGridScreen(session: GlowGridSession, elapsedMs: Long, onInput: () -> Unit) {
    val haptics = LocalHapticFeedback.current
    val currentOnInput by rememberUpdatedState(onInput)

    // elapsedMs ticks every frame; reading it keeps this recomposing while phases change.
    @Suppress("UNUSED_VARIABLE") val tick = elapsedMs
    val phase = session.phase
    val round = session.round
    val visible = session.patternVisible()

    // Whole-board shake when a round fails.
    val shake = remember(session) { Animatable(0f) }
    val failedKey = session.roundsPlayed - session.roundsCleared
    LaunchedEffect(failedKey) {
        if (failedKey > 0) {
            for (x in listOf(18f, -14f, 10f, -6f, 0f)) shake.animateTo(x, tween(45))
        }
    }

    fun onTileTap(index: Int) {
        when (session.tap(index)) {
            TapResult.HIT -> haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            TapResult.ROUND_CLEARED -> haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            TapResult.MISS, TapResult.ROUND_FAILED -> haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            TapResult.IGNORED -> Unit
        }
        currentOnInput()
    }

    val total = round.pattern.size
    val (title, subtitle) = when (phase) {
        Phase.SHOW -> "Memorize" to "$total tiles glow"
        Phase.INPUT -> "Recall" to "${session.found.size} of $total · ${GlowGridSession.MAX_WRONG - session.wrong.size} misses left"
        Phase.FEEDBACK -> if (session.lastRoundCleared) "Brilliant!" to "Next pattern gets bigger" else "So close" to "Here's what you missed"
        Phase.DONE -> "Time's up" to "${session.roundsCleared} rounds · ${session.tilesFound} tiles"
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            title,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.semantics {
                heading()
                liveRegion = LiveRegionMode.Polite
            },
        )
        Text(subtitle, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(10.dp))
        StudyRing(
            progress = if (phase == Phase.SHOW) 1f - session.studyProgress() else 0f,
            label = "Round ${session.roundsPlayed + 1}",
        )
        Spacer(Modifier.height(12.dp))
        Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
            Column(
                Modifier
                    .aspectRatio(1f)
                    .graphicsLayer { translationX = shake.value }
                    .clip(RoundedCornerShape(24.dp))
                    .background(Brush.verticalGradient(listOf(Color(0xFF10132A), Color(0xFF060814))))
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                for (row in 0 until round.gridSize) {
                    Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        for (col in 0 until round.gridSize) {
                            val index = row * round.gridSize + col
                            val inPattern = index in round.pattern
                            val look = when {
                                phase == Phase.SHOW && visible && inPattern -> GlowLook.GLOW
                                index in session.found -> GlowLook.FOUND
                                index in session.wrong -> GlowLook.WRONG
                                (phase == Phase.FEEDBACK || phase == Phase.DONE) && inPattern -> GlowLook.MISSED
                                else -> GlowLook.HIDDEN
                            }
                            GlowTile(
                                look = look,
                                enabled = phase == Phase.INPUT,
                                label = "Row ${row + 1}, column ${col + 1}",
                                onTap = { onTileTap(index) },
                                // Re-key per round so new tiles flip in fresh.
                                key = round,
                                modifier = Modifier.weight(1f).fillMaxSize(),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun GlowTile(
    look: GlowLook,
    enabled: Boolean,
    label: String,
    onTap: () -> Unit,
    key: Any,
    modifier: Modifier,
) {
    val faceUp = look != GlowLook.HIDDEN
    // 3D card flip: 0° shows the dark back, 180° shows the lit face.
    val rotation by animateFloatAsState(
        targetValue = if (faceUp) 180f else 0f,
        animationSpec = spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessMediumLow),
        label = "flip",
    )
    val pop by animateFloatAsState(
        targetValue = if (look == GlowLook.FOUND || look == GlowLook.GLOW) 1f else 0.94f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "pop",
    )
    val shape = RoundedCornerShape(14.dp)
    val showFace = rotation > 90f
    val faceBrush = when (look) {
        GlowLook.GLOW -> Brush.linearGradient(listOf(GlowTop, GlowBottom))
        GlowLook.FOUND -> Brush.linearGradient(listOf(Color(0xFF8BFFB0), GameColors.GREEN.color))
        GlowLook.WRONG -> Brush.linearGradient(listOf(Color(0xFFFF9A6B), GameColors.RED.color))
        GlowLook.MISSED -> Brush.linearGradient(listOf(Color(0xFFFFF59D), GameColors.ORANGE.color))
        GlowLook.HIDDEN -> Brush.linearGradient(listOf(BackTop, BackBottom))
    }
    val glowColor = when (look) {
        GlowLook.GLOW -> GlowTop
        GlowLook.FOUND -> GameColors.GREEN.color
        GlowLook.WRONG -> GameColors.RED.color
        GlowLook.MISSED -> GameColors.ORANGE.color
        GlowLook.HIDDEN -> Color.Transparent
    }
    // Non-color cue for every state.
    val symbol = when (look) {
        GlowLook.FOUND -> "✓"
        GlowLook.WRONG -> "✗"
        GlowLook.MISSED -> "!"
        else -> ""
    }
    val currentEnabled by rememberUpdatedState(enabled)
    val currentOnTap by rememberUpdatedState(onTap)
    Box(
        modifier = modifier
            .graphicsLayer {
                rotationY = rotation
                cameraDistance = 12f * density
                scaleX = pop
                scaleY = pop
            }
            .shadow(if (showFace) 14.dp else 2.dp, shape, ambientColor = glowColor, spotColor = glowColor)
            .clip(shape)
            .background(if (showFace) faceBrush else Brush.linearGradient(listOf(BackTop, BackBottom)))
            .border(1.dp, Color.White.copy(alpha = if (showFace) 0.55f else 0.08f), shape)
            .pointerInput(key) {
                detectTapGestures(onPress = { if (currentEnabled) currentOnTap() })
            }
            .clearAndSetSemantics {
                role = Role.Button
                contentDescription = label
                stateDescription = when (look) {
                    GlowLook.GLOW -> "glowing"
                    GlowLook.FOUND -> "found"
                    GlowLook.WRONG -> "wrong"
                    GlowLook.MISSED -> "missed"
                    GlowLook.HIDDEN -> ""
                }
                if (!enabled) disabled()
                onClick(label = "Choose tile") {
                    if (currentEnabled) currentOnTap()
                    currentEnabled
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        if (showFace) {
            // Soft inner highlight for a glassy look; mirrored back since the card is flipped.
            Canvas(Modifier.fillMaxSize().graphicsLayer { rotationY = 180f }) {
                drawCircle(
                    brush = Brush.radialGradient(
                        listOf(Color.White.copy(alpha = 0.45f), Color.Transparent),
                        center = Offset(size.width * 0.3f, size.height * 0.25f),
                        radius = size.minDimension * 0.7f,
                    ),
                    radius = size.minDimension * 0.7f,
                    center = Offset(size.width * 0.3f, size.height * 0.25f),
                )
            }
            if (symbol.isNotEmpty()) {
                Text(
                    symbol,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Black,
                    color = Color.Black.copy(alpha = 0.75f),
                    modifier = Modifier.graphicsLayer { rotationY = 180f },
                )
            }
        } else {
            // Subtle dot on card backs.
            Box(Modifier.size(6.dp).clip(RoundedCornerShape(3.dp)).background(Color.White.copy(alpha = 0.12f)))
        }
    }
}

@Composable
private fun StudyRing(progress: Float, label: String) {
    val track = MaterialTheme.colorScheme.surfaceVariant
    Box(Modifier.size(44.dp).clearAndSetSemantics { }, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = Stroke(width = 5.dp.toPx(), cap = StrokeCap.Round)
            drawArc(track, 0f, 360f, false, style = stroke)
            drawArc(
                brush = Brush.sweepGradient(listOf(GlowBottom, GlowTop, GlowBottom)),
                startAngle = -90f,
                sweepAngle = 360f * progress,
                useCenter = false,
                style = stroke,
            )
        }
        Text(label.removePrefix("Round "), fontWeight = FontWeight.Bold, fontSize = 14.sp)
    }
}
