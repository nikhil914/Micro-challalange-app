package com.microchallenge.app.games

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateIntOffsetAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.microchallenge.core.challenges.games.SlidingTileSession
import com.microchallenge.core.challenges.games.SlidingTileSession.TapResult
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/** How long the "Solved!" celebration plays. */
private const val SLIDE_SOLVED_MS = 900L

private val SlideBoardTop = Color(0xFF232845)
private val SlideBoardBottom = Color(0xFF12152A)
private val SlideSlot = Color(0xFF0B0D1C)
private val SlideInk = Color(0xFF101322)

/** One color band per home row, so the solved board reads as a smooth gradient. */
private val SlideRowColors = listOf(
    Color(0xFF00E5FF), // cyan
    Color(0xFF7C9CFF), // periwinkle
    Color(0xFFB388FF), // violet
    Color(0xFFFF6FA8), // pink
)

private fun slideTileColor(homeRow: Int, homeCol: Int, size: Int): Color {
    val base = SlideRowColors[homeRow.coerceIn(0, SlideRowColors.lastIndex)]
    // Slight left-to-right brightening inside each band.
    return lerp(base, Color.White, 0.18f * homeCol / (size - 1).coerceAtLeast(1))
}

/** Slide Puzzle: tap (or swipe) tiles into the gap until 1..n-1 are back in order. */
@Composable
fun SlidingTileScreen(session: SlidingTileSession, elapsedMs: Long, onInput: () -> Unit) {
    val haptics = LocalHapticFeedback.current
    val currentOnInput by rememberUpdatedState(onInput)
    val currentElapsed by rememberUpdatedState(elapsedMs)
    var solvedAt by remember(session) { mutableLongStateOf(-1L) }

    val timeUp = session.isTimeUp
    val sinceSolved = if (solvedAt >= 0) elapsedMs - solvedAt else Long.MAX_VALUE
    val celebrating = sinceSolved < SLIDE_SOLVED_MS
    val n = session.size

    fun tapCell(cell: Int) {
        when (session.tap(cell)) {
            TapResult.MOVED -> haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            TapResult.SOLVED -> {
                solvedAt = currentElapsed
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            }
            TapResult.IGNORED -> Unit
        }
        currentOnInput()
    }

    /** Swipe moves the tile on the opposite side of the gap into it. */
    fun swipe(dx: Float, dy: Float) {
        val b = session.blankCell
        val r = b / n
        val c = b % n
        val target = if (abs(dx) > abs(dy)) {
            if (dx > 0 && c > 0) b - 1 else if (dx < 0 && c < n - 1) b + 1 else -1
        } else {
            if (dy > 0 && r > 0) b - n else if (dy < 0 && r < n - 1) b + n else -1
        }
        if (target >= 0) tapCell(target)
    }

    val title = when {
        timeUp -> "Time's up"
        celebrating -> "Solved!"
        else -> "Slide into order"
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            title,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = if (celebrating && !timeUp) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.semantics {
                heading()
                liveRegion = LiveRegionMode.Polite
            },
        )
        Text(
            "Solved ${session.solvedCount} · Moves ${session.moves} · Par ${session.par}",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(16.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            contentAlignment = Alignment.Center,
        ) {
            BoxWithConstraints(Modifier.aspectRatio(1f)) {
                val density = LocalDensity.current
                val boardPx = with(density) { maxWidth.toPx() }
                val padPx = boardPx * 0.035f
                val gapPx = boardPx * 0.022f
                val cellPx = (boardPx - 2 * padPx) / n
                val tilePx = cellPx - gapPx
                val tileDp = with(density) { tilePx.toDp() }

                // Pulse the board right after a solve.
                val pulse = if (celebrating) {
                    val p = sinceSolved / SLIDE_SOLVED_MS.toFloat()
                    1f + 0.04f * sin(p * PI).toFloat()
                } else {
                    1f
                }

                Box(
                    Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            scaleX = pulse
                            scaleY = pulse
                        }
                        // Tap fires on release so a swipe never also counts as a tap.
                        // Keyed on session and cell size so a new run or resize gets fresh detectors.
                        .pointerInput(session, cellPx) {
                            detectTapGestures(onTap = { pos ->
                                val col = ((pos.x - padPx) / cellPx).toInt()
                                val row = ((pos.y - padPx) / cellPx).toInt()
                                if (row in 0 until n && col in 0 until n) tapCell(row * n + col)
                            })
                        }
                        .pointerInput(session, cellPx) {
                            var total = Offset.Zero
                            detectDragGestures(
                                onDragStart = { total = Offset.Zero },
                                onDragEnd = {
                                    if (total.getDistance() > cellPx * 0.25f) swipe(total.x, total.y)
                                },
                                onDrag = { change, amount ->
                                    change.consume()
                                    total += amount
                                },
                            )
                        },
                ) {
                    // Board frame and empty slots.
                    Canvas(Modifier.fillMaxSize()) {
                        val corner = boardPx * 0.06f
                        drawRoundRect(
                            brush = Brush.verticalGradient(listOf(SlideBoardTop, SlideBoardBottom)),
                            cornerRadius = CornerRadius(corner, corner),
                        )
                        drawRoundRect(
                            color = Color.White.copy(alpha = 0.08f),
                            cornerRadius = CornerRadius(corner, corner),
                            style = Stroke(width = 3f),
                        )
                        for (cell in 0 until n * n) {
                            val tl = Offset(padPx + gapPx / 2 + (cell % n) * cellPx, padPx + gapPx / 2 + (cell / n) * cellPx)
                            drawRoundRect(
                                color = SlideSlot,
                                topLeft = tl,
                                size = Size(tilePx, tilePx),
                                cornerRadius = CornerRadius(tilePx * 0.18f, tilePx * 0.18f),
                            )
                        }
                    }

                    for (cell in 0 until n * n) {
                        val tile = session.tileAt(cell)
                        if (tile == 0) continue
                        // key(tile) keeps each tile's animation state as it moves between cells.
                        key(session, tile) {
                            SlideTile(
                                session = session,
                                tile = tile,
                                cell = cell,
                                target = IntOffset(
                                    (padPx + gapPx / 2 + (cell % n) * cellPx).roundToInt(),
                                    (padPx + gapPx / 2 + (cell / n) * cellPx).roundToInt(),
                                ),
                                tileDp = tileDp,
                                tilePx = tilePx,
                                enabled = !timeUp,
                                onTap = { tapCell(cell) },
                            )
                        }
                    }

                    // Celebration: sparks burst from every tile, plus a ring from the centre.
                    if (celebrating) {
                        val p = sinceSolved / SLIDE_SOLVED_MS.toFloat()
                        Canvas(Modifier.fillMaxSize()) {
                            val center = Offset(size.width / 2, size.height / 2)
                            drawGlow(center, size.width * 0.6f * (0.4f + p), Color(0xFF64FFDA), 0.35f * (1f - p))
                            drawBurst(center, p, Color(0xFFFFD740), size.width * 0.32f, sparks = 18)
                            for (cell in 0 until n * n - 1) {
                                val c = Offset(padPx + (cell % n + 0.5f) * cellPx, padPx + (cell / n + 0.5f) * cellPx)
                                val local = ((p - 0.06f * (cell % n + cell / n)) / 0.7f).coerceIn(0f, 1f)
                                if (local > 0f) {
                                    drawBurst(c, local, slideTileColor(cell / n, cell % n, n), tilePx * 0.5f, sparks = 8)
                                }
                            }
                            // Confetti falling from the top edge.
                            for (i in 0 until 24) {
                                val seedX = (i * 73 % 100) / 100f
                                val drift = sin((p * 4 + i) * 1.3f) * size.width * 0.03f
                                val x = seedX * size.width + drift
                                val y = -20f + p * size.height * (0.7f + (i % 5) * 0.08f)
                                val a = (p * 6 + i).toDouble()
                                val w = tilePx * 0.08f
                                drawRect(
                                    color = SlideRowColors[i % SlideRowColors.size].copy(alpha = 1f - p),
                                    topLeft = Offset(x + (cos(a) * w).toFloat(), y),
                                    size = Size(w * (0.6f + abs(sin(a)).toFloat()), w * 1.6f),
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
private fun SlideTile(
    session: SlidingTileSession,
    tile: Int,
    cell: Int,
    target: IntOffset,
    tileDp: androidx.compose.ui.unit.Dp,
    tilePx: Float,
    enabled: Boolean,
    onTap: () -> Unit,
) {
    val n = session.size
    val home = session.homeCell(tile)
    val atHome = home == cell
    val color = slideTileColor(home / n, home % n, n)
    val offset by animateIntOffsetAsState(
        targetValue = target,
        animationSpec = spring(dampingRatio = 0.72f, stiffness = Spring.StiffnessMediumLow * 1.6f),
        label = "slideTile$tile",
    )
    val canSlide = enabled && session.canSlide(cell)
    val label = "Tile $tile, row ${cell / n + 1}, column ${cell % n + 1}"
    Box(
        modifier = Modifier
            .offset { offset }
            .size(tileDp)
            .semantics {
                role = Role.Button
                contentDescription = label
                stateDescription = if (atHome) "in place" else "out of place"
                if (!canSlide) disabled()
                onClick(label = "Slide tile") {
                    if (canSlide) onTap()
                    canSlide
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val corner = tilePx * 0.18f
            // Drop shadow.
            drawRoundRect(
                color = Color.Black.copy(alpha = 0.35f),
                topLeft = Offset(0f, tilePx * 0.05f),
                size = size,
                cornerRadius = CornerRadius(corner, corner),
            )
            drawGlossyTile(
                topLeft = Offset.Zero,
                size = Size(size.width, size.height * 0.95f),
                color = if (atHome) color else lerp(color, SlideBoardBottom, 0.28f),
                corner = corner,
                rim = if (atHome) Color.White.copy(alpha = 0.75f) else Color.White.copy(alpha = 0.18f),
                rimWidth = if (atHome) tilePx * 0.035f else 2f,
            )
            // A small dot marks a tile that is home, so "in place" never depends on color alone.
            if (atHome) {
                drawCircle(
                    color = SlideInk.copy(alpha = 0.55f),
                    radius = tilePx * 0.045f,
                    center = Offset(size.width * 0.82f, size.height * 0.16f),
                )
            }
        }
        Text(
            "$tile",
            fontSize = (tileDp.value * 0.42f).sp,
            fontWeight = FontWeight.ExtraBold,
            color = SlideInk,
            modifier = Modifier.padding(bottom = 4.dp),
        )
    }
}
