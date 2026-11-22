package com.microchallenge.app.games

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.snap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
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
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.microchallenge.core.challenges.games.PipeFlowSession
import com.microchallenge.core.challenges.games.PipeFlowSession.Companion.E
import com.microchallenge.core.challenges.games.PipeFlowSession.Companion.N
import com.microchallenge.core.challenges.games.PipeFlowSession.Companion.S
import com.microchallenge.core.challenges.games.PipeFlowSession.Companion.W
import com.microchallenge.core.challenges.games.PipeFlowSession.Phase
import com.microchallenge.core.challenges.games.PipeFlowSession.TapResult
import kotlin.math.abs

private val WaterLight = Color(0xFF8FF6FF)
private val Water = Color(0xFF22C7FF)
private val WaterDeep = Color(0xFF1565FF)
private val PipeLight = Color(0xFF8C93AD)
private val PipeDark = Color(0xFF3B4058)
private val TileTop = Color(0xFF23283F)
private val TileBottom = Color(0xFF151827)

@Composable
fun PipeFlowScreen(session: PipeFlowSession, elapsedMs: Long, onInput: () -> Unit) {
    val haptics = LocalHapticFeedback.current
    val currentOnInput by rememberUpdatedState(onInput)

    val phase = session.phase
    val size = session.size
    val flowing = phase == Phase.FLOW
    // Wave front sweeps from source to drain while a solved board flows.
    val wave = session.flowProgress() * (session.maxWaterDistance + 2)

    fun onTileTap(cell: Int) {
        when (session.tap(cell)) {
            TapResult.ROTATED -> haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            TapResult.SOLVED -> haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            TapResult.IGNORED -> Unit
        }
        currentOnInput()
    }

    val (title, subtitle) = when (phase) {
        Phase.PLAY -> "Connect the water" to "Boards flowing: ${session.solvedCount}"
        Phase.FLOW -> "Flowing!" to "Next board coming"
        Phase.DONE -> "Time's up" to "${session.solvedCount} boards · ${session.totalMoves} turns"
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            title,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.semantics {
                heading()
                liveRegion = LiveRegionMode.Polite
            },
        )
        Text(subtitle, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(16.dp))
        Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
            Row(Modifier.aspectRatio(1.18f), verticalAlignment = Alignment.CenterVertically) {
                EdgePort(rows = size, activeRow = session.sourceRow, isSource = true, lit = true, elapsedMs = elapsedMs)
                Column(
                    Modifier
                        .weight(1f)
                        .aspectRatio(1f)
                        .shadow(if (flowing) 24.dp else 6.dp, RoundedCornerShape(22.dp), ambientColor = Water, spotColor = Water)
                        .clip(RoundedCornerShape(22.dp))
                        .background(Brush.verticalGradient(listOf(Color(0xFF0E1226), Color(0xFF05070F))))
                        .border(1.dp, if (flowing) WaterLight.copy(alpha = 0.7f) else Color.White.copy(alpha = 0.06f), RoundedCornerShape(22.dp))
                        .padding(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    for (row in 0 until size) {
                        Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            for (col in 0 until size) {
                                val cell = row * size + col
                                val dist = session.waterDistance(cell)
                                // During the flow, tiles brighten as the wave passes them.
                                val surge = if (flowing && dist >= 0) (1f - abs(wave - dist) / 1.5f).coerceIn(0f, 1f) else 0f
                                PipeTile(
                                    mask = session.mask(cell),
                                    turns = session.turns(cell),
                                    boardId = session.boardId,
                                    wet = dist >= 0,
                                    from = session.waterFrom(cell),
                                    surge = surge,
                                    elapsedMs = elapsedMs,
                                    enabled = phase == Phase.PLAY,
                                    label = "Row ${row + 1}, column ${col + 1}",
                                    onTap = { onTileTap(cell) },
                                    modifier = Modifier.weight(1f).fillMaxHeight(),
                                )
                            }
                        }
                    }
                }
                EdgePort(rows = size, activeRow = session.drainRow, isSource = false, lit = session.isFlowing, elapsedMs = elapsedMs)
            }
        }
    }
}

/** Source tap (left) or drain (right), aligned with its row of the grid. */
@Composable
private fun EdgePort(rows: Int, activeRow: Int, isSource: Boolean, lit: Boolean, elapsedMs: Long) {
    Column(
        Modifier.width(26.dp).fillMaxHeight().padding(vertical = 8.dp).clearAndSetSemantics { },
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        for (row in 0 until rows) {
            Box(Modifier.weight(1f).fillMaxWidth()) {
                if (row == activeRow) {
                    Canvas(Modifier.fillMaxSize()) {
                        val cy = size.height / 2
                        val pulse = 0.5f + 0.5f * kotlin.math.sin(elapsedMs / 180f)
                        val color = if (lit) Water else PipeLight
                        // Stub pipe joining the grid edge.
                        val x0 = if (isSource) size.width * 0.35f else 0f
                        val x1 = if (isSource) size.width else size.width * 0.65f
                        drawLine(PipeDark, Offset(x0, cy), Offset(x1, cy), size.width * 0.42f, StrokeCap.Butt)
                        drawLine(color, Offset(x0, cy), Offset(x1, cy), size.width * 0.22f, StrokeCap.Butt)
                        val c = Offset(if (isSource) size.width * 0.35f else size.width * 0.65f, cy)
                        val r = size.width * 0.34f
                        if (lit) {
                            drawCircle(
                                Brush.radialGradient(listOf(WaterLight.copy(alpha = 0.6f * pulse), Color.Transparent), c, r * 2.2f),
                                r * 2.2f,
                                c,
                            )
                        }
                        drawCircle(if (lit) Brush.radialGradient(listOf(WaterLight, WaterDeep), c, r) else Brush.radialGradient(listOf(PipeLight, PipeDark), c, r), r, c)
                        if (!isSource) drawCircle(Color.Black.copy(alpha = 0.55f), r * 0.45f, c)
                    }
                }
            }
        }
    }
}

@Composable
private fun PipeTile(
    mask: Int,
    turns: Int,
    boardId: Int,
    wet: Boolean,
    from: Int,
    surge: Float,
    elapsedMs: Long,
    enabled: Boolean,
    label: String,
    onTap: () -> Unit,
    modifier: Modifier,
) {
    // Draw the current openings, and spin from the previous angle into place.
    val target = boardId * 3600f + turns * 90f
    val angle by animateFloatAsState(
        targetValue = target,
        animationSpec = if (turns == 0) snap() else spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessMedium),
        label = "turn",
    )
    val lag = (angle - target).coerceIn(-90f, 90f)
    val wetness by animateFloatAsState(if (wet) 1f else 0f, spring(stiffness = Spring.StiffnessMediumLow), label = "wet")
    val shape = RoundedCornerShape(12.dp)
    val currentEnabled by rememberUpdatedState(enabled)
    val currentOnTap by rememberUpdatedState(onTap)

    Box(
        modifier = modifier
            .graphicsLayer {
                val s = 1f + 0.06f * surge
                scaleX = s
                scaleY = s
            }
            .clip(shape)
            .background(Brush.linearGradient(listOf(TileTop, TileBottom)))
            .border(1.dp, Color.White.copy(alpha = 0.07f + 0.4f * surge), shape)
            .pointerInput(boardId) {
                detectTapGestures(onPress = { if (currentEnabled) currentOnTap() })
            }
            .clearAndSetSemantics {
                role = Role.Button
                contentDescription = "$label, ${shapeName(mask)}"
                stateDescription = if (wet) "water flowing" else "dry"
                if (!enabled) disabled()
                onClick(label = "Turn pipe") {
                    if (currentEnabled) currentOnTap()
                    currentEnabled
                }
            },
    ) {
        Canvas(Modifier.fillMaxSize().graphicsLayer { rotationZ = lag }) {
            drawPipe(mask, wetness, from, surge, elapsedMs)
        }
    }
}

private fun sideOffset(side: Int, w: Float, h: Float): Offset = when (side) {
    N -> Offset(w / 2, 0f)
    E -> Offset(w, h / 2)
    S -> Offset(w / 2, h)
    else -> Offset(0f, h / 2)
}

private fun DrawScope.drawPipe(mask: Int, wetness: Float, from: Int, surge: Float, elapsedMs: Long) {
    val w = size.width
    val h = size.height
    val center = Offset(w / 2, h / 2)
    val outer = w * 0.36f
    val inner = w * 0.2f
    val sides = intArrayOf(N, E, S, W).filter { mask and it != 0 }

    // Metal casing, a highlight seam, then the channel.
    for (side in sides) drawLine(PipeDark, center, sideOffset(side, w, h), outer, StrokeCap.Butt)
    drawCircle(PipeDark, outer / 2, center)
    for (side in sides) drawLine(PipeLight.copy(alpha = 0.5f), center, sideOffset(side, w, h), outer * 0.78f, StrokeCap.Butt)
    drawCircle(PipeLight.copy(alpha = 0.5f), outer * 0.39f, center)
    for (side in sides) drawLine(Color(0xFF0A0C16), center, sideOffset(side, w, h), inner, StrokeCap.Butt)
    drawCircle(Color(0xFF0A0C16), inner / 2, center)

    if (wetness > 0f) {
        val water = Water.copy(alpha = wetness)
        for (side in sides) drawLine(water, center, sideOffset(side, w, h), inner, StrokeCap.Butt)
        drawCircle(water, inner / 2, center)
        // Moving bubbles: travel inward on the entry side, outward on the others.
        val dash = w * 0.09f
        val phase = (elapsedMs / 6f) % (dash * 2)
        val effect = PathEffect.dashPathEffect(floatArrayOf(dash, dash), -phase)
        val shine = WaterLight.copy(alpha = wetness * (0.55f + 0.45f * surge))
        for (side in sides) {
            val edge = sideOffset(side, w, h)
            val (a, b) = if (side == from) edge to center else center to edge
            drawLine(shine, a, b, inner * 0.35f, StrokeCap.Round, effect)
        }
        if (surge > 0f) {
            drawCircle(
                Brush.radialGradient(listOf(WaterLight.copy(alpha = 0.5f * surge), Color.Transparent), center, w * 0.6f),
                w * 0.6f,
                center,
            )
        }
    }
}

private fun shapeName(mask: Int): String = when (Integer.bitCount(mask)) {
    1 -> "dead end"
    2 -> if (PipeFlowSession.isStraight(mask)) "straight pipe" else "bend"
    3 -> "T pipe"
    else -> "cross pipe"
}
