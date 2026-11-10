package com.microchallenge.app.games

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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.microchallenge.core.challenges.games.FlankerSession
import com.microchallenge.core.challenges.games.FlankerSession.AnswerResult
import com.microchallenge.core.challenges.games.FlankerSession.Glyph

/** How long the hit/miss tag stays after an answer. */
private const val FLANKER_FEEDBACK_MS = 450L

@Composable
fun FlankerScreen(session: FlankerSession, elapsedMs: Long, onInput: () -> Unit) {
    val haptics = LocalHapticFeedback.current
    val currentOnInput by rememberUpdatedState(onInput)
    val currentElapsed by rememberUpdatedState(elapsedMs)
    var feedback by remember(session) { mutableStateOf(AnswerResult.IGNORED) }
    var feedbackAt by remember(session) { mutableLongStateOf(-1L) }

    val timeUp = session.isTimeUp
    val showFeedback = feedback != AnswerResult.IGNORED && elapsedMs - feedbackAt < FLANKER_FEEDBACK_MS

    fun onAnswer(left: Boolean) {
        val r = session.answer(left)
        if (r != AnswerResult.IGNORED) {
            feedback = r
            feedbackAt = currentElapsed
        }
        if (r == AnswerResult.WRONG) haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        currentOnInput()
    }

    val row = session.row
    val rowSpoken = row.joinToString(", ") {
        when (it) {
            Glyph.LEFT -> "left"
            Glyph.RIGHT -> "right"
            Glyph.DASH -> "dash"
        }
    }
    val arrowColor = MaterialTheme.colorScheme.primary

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "Eyes on the MIDDLE",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                "✓ ${session.correct}   ✗ ${session.mistakes}",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.tertiary,
                modifier = Modifier.clearAndSetSemantics {
                    contentDescription = "${session.correct} hits, ${session.mistakes} misses"
                },
            )
        }
        Spacer(Modifier.height(12.dp))
        // Arrow card. Every arrow is drawn the same: only its position marks the target.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .clip(RoundedCornerShape(24.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .border(2.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(24.dp))
                .clearAndSetSemantics {
                    contentDescription = if (timeUp) "Time is up" else "Row of ${row.size}: $rowSpoken"
                    liveRegion = LiveRegionMode.Polite
                },
            contentAlignment = Alignment.Center,
        ) {
            if (timeUp) {
                Text(
                    "TIME",
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 64.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 2.sp,
                )
            } else {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 28.dp)
                        // From level 8 the row jumps around between trials.
                        .offset(x = (session.offsetSlot * 8).dp, y = (session.offsetSlot * 30).dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    row.forEach { glyph ->
                        FlankerGlyph(
                            glyph = glyph,
                            color = arrowColor,
                            modifier = Modifier
                                .weight(1f)
                                .aspectRatio(1f),
                        )
                    }
                }
            }
            if (showFeedback) {
                val hit = feedback == AnswerResult.CORRECT
                Text(
                    text = if (hit) "✓ Locked on" else "✗ Fooled",
                    color = if (hit) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 12.dp),
                )
            }
        }
        Spacer(Modifier.height(16.dp))
        Row(
            Modifier
                .fillMaxWidth()
                .height(132.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            FlankerButton(
                glyph = Glyph.LEFT,
                label = "LEFT",
                enabled = !timeUp,
                onPress = { onAnswer(true) },
                tapKey = session,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
            )
            FlankerButton(
                glyph = Glyph.RIGHT,
                label = "RIGHT",
                enabled = !timeUp,
                onPress = { onAnswer(false) },
                tapKey = session,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
            )
        }
    }
}

/** A chevron or dash drawn as strokes, so it looks the same on every device font. */
@Composable
private fun FlankerGlyph(glyph: Glyph, color: Color, modifier: Modifier) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val thickness = w * 0.17f
        when (glyph) {
            Glyph.DASH -> drawLine(
                color = color,
                start = Offset(w * 0.24f, h * 0.5f),
                end = Offset(w * 0.76f, h * 0.5f),
                strokeWidth = thickness,
                cap = StrokeCap.Round,
            )
            else -> {
                val tip = if (glyph == Glyph.LEFT) 0.26f else 0.74f
                val tail = if (glyph == Glyph.LEFT) 0.68f else 0.32f
                val path = Path().apply {
                    moveTo(w * tail, h * 0.18f)
                    lineTo(w * tip, h * 0.5f)
                    lineTo(w * tail, h * 0.82f)
                }
                drawPath(
                    path = path,
                    color = color,
                    style = Stroke(width = thickness, cap = StrokeCap.Round, join = StrokeJoin.Round),
                )
            }
        }
    }
}

@Composable
private fun FlankerButton(
    glyph: Glyph,
    label: String,
    enabled: Boolean,
    onPress: () -> Unit,
    tapKey: Any,
    modifier: Modifier,
) {
    val currentOnPress by rememberUpdatedState(onPress)
    val currentEnabled by rememberUpdatedState(enabled)
    val scheme = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(20.dp)
    val content = if (enabled) scheme.onSurface else scheme.onSurface.copy(alpha = 0.35f)
    Box(
        modifier = modifier
            .clip(shape)
            .background(scheme.surfaceVariant)
            .border(2.dp, if (enabled) scheme.primary else scheme.outlineVariant, shape)
            // Fires on touch-down: reaction time is the whole game.
            .pointerInput(tapKey) {
                detectTapGestures(onPress = { if (currentEnabled) currentOnPress() })
            }
            .clearAndSetSemantics {
                role = Role.Button
                contentDescription = label
                onClick(label = "Answer $label") {
                    if (currentEnabled) currentOnPress()
                    currentEnabled
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            FlankerGlyph(
                glyph = glyph,
                color = content,
                modifier = Modifier
                    .height(56.dp)
                    .aspectRatio(1f),
            )
            Text(
                label,
                color = content,
                fontSize = 20.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 1.sp,
            )
        }
    }
}
