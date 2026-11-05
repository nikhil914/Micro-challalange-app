package com.microchallenge.app.games

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
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
import com.microchallenge.app.ui.GameColors
import com.microchallenge.core.challenges.games.ReactionTapSession
import com.microchallenge.core.challenges.games.ReactionTapSession.Phase
import com.microchallenge.core.challenges.games.ReactionTapSession.TapResult

/** How long the "Too early!" banner stays after a false start. */
private const val FALSE_START_BANNER_MS = 900L

/**
 * Whole screen is one big tap target. Taps register on touch-down (not release)
 * so measured reaction times are not inflated by finger lift.
 * The GO color switch is never animated, for the same reason.
 */
@Composable
fun ReactionTapScreen(session: ReactionTapSession, elapsedMs: Long, onInput: () -> Unit) {
    val haptics = LocalHapticFeedback.current
    val currentOnInput by rememberUpdatedState(onInput)
    val currentElapsed by rememberUpdatedState(elapsedMs)
    var falseStartAt by remember(session) { mutableLongStateOf(-1L) }

    // elapsedMs changes every frame, so phase is re-read from the session's clock each frame.
    val phase = session.phase
    val showFalseStart = falseStartAt >= 0 && elapsedMs - falseStartAt < FALSE_START_BANNER_MS

    fun handleTap() {
        when (session.tap()) {
            TapResult.FALSE_START -> {
                falseStartAt = currentElapsed
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            }
            TapResult.REACTION -> haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            TapResult.IGNORED -> Unit
        }
        currentOnInput()
    }

    val look = when (phase) {
        Phase.WAITING -> PhaseLook(GameColors.RED.color, "Hold…", "Tap on green", RoundedCornerShape(12.dp), 0f)
        Phase.DECOY -> PhaseLook(GameColors.YELLOW.color, "Decoy", "Don't bite. Hold.", RoundedCornerShape(12.dp), 45f)
        Phase.GO -> PhaseLook(GameColors.GREEN.color, "TAP!", "Go go go", CircleShape, 0f)
        Phase.DONE -> PhaseLook(MaterialTheme.colorScheme.surfaceVariant, "Run over", "", CircleShape, 0f)
    }
    val onBg = if (phase == Phase.DONE) MaterialTheme.colorScheme.onSurfaceVariant else readableOn(look.background)

    val a11yLabel = when (phase) {
        Phase.WAITING -> "Waiting. Do not tap yet."
        Phase.DECOY -> "Yellow decoy. Do not tap."
        Phase.GO -> "Green. Tap now."
        Phase.DONE -> "Finished."
    } + if (session.lastReactionMs >= 0) " Last reaction ${session.lastReactionMs} milliseconds." else ""

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(look.background)
            .pointerInput(session) {
                detectTapGestures(onPress = { handleTap() })
            }
            .clearAndSetSemantics {
                role = Role.Button
                contentDescription = a11yLabel
                liveRegion = LiveRegionMode.Assertive
                onClick(label = "React") { handleTap(); true }
            },
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(24.dp),
        ) {
            // Shape cue so the state never depends on color alone:
            // square = wait, diamond = decoy, circle = go.
            Box(
                Modifier
                    .size(112.dp)
                    .rotate(look.rotation)
                    .border(8.dp, onBg, look.shape),
            )
            Spacer(Modifier.height(28.dp))
            Text(
                text = if (showFalseStart && phase != Phase.GO) "Too early!" else look.title,
                style = MaterialTheme.typography.displayLarge,
                fontWeight = FontWeight.Black,
                color = onBg,
                textAlign = TextAlign.Center,
            )
            if (look.subtitle.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = if (showFalseStart && phase != Phase.GO) "Reset. Hold for green." else look.subtitle,
                    style = MaterialTheme.typography.titleMedium,
                    color = onBg,
                    textAlign = TextAlign.Center,
                )
            }
            Spacer(Modifier.height(32.dp))
            RoundDots(done = session.roundsDone, total = ReactionTapSession.MAX_ROUNDS, color = onBg)
            Spacer(Modifier.height(16.dp))
            val last = session.lastReactionMs
            Text(
                text = buildString {
                    append(if (last >= 0) "Last: $last ms" else "Round ${session.roundsDone + 1} of ${ReactionTapSession.MAX_ROUNDS}")
                    if (session.falseStarts > 0) append("   ·   False starts: ${session.falseStarts}")
                },
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                color = onBg,
            )
            if (phase == Phase.DONE && session.roundsDone > 0) {
                Spacer(Modifier.height(8.dp))
                Text(
                    "Avg ${session.meanReactionMs()} ms",
                    style = MaterialTheme.typography.headlineSmall,
                    color = onBg,
                )
            }
        }
    }
}

private data class PhaseLook(
    val background: Color,
    val title: String,
    val subtitle: String,
    val shape: Shape,
    val rotation: Float,
)

/** Black or white, whichever contrasts more with [background]. */
private fun readableOn(background: Color): Color =
    if (background.luminance() > 0.179f) Color.Black else Color.White

@Composable
private fun RoundDots(done: Int, total: Int, color: Color) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        repeat(total) { i ->
            val filled = i < done
            Box(
                Modifier
                    .size(18.dp)
                    .then(
                        if (filled) Modifier.background(color, CircleShape)
                        else Modifier.border(2.dp, color, CircleShape),
                    ),
            )
        }
    }
}
