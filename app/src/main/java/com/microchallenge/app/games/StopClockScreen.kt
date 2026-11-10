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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.microchallenge.app.ui.GameColors
import com.microchallenge.core.challenges.games.StopClockSession
import kotlin.math.abs

/**
 * Stop the Clock: the whole area is one stop pad that fires on touch-down,
 * so the release never adds to the measured time.
 */
@Composable
fun StopClockScreen(session: StopClockSession, elapsedMs: Long, onInput: () -> Unit) {
    val haptics = LocalHapticFeedback.current
    val currentOnInput by rememberUpdatedState(onInput)

    // elapsedMs changes every frame, which keeps the readout below live.
    val timeUp = elapsedMs >= 10_000L
    val running = session.isRunning() && !timeUp
    val readingMs = session.runningMs()
    val targetMs = session.targetMs
    val readoutAlpha = session.displayAlpha()
    val blind = running && !session.displayVisible()
    val lastError = session.lastErrorMs
    val roundsDone = session.roundsDone
    val roundNumber = (if (running) roundsDone + 1 else roundsDone).coerceIn(1, StopClockSession.ROUNDS)

    val handleStop: () -> Unit = {
        val error = session.stop()
        if (error != null && abs(error) > StopClockSession.GOOD_ERROR_MS) {
            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        }
        currentOnInput()
    }
    val stopHandler by rememberUpdatedState(handleStop)

    // Result of the round just stopped; nothing while the clock runs.
    val shownError = if (running) null else lastError
    val resultText = when {
        shownError == null -> " "
        shownError == 0L -> "Dead on"
        shownError > 0L -> "$shownError ms late"
        else -> "${-shownError} ms early"
    }
    val resultGood = lastError != null && abs(lastError) <= StopClockSession.GOOD_ERROR_MS

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .pointerInput(session) {
                detectTapGestures(onPress = { stopHandler() })
            }
            .semantics {
                role = Role.Button
                contentDescription = "Stop the clock at ${formatSeconds(targetMs)} seconds"
                onClick(label = "Stop") { stopHandler(); true }
            },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = "Round $roundNumber/${StopClockSession.ROUNDS}",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = if (blind) "Blind" else " ",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.tertiary,
            )
        }

        Text(
            text = "Target",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = "${formatSeconds(targetMs)} s",
            fontSize = 44.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.tertiary,
        )

        Box(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = formatSeconds(readingMs),
                    fontSize = 84.sp,
                    fontWeight = FontWeight.Black,
                    color = (if (running) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.primary)
                        .copy(alpha = readoutAlpha),
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = resultText,
                    fontSize = 26.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (resultGood) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                )
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .height(96.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(
                    if (running) GameColors.RED.color else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f),
                ),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = if (running) "STOP" else if (roundsDone >= StopClockSession.ROUNDS) "DONE" else "GET READY",
                fontSize = 32.sp,
                fontWeight = FontWeight.Black,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
        Spacer(Modifier.height(12.dp))
        Text(
            text = "Tap anywhere to stop",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(20.dp))
    }
}

/** 2480 -> "2.48" (hundredths, truncated like a real stopwatch). */
private fun formatSeconds(ms: Long): String {
    val hundredths = ms.coerceAtLeast(0L) / 10L
    return "${hundredths / 100L}.${(hundredths % 100L).toString().padStart(2, '0')}"
}
