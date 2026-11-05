package com.microchallenge.app.games

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.microchallenge.app.ui.GameColors
import com.microchallenge.core.challenges.games.QuickCompareSession

private const val FEEDBACK_MS = 300L

/** Quick Compare: two big halves; tap the one with the larger value. Fires on touch-down. */
@Composable
fun QuickCompareScreen(session: QuickCompareSession, elapsedMs: Long, onInput: () -> Unit) {
    val haptics = LocalHapticFeedback.current
    val currentOnInput by rememberUpdatedState(onInput)
    var feedbackAt by remember { mutableLongStateOf(-1L) }
    var feedbackLeft by remember { mutableStateOf(true) }

    val pair = session.current
    val emphasizedLeft = session.leftEmphasized
    val lastOk = session.lastAnswerCorrect
    val showFeedback = feedbackAt >= 0 && elapsedMs - feedbackAt < FEEDBACK_MS
    val answeredAtCompose = session.answeredCount

    val choose: (Boolean) -> Unit = { left ->
        // Ignore a second touch that lands before the new pair is drawn.
        if (session.answeredCount == answeredAtCompose) {
            val ok = session.choose(left)
            if (!ok) haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            feedbackLeft = left
            feedbackAt = elapsedMs
            currentOnInput()
        }
    }
    val chooseHandler by rememberUpdatedState(choose)

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = "✓ ${session.correct}",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.semantics { contentDescription = "${session.correct} right" },
            )
            Text(
                text = when {
                    !showFeedback -> "Tap the bigger one"
                    lastOk == true -> "Clean hit"
                    else -> "Wrong"
                },
                style = MaterialTheme.typography.titleMedium,
                color = if (showFeedback && lastOk == false) MaterialTheme.colorScheme.error
                else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = "✗ ${session.mistakes}",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.semantics { contentDescription = "${session.mistakes} wrong" },
            )
        }

        Row(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            for (left in listOf(true, false)) {
                val label = if (left) pair.leftLabel else pair.rightLabel
                val size = fontSizeFor(label, emphasized = emphasizedLeft?.let { it == left })
                val flash = when {
                    !showFeedback || feedbackLeft != left -> null
                    lastOk == true -> GameColors.GREEN
                    else -> GameColors.RED
                }
                CompareSide(
                    label = label,
                    fontSize = size,
                    flashColor = flash,
                    description = "${if (left) "Left" else "Right"}: ${spokenValue(label)}",
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                    onChoose = { chooseHandler(left) },
                )
            }
        }
    }
}

@Composable
private fun CompareSide(
    label: String,
    fontSize: TextUnit,
    flashColor: GameColors?,
    description: String,
    modifier: Modifier,
    onChoose: () -> Unit,
) {
    val shape = RoundedCornerShape(24.dp)
    val currentOnChoose by rememberUpdatedState(onChoose)
    Box(
        modifier = modifier
            .clip(shape)
            .background(flashColor?.color?.copy(alpha = 0.22f) ?: MaterialTheme.colorScheme.surfaceVariant)
            .then(if (flashColor != null) Modifier.border(3.dp, flashColor.color, shape) else Modifier)
            .pointerInput(Unit) {
                detectTapGestures(onPress = { currentOnChoose() })
            }
            .semantics {
                role = Role.Button
                contentDescription = description
                onClick(label = "Choose") { currentOnChoose(); true }
            },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            fontSize = fontSize,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Visible,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(8.dp),
        )
    }
}

/**
 * Font size scaled to fit half the screen. [emphasized]: true = drawn big (the smaller value under
 * the font-size trick), false = drawn small, null = no trick.
 */
private fun fontSizeFor(label: String, emphasized: Boolean?): TextUnit {
    val base = when (label.length) {
        in 0..2 -> 76f
        3 -> 64f
        4 -> 52f
        else -> 42f
    }
    val factor = when (emphasized) {
        true -> 1.0f
        false -> 0.55f
        null -> 0.85f
    }
    return (base * factor).sp
}

private fun spokenValue(label: String): String = label
    .replace("×", " times ")
    .replace("÷", " divided by ")
    .replace("−", " minus ")
    .replace("+", " plus ")
    .replace("/", " over ")
