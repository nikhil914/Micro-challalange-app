package com.microchallenge.app.games

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
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
import com.microchallenge.app.ui.GameColors
import com.microchallenge.core.challenges.games.ChangeTile
import com.microchallenge.core.challenges.games.SpotChangeSession
import kotlin.math.cos
import kotlin.math.sin

private const val FEEDBACK_MS = 450L

/** Spot the Change: study the grid, it blinks, tap the tile that came back different. */
@Composable
fun SpotChangeScreen(session: SpotChangeSession, elapsedMs: Long, onInput: () -> Unit) {
    val haptics = LocalHapticFeedback.current
    var feedbackAt by remember { mutableLongStateOf(-1L) }

    val phase = session.phase()
    val tiles = session.tiles()
    val n = session.gridSize
    // Guards against a second touch landing on the next round before recomposition.
    val roundAtCompose = session.roundsPlayed
    val showFeedback = feedbackAt >= 0 && elapsedMs - feedbackAt < FEEDBACK_MS
    val lastOk = session.lastTapCorrect

    val status = when {
        showFeedback && lastOk == true -> "Spotted"
        showFeedback && lastOk == false -> "Missed it"
        phase == SpotChangeSession.Phase.FIND -> "Tap what changed"
        else -> "Memorize"
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
                text = "Spotted ${session.solved}",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = status,
                style = MaterialTheme.typography.titleLarge,
                color = when {
                    showFeedback && lastOk == false -> MaterialTheme.colorScheme.error
                    phase == SpotChangeSession.Phase.FIND -> MaterialTheme.colorScheme.tertiary
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                },
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            )
        }

        BoxWithConstraints(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentAlignment = Alignment.Center,
        ) {
            val side = if (maxWidth < maxHeight) maxWidth else maxHeight
            Column(
                modifier = Modifier.size(side),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                for (r in 0 until n) {
                    Row(
                        modifier = Modifier.fillMaxWidth().weight(1f),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        for (c in 0 until n) {
                            val index = r * n + c
                            // Null during the mask: the cell stays, its shape is hidden.
                            val tile = tiles.getOrNull(index)
                            ChangeTileCell(
                                tile = tile,
                                description = "Row ${r + 1}, column ${c + 1}: " +
                                    (if (tile == null) "hidden" else describeTile(tile)),
                                modifier = Modifier.weight(1f).aspectRatio(1f),
                                onTap = {
                                    if (session.roundsPlayed == roundAtCompose &&
                                        session.phase() == SpotChangeSession.Phase.FIND
                                    ) {
                                        val ok = session.tap(index)
                                        if (!ok) haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                        feedbackAt = elapsedMs
                                        onInput()
                                    }
                                },
                            )
                        }
                    }
                }
            }
        }

        Text(
            text = "One tile changes after the blink",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 10.dp),
        )
    }
}

@Composable
private fun ChangeTileCell(
    tile: ChangeTile?,
    description: String,
    modifier: Modifier,
    onTap: () -> Unit,
) {
    val currentOnTap by rememberUpdatedState(onTap)
    val outline = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f)
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .pointerInput(Unit) {
                detectTapGestures(onPress = { currentOnTap() })
            }
            .semantics {
                role = Role.Button
                contentDescription = description
                onClick(label = "Pick this tile") { currentOnTap(); true }
            },
        contentAlignment = Alignment.Center,
    ) {
        if (tile != null) {
            val color = GameColors.entries.getOrElse(tile.colorIndex) { GameColors.BLUE }.color
            Canvas(modifier = Modifier.fillMaxSize().padding(8.dp)) {
                drawChangeShape(tile.shape, color, outline)
            }
        }
    }
}

private fun describeTile(tile: ChangeTile): String {
    val shapeName = when (tile.shape) {
        SpotChangeSession.SHAPE_CIRCLE -> "circle"
        SpotChangeSession.SHAPE_SQUARE -> "square"
        SpotChangeSession.SHAPE_TRIANGLE -> "triangle"
        SpotChangeSession.SHAPE_DIAMOND -> "diamond"
        SpotChangeSession.SHAPE_STAR -> "star"
        else -> "hexagon"
    }
    val colorName = GameColors.entries.getOrElse(tile.colorIndex) { GameColors.BLUE }.label.lowercase()
    return "$colorName $shapeName"
}

/** Draws one of the SpotChangeSession shapes centred in the canvas. */
private fun DrawScope.drawChangeShape(shape: Int, color: Color, outline: Color) {
    val r = size.minDimension / 2f * 0.9f
    val c = center
    val strokeWidth = (size.minDimension * 0.03f).coerceAtLeast(1f)
    val path: Path? = when (shape) {
        SpotChangeSession.SHAPE_CIRCLE -> null
        SpotChangeSession.SHAPE_SQUARE -> changePolygon(c, r * 1.1f, 4, -45f)
        SpotChangeSession.SHAPE_TRIANGLE -> changePolygon(c.copy(y = c.y + r * 0.12f), r * 1.05f, 3, -90f)
        SpotChangeSession.SHAPE_DIAMOND -> Path().apply {
            moveTo(c.x, c.y - r); lineTo(c.x + r * 0.62f, c.y)
            lineTo(c.x, c.y + r); lineTo(c.x - r * 0.62f, c.y); close()
        }
        SpotChangeSession.SHAPE_STAR -> Path().apply {
            for (i in 0 until 10) {
                val radius = if (i % 2 == 0) r else r * 0.45f
                val a = Math.toRadians(-90.0 + i * 36.0)
                val x = c.x + radius * cos(a).toFloat()
                val y = c.y + radius * sin(a).toFloat()
                if (i == 0) moveTo(x, y) else lineTo(x, y)
            }
            close()
        }
        else -> changePolygon(c, r, 6, 0f)
    }
    if (path == null) {
        drawCircle(color = color, radius = r, center = c)
        drawCircle(color = outline, radius = r, center = c, style = Stroke(width = strokeWidth))
    } else {
        drawPath(path = path, color = color)
        drawPath(path = path, color = outline, style = Stroke(width = strokeWidth))
    }
}

private fun changePolygon(center: Offset, radius: Float, sides: Int, startDeg: Float): Path = Path().apply {
    for (i in 0 until sides) {
        val a = Math.toRadians((startDeg + i * 360f / sides).toDouble())
        val x = center.x + radius * cos(a).toFloat()
        val y = center.y + radius * sin(a).toFloat()
        if (i == 0) moveTo(x, y) else lineTo(x, y)
    }
    close()
}
