package com.microchallenge.app.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.microchallenge.app.Screen
import com.microchallenge.app.games.ColorWordScreen
import com.microchallenge.app.games.GoNoGoScreen
import com.microchallenge.app.games.MathSprintScreen
import com.microchallenge.app.games.NumberMemoryScreen
import com.microchallenge.app.games.OddOneOutScreen
import com.microchallenge.app.games.QuickCompareScreen
import com.microchallenge.app.games.ReactionTapScreen
import com.microchallenge.app.games.SequenceMemoryScreen
import com.microchallenge.core.challenges.games.ColorWordSession
import com.microchallenge.core.challenges.games.GoNoGoSession
import com.microchallenge.core.challenges.games.MathSprintSession
import com.microchallenge.core.challenges.games.NumberMemorySession
import com.microchallenge.core.challenges.games.OddOneOutSession
import com.microchallenge.core.challenges.games.QuickCompareSession
import com.microchallenge.core.challenges.games.ReactionTapSession
import com.microchallenge.core.challenges.games.SequenceMemorySession
import java.util.Locale

/** Haptics that respect the user's setting; games use LocalHapticFeedback as usual. */
private object NoHaptics : HapticFeedback {
    override fun performHapticFeedback(hapticFeedbackType: HapticFeedbackType) = Unit
}

/**
 * Runs the 10-second window: a frame loop drives [elapsedMs], the timer bar,
 * and ends the play when time is up or the session finishes early.
 */
@Composable
fun ChallengeHost(playing: Screen.Playing, hapticsOn: Boolean, onFinished: () -> Unit) {
    val session = playing.session
    val countdown = playing.countdown
    var elapsedMs by remember(playing) { mutableLongStateOf(0L) }
    val finish by rememberUpdatedState(onFinished)

    LaunchedEffect(playing) {
        while (true) {
            withFrameMillis { }
            elapsedMs = countdown.elapsedMs()
            if (countdown.isFinished() || session.isComplete) break
        }
        finish()
    }

    // elapsedMs changes every frame, so games recompose each frame and input shows within ~16 ms.
    val onInput: () -> Unit = { elapsedMs = countdown.elapsedMs() }
    val haptics = if (hapticsOn) LocalHapticFeedback.current else NoHaptics
    val remaining = (countdown.durationMs - elapsedMs).coerceAtLeast(0)

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(session.type.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f))
            Text(String.format(Locale.US, "%.1fs", remaining / 1000f),
                style = MaterialTheme.typography.titleLarge,
                color = if (remaining < 3_000) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onBackground)
        }
        LinearProgressIndicator(
            progress = { remaining.toFloat() / countdown.durationMs },
            modifier = Modifier.fillMaxWidth().height(8.dp)
                .semantics { contentDescription = "${remaining / 1000} seconds left" },
        )
        Spacer(Modifier.height(16.dp))
        CompositionLocalProvider(LocalHapticFeedback provides haptics) {
            when (session) {
                is ReactionTapSession -> ReactionTapScreen(session, elapsedMs, onInput)
                is NumberMemorySession -> NumberMemoryScreen(session, elapsedMs, onInput)
                is SequenceMemorySession -> SequenceMemoryScreen(session, elapsedMs, onInput)
                is ColorWordSession -> ColorWordScreen(session, elapsedMs, onInput)
                is MathSprintSession -> MathSprintScreen(session, elapsedMs, onInput)
                is OddOneOutSession -> OddOneOutScreen(session, elapsedMs, onInput)
                is GoNoGoSession -> GoNoGoScreen(session, elapsedMs, onInput)
                is QuickCompareSession -> QuickCompareScreen(session, elapsedMs, onInput)
                else -> Text("Unknown challenge")
            }
        }
    }
}
