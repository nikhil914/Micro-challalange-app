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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
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
import com.microchallenge.core.challenges.games.GoNoGoSession
import com.microchallenge.core.challenges.games.GoNoGoStimulus
import com.microchallenge.core.challenges.games.GoNoGoTapOutcome

private const val FEEDBACK_MS = 450L

/**
 * Go / No-Go: the whole area is one tap target that fires on touch-down.
 * GO = green circle labelled "TAP", NO-GO = red square labelled "STOP"
 * (shape and label differ too, so color is never the only cue).
 */
@Composable
fun GoNoGoScreen(session: GoNoGoSession, elapsedMs: Long, onInput: () -> Unit) {
    val haptics = LocalHapticFeedback.current
    val currentOnInput by rememberUpdatedState(onInput)
    var feedbackAt by remember { mutableLongStateOf(-1L) }

    val stimulus = session.visible()
    val outcome = session.lastOutcome
    val showFeedback = feedbackAt >= 0 && elapsedMs - feedbackAt < FEEDBACK_MS

    val handleTap: () -> Unit = {
        val result = session.tap()
        if (result == GoNoGoTapOutcome.FALSE_ALARM || result == GoNoGoTapOutcome.STRAY) {
            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        }
        feedbackAt = session.elapsedMs()
        currentOnInput()
    }
    val tapHandler by rememberUpdatedState(handleTap)

    val stimulusDescription = when (stimulus?.type) {
        GoNoGoStimulus.GO -> "Green circle: tap now"
        GoNoGoStimulus.NO_GO -> "Red square: do not tap"
        null -> "Wait for the next shape"
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .pointerInput(session) {
                // onPress fires on touch-down: reaction time is not inflated by the release.
                detectTapGestures(onPress = { tapHandler() })
            }
            .semantics {
                role = Role.Button
                contentDescription = stimulusDescription
                liveRegion = LiveRegionMode.Assertive
                onClick(label = "Tap") { tapHandler(); true }
            },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = "Hits ${session.hits}/${session.goCount}",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = "Red taps ${session.falseAlarms}",
                style = MaterialTheme.typography.titleMedium,
                color = if (session.falseAlarms > 0) MaterialTheme.colorScheme.error
                else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Box(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentAlignment = Alignment.Center,
        ) {
            when (stimulus?.type) {
                GoNoGoStimulus.GO -> StimulusShape(
                    color = GameColors.GREEN.color,
                    isCircle = true,
                    label = "TAP",
                )
                GoNoGoStimulus.NO_GO -> StimulusShape(
                    color = GameColors.RED.color,
                    isCircle = false,
                    label = "STOP",
                )
                null -> Box(
                    modifier = Modifier
                        .size(20.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)),
                )
            }
        }

        val feedbackText = if (!showFeedback) " " else when (outcome) {
            GoNoGoTapOutcome.HIT -> session.hitReactionTimesMs.lastOrNull()?.let { "Clean hit · $it ms" } ?: "Clean hit"
            GoNoGoTapOutcome.FALSE_ALARM -> "Wrong · that was red"
            GoNoGoTapOutcome.STRAY -> "Too early"
            else -> " "
        }
        Text(
            text = feedbackText,
            fontSize = 26.sp,
            fontWeight = FontWeight.SemiBold,
            color = if (outcome == GoNoGoTapOutcome.HIT) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.error,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = "Tap green circles · never red squares",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(20.dp))
    }
}

@Composable
private fun StimulusShape(color: Color, isCircle: Boolean, label: String) {
    Box(
        modifier = Modifier
            .size(200.dp)
            .clip(if (isCircle) CircleShape else RoundedCornerShape(8.dp))
            .background(color),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = Color.White,
            fontSize = 40.sp,
            fontWeight = FontWeight.Black,
        )
    }
}
