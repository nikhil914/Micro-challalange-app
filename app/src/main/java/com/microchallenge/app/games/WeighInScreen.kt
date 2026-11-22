package com.microchallenge.app.games

import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.microchallenge.core.challenges.games.Scale
import com.microchallenge.core.challenges.games.WeighInSession
import com.microchallenge.core.challenges.games.WeighInSession.Ask
import kotlin.math.cos
import kotlin.math.sin

private const val TILT_MS = 380L
private const val WEIGH_FEEDBACK_MS = 500L
private const val TILT_DEG = 11f

/** Weigh In: read the balance scales and tap the heaviest (or lightest) weight. */
@Composable
fun WeighInScreen(session: WeighInSession, elapsedMs: Long, onInput: () -> Unit) {
    val haptics = LocalHapticFeedback.current
    val currentOnInput by rememberUpdatedState(onInput)
    val currentElapsed by rememberUpdatedState(elapsedMs)
    var roundAt by remember(session) { mutableLongStateOf(0L) }
    var feedbackAt by remember(session) { mutableLongStateOf(-1L) }

    val timeUp = session.isTimeUp
    val showFeedback = feedbackAt >= 0 && elapsedMs - feedbackAt < WEIGH_FEEDBACK_MS
    val tilt = easeOutBack(((elapsedMs - roundAt) / TILT_MS.toFloat()).coerceIn(0f, 1f))

    fun onChoose(id: Int) {
        if (timeUp) return
        val ok = session.choose(id)
        haptics.performHapticFeedback(if (ok) HapticFeedbackType.TextHandleMove else HapticFeedbackType.LongPress)
        roundAt = currentElapsed
        feedbackAt = currentElapsed
        currentOnInput()
    }

    val scheme = MaterialTheme.colorScheme
    val heaviest = session.ask == Ask.HEAVIEST
    val title = when {
        timeUp -> "Time's up"
        showFeedback && session.lastCorrect == true -> "Correct!"
        showFeedback && session.lastCorrect == false -> "Wrong"
        heaviest -> "Tap the HEAVIEST"
        else -> "Tap the LIGHTEST"
    }
    val titleColor = when {
        showFeedback && session.lastCorrect == true -> scheme.tertiary
        showFeedback && session.lastCorrect == false -> scheme.error
        heaviest -> scheme.primary
        else -> scheme.secondary
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            title,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Black,
            color = titleColor,
            modifier = Modifier.semantics {
                heading()
                liveRegion = LiveRegionMode.Polite
            },
        )
        Text(
            "Right ${session.correct} · Wrong ${session.mistakes}",
            style = MaterialTheme.typography.titleMedium,
            color = scheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
        Column(
            modifier = Modifier.fillMaxWidth().weight(1f),
            verticalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            for ((i, scale) in session.scales.withIndex()) {
                val spoken = "${GameArt.name(scale.heavier)} is heavier than ${GameArt.name(scale.lighter)}"
                Canvas(
                    Modifier
                        .weight(1f, fill = false)
                        .heightIn(max = 150.dp)
                        .aspectRatio(2.4f)
                        .clearAndSetSemantics { contentDescription = "Scale ${i + 1}: $spoken" },
                ) {
                    drawScale(scale, tilt, scheme.primary)
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
        ) {
            for (id in session.weights) {
                WeightButton(
                    icon = id,
                    enabled = !timeUp,
                    onTap = { onChoose(id) },
                    tapKey = session,
                    modifier = Modifier.weight(1f).widthIn(max = 88.dp).aspectRatio(1f),
                )
            }
        }
    }
}

@Composable
private fun WeightButton(icon: Int, enabled: Boolean, onTap: () -> Unit, tapKey: Any, modifier: Modifier) {
    val currentEnabled by rememberUpdatedState(enabled)
    val currentOnTap by rememberUpdatedState(onTap)
    val surface = MaterialTheme.colorScheme.surfaceVariant
    Box(
        modifier = modifier
            .pointerInput(tapKey) {
                detectTapGestures(onPress = { if (currentEnabled) currentOnTap() })
            }
            .clearAndSetSemantics {
                role = Role.Button
                contentDescription = GameArt.name(icon)
                if (!enabled) disabled()
                onClick(label = "Choose ${GameArt.name(icon)}") {
                    if (currentEnabled) currentOnTap()
                    currentEnabled
                }
            },
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val c = Offset(size.width / 2, size.height / 2)
            val r = size.minDimension / 2
            drawCircle(
                brush = Brush.verticalGradient(listOf(surface, Color(0xFF10131B))),
                radius = r,
                center = c,
            )
            drawCircle(color = GameArt.color(icon).copy(alpha = 0.7f), radius = r * 0.96f, center = c, style = Stroke(width = r * 0.07f))
            drawGlow(c, r * 0.9f, GameArt.color(icon), 0.25f)
            drawIcon(icon, c, r * 0.55f)
        }
    }
}

/**
 * A balance scale: stand, beam tipped toward the heavier pan (scaled by [progress]),
 * and a weight sitting in each pan.
 */
private fun DrawScope.drawScale(scale: Scale, progress: Float, accent: Color) {
    val w = size.width
    val h = size.height
    val metal = Color(0xFF9AA3B8)
    val metalDark = Color(0xFF5B6275)
    val pivot = Offset(w / 2, h * 0.2f)
    val arm = w * 0.33f
    val angle = Math.toRadians((TILT_DEG * progress).toDouble()).toFloat() * if (scale.leftHeavier) 1f else -1f
    val dx = arm * cos(angle)
    val dy = arm * sin(angle)
    val leftEnd = Offset(pivot.x - dx, pivot.y + dy)
    val rightEnd = Offset(pivot.x + dx, pivot.y - dy)

    // Stand and base.
    drawLine(metalDark, pivot, Offset(pivot.x, h * 0.92f), strokeWidth = w * 0.022f, cap = StrokeCap.Round)
    val base = Path().apply {
        moveTo(pivot.x - w * 0.12f, h * 0.98f)
        lineTo(pivot.x + w * 0.12f, h * 0.98f)
        lineTo(pivot.x + w * 0.05f, h * 0.88f)
        lineTo(pivot.x - w * 0.05f, h * 0.88f)
        close()
    }
    drawPath(base, brush = Brush.verticalGradient(listOf(metal, metalDark), startY = h * 0.88f, endY = h * 0.98f))

    // Beam.
    drawLine(
        brush = Brush.linearGradient(listOf(metal, Color.White, metal), start = leftEnd, end = rightEnd),
        start = leftEnd,
        end = rightEnd,
        strokeWidth = h * 0.05f,
        cap = StrokeCap.Round,
    )
    drawGlow(pivot, h * 0.16f, accent, 0.5f)
    drawCircle(accent, radius = h * 0.045f, center = pivot)

    // Pans hang straight down from each end.
    val hang = h * 0.36f
    val panHalf = w * 0.12f
    val weightR = h * 0.17f
    for ((end, icon) in listOf(leftEnd to scale.left, rightEnd to scale.right)) {
        val panY = end.y + hang
        drawLine(metal.copy(alpha = 0.8f), end, Offset(end.x - panHalf * 0.8f, panY), strokeWidth = 2f)
        drawLine(metal.copy(alpha = 0.8f), end, Offset(end.x + panHalf * 0.8f, panY), strokeWidth = 2f)
        drawIcon(icon, Offset(end.x, panY - weightR * 0.95f), weightR)
        drawArc(
            brush = Brush.verticalGradient(listOf(metal, metalDark), startY = panY, endY = panY + h * 0.12f),
            startAngle = 0f,
            sweepAngle = 180f,
            useCenter = true,
            topLeft = Offset(end.x - panHalf, panY - h * 0.1f),
            size = Size(panHalf * 2, h * 0.22f),
        )
    }
}
