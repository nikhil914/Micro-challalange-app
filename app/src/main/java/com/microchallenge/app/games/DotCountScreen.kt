package com.microchallenge.app.games

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.microchallenge.app.ui.GameColors
import com.microchallenge.core.challenges.games.DotCountSession

private const val FEEDBACK_MS = 450L

/**
 * Dot Flash: dots flash in a square field, then four counts to pick from.
 * Targets are cyan circles; decoys are orange squares (shape and color both differ).
 */
@Composable
fun DotCountScreen(session: DotCountSession, elapsedMs: Long, onInput: () -> Unit) {
    val haptics = LocalHapticFeedback.current
    var feedbackAt by remember { mutableLongStateOf(-1L) }

    val phase = session.phase()
    val showing = phase == DotCountSession.Phase.SHOW
    val dots = session.dots
    val choices = session.choices
    // Guards against a second touch landing on the next round before recomposition.
    val answeredAtCompose = session.answeredCount
    val showFeedback = feedbackAt >= 0 && elapsedMs - feedbackAt < FEEDBACK_MS
    val lastOk = session.lastAnswerCorrect

    val targetColor = MaterialTheme.colorScheme.primary
    val decoyColor = GameColors.ORANGE.color
    val question = if (session.hasDecoys) "How many circles?" else "How many dots?"

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = "Counted ${session.correct}",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = when {
                    !showFeedback -> "Misses ${session.mistakes}"
                    lastOk == true -> "Spot on"
                    else -> "It was ${session.lastTargetCount}"
                },
                style = MaterialTheme.typography.titleLarge,
                color = when {
                    showFeedback && lastOk == true -> MaterialTheme.colorScheme.primary
                    showFeedback -> MaterialTheme.colorScheme.error
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                },
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            )
        }

        BoxWithConstraints(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentAlignment = Alignment.Center,
        ) {
            val side = if (maxWidth < maxHeight) maxWidth else maxHeight
            Box(
                modifier = Modifier
                    .size(side)
                    .clip(RoundedCornerShape(24.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .semantics {
                        liveRegion = LiveRegionMode.Polite
                        contentDescription = if (showing) "Dots are flashing" else question
                    },
                contentAlignment = Alignment.Center,
            ) {
                if (showing) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val w = size.minDimension
                        for (dot in dots) {
                            val center = Offset(dot.x * w, dot.y * w)
                            val r = dot.radius * w
                            if (dot.isTarget) {
                                drawCircle(color = targetColor, radius = r, center = center)
                            } else {
                                // Slightly smaller square: same visual weight as a circle of radius r.
                                val half = r * 0.88f
                                drawRect(
                                    color = decoyColor,
                                    topLeft = Offset(center.x - half, center.y - half),
                                    size = Size(half * 2f, half * 2f),
                                )
                            }
                        }
                    }
                } else {
                    Text(
                        text = question,
                        fontSize = 34.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }

        Text(
            text = if (session.hasDecoys) "Count the circles · skip the squares" else "Count the dots before they vanish",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(vertical = 10.dp),
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            choices.forEachIndexed { index, value ->
                DotChoicePad(
                    label = value.toString(),
                    live = !showing,
                    modifier = Modifier.weight(1f).height(96.dp),
                    onChoose = {
                        if (session.answeredCount == answeredAtCompose &&
                            session.phase() == DotCountSession.Phase.ANSWER
                        ) {
                            val ok = session.answer(index)
                            if (!ok) haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            feedbackAt = elapsedMs
                            onInput()
                        }
                    },
                )
            }
        }
    }
}

/** Count pad that fires on touch-down; dimmed and blank while the dots are still showing. */
@Composable
private fun DotChoicePad(label: String, live: Boolean, modifier: Modifier, onChoose: () -> Unit) {
    val currentOnChoose by rememberUpdatedState(onChoose)
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.secondary.copy(alpha = if (live) 0.22f else 0.08f))
            .pointerInput(Unit) {
                detectTapGestures(onPress = { currentOnChoose() })
            }
            .semantics {
                role = Role.Button
                contentDescription = if (live) "$label dots" else "Wait for the dots to vanish"
                onClick(label = "Choose") { currentOnChoose(); true }
            },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = if (live) label else "·",
            fontSize = 32.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = if (live) 1f else 0.4f),
        )
    }
}
