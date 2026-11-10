package com.microchallenge.app.games

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.runtime.mutableStateOf
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
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.microchallenge.core.challenges.games.RuleSwitchSession
import com.microchallenge.core.challenges.games.RuleSwitchSession.AnswerResult
import com.microchallenge.core.challenges.games.RuleSwitchSession.Rule
import com.microchallenge.core.challenges.games.RuleSwitchSession.Tint

/** How long the hit/miss tag stays after an answer. */
private const val RULE_FEEDBACK_MS = 450L

@Composable
fun RuleSwitchScreen(session: RuleSwitchSession, elapsedMs: Long, onInput: () -> Unit) {
    val haptics = LocalHapticFeedback.current
    val currentOnInput by rememberUpdatedState(onInput)
    val currentElapsed by rememberUpdatedState(elapsedMs)
    var feedback by remember(session) { mutableStateOf(AnswerResult.IGNORED) }
    var feedbackAt by remember(session) { mutableLongStateOf(-1L) }

    val scheme = MaterialTheme.colorScheme
    val timeUp = session.isTimeUp
    val showFeedback = feedback != AnswerResult.IGNORED && elapsedMs - feedbackAt < RULE_FEEDBACK_MS
    // elapsedMs changes every frame, so the brief cue of the top levels times out on its own.
    val cueVisible = session.cueVisible
    val rule = session.rule
    val tint = session.tint
    val switched = session.isSwitch && session.trialIndex > 0

    fun onAnswer(first: Boolean) {
        val r = session.answer(first)
        if (r != AnswerResult.IGNORED) {
            feedback = r
            feedbackAt = currentElapsed
        }
        if (r == AnswerResult.WRONG) haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        currentOnInput()
    }

    val tintColor = when (tint) {
        Tint.CYAN -> scheme.primary
        Tint.GOLD -> scheme.tertiary
        Tint.NONE -> scheme.onSurface
    }
    val tintSpoken = if (tint == Tint.NONE) "" else ", ${tint.label}, ${tint.shape} badge"
    val cueShape = RoundedCornerShape(18.dp)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                when {
                    switched -> "SWITCH!"
                    session.trialIndex == 0 -> "Read the rule"
                    else -> "Same rule"
                },
                style = MaterialTheme.typography.titleMedium,
                fontWeight = if (switched) FontWeight.Black else FontWeight.Normal,
                color = if (switched) scheme.tertiary else scheme.onSurfaceVariant,
                modifier = Modifier.clearAndSetSemantics { },
            )
            Text(
                "✓ ${session.correct}   ✗ ${session.mistakes}",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = scheme.tertiary,
                modifier = Modifier.clearAndSetSemantics {
                    contentDescription = "${session.correct} hits, ${session.mistakes} misses"
                },
            )
        }
        Spacer(Modifier.height(10.dp))
        // Rule cue. The border thickens on a switch, as a second cue besides the text.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(76.dp)
                .clip(cueShape)
                .background(scheme.surfaceVariant)
                .border(if (switched) 4.dp else 2.dp, if (switched) scheme.tertiary else scheme.primary, cueShape)
                .clearAndSetSemantics {
                    contentDescription = if (timeUp) "Time is up" else "Rule: ${rule.prompt}"
                    liveRegion = LiveRegionMode.Polite
                },
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = when {
                    timeUp -> "TIME"
                    cueVisible -> rule.prompt
                    else -> "? ? ?"
                },
                color = if (cueVisible || timeUp) scheme.onSurface else scheme.onSurfaceVariant,
                fontSize = 30.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 1.sp,
                textAlign = TextAlign.Center,
                maxLines = 1,
            )
        }
        Spacer(Modifier.height(12.dp))
        // Number card. Cyan numbers sit in a circle, gold ones in a square.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .clip(RoundedCornerShape(24.dp))
                .background(scheme.surfaceContainerHigh)
                .border(2.dp, scheme.outlineVariant, RoundedCornerShape(24.dp))
                .clearAndSetSemantics {
                    contentDescription = "Number ${session.stimulus}$tintSpoken"
                    liveRegion = LiveRegionMode.Polite
                },
            contentAlignment = Alignment.Center,
        ) {
            val badgeShape = if (tint == Tint.CYAN) CircleShape else RoundedCornerShape(10.dp)
            val badge = if (tint == Tint.NONE) {
                Modifier.size(168.dp)
            } else {
                Modifier
                    .size(168.dp)
                    .border(6.dp, tintColor, badgeShape)
            }
            Box(badge, contentAlignment = Alignment.Center) {
                Text(
                    text = session.stimulus.toString(),
                    color = tintColor,
                    fontSize = 96.sp,
                    fontWeight = FontWeight.Black,
                    maxLines = 1,
                )
            }
            if (showFeedback) {
                val hit = feedback == AnswerResult.CORRECT
                Text(
                    text = if (hit) "✓ Nailed it" else "✗ Wrong rule?",
                    color = if (hit) scheme.primary else scheme.error,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 10.dp),
                )
            }
        }
        Spacer(Modifier.height(16.dp))
        Row(
            Modifier
                .fillMaxWidth()
                .height(124.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            val tintRule = rule == Rule.TINT
            RuleButton(
                label = session.firstLabel,
                hint = if (tintRule) Tint.CYAN.shape else "",
                accent = if (tintRule) scheme.primary else scheme.outline,
                labelVisible = cueVisible,
                enabled = !timeUp,
                onPress = { onAnswer(true) },
                tapKey = session,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
            )
            RuleButton(
                label = session.secondLabel,
                hint = if (tintRule) Tint.GOLD.shape else "",
                accent = if (tintRule) scheme.tertiary else scheme.outline,
                labelVisible = cueVisible,
                enabled = !timeUp,
                onPress = { onAnswer(false) },
                tapKey = session,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
            )
        }
    }
}

/**
 * Answer button. When the cue has timed out the printed label goes too (the sides never
 * move: left is always ODD / LOW / CYAN); screen readers still get the real label.
 */
@Composable
private fun RuleButton(
    label: String,
    hint: String,
    accent: Color,
    labelVisible: Boolean,
    enabled: Boolean,
    onPress: () -> Unit,
    tapKey: Any,
    modifier: Modifier,
) {
    val currentOnPress by rememberUpdatedState(onPress)
    val currentEnabled by rememberUpdatedState(enabled)
    val scheme = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(20.dp)
    val showAccent = enabled && labelVisible
    val content = if (enabled) scheme.onSurface else scheme.onSurface.copy(alpha = 0.35f)
    Box(
        modifier = modifier
            .clip(shape)
            .background(scheme.surfaceVariant)
            .border(3.dp, if (showAccent) accent else scheme.outlineVariant, shape)
            // Fires on touch-down: the switch cost is measured in milliseconds.
            .pointerInput(tapKey) {
                detectTapGestures(onPress = { if (currentEnabled) currentOnPress() })
            }
            .clearAndSetSemantics {
                role = Role.Button
                contentDescription = label
                onClick(label = "Answer $label") {
                    if (currentEnabled) currentOnPress()
                    currentEnabled
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                if (labelVisible) label else "?",
                color = content,
                fontSize = 30.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 1.sp,
                maxLines = 1,
            )
            if (labelVisible && hint.isNotEmpty()) {
                Text(
                    hint,
                    color = scheme.onSurfaceVariant,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                )
            }
        }
    }
}
