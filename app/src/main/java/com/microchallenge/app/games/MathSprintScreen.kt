package com.microchallenge.app.games

import androidx.compose.foundation.background
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
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.microchallenge.app.ui.GameColors
import com.microchallenge.core.challenges.games.MathSprintSession

private const val FEEDBACK_MS = 350L

/** Math Sprint: problem on top, 3-4 big answer buttons below. */
@Composable
fun MathSprintScreen(session: MathSprintSession, elapsedMs: Long, onInput: () -> Unit) {
    val haptics = LocalHapticFeedback.current
    var feedbackAt by remember { mutableLongStateOf(-1L) }

    val problem = session.current
    // Guards against a second tap landing on the next problem before recomposition.
    val answeredAtCompose = session.answeredCount
    val showFeedback = feedbackAt >= 0 && elapsedMs - feedbackAt < FEEDBACK_MS
    val lastOk = session.lastAnswerCorrect

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = "✓ ${session.correct}",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.semantics { contentDescription = "${session.correct} correct" },
            )
            Text(
                text = "✗ ${session.mistakes}",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.semantics { contentDescription = "${session.mistakes} wrong" },
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
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "${problem.prompt} = ?",
                    fontSize = if (problem.prompt.length > 9) 44.sp else 56.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.semantics {
                        heading()
                        liveRegion = LiveRegionMode.Polite
                        contentDescription = "What is ${spokenMath(problem.prompt)}?"
                    },
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = when {
                        !showFeedback -> " "
                        lastOk == true -> "Clean hit"
                        else -> "Wrong"
                    },
                    style = MaterialTheme.typography.titleMedium,
                    color = if (lastOk == false) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                )
            }
        }

        problem.choices.chunked(2).forEachIndexed { row, values ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                values.forEachIndexed { col, value ->
                    val index = row * 2 + col
                    FilledTonalButton(
                        onClick = {
                            if (session.answeredCount != answeredAtCompose) return@FilledTonalButton
                            val ok = session.answer(index)
                            if (!ok) haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            feedbackAt = elapsedMs
                            onInput()
                        },
                        shape = RoundedCornerShape(20.dp),
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 88.dp)
                            .semantics { contentDescription = "Answer $value" },
                    ) {
                        Text(text = value.toString(), fontSize = 34.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}

/** Turns "7 × 8 − 3" into words TalkBack reads naturally. */
private fun spokenMath(prompt: String): String = prompt
    .replace("×", "times")
    .replace("÷", "divided by")
    .replace("−", "minus")
    .replace("+", "plus")
