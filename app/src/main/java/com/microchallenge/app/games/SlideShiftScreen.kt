package com.microchallenge.app.games

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.microchallenge.app.ui.GameColors
import com.microchallenge.core.challenges.games.Shift
import com.microchallenge.core.challenges.games.SlideShiftSession
import com.microchallenge.core.challenges.games.SlideShiftSession.Axis
import com.microchallenge.core.challenges.games.SlideShiftSession.MoveResult
import kotlin.math.abs

private const val SLIDE_ANIM_MS = 130L
private const val SOLVED_BANNER_MS = 650L

/** Slide Shift: swipe rows and columns (tiles wrap around) until the grid matches the target. */
@Composable
fun SlideShiftScreen(session: SlideShiftSession, elapsedMs: Long, onInput: () -> Unit) {
    val haptics = LocalHapticFeedback.current
    val currentOnInput by rememberUpdatedState(onInput)
    val currentElapsed by rememberUpdatedState(elapsedMs)
    var lastShift by remember(session) { mutableStateOf<Shift?>(null) }
    var shiftAt by remember(session) { mutableLongStateOf(-1L) }
    var solvedAt by remember(session) { mutableLongStateOf(-1L) }

    val timeUp = session.isTimeUp
    val justSolved = solvedAt >= 0 && elapsedMs - solvedAt < SOLVED_BANNER_MS
    val n = session.size

    fun move(axis: Axis, line: Int, forward: Boolean) {
        when (session.shift(axis, line, forward)) {
            MoveResult.MOVED -> {
                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                lastShift = Shift(axis, line, forward)
                shiftAt = currentElapsed
            }
            MoveResult.SOLVED -> {
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                lastShift = null
                solvedAt = currentElapsed
            }
            MoveResult.IGNORED -> Unit
        }
        currentOnInput()
    }
    val currentMove by rememberUpdatedState(::move)

    val scheme = MaterialTheme.colorScheme
    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(Modifier.semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite }) {
                Text(
                    when {
                        timeUp -> "Time's up"
                        justSolved -> "Matched!"
                        else -> "Match the target"
                    },
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = if (justSolved && !timeUp) scheme.tertiary else scheme.onSurface,
                    modifier = Modifier.semantics { heading() },
                )
                Text(
                    "${session.correctCount}/${session.cellCount} in place · Moves ${session.moves} · Par ${session.par}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = scheme.onSurfaceVariant,
                )
                Text(
                    "Boards ${session.solvedCount}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = scheme.onSurfaceVariant,
                )
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("TARGET", style = MaterialTheme.typography.labelMedium, color = scheme.secondary)
                val target = session.target
                Canvas(
                    Modifier
                        .size(76.dp)
                        .clearAndSetSemantics { contentDescription = "Target pattern: " + describe(target, n) },
                ) {
                    drawGrid(target, n, null, 0f, inPlace = null)
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        Box(
            modifier = Modifier.fillMaxWidth().weight(1f),
            contentAlignment = Alignment.Center,
        ) {
            val tiles = session.tilesNow
            val target = session.target
            val shift = lastShift
            val p = if (shift != null && shiftAt >= 0) ((elapsedMs - shiftAt) / SLIDE_ANIM_MS.toFloat()).coerceIn(0f, 1f) else 1f
            val actions = buildList {
                for (l in 0 until n) {
                    add(CustomAccessibilityAction("Row ${l + 1} left") { currentMove(Axis.ROW, l, false); true })
                    add(CustomAccessibilityAction("Row ${l + 1} right") { currentMove(Axis.ROW, l, true); true })
                    add(CustomAccessibilityAction("Column ${l + 1} up") { currentMove(Axis.COLUMN, l, false); true })
                    add(CustomAccessibilityAction("Column ${l + 1} down") { currentMove(Axis.COLUMN, l, true); true })
                }
            }
            Canvas(
                Modifier
                    .aspectRatio(1f)
                    .pointerInput(session) {
                        var start = Offset.Zero
                        var drag = Offset.Zero
                        var fired = false
                        detectDragGestures(
                            onDragStart = {
                                start = it
                                drag = Offset.Zero
                                fired = false
                            },
                            onDrag = { change, amount ->
                                change.consume()
                                drag += amount
                                val cellPx = size.width / n.toFloat()
                                val threshold = cellPx * 0.28f
                                // Fire once per swipe, as soon as the direction is clear: feels instant.
                                if (!fired && (abs(drag.x) > threshold || abs(drag.y) > threshold)) {
                                    fired = true
                                    if (abs(drag.x) >= abs(drag.y)) {
                                        val row = (start.y / cellPx).toInt().coerceIn(0, n - 1)
                                        currentMove(Axis.ROW, row, drag.x > 0)
                                    } else {
                                        val col = (start.x / cellPx).toInt().coerceIn(0, n - 1)
                                        currentMove(Axis.COLUMN, col, drag.y > 0)
                                    }
                                }
                            },
                        )
                    }
                    .clearAndSetSemantics {
                        contentDescription = "Puzzle grid: " + describe(tiles, n) +
                            ". Swipe a row or column, or use actions to slide."
                        customActions = actions
                    },
            ) {
                drawGrid(tiles, n, shift, 1f - p, inPlace = target)
            }
        }
    }
}

/** Short spoken summary of a grid, row by row. */
private fun describe(grid: List<Int>, n: Int): String =
    (0 until n).joinToString("; ") { r ->
        "row ${r + 1} " + (0 until n).joinToString(", ") { c ->
            GameColors.entries.getOrElse(grid.getOrElse(r * n + c) { 0 }) { GameColors.BLUE }.label.lowercase()
        }
    }

/**
 * Draws an n x n grid of glossy colored tiles, each with its own icon so color is never
 * the only cue. [slide] (0..1) offsets the tiles of [shift]'s line back toward where they
 * came from, for a short slide-in. [inPlace] marks tiles already matching the target.
 */
private fun DrawScope.drawGrid(grid: List<Int>, n: Int, shift: Shift?, slide: Float, inPlace: List<Int>?) {
    val gap = size.minDimension * 0.03f
    val cell = (size.minDimension - gap * (n + 1)) / n
    val step = cell + gap
    val corner = cell * 0.18f
    drawRoundRect(
        color = Color(0xFF1E2230),
        cornerRadius = CornerRadius(corner * 1.2f),
    )
    clipRect {
        for (r in 0 until n) for (c in 0 until n) {
            val i = r * n + c
            val idx = grid.getOrElse(i) { 0 }
            var x = gap + c * step
            var y = gap + r * step
            if (shift != null && slide > 0f) {
                val dir = if (shift.forward) -1f else 1f
                if (shift.axis == Axis.ROW && shift.line == r) x += dir * step * slide
                if (shift.axis == Axis.COLUMN && shift.line == c) y += dir * step * slide
            }
            val color = GameColors.entries.getOrElse(idx) { GameColors.BLUE }.color
            drawGlossyTile(Offset(x, y), Size(cell, cell), color, corner)
            drawIcon(idx, Offset(x + cell / 2, y + cell / 2), cell * 0.26f, Color.Black, alpha = 0.55f)
            if (inPlace != null && inPlace.getOrNull(i) == idx) {
                drawCircle(
                    color = Color.White.copy(alpha = 0.85f),
                    radius = cell * 0.07f,
                    center = Offset(x + cell * 0.82f, y + cell * 0.18f),
                )
                drawCircle(
                    color = Color.Black.copy(alpha = 0.4f),
                    radius = cell * 0.07f,
                    center = Offset(x + cell * 0.82f, y + cell * 0.18f),
                    style = Stroke(width = cell * 0.02f + 1f),
                )
            }
        }
    }
}
