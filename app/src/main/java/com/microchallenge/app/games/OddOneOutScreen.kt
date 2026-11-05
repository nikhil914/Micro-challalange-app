package com.microchallenge.app.games

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.microchallenge.app.ui.GameColors
import com.microchallenge.core.challenges.games.OddOneOutSession
import com.microchallenge.core.challenges.games.OddTile
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

private const val WRONG_FLASH_MS = 400L

/** Odd One Out: square grid of Canvas-drawn tiles; tap the one that differs. */
@Composable
fun OddOneOutScreen(session: OddOneOutSession, elapsedMs: Long, onInput: () -> Unit) {
    val haptics = LocalHapticFeedback.current
    var wrongIndex by remember { mutableIntStateOf(-1) }
    var wrongAt by remember { mutableLongStateOf(-1L) }
    val showWrong = wrongAt >= 0 && elapsedMs - wrongAt < WRONG_FLASH_MS

    val n = session.gridSize
    val round = session.solved
    val tiles = session.tiles

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = "Found ${session.solved}",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = if (showWrong) "Miss" else "Misses ${session.mistakes}",
                style = MaterialTheme.typography.titleLarge,
                color = if (showWrong) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        BoxWithConstraints(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentAlignment = Alignment.Center,
        ) {
            val side = if (maxWidth < maxHeight) maxWidth else maxHeight
            // key(round): fresh tile state (no stale press ripples) when a new grid appears.
            key(round) {
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
                                val tile = tiles.getOrNull(index) ?: continue
                                val isWrongFlash = showWrong && wrongIndex == index
                                OddTileCell(
                                    tile = tile,
                                    flaggedWrong = isWrongFlash,
                                    description = "Row ${r + 1}, column ${c + 1}: ${describe(tile)}",
                                    modifier = Modifier.weight(1f).aspectRatio(1f),
                                    onTap = {
                                        if (session.solved != round) return@OddTileCell
                                        val ok = session.tap(index)
                                        if (!ok) {
                                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                            wrongIndex = index
                                            wrongAt = elapsedMs
                                        } else {
                                            wrongAt = -1L
                                        }
                                        onInput()
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun OddTileCell(
    tile: OddTile,
    flaggedWrong: Boolean,
    description: String,
    modifier: Modifier,
    onTap: () -> Unit,
) {
    val shape = RoundedCornerShape(12.dp)
    val color = GameColors.entries.getOrElse(tile.colorIndex) { GameColors.BLUE }.color
    val outline = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f)
    Box(
        modifier = modifier
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .then(
                if (flaggedWrong) Modifier.border(3.dp, MaterialTheme.colorScheme.error, shape) else Modifier,
            )
            .clickable(role = Role.Button, onClickLabel = "Pick this tile", onClick = onTap)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.fillMaxSize().padding(6.dp)) {
            drawGameShape(tile.shape, color, outline, tile.rotationDeg.toFloat(), tile.scale)
        }
        if (flaggedWrong) {
            Text("✗", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.headlineSmall)
        }
    }
}

private fun describe(tile: OddTile): String {
    val shapeName = when (tile.shape) {
        OddOneOutSession.SHAPE_CIRCLE -> "circle"
        OddOneOutSession.SHAPE_SQUARE -> "square"
        OddOneOutSession.SHAPE_TRIANGLE -> "triangle"
        OddOneOutSession.SHAPE_DIAMOND -> "diamond"
        OddOneOutSession.SHAPE_STAR -> "star"
        OddOneOutSession.SHAPE_HEXAGON -> "hexagon"
        OddOneOutSession.SHAPE_ARROW -> "arrow"
        else -> "L shape"
    }
    val colorName = GameColors.entries.getOrElse(tile.colorIndex) { GameColors.BLUE }.label.lowercase()
    val size = "${(tile.scale * 100).roundToInt()} percent size"
    val rot = if (tile.rotationDeg != 0) ", rotated ${tile.rotationDeg} degrees" else ""
    return "$colorName $shapeName, $size$rot"
}

/** Draws one of the OddOneOutSession shapes centred in the canvas. */
private fun DrawScope.drawGameShape(shape: Int, color: Color, outline: Color, rotationDeg: Float, scale: Float) {
    val r = size.minDimension / 2f * 0.82f * scale
    val c = center
    val strokeWidth = (size.minDimension * 0.025f).coerceAtLeast(1f)
    rotate(degrees = rotationDeg, pivot = c) {
        val path: Path? = when (shape) {
            OddOneOutSession.SHAPE_CIRCLE -> null
            OddOneOutSession.SHAPE_SQUARE -> polygon(c, r * 1.05f, 4, -45f)
            OddOneOutSession.SHAPE_TRIANGLE -> polygon(c.copy(y = c.y + r * 0.12f), r * 1.05f, 3, -90f)
            OddOneOutSession.SHAPE_DIAMOND -> Path().apply {
                moveTo(c.x, c.y - r); lineTo(c.x + r * 0.62f, c.y)
                lineTo(c.x, c.y + r); lineTo(c.x - r * 0.62f, c.y); close()
            }
            OddOneOutSession.SHAPE_STAR -> star(c, r, r * 0.45f, 5)
            OddOneOutSession.SHAPE_HEXAGON -> polygon(c, r, 6, 0f)
            OddOneOutSession.SHAPE_ARROW -> Path().apply {
                // Up-pointing arrow: head + shaft.
                moveTo(c.x, c.y - r)
                lineTo(c.x + r * 0.75f, c.y - r * 0.1f)
                lineTo(c.x + r * 0.28f, c.y - r * 0.1f)
                lineTo(c.x + r * 0.28f, c.y + r)
                lineTo(c.x - r * 0.28f, c.y + r)
                lineTo(c.x - r * 0.28f, c.y - r * 0.1f)
                lineTo(c.x - r * 0.75f, c.y - r * 0.1f)
                close()
            }
            else -> Path().apply { // L shape
                val t = r * 0.55f
                moveTo(c.x - r * 0.7f, c.y - r)
                lineTo(c.x - r * 0.7f + t, c.y - r)
                lineTo(c.x - r * 0.7f + t, c.y + r - t)
                lineTo(c.x + r * 0.7f, c.y + r - t)
                lineTo(c.x + r * 0.7f, c.y + r)
                lineTo(c.x - r * 0.7f, c.y + r)
                close()
            }
        }
        if (path == null) {
            drawCircle(color = color, radius = r, center = c)
            drawCircle(color = outline, radius = r, center = c, style = Stroke(width = strokeWidth))
        } else {
            drawPath(path = path, color = color)
            drawPath(path = path, color = outline, style = Stroke(width = strokeWidth))
        }
    }
}

private fun polygon(center: Offset, radius: Float, sides: Int, startDeg: Float): Path = Path().apply {
    for (i in 0 until sides) {
        val a = Math.toRadians((startDeg + i * 360f / sides).toDouble())
        val x = center.x + radius * cos(a).toFloat()
        val y = center.y + radius * sin(a).toFloat()
        if (i == 0) moveTo(x, y) else lineTo(x, y)
    }
    close()
}

private fun star(center: Offset, outer: Float, inner: Float, points: Int): Path = Path().apply {
    for (i in 0 until points * 2) {
        val radius = if (i % 2 == 0) outer else inner
        val a = Math.toRadians((-90.0 + i * 180.0 / points))
        val x = center.x + radius * cos(a).toFloat()
        val y = center.y + radius * sin(a).toFloat()
        if (i == 0) moveTo(x, y) else lineTo(x, y)
    }
    close()
}
