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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
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
import com.microchallenge.core.challenges.games.SequenceMemorySession
import com.microchallenge.core.challenges.games.SequenceMemorySession.Phase
import com.microchallenge.core.challenges.games.SequenceMemorySession.TapResult

/** How long a tapped tile flashes its ✓ feedback. */
private const val TAP_FLASH_MS = 250L

private enum class TileLook { IDLE, LIT, TAPPED, WRONG }

@Composable
fun SequenceMemoryScreen(session: SequenceMemorySession, elapsedMs: Long, onInput: () -> Unit) {
    val haptics = LocalHapticFeedback.current
    val currentOnInput by rememberUpdatedState(onInput)
    val currentElapsed by rememberUpdatedState(elapsedMs)
    var flashTile by remember(session) { mutableIntStateOf(-1) }
    var flashAt by remember(session) { mutableLongStateOf(-1L) }

    // elapsedMs changes every frame, so playback is re-sampled from the session's clock.
    val phase = session.phase
    val lit = session.litTile()
    val step = session.litStep()
    val total = session.sequence.size

    fun onTileTap(index: Int) {
        when (session.tap(index)) {
            TapResult.CORRECT, TapResult.COMPLETED -> {
                flashTile = index
                flashAt = currentElapsed
                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            }
            TapResult.WRONG -> haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            TapResult.IGNORED -> Unit
        }
        currentOnInput()
    }

    val (title, subtitle) = when (phase) {
        Phase.PLAYBACK -> "Watch" to (if (step >= 0) "Tile ${step + 1} of $total" else "Incoming…")
        Phase.INPUT -> "Your turn" to "${session.progress} of $total"
        Phase.DONE -> when {
            session.progress == total -> "Flawless" to "All $total in order"
            session.wrongTile >= 0 -> "Miss" to "${session.progress} of $total correct · order shown"
            else -> "Time's up" to "${session.progress} of $total correct"
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
        Spacer(Modifier.height(12.dp))
        ProgressDots(
            filled = if (phase == Phase.PLAYBACK) step + 1 else session.progress,
            total = total,
            failed = session.wrongTile >= 0,
        )
        Spacer(Modifier.height(16.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                Modifier.aspectRatio(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                for (row in 0 until session.gridSize) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        for (col in 0 until session.gridSize) {
                            val index = row * session.gridSize + col
                            val look = when {
                                phase == Phase.PLAYBACK && index == lit -> TileLook.LIT
                                index == session.wrongTile -> TileLook.WRONG
                                index == flashTile && elapsedMs - flashAt < TAP_FLASH_MS -> TileLook.TAPPED
                                else -> TileLook.IDLE
                            }
                            // After the run, show where each step was, to help the player learn.
                            val order = if (phase == Phase.DONE) {
                                session.sequence.withIndex().filter { it.value == index }.map { it.index + 1 }
                            } else {
                                emptyList()
                            }
                            Tile(
                                look = look,
                                order = order,
                                enabled = phase == Phase.INPUT,
                                label = "Row ${row + 1}, column ${col + 1}",
                                onTap = { onTileTap(index) },
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxSize(),
                                tapKey = session,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Tile(
    look: TileLook,
    order: List<Int>,
    enabled: Boolean,
    label: String,
    onTap: () -> Unit,
    modifier: Modifier,
    tapKey: Any,
) {
    val scheme = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(16.dp)
    val background = when (look) {
        TileLook.LIT -> GameColors.YELLOW.color
        TileLook.TAPPED -> GameColors.GREEN.color
        TileLook.WRONG -> GameColors.RED.color
        TileLook.IDLE -> scheme.surfaceVariant
    }
    val borderColor = when (look) {
        TileLook.IDLE -> scheme.outlineVariant
        else -> scheme.onSurface
    }
    // Symbols give a non-color cue for every state.
    val symbol = when (look) {
        TileLook.LIT -> "●"
        TileLook.TAPPED -> "✓"
        TileLook.WRONG -> "✗"
        TileLook.IDLE -> if (order.isNotEmpty()) order.joinToString(",") else ""
    }
    val currentEnabled by rememberUpdatedState(enabled)
    val currentOnTap by rememberUpdatedState(onTap)
    Box(
        modifier = modifier
            .clip(shape)
            .background(background)
            .border(if (look == TileLook.IDLE) 1.dp else 4.dp, borderColor, shape)
            // Touch-down for snappy input; key on the session so a new run gets a fresh detector.
            .pointerInput(tapKey) {
                detectTapGestures(onPress = { if (currentEnabled) currentOnTap() })
            }
            .clearAndSetSemantics {
                role = Role.Button
                contentDescription = label
                stateDescription = when (look) {
                    TileLook.LIT -> "lit"
                    TileLook.TAPPED -> "correct"
                    TileLook.WRONG -> "wrong"
                    TileLook.IDLE -> if (order.isNotEmpty()) "step ${order.joinToString(" and ")}" else ""
                }
                if (!enabled) disabled()
                onClick(label = "Choose tile") {
                    if (currentEnabled) currentOnTap()
                    currentEnabled
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        if (symbol.isNotEmpty()) {
            Text(
                symbol,
                fontSize = if (look == TileLook.IDLE) 22.sp else 34.sp,
                fontWeight = FontWeight.Bold,
                color = if (look == TileLook.IDLE) scheme.onSurfaceVariant else Color.Black,
            )
        }
    }
}

@Composable
private fun ProgressDots(filled: Int, total: Int, failed: Boolean) {
    val color = MaterialTheme.colorScheme.primary
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.clearAndSetSemantics { },
    ) {
        repeat(total) { i ->
            val dot = Modifier.size(14.dp)
            Box(
                when {
                    i < filled -> dot.background(color, CircleShape)
                    failed && i == filled -> dot.background(GameColors.RED.color, CircleShape)
                    else -> dot.border(2.dp, color, CircleShape)
                },
            )
        }
    }
}
