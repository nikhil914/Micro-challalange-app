package com.microchallenge.app.games

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
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
import com.microchallenge.core.challenges.games.MirrorMatchSession
import com.microchallenge.core.challenges.games.ShapeCell

private const val FEEDBACK_MS = 350L

/** Mirror Match: two block shapes side by side, SAME and MIRROR pads below. */
@Composable
fun MirrorMatchScreen(session: MirrorMatchSession, elapsedMs: Long, onInput: () -> Unit) {
    val haptics = LocalHapticFeedback.current
    var feedbackAt by remember { mutableLongStateOf(-1L) }

    val trial = session.current
    // Guards against a second touch landing on the next pair before recomposition.
    val answeredAtCompose = session.answeredCount
    val showFeedback = feedbackAt >= 0 && elapsedMs - feedbackAt < FEEDBACK_MS
    val lastOk = session.lastAnswerCorrect

    val leftColor = GameColors.entries.getOrElse(trial.leftColorIndex) { GameColors.BLUE }.color
    val rightColor = GameColors.entries.getOrElse(trial.rightColorIndex) { GameColors.BLUE }.color
    // Both shapes share one cell size (before scaling), taken from the larger bounding box side.
    val span = (trial.left + trial.right).maxOf { if (it.x > it.y) it.x else it.y } + 1

    val choose: (Boolean) -> Unit = { same ->
        if (session.answeredCount == answeredAtCompose) {
            val ok = session.answer(same)
            if (!ok) haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            feedbackAt = elapsedMs
            onInput()
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = "Matched ${session.correct}",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = when {
                    !showFeedback -> "Wrong ${session.mistakes}"
                    lastOk == true -> "Clean hit"
                    else -> "Wrong"
                },
                style = MaterialTheme.typography.titleLarge,
                color = when {
                    showFeedback && lastOk == true -> MaterialTheme.colorScheme.primary
                    showFeedback || session.mistakes > 0 -> MaterialTheme.colorScheme.error
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                },
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            )
        }

        Row(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            ShapePanel(
                cells = trial.left,
                span = span,
                scale = 1f,
                color = leftColor,
                flash = if (showFeedback) lastOk else null,
                description = "Left shape, ${trial.left.size} blocks",
                modifier = Modifier.weight(1f).fillMaxHeight(),
            )
            ShapePanel(
                cells = trial.right,
                span = span,
                scale = trial.rightScale,
                color = rightColor,
                flash = if (showFeedback) lastOk else null,
                description = "Right shape, ${trial.right.size} blocks",
                modifier = Modifier.weight(1f).fillMaxHeight(),
            )
        }

        Text(
            text = "Rotated only, or flipped?",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(vertical = 10.dp),
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            MirrorAnswerPad(
                label = "SAME",
                hint = "rotated",
                description = "Same shape, only rotated",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.weight(1f).height(104.dp),
                onChoose = { choose(true) },
            )
            MirrorAnswerPad(
                label = "MIRROR",
                hint = "flipped",
                description = "Mirror image",
                tint = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.weight(1f).height(104.dp),
                onChoose = { choose(false) },
            )
        }
    }
}

@Composable
private fun ShapePanel(
    cells: List<ShapeCell>,
    span: Int,
    scale: Float,
    color: Color,
    flash: Boolean?,
    description: String,
    modifier: Modifier,
) {
    val outline = MaterialTheme.colorScheme.background
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(24.dp))
            .background(
                when (flash) {
                    true -> GameColors.GREEN.color.copy(alpha = 0.18f)
                    false -> GameColors.RED.color.copy(alpha = 0.18f)
                    null -> MaterialTheme.colorScheme.surfaceVariant
                },
            )
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.fillMaxSize().padding(16.dp)) {
            if (cells.isNotEmpty() && span > 0) {
                val cell = size.minDimension / span * scale
                val widthCells = cells.maxOf { it.x } + 1
                val heightCells = cells.maxOf { it.y } + 1
                val originX = (size.width - widthCells * cell) / 2f
                val originY = (size.height - heightCells * cell) / 2f
                val strokeWidth = (cell * 0.08f).coerceAtLeast(1f)
                for (c in cells) {
                    val topLeft = Offset(originX + c.x * cell, originY + c.y * cell)
                    drawRect(color = color, topLeft = topLeft, size = Size(cell, cell))
                    // Dark seams between blocks make the cell count readable.
                    drawRect(
                        color = outline,
                        topLeft = topLeft,
                        size = Size(cell, cell),
                        style = Stroke(width = strokeWidth),
                    )
                }
            }
        }
    }
}

/** Answer pad that fires on touch-down. */
@Composable
private fun MirrorAnswerPad(
    label: String,
    hint: String,
    description: String,
    tint: Color,
    modifier: Modifier,
    onChoose: () -> Unit,
) {
    val currentOnChoose by rememberUpdatedState(onChoose)
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(24.dp))
            .background(tint.copy(alpha = 0.22f))
            .pointerInput(Unit) {
                detectTapGestures(onPress = { currentOnChoose() })
            }
            .semantics {
                role = Role.Button
                contentDescription = description
                onClick(label = "Choose") { currentOnChoose(); true }
            },
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(text = label, fontSize = 28.sp, fontWeight = FontWeight.Black, color = tint)
            Text(
                text = hint,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
