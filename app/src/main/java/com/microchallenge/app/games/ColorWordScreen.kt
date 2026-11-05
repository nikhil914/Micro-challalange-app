package com.microchallenge.app.games

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.microchallenge.app.ui.GameColors
import com.microchallenge.core.challenges.games.ColorWordSession
import com.microchallenge.core.challenges.games.ColorWordSession.AnswerResult

/** How long the hit/miss tag stays after an answer. */
private const val FEEDBACK_MS = 450L

@Composable
fun ColorWordScreen(session: ColorWordSession, elapsedMs: Long, onInput: () -> Unit) {
    val haptics = LocalHapticFeedback.current
    val currentOnInput by rememberUpdatedState(onInput)
    val currentElapsed by rememberUpdatedState(elapsedMs)
    var feedback by remember(session) { mutableStateOf(AnswerResult.IGNORED) }
    var feedbackAt by remember(session) { mutableLongStateOf(-1L) }

    // GameColors order matches ColorWordSession.COLOR_NAMES.
    val palette = GameColors.entries
    val timeUp = session.isTimeUp
    val showFeedback = feedback != AnswerResult.IGNORED && elapsedMs - feedbackAt < FEEDBACK_MS

    fun onAnswer(index: Int) {
        val r = session.answer(index)
        if (r != AnswerResult.IGNORED) {
            feedback = r
            feedbackAt = currentElapsed
        }
        if (r == AnswerResult.WRONG) haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        currentOnInput()
    }

    val wordName = ColorWordSession.COLOR_NAMES[session.word]
    val inkName = ColorWordSession.COLOR_NAMES[session.ink]
    val ink = palette[session.ink].color

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
                "Hit the INK",
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
        // Word card: the word names one color but is drawn in another (the answer).
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(0.9f)
                .clip(RoundedCornerShape(24.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .border(2.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(24.dp))
                .clearAndSetSemantics {
                    contentDescription = "Word $wordName, in $inkName ink"
                    liveRegion = LiveRegionMode.Polite
                },
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = if (timeUp) "TIME" else wordName.uppercase(),
                color = if (timeUp) MaterialTheme.colorScheme.onSurface else ink,
                fontSize = 64.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 2.sp,
                textAlign = TextAlign.Center,
                maxLines = 1,
            )
            if (showFeedback) {
                val hit = feedback == AnswerResult.CORRECT
                Text(
                    text = if (hit) "✓ Clean hit" else "✗ Miss",
                    color = if (hit) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 16.dp),
                )
            }
        }
        Spacer(Modifier.height(16.dp))
        // Answer grid, two per row; an odd last button spans the row.
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1.1f),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            (0 until session.colorCount).chunked(2).forEach { row ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    row.forEach { index ->
                        ColorButton(
                            color = palette[index].color,
                            label = palette[index].label,
                            enabled = !timeUp,
                            onPress = { onAnswer(index) },
                            tapKey = session,
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .heightIn(min = 64.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ColorButton(
    color: Color,
    label: String,
    enabled: Boolean,
    onPress: () -> Unit,
    tapKey: Any,
    modifier: Modifier,
) {
    val currentOnPress by rememberUpdatedState(onPress)
    val currentEnabled by rememberUpdatedState(enabled)
    val shape = RoundedCornerShape(18.dp)
    val textColor = if (color.luminance() > 0.179f) Color.Black else Color.White
    Box(
        modifier = modifier
            .clip(shape)
            .background(if (enabled) color else color.copy(alpha = 0.35f))
            .border(2.dp, MaterialTheme.colorScheme.outline, shape)
            // Fires on touch-down: speed matters in a Stroop stream.
            .pointerInput(tapKey) {
                detectTapGestures(onPress = { if (currentEnabled) currentOnPress() })
            }
            .clearAndSetSemantics {
                role = Role.Button
                contentDescription = label
                onClick(label = "Choose $label") {
                    if (currentEnabled) currentOnPress()
                    currentEnabled
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        // Label on every button, so the answer never depends on color vision alone.
        Text(
            label.uppercase(),
            color = textColor,
            fontSize = 22.sp,
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = 1.sp,
        )
    }
}
