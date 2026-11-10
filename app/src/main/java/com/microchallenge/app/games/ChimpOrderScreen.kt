package com.microchallenge.app.games

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
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
import com.microchallenge.core.challenges.games.ChimpOrderSession
import com.microchallenge.core.challenges.games.ChimpOrderSession.Phase
import com.microchallenge.core.challenges.games.ChimpOrderSession.TapResult
import com.microchallenge.core.model.CHALLENGE_DURATION_MS

private enum class ChimpTileLook { SHOWN, HIDDEN, CLEARED, WRONG }

@Composable
fun ChimpOrderScreen(session: ChimpOrderSession, elapsedMs: Long, onInput: () -> Unit) {
    val haptics = LocalHapticFeedback.current
    val currentOnInput by rememberUpdatedState(onInput)

    // elapsedMs changes every frame, so the show/hide phase is re-sampled from the session's clock.
    val phase = if (elapsedMs >= CHALLENGE_DURATION_MS) Phase.DONE else session.phase
    val count = session.count

    fun onTileTap(cell: Int) {
        when (session.tap(cell)) {
            TapResult.CORRECT, TapResult.BOARD_CLEARED, TapResult.COMPLETED ->
                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            TapResult.WRONG -> haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            TapResult.IGNORED -> Unit
        }
        currentOnInput()
    }

    val boardTag = if (session.boardIndex == 0) "$count numbers" else "Bonus board · $count numbers"
    val (title, subtitle) = when (phase) {
        Phase.SHOW -> "Memorize" to "$boardTag · tap 1 when ready"
        Phase.INPUT -> "Tap ${session.nextExpected}" to "${session.nextExpected - 1} of $count found"
        Phase.DONE -> when {
            session.wrongCell >= 0 -> "Wrong tile" to "${session.nextExpected - 1} of $count in order"
            session.boardIndex == 1 && session.nextExpected > count -> "Flawless" to "Both boards cleared"
            else -> "Time's up" to "${session.nextExpected - 1} of $count in order"
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
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
        Text(
            subtitle,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(16.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                Modifier.aspectRatio(session.cols.toFloat() / session.rows.toFloat()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                for (row in 0 until session.rows) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        for (col in 0 until session.cols) {
                            val cell = row * session.cols + col
                            val number = session.numberAt(cell)
                            val cellModifier = Modifier
                                .weight(1f)
                                .fillMaxSize()
                            if (number == 0) {
                                // Only numbered cells get a tile; the rest of the grid stays dark.
                                Box(cellModifier)
                            } else {
                                val cleared = cell in session.revealed
                                val look = when {
                                    cell == session.wrongCell -> ChimpTileLook.WRONG
                                    cleared -> ChimpTileLook.CLEARED
                                    session.isNumberVisible(cell) -> ChimpTileLook.SHOWN
                                    else -> ChimpTileLook.HIDDEN
                                }
                                ChimpTile(
                                    number = number,
                                    look = look,
                                    enabled = phase != Phase.DONE && !cleared,
                                    label = "Tile, row ${row + 1}, column ${col + 1}",
                                    onTap = { onTileTap(cell) },
                                    modifier = cellModifier,
                                    tapKey = session,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ChimpTile(
    number: Int,
    look: ChimpTileLook,
    enabled: Boolean,
    label: String,
    onTap: () -> Unit,
    modifier: Modifier,
    tapKey: Any,
) {
    val scheme = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(16.dp)
    val background = when (look) {
        ChimpTileLook.SHOWN -> scheme.surfaceVariant
        ChimpTileLook.HIDDEN -> scheme.primary
        ChimpTileLook.CLEARED -> GameColors.GREEN.color
        ChimpTileLook.WRONG -> GameColors.RED.color
    }
    val borderColor = when (look) {
        ChimpTileLook.SHOWN -> scheme.primary
        else -> scheme.onSurface
    }
    // Text gives a non-color cue for every state: number, blank, tick or cross.
    val text = when (look) {
        ChimpTileLook.SHOWN -> number.toString()
        ChimpTileLook.HIDDEN -> ""
        ChimpTileLook.CLEARED -> "✓$number"
        ChimpTileLook.WRONG -> "✗$number"
    }
    val currentEnabled by rememberUpdatedState(enabled)
    val currentOnTap by rememberUpdatedState(onTap)
    Box(
        modifier = modifier
            .clip(shape)
            .background(background)
            .border(if (look == ChimpTileLook.WRONG) 4.dp else 2.dp, borderColor, shape)
            // Touch-down for snappy input; key on the session so a new run gets a fresh detector.
            .pointerInput(tapKey) {
                detectTapGestures(onPress = { if (currentEnabled) currentOnTap() })
            }
            .clearAndSetSemantics {
                role = Role.Button
                contentDescription = label
                stateDescription = when (look) {
                    ChimpTileLook.SHOWN -> "number $number"
                    ChimpTileLook.HIDDEN -> "hidden"
                    ChimpTileLook.CLEARED -> "cleared, number $number"
                    ChimpTileLook.WRONG -> "wrong, number $number"
                }
                if (!enabled) disabled()
                onClick(label = "Choose tile") {
                    if (currentEnabled) currentOnTap()
                    currentEnabled
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        if (text.isNotEmpty()) {
            Text(
                text,
                fontSize = if (look == ChimpTileLook.SHOWN) 34.sp else 26.sp,
                fontWeight = FontWeight.Black,
                color = if (look == ChimpTileLook.SHOWN) scheme.onSurface else Color.Black,
                maxLines = 1,
            )
        }
    }
}
