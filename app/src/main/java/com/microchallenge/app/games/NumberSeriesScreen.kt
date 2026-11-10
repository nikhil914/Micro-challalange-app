package com.microchallenge.app.games

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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.microchallenge.app.ui.GameColors
import com.microchallenge.core.challenges.games.NumberSeriesSession

private const val FEEDBACK_MS = 350L

/** Next in Line: the series on top, four big answer pads below. */
@Composable
fun NumberSeriesScreen(session: NumberSeriesSession, elapsedMs: Long, onInput: () -> Unit) {
    val haptics = LocalHapticFeedback.current
    var feedbackAt by remember { mutableLongStateOf(-1L) }

    val puzzle = session.current
    // Guards against a second touch landing on the next puzzle before recomposition.
    val answeredAtCompose = session.answeredCount
    val showFeedback = feedbackAt >= 0 && elapsedMs - feedbackAt < FEEDBACK_MS
    val lastOk = session.lastAnswerCorrect
    val seriesText = puzzle.terms.joinToString("   ") + "   ?"

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = "Cracked ${session.correct}",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = "Wrong ${session.mistakes}",
                style = MaterialTheme.typography.titleLarge,
                color = if (session.mistakes > 0) MaterialTheme.colorScheme.error
                else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(vertical = 16.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(
                    when {
                        showFeedback && lastOk == true -> GameColors.GREEN.color.copy(alpha = 0.18f)
                        showFeedback && lastOk == false -> GameColors.RED.color.copy(alpha = 0.18f)
                        else -> MaterialTheme.colorScheme.surfaceVariant
                    },
                ),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = seriesText,
                    fontSize = if (seriesText.length > 26) 32.sp else 40.sp,
                    lineHeight = 52.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.semantics {
                        heading()
                        liveRegion = LiveRegionMode.Polite
                        contentDescription = "Series: ${puzzle.terms.joinToString(", ")}. What comes next?"
                    },
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = when {
                        !showFeedback -> "What comes next?"
                        lastOk == true -> "Cracked it"
                        else -> "Wrong"
                    },
                    style = MaterialTheme.typography.titleMedium,
                    color = when {
                        !showFeedback -> MaterialTheme.colorScheme.onSurfaceVariant
                        lastOk == false -> MaterialTheme.colorScheme.error
                        else -> MaterialTheme.colorScheme.primary
                    },
                )
            }
        }

        puzzle.choices.chunked(2).forEachIndexed { row, values ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                values.forEachIndexed { col, value ->
                    val index = row * 2 + col
                    SeriesChoicePad(
                        label = value.toString(),
                        modifier = Modifier.weight(1f).heightIn(min = 88.dp),
                        onChoose = {
                            if (session.answeredCount == answeredAtCompose) {
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
}

/** Answer pad that fires on touch-down. */
@Composable
private fun SeriesChoicePad(label: String, modifier: Modifier, onChoose: () -> Unit) {
    val currentOnChoose by rememberUpdatedState(onChoose)
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.secondary.copy(alpha = 0.22f))
            .pointerInput(Unit) {
                detectTapGestures(onPress = { currentOnChoose() })
            }
            .semantics {
                role = Role.Button
                contentDescription = "Answer $label"
                onClick(label = "Choose") { currentOnChoose(); true }
            },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            fontSize = 34.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}
