package com.microchallenge.app.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.sp
import com.microchallenge.app.ui.design.AppFonts
import com.microchallenge.app.ui.design.FeedbackOverlay
import com.microchallenge.app.ui.design.LocalGameFeedback
import com.microchallenge.app.ui.design.LocalReduceMotion
import com.microchallenge.app.ui.design.Palette
import com.microchallenge.app.ui.design.Shapes
import com.microchallenge.app.ui.design.rememberGameFeedback
import com.microchallenge.app.ui.design.style
import com.microchallenge.core.model.ChallengeType
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import com.microchallenge.app.games.FlankerScreen
import com.microchallenge.core.challenges.games.FlankerSession
import com.microchallenge.app.games.ChimpOrderScreen
import com.microchallenge.core.challenges.games.ChimpOrderSession
import com.microchallenge.app.games.RuleSwitchScreen
import com.microchallenge.core.challenges.games.RuleSwitchSession
import com.microchallenge.app.games.LightsOutScreen
import com.microchallenge.app.games.BankShotScreen
import com.microchallenge.core.challenges.games.LightsOutSession
import com.microchallenge.core.challenges.games.BankShotSession
import com.microchallenge.app.games.NumberSeriesScreen
import com.microchallenge.core.challenges.games.NumberSeriesSession
import com.microchallenge.app.games.DotCountScreen
import com.microchallenge.core.challenges.games.DotCountSession
import com.microchallenge.app.games.SpotChangeScreen
import com.microchallenge.core.challenges.games.SpotChangeSession
import com.microchallenge.app.games.StopClockScreen
import com.microchallenge.core.challenges.games.StopClockSession
import com.microchallenge.app.games.MirrorMatchScreen
import com.microchallenge.app.games.ColorRushScreen
import com.microchallenge.core.challenges.games.MirrorMatchSession
import com.microchallenge.core.challenges.games.ColorRushSession
import com.microchallenge.app.games.PairUpScreen
import com.microchallenge.core.challenges.games.PairUpSession
import com.microchallenge.app.games.BubbleSumScreen
import com.microchallenge.core.challenges.games.BubbleSumSession
import com.microchallenge.app.games.StarCatcherScreen
import com.microchallenge.core.challenges.games.StarCatcherSession
import com.microchallenge.app.games.SlideShiftScreen
import com.microchallenge.core.challenges.games.SlideShiftSession
import com.microchallenge.app.games.WeighInScreen
import com.microchallenge.core.challenges.games.WeighInSession
import com.microchallenge.app.games.GlowGridScreen
import com.microchallenge.app.games.TwinScenesScreen
import com.microchallenge.core.challenges.games.TwinScenesSession
import com.microchallenge.app.games.WordScrambleScreen
import com.microchallenge.core.challenges.games.GlowGridSession
import com.microchallenge.app.games.SlidingTileScreen
import com.microchallenge.core.challenges.games.SlidingTileSession
import com.microchallenge.core.challenges.games.WordScrambleSession
import com.microchallenge.app.games.PipeFlowScreen
import com.microchallenge.core.challenges.games.PipeFlowSession
import com.microchallenge.app.games.NeonDashScreen
import com.microchallenge.core.challenges.games.NeonDashSession
import com.microchallenge.app.games.MathBlitzScreen
import com.microchallenge.app.games.StackTowerScreen
import com.microchallenge.core.challenges.games.MathBlitzSession
import com.microchallenge.app.games.RhythmTapScreen
import com.microchallenge.core.challenges.games.RhythmTapSession
import com.microchallenge.core.challenges.games.StackTowerSession
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

    val feedback = rememberGameFeedback(haptics)

    Column(Modifier.fillMaxSize()) {
        HostHeader(session.type, remaining, countdown.durationMs)
        Spacer(Modifier.height(12.dp))
        Box(Modifier.weight(1f).fillMaxWidth()) {
        CompositionLocalProvider(LocalHapticFeedback provides haptics, LocalGameFeedback provides feedback) {
            when (session) {
                is ReactionTapSession -> ReactionTapScreen(session, elapsedMs, onInput)
                is NumberMemorySession -> NumberMemoryScreen(session, elapsedMs, onInput)
                is SequenceMemorySession -> SequenceMemoryScreen(session, elapsedMs, onInput)
                is ColorWordSession -> ColorWordScreen(session, elapsedMs, onInput)
                is MathSprintSession -> MathSprintScreen(session, elapsedMs, onInput)
                is OddOneOutSession -> OddOneOutScreen(session, elapsedMs, onInput)
                is GoNoGoSession -> GoNoGoScreen(session, elapsedMs, onInput)
                is QuickCompareSession -> QuickCompareScreen(session, elapsedMs, onInput)
                is FlankerSession -> FlankerScreen(session, elapsedMs, onInput)
                is ChimpOrderSession -> ChimpOrderScreen(session, elapsedMs, onInput)
                is RuleSwitchSession -> RuleSwitchScreen(session, elapsedMs, onInput)
                is LightsOutSession -> LightsOutScreen(session, elapsedMs, onInput)
                is NumberSeriesSession -> NumberSeriesScreen(session, elapsedMs, onInput)
                is DotCountSession -> DotCountScreen(session, elapsedMs, onInput)
                is SpotChangeSession -> SpotChangeScreen(session, elapsedMs, onInput)
                is StopClockSession -> StopClockScreen(session, elapsedMs, onInput)
                is MirrorMatchSession -> MirrorMatchScreen(session, elapsedMs, onInput)
                is ColorRushSession -> ColorRushScreen(session, elapsedMs, onInput)
                is PairUpSession -> PairUpScreen(session, elapsedMs, onInput)
                is BubbleSumSession -> BubbleSumScreen(session, elapsedMs, onInput)
                is StarCatcherSession -> StarCatcherScreen(session, elapsedMs, onInput)
                is SlideShiftSession -> SlideShiftScreen(session, elapsedMs, onInput)
                is WeighInSession -> WeighInScreen(session, elapsedMs, onInput)
                is BankShotSession -> BankShotScreen(session, elapsedMs, onInput)
                is GlowGridSession -> GlowGridScreen(session, elapsedMs, onInput)
                is WordScrambleSession -> WordScrambleScreen(session, elapsedMs, onInput)
                is PipeFlowSession -> PipeFlowScreen(session, elapsedMs, onInput)
                is NeonDashSession -> NeonDashScreen(session, elapsedMs, onInput)
                is MathBlitzSession -> MathBlitzScreen(session, elapsedMs, onInput)
                is SlidingTileSession -> SlidingTileScreen(session, elapsedMs, onInput)
                is RhythmTapSession -> RhythmTapScreen(session, elapsedMs, onInput)
                is StackTowerSession -> StackTowerScreen(session, elapsedMs, onInput)
                is TwinScenesSession -> TwinScenesScreen(session, elapsedMs, onInput)
                else -> Text("Unknown challenge")
            }
        }
            FeedbackOverlay(feedback)
        }
    }
}

/** Game name plus a big seconds counter and a thick gradient timer that turns coral and pulses near the end. */
@Composable
private fun HostHeader(type: ChallengeType, remaining: Long, durationMs: Long) {
    val style = type.skill.style()
    val low = remaining in 1..2_999
    val reduce = LocalReduceMotion.current
    val pulse = if (low && !reduce) {
        val t = rememberInfiniteTransition(label = "low")
        t.animateFloat(1f, 1.14f, infiniteRepeatable(tween(260, easing = FastOutSlowInEasing), RepeatMode.Reverse),
            label = "pulse").value
    } else 1f
    Column {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.width(6.dp).height(26.dp).clip(Shapes.pill).background(style.brush))
            Text(type.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f).padding(start = 10.dp))
            Text(
                String.format(Locale.US, "%.1f", remaining / 1000f),
                fontFamily = AppFonts.display, fontWeight = FontWeight.Black, fontSize = 28.sp,
                color = if (low) Palette.Wrong else Palette.Text,
                modifier = Modifier.scale(pulse),
            )
            Text("s", style = MaterialTheme.typography.titleMedium, color = Palette.TextFaint,
                modifier = Modifier.padding(start = 2.dp, top = 6.dp))
        }
        Spacer(Modifier.height(8.dp))
        val fraction = (remaining.toFloat() / durationMs).coerceIn(0f, 1f)
        Canvas(
            Modifier.fillMaxWidth().height(14.dp)
                .semantics { contentDescription = "${remaining / 1000} seconds left" },
        ) {
            val r = CornerRadius(size.height / 2, size.height / 2)
            drawRoundRect(Palette.Surface2, cornerRadius = r)
            val w = size.width * fraction
            if (w > 0f) {
                val brush = if (low) Brush.horizontalGradient(listOf(Palette.Wrong, Color(0xFFFF9A6B)))
                else style.horizontal
                drawRoundRect(brush, size = Size(w.coerceAtLeast(size.height), size.height), cornerRadius = r)
                drawRoundRect(Color.White.copy(alpha = 0.25f), topLeft = androidx.compose.ui.geometry.Offset(size.height / 3, size.height * 0.2f),
                    size = Size((w - size.height * 2 / 3).coerceAtLeast(0f), size.height * 0.2f), cornerRadius = r)
            }
        }
    }
}
