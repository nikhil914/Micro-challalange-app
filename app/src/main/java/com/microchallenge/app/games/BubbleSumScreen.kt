package com.microchallenge.app.games

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.microchallenge.core.challenges.games.BubbleSumSession
import com.microchallenge.core.challenges.games.BubbleSumSession.PopResult
import com.microchallenge.core.challenges.games.SumBubble
import kotlin.math.PI
import kotlin.math.roundToInt
import kotlin.math.sin

private const val BUBBLE_FEEDBACK_MS = 550L

/** Bubbles per row for each board size; rows alternate offsets like a honeycomb. */
private fun rowSizes(n: Int): List<Int> = when (n) {
    5 -> listOf(2, 1, 2)
    6 -> listOf(2, 2, 2)
    else -> listOf(2, 3, 2)
}

/** Bubble Sum: pop floating bubbles that add up exactly to the target. */
@Composable
fun BubbleSumScreen(session: BubbleSumSession, elapsedMs: Long, onInput: () -> Unit) {
    val haptics = LocalHapticFeedback.current
    val currentOnInput by rememberUpdatedState(onInput)
    val currentElapsed by rememberUpdatedState(elapsedMs)
    var feedbackAt by remember(session) { mutableLongStateOf(-1L) }
    var feedback by remember(session) { mutableStateOf(PopResult.IGNORED) }

    val timeUp = session.isTimeUp
    val sinceFeedback = if (feedbackAt >= 0) elapsedMs - feedbackAt else Long.MAX_VALUE
    val showFeedback = sinceFeedback < BUBBLE_FEEDBACK_MS
    val target = session.target
    val sum = session.selectedSum

    fun onBubbleTap(id: Int) {
        val result = session.pop(id)
        when (result) {
            PopResult.SELECTED, PopResult.DESELECTED -> haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            PopResult.SOLVED, PopResult.BUST -> {
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                feedback = result
                feedbackAt = currentElapsed
            }
            PopResult.IGNORED -> Unit
        }
        currentOnInput()
    }

    val scheme = MaterialTheme.colorScheme
    val status = when {
        timeUp -> "Time's up"
        showFeedback && feedback == PopResult.SOLVED -> "Exact!"
        showFeedback && feedback == PopResult.BUST -> "Bust!"
        else -> "Make $target"
    }
    val statusColor = when {
        showFeedback && feedback == PopResult.SOLVED -> scheme.tertiary
        showFeedback && feedback == PopResult.BUST -> scheme.error
        else -> scheme.onSurface
    }
    // A bust shakes the bubble field for a moment.
    val shake = if (showFeedback && feedback == PopResult.BUST) {
        val p = sinceFeedback / BUBBLE_FEEDBACK_MS.toFloat()
        (sin(p * 6 * PI) * 14 * (1 - p)).toFloat()
    } else {
        0f
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            TargetOrb(target = target, modifier = Modifier.size(84.dp))
            Column(
                horizontalAlignment = Alignment.End,
                modifier = Modifier.semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
            ) {
                Text(
                    status,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = statusColor,
                    modifier = Modifier.semantics { heading() },
                )
                Text(
                    "Sum $sum / $target",
                    style = MaterialTheme.typography.titleMedium,
                    color = if (sum > 0) scheme.primary else scheme.onSurfaceVariant,
                )
                Text(
                    "Sums ${session.solved} · Busts ${session.busts}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = scheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        SumMeter(sum = sum, target = target, modifier = Modifier.fillMaxWidth().height(12.dp))
        Spacer(Modifier.height(12.dp))
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .graphicsLayer { translationX = shake * density },
        ) {
            val density = LocalDensity.current
            val wPx = constraints.maxWidth.toFloat()
            val hPx = constraints.maxHeight.toFloat()
            val rows = rowSizes(session.bubbleCount)
            val maxPerRow = rows.max()
            val slotW = wPx / (maxPerRow + 0.5f)
            val slotH = hPx / rows.size
            val diameterPx = minOf(slotW, slotH) * 0.84f
            val diameterDp = with(density) { diameterPx.toDp() }
            val bobPx = with(density) { 7.dp.toPx() }
            val t = elapsedMs / 1000f

            // Bubbles take slots left to right by lane, so the layout is stable within a round.
            val ordered = session.bubbles.sortedBy { it.lane }
            var slot = 0
            for ((rowIndex, count) in rows.withIndex()) {
                val rowWidth = count * slotW
                val left = (wPx - rowWidth) / 2f
                for (k in 0 until count) {
                    val bubble = ordered.getOrNull(slot++)
                    if (bubble == null) continue
                    val wave = (t * 1.4f + bubble.phase) * 2f * PI.toFloat()
                    val cx = left + (k + 0.5f) * slotW + sin(wave * 0.7f) * bobPx * 0.6f
                    val cy = (rowIndex + 0.5f) * slotH + sin(wave) * bobPx
                    key(bubble.id) {
                        Bubble(
                            bubble = bubble,
                            selected = session.isSelected(bubble.id),
                            enabled = !timeUp,
                            onTap = { onBubbleTap(bubble.id) },
                            tapKey = session,
                            modifier = Modifier
                                .offset { IntOffset((cx - diameterPx / 2).roundToInt(), (cy - diameterPx / 2).roundToInt()) }
                                .size(diameterDp),
                        )
                    }
                }
            }
            if (showFeedback && feedback == PopResult.SOLVED) {
                Canvas(Modifier.fillMaxSize()) {
                    drawBurst(
                        center = Offset(size.width / 2, size.height / 2),
                        progress = sinceFeedback / BUBBLE_FEEDBACK_MS.toFloat(),
                        color = scheme.tertiary,
                        radius = size.minDimension * 0.3f,
                        sparks = 16,
                    )
                }
            }
        }
    }
}

@Composable
private fun TargetOrb(target: Int, modifier: Modifier) {
    val scheme = MaterialTheme.colorScheme
    Box(
        modifier = modifier.semantics(mergeDescendants = true) { contentDescription = "Target $target" },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val c = Offset(size.width / 2, size.height / 2)
            val r = size.minDimension / 2
            drawGlow(c, r, scheme.tertiary, 0.45f)
            drawCircle(
                brush = Brush.radialGradient(
                    listOf(lerp(scheme.tertiary, Color.White, 0.4f), scheme.tertiary, lerp(scheme.tertiary, Color.Black, 0.45f)),
                    center = Offset(c.x - r * 0.25f, c.y - r * 0.3f),
                    radius = r * 1.1f,
                ),
                radius = r * 0.78f,
                center = c,
            )
        }
        Text(
            "$target",
            fontSize = 30.sp,
            fontWeight = FontWeight.Black,
            color = Color(0xFF261A00),
        )
    }
}

@Composable
private fun SumMeter(sum: Int, target: Int, modifier: Modifier) {
    val scheme = MaterialTheme.colorScheme
    val fraction by animateFloatAsState(
        targetValue = if (target <= 0) 0f else (sum.toFloat() / target).coerceIn(0f, 1f),
        animationSpec = tween(160),
        label = "sumMeter",
    )
    Canvas(modifier.clearAndSetSemantics { }) {
        val corner = CornerRadius(size.height / 2)
        drawRoundRect(color = scheme.surfaceVariant, cornerRadius = corner)
        if (fraction > 0f) {
            drawRoundRect(
                brush = Brush.horizontalGradient(listOf(scheme.primary, scheme.tertiary)),
                size = Size(size.width * fraction, size.height),
                cornerRadius = corner,
            )
        }
    }
}

@Composable
private fun Bubble(
    bubble: SumBubble,
    selected: Boolean,
    enabled: Boolean,
    onTap: () -> Unit,
    tapKey: Any,
    modifier: Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val currentEnabled by rememberUpdatedState(enabled)
    val currentOnTap by rememberUpdatedState(onTap)
    val grow by animateFloatAsState(if (selected) 1.1f else 1f, tween(140), label = "bubbleGrow")
    val color = GameArt.color(bubble.value)
    val ring = scheme.tertiary
    Box(
        modifier = modifier
            .graphicsLayer {
                scaleX = grow
                scaleY = grow
            }
            .pointerInput(tapKey) {
                detectTapGestures(onPress = { if (currentEnabled) currentOnTap() })
            }
            .clearAndSetSemantics {
                role = Role.Button
                contentDescription = "Bubble ${bubble.value}"
                stateDescription = if (selected) "selected" else "not selected"
                if (!enabled) disabled()
                onClick(label = if (selected) "Unselect" else "Select") {
                    if (currentEnabled) currentOnTap()
                    currentEnabled
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val c = Offset(size.width / 2, size.height / 2)
            val r = size.minDimension / 2
            if (selected) drawGlow(c, r * 1.25f, ring, 0.7f)
            // Translucent soap-bubble body: clear centre, tinted rim.
            drawCircle(
                brush = Brush.radialGradient(
                    listOf(
                        color.copy(alpha = if (selected) 0.55f else 0.18f),
                        color.copy(alpha = if (selected) 0.75f else 0.4f),
                        color.copy(alpha = 0.95f),
                    ),
                    center = c,
                    radius = r,
                ),
                radius = r * 0.96f,
                center = c,
            )
            drawCircle(
                color = if (selected) ring else Color.White.copy(alpha = 0.45f),
                radius = r * 0.96f,
                center = c,
                style = Stroke(width = if (selected) r * 0.1f else r * 0.04f),
            )
            // Specular highlight.
            drawOval(
                color = Color.White.copy(alpha = 0.6f),
                topLeft = Offset(c.x - r * 0.55f, c.y - r * 0.62f),
                size = Size(r * 0.5f, r * 0.28f),
            )
            drawCircle(color = Color.White.copy(alpha = 0.5f), radius = r * 0.06f, center = Offset(c.x + r * 0.05f, c.y - r * 0.6f))
        }
        Text(
            "${bubble.value}",
            fontSize = 30.sp,
            fontWeight = FontWeight.Black,
            color = Color.White,
            textAlign = TextAlign.Center,
        )
    }
}
