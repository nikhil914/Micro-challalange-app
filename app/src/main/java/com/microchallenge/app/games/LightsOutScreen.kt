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
import com.microchallenge.core.challenges.games.LightsOutSession
import com.microchallenge.core.challenges.games.LightsOutSession.TapResult

/** How long the "board cleared" banner stays up. */
private const val LIGHTS_CLEARED_MS = 700L

@Composable
fun LightsOutScreen(session: LightsOutSession, elapsedMs: Long, onInput: () -> Unit) {
    val haptics = LocalHapticFeedback.current
    val currentOnInput by rememberUpdatedState(onInput)
    val currentElapsed by rememberUpdatedState(elapsedMs)
    var clearedAt by remember(session) { mutableLongStateOf(-1L) }

    val timeUp = session.isTimeUp
    val justCleared = clearedAt >= 0 && elapsedMs - clearedAt < LIGHTS_CLEARED_MS
    val onCount = session.onCount

    fun onTileTap(cell: Int) {
        when (session.tap(cell)) {
            TapResult.TOGGLED -> haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            TapResult.SOLVED -> {
                clearedAt = currentElapsed
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            }
            TapResult.IGNORED -> Unit
        }
        currentOnInput()
    }

    val title = when {
        timeUp -> "Time's up"
        justCleared -> "Blackout!"
        else -> "$onCount ${if (onCount == 1) "light" else "lights"} on"
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
            color = if (justCleared && !timeUp) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.semantics {
                heading()
                liveRegion = LiveRegionMode.Polite
            },
        )
        Text(
            "Boards ${session.solvedCount} · Moves ${session.moves} · Par ${session.par}",
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
                Modifier.aspectRatio(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                for (row in 0 until session.size) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        for (col in 0 until session.size) {
                            val cell = row * session.size + col
                            LightTile(
                                on = session.isOn(cell),
                                enabled = !timeUp,
                                label = "Row ${row + 1}, column ${col + 1}",
                                onTap = { onTileTap(cell) },
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
private fun LightTile(
    on: Boolean,
    enabled: Boolean,
    label: String,
    onTap: () -> Unit,
    modifier: Modifier,
    tapKey: Any,
) {
    val scheme = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(16.dp)
    val currentEnabled by rememberUpdatedState(enabled)
    val currentOnTap by rememberUpdatedState(onTap)
    Box(
        modifier = modifier
            .clip(shape)
            .background(if (on) GameColors.YELLOW.color else scheme.surfaceVariant)
            .border(if (on) 4.dp else 1.dp, if (on) scheme.onSurface else scheme.outlineVariant, shape)
            // Touch-down for snappy input; key on the session so a new run gets a fresh detector.
            .pointerInput(tapKey) {
                detectTapGestures(onPress = { if (currentEnabled) currentOnTap() })
            }
            .clearAndSetSemantics {
                role = Role.Button
                contentDescription = label
                stateDescription = if (on) "on" else "off"
                if (!enabled) disabled()
                onClick(label = "Flip tile") {
                    if (currentEnabled) currentOnTap()
                    currentEnabled
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        // A dot marks a lit tile, so on/off never depends on color alone.
        if (on) {
            Text(
                "●",
                fontSize = 34.sp,
                fontWeight = FontWeight.Bold,
                color = Color.Black,
            )
        }
    }
}
