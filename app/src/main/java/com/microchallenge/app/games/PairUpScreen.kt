package com.microchallenge.app.games

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
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
import com.microchallenge.core.challenges.games.PairUpSession
import com.microchallenge.core.challenges.games.PairUpSession.FlipResult
import com.microchallenge.core.challenges.games.PairUpSession.Phase

private const val PAIR_FEEDBACK_MS = 600L

/** Pair Up: memorize the board, then flip two cards at a time to find the pairs. */
@Composable
fun PairUpScreen(session: PairUpSession, elapsedMs: Long, onInput: () -> Unit) {
    val haptics = LocalHapticFeedback.current
    val currentOnInput by rememberUpdatedState(onInput)
    val currentElapsed by rememberUpdatedState(elapsedMs)
    var feedbackAt by remember(session) { mutableLongStateOf(-1L) }
    var feedback by remember(session) { mutableStateOf(FlipResult.IGNORED) }

    val phase = session.phase()
    val timeUp = session.isTimeUp
    val showFeedback = feedbackAt >= 0 && elapsedMs - feedbackAt < PAIR_FEEDBACK_MS

    fun onCardTap(index: Int) {
        val result = session.tap(index)
        when (result) {
            FlipResult.FLIPPED -> haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            FlipResult.MATCH, FlipResult.BOARD_CLEARED -> haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            FlipResult.MISMATCH -> haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            FlipResult.IGNORED -> Unit
        }
        if (result != FlipResult.IGNORED && result != FlipResult.FLIPPED) {
            feedback = result
            feedbackAt = currentElapsed
        }
        currentOnInput()
    }

    val scheme = MaterialTheme.colorScheme
    val (title, titleColor) = when {
        timeUp -> "Time's up" to scheme.onSurface
        showFeedback && feedback == FlipResult.BOARD_CLEARED -> "Board clear!" to scheme.tertiary
        phase == Phase.PREVIEW -> "Memorize!" to scheme.secondary
        showFeedback && feedback == FlipResult.MATCH -> "Pair!" to scheme.primary
        showFeedback && feedback == FlipResult.MISMATCH -> "Nope" to scheme.error
        else -> "Find the pairs" to scheme.onSurface
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            title,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = titleColor,
            modifier = Modifier.semantics {
                heading()
                liveRegion = LiveRegionMode.Polite
            },
        )
        Text(
            "Pairs ${session.matches} · Misses ${session.mistakes} · Boards ${session.boardsCleared}",
            style = MaterialTheme.typography.titleMedium,
            color = scheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(16.dp))
        BoxWithConstraints(
            modifier = Modifier.fillMaxWidth().weight(1f),
            contentAlignment = Alignment.Center,
        ) {
            val gap = 10.dp
            val cols = session.columns
            val rows = session.rows
            val byWidth = (maxWidth - gap * (cols - 1)) / cols
            val byHeight = ((maxHeight - gap * (rows - 1)) / rows) * 0.78f
            val cardW = minOf(byWidth, byHeight)
            val cardH = cardW / 0.78f
            Column(verticalArrangement = Arrangement.spacedBy(gap)) {
                for (r in 0 until rows) {
                    Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                        for (c in 0 until cols) {
                            val i = r * cols + c
                            FlipCard(
                                icon = session.iconAt(i),
                                faceUp = session.isFaceUp(i),
                                matched = session.isMatched(i),
                                enabled = !timeUp && phase == Phase.PLAY,
                                label = "Card ${i + 1}",
                                onTap = { onCardTap(i) },
                                tapKey = session,
                                modifier = Modifier.size(cardW, cardH),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FlipCard(
    icon: Int,
    faceUp: Boolean,
    matched: Boolean,
    enabled: Boolean,
    label: String,
    onTap: () -> Unit,
    tapKey: Any,
    modifier: Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val currentEnabled by rememberUpdatedState(enabled)
    val currentOnTap by rememberUpdatedState(onTap)
    val rotation by animateFloatAsState(
        targetValue = if (faceUp) 180f else 0f,
        animationSpec = tween(durationMillis = 230),
        label = "cardFlip",
    )
    val density = LocalDensity.current.density
    val backA = scheme.secondary
    val backB = scheme.primary
    val face = scheme.surfaceVariant
    val gold = scheme.tertiary
    val iconColor = GameArt.color(icon)
    Box(
        modifier = modifier
            .graphicsLayer {
                rotationY = rotation
                cameraDistance = 14f * density
            }
            .pointerInput(tapKey) {
                detectTapGestures(onPress = { if (currentEnabled) currentOnTap() })
            }
            .clearAndSetSemantics {
                role = Role.Button
                contentDescription = label
                stateDescription = when {
                    matched -> "${GameArt.name(icon)}, matched"
                    faceUp -> GameArt.name(icon)
                    else -> "face down"
                }
                if (!enabled) disabled()
                onClick(label = "Flip card") {
                    if (currentEnabled) currentOnTap()
                    currentEnabled
                }
            },
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val corner = CornerRadius(size.minDimension * 0.14f)
            if (rotation > 90f) {
                // Mirror the front so it is not drawn back to front while the card is turned.
                scale(scaleX = -1f, scaleY = 1f) {
                    drawRoundRect(
                        brush = Brush.verticalGradient(listOf(face, Color(0xFF10131B))),
                        cornerRadius = corner,
                    )
                    val c = Offset(size.width / 2, size.height / 2)
                    val r = size.minDimension * 0.3f
                    drawGlow(c, r * 2.1f, iconColor, if (matched) 0.55f else 0.3f)
                    drawIcon(icon, c, r)
                    drawRoundRect(
                        color = if (matched) gold else iconColor.copy(alpha = 0.6f),
                        cornerRadius = corner,
                        style = Stroke(width = if (matched) 4.dp.toPx() else 2.dp.toPx()),
                    )
                }
            } else {
                drawRoundRect(
                    brush = Brush.linearGradient(
                        listOf(backA, Color(0xFF2A1B5C), backB.copy(alpha = 0.8f)),
                        start = Offset.Zero,
                        end = Offset(size.width, size.height),
                    ),
                    cornerRadius = corner,
                )
                // Back pattern: concentric rings and a faint star emblem.
                val c = Offset(size.width / 2, size.height / 2)
                val r = size.minDimension * 0.32f
                for (k in 1..3) {
                    drawCircle(
                        color = Color.White.copy(alpha = 0.10f * k),
                        radius = r * (1.3f - 0.3f * k),
                        center = c,
                        style = Stroke(width = 2.dp.toPx()),
                    )
                }
                drawIcon(GameArt.ICON_STAR, c, r * 0.45f, Color.White, alpha = 0.35f)
                drawRoundRect(
                    color = Color.White.copy(alpha = 0.25f),
                    cornerRadius = corner,
                    style = Stroke(width = 2.dp.toPx()),
                )
            }
        }
    }
}
