package com.microchallenge.app.games

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.microchallenge.app.ui.GameColors
import com.microchallenge.core.challenges.games.NumberMemorySession
import com.microchallenge.core.challenges.games.NumberMemorySession.Phase

/**
 * Show phase: big digits plus a draining bar. Input phase: answer slots plus an
 * on-screen keypad (no soft keyboard, so layout never jumps and keys stay large).
 */
@Composable
fun NumberMemoryScreen(session: NumberMemorySession, elapsedMs: Long, onInput: () -> Unit) {
    val haptics = LocalHapticFeedback.current
    // elapsedMs changes every frame, which recomposes this screen; phase and the
    // show countdown are then re-read from the session's own clock.
    val phase = session.phase
    val remaining = session.showRemainingMs()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        when (phase) {
            Phase.SHOW -> ShowPhase(
                digits = session.digits,
                fraction = (remaining.toFloat() / session.showMs).coerceIn(0f, 1f),
                onReady = {
                    session.startInput()
                    onInput()
                },
            )
            Phase.INPUT -> InputPhase(
                length = session.length,
                input = session.input,
                cursorVisible = (elapsedMs / 500) % 2 == 0L,
                onDigit = { d ->
                    if (session.typeDigit(d)) haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onInput()
                },
                onBackspace = {
                    session.backspace()
                    onInput()
                },
                onSubmit = {
                    val correct = session.submit()
                    if (correct in 0 until session.length) {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    }
                    onInput()
                },
            )
            Phase.DONE -> DonePhase(digits = session.digits, input = session.input)
        }
    }
}

private fun digitStyle(length: Int): TextStyle = TextStyle(
    fontFamily = FontFamily.Monospace,
    fontWeight = FontWeight.Bold,
    fontSize = when {
        length <= 5 -> 64.sp
        length <= 7 -> 52.sp
        else -> 40.sp
    },
    letterSpacing = if (length <= 7) 6.sp else 2.sp,
)

@Composable
private fun ColumnScope.ShowPhase(digits: String, fraction: Float, onReady: () -> Unit) {
    Spacer(Modifier.height(24.dp))
    Text(
        "Lock it in",
        style = MaterialTheme.typography.titleLarge,
        modifier = Modifier.semantics { heading() },
    )
    Spacer(Modifier.height(24.dp))
    Box(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(24.dp))
            .padding(vertical = 32.dp, horizontal = 12.dp)
            .clearAndSetSemantics {
                // Spaced out so TalkBack reads digit by digit.
                contentDescription = "Memorize the digits: " + digits.toList().joinToString(" ")
                liveRegion = LiveRegionMode.Polite
            },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = digits,
            style = digitStyle(digits.length),
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            textAlign = TextAlign.Center,
            maxLines = 1,
        )
    }
    Spacer(Modifier.height(20.dp))
    LinearProgressIndicator(
        progress = { fraction },
        modifier = Modifier
            .fillMaxWidth()
            .height(10.dp),
    )
    Spacer(Modifier.height(8.dp))
    Text(
        "${digits.length} digits · vanishing",
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.weight(1f))
    Button(
        onClick = onReady,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp),
    ) {
        Text("Ready", style = MaterialTheme.typography.titleLarge)
    }
    Spacer(Modifier.height(8.dp))
}

@Composable
private fun ColumnScope.InputPhase(
    length: Int,
    input: String,
    cursorVisible: Boolean,
    onDigit: (Int) -> Unit,
    onBackspace: () -> Unit,
    onSubmit: () -> Unit,
) {
    Text(
        "Punch it in",
        style = MaterialTheme.typography.titleLarge,
        modifier = Modifier.semantics { heading() },
    )
    Spacer(Modifier.height(12.dp))
    AnswerSlots(length = length, input = input, cursorVisible = cursorVisible)
    Spacer(Modifier.height(16.dp))
    // Keypad fills the rest of the screen; every key is at least 64dp tall.
    val rows = listOf(listOf(1, 2, 3), listOf(4, 5, 6), listOf(7, 8, 9))
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .weight(1f),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        rows.forEach { row ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .weight(1f),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                row.forEach { d -> DigitKey(d, enabled = input.length < length, onDigit = onDigit, modifier = Modifier.weight(1f)) }
            }
        }
        Row(
            Modifier
                .fillMaxWidth()
                .weight(1f),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedButton(
                onClick = onBackspace,
                enabled = input.isNotEmpty(),
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .heightIn(min = 64.dp)
                    .semantics { contentDescription = "Delete last digit" },
                shape = RoundedCornerShape(16.dp),
            ) {
                Text("⌫", fontSize = 28.sp)
            }
            DigitKey(0, enabled = input.length < length, onDigit = onDigit, modifier = Modifier.weight(1f))
            Button(
                onClick = onSubmit,
                enabled = input.isNotEmpty(),
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .heightIn(min = 64.dp)
                    .semantics { contentDescription = "Submit answer" },
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = GameColors.GREEN.color,
                    contentColor = Color.Black,
                ),
            ) {
                Text("✓ OK", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun DigitKey(digit: Int, enabled: Boolean, onDigit: (Int) -> Unit, modifier: Modifier) {
    FilledTonalButton(
        onClick = { onDigit(digit) },
        enabled = enabled,
        modifier = modifier
            .fillMaxHeight()
            .heightIn(min = 64.dp),
        shape = RoundedCornerShape(16.dp),
        contentPadding = PaddingValues(0.dp),
    ) {
        Text("$digit", fontSize = 30.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun AnswerSlots(length: Int, input: String, cursorVisible: Boolean) {
    val slotWidth = if (length <= 6) 44.dp else 28.dp
    Row(
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        modifier = Modifier.clearAndSetSemantics {
            contentDescription = if (input.isEmpty()) {
                "No digits entered, $length needed"
            } else {
                "Entered ${input.toList().joinToString(" ")}, ${input.length} of $length"
            }
            liveRegion = LiveRegionMode.Polite
        },
    ) {
        repeat(length) { i ->
            val ch = input.getOrNull(i)
            val active = i == input.length
            Box(
                Modifier
                    .widthIn(min = slotWidth)
                    .height(56.dp)
                    .border(
                        width = if (active) 3.dp else 1.dp,
                        color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                        shape = RoundedCornerShape(10.dp),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    // Blinking caret in the next empty slot.
                    text = ch?.toString() ?: if (active && cursorVisible) "_" else "",
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = if (length <= 6) 30.sp else 24.sp,
                )
            }
        }
    }
}

@Composable
private fun ColumnScope.DonePhase(digits: String, input: String) {
    val correct = input.indices.count { input[it] == digits.getOrNull(it) }
    Spacer(Modifier.height(24.dp))
    Text(
        text = if (correct == digits.length) "Flawless" else "$correct / ${digits.length} locked",
        style = MaterialTheme.typography.headlineMedium,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.semantics {
            heading()
            liveRegion = LiveRegionMode.Polite
        },
    )
    Spacer(Modifier.height(24.dp))
    Row(
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        modifier = Modifier.clearAndSetSemantics {
            contentDescription = "Digits were ${digits.toList().joinToString(" ")}. " +
                "You typed ${if (input.isEmpty()) "nothing" else input.toList().joinToString(" ")}."
        },
    ) {
        digits.forEachIndexed { i, target ->
            val typed = input.getOrNull(i)
            val ok = typed == target
            val mark = if (ok) GameColors.GREEN.color else GameColors.RED.color
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("$target", fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 28.sp)
                Box(
                    Modifier
                        .size(width = 28.dp, height = 40.dp)
                        .border(2.dp, mark, RoundedCornerShape(8.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(typed?.toString() ?: "–", fontFamily = FontFamily.Monospace, fontSize = 22.sp)
                }
                // Symbol as well as color, so results are clear without color vision.
                Text(if (ok) "✓" else "✗", color = mark, fontWeight = FontWeight.Bold, fontSize = 18.sp)
            }
        }
    }
}
