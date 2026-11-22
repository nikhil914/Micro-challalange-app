package com.microchallenge.app.games

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.microchallenge.core.challenges.games.BankShotSession
import com.microchallenge.core.challenges.games.BankShotSession.Companion.BALL_R
import com.microchallenge.core.challenges.games.BankShotSession.Companion.HEIGHT
import com.microchallenge.core.challenges.games.BankShotSession.Companion.HOLE_R
import com.microchallenge.core.challenges.games.BankShotSession.Companion.WIDTH
import kotlin.math.min

/** How long the "Sunk!" banner stays up. */
private const val SUNK_BANNER_MS = 800L

private val Felt = Color(0xFF1B7F4B)
private val FeltDark = Color(0xFF0F5A34)
private val Rail = Color(0xFF6B3E1F)
private val BlockTop = Color(0xFFE69F00)
private val BlockSide = Color(0xFF9C6B00)

@Composable
fun BankShotScreen(session: BankShotSession, elapsedMs: Long, onInput: () -> Unit) {
    val haptics = LocalHapticFeedback.current
    val currentOnInput by rememberUpdatedState(onInput)
    var dragStart by remember(session) { mutableStateOf<Offset?>(null) }
    var dragNow by remember(session) { mutableStateOf<Offset?>(null) }
    var lastBounces by remember(session) { mutableIntStateOf(0) }
    var lastSunk by remember(session) { mutableIntStateOf(0) }

    // Read once per frame so physics advances with the countdown.
    session.advance()
    val t = session.timeS
    val rolling = session.isRolling
    val timeUp = session.isTimeUp
    val justSunk = session.lastSunkMs >= 0 && t * 1000 - session.lastSunkMs < SUNK_BANNER_MS

    LaunchedEffect(session.bounces, session.holesSunk) {
        if (session.holesSunk > lastSunk) {
            lastSunk = session.holesSunk
            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        } else if (session.bounces > lastBounces) {
            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        }
        lastBounces = session.bounces
    }

    val title = when {
        timeUp -> "Time's up"
        justSunk -> "Sunk!"
        rolling -> "Rolling..."
        else -> "Drag back to aim"
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            title,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = if (justSunk && !timeUp) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.semantics { heading(); liveRegion = LiveRegionMode.Polite },
        )
        Text(
            "Holes ${session.holesSunk} · Shots ${session.shots}",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(12.dp))
        Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
            Canvas(
                Modifier
                    .aspectRatio(WIDTH / HEIGHT)
                    .semantics {
                        contentDescription = "Mini golf course. Drag from the ball away from the hole, then release to shoot."
                    }
                    .pointerInput(session) {
                        detectDragGestures(
                            onDragStart = { dragStart = it; dragNow = it },
                            onDrag = { change, _ -> dragNow = change.position },
                            onDragCancel = { dragStart = null; dragNow = null },
                            onDragEnd = {
                                val s = dragStart; val e = dragNow
                                if (s != null && e != null) {
                                    val k = WIDTH / size.width
                                    session.shoot((e.x - s.x) * k, (e.y - s.y) * k)
                                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                }
                                dragStart = null; dragNow = null
                                currentOnInput()
                            },
                        )
                    },
            ) {
                val k = size.width / WIDTH
                fun w(x: Float, y: Float) = Offset(x * k, y * k)
                val rail = 6.dp.toPx()

                // Table felt with a soft vignette.
                drawRect(Brush.radialGradient(listOf(Felt, FeltDark), center = center, radius = size.maxDimension * 0.7f))
                // Subtle mowing stripes.
                val stripe = size.height / 10f
                for (i in 0 until 10 step 2) {
                    drawRect(Color.White.copy(alpha = 0.04f), topLeft = Offset(0f, i * stripe), size = Size(size.width, stripe))
                }
                drawRect(Rail, style = Stroke(width = rail * 2))

                // Hole with flag.
                val hole = w(session.holeX, session.holeY)
                drawCircle(Color.Black.copy(alpha = 0.25f), radius = HOLE_R * k * 1.35f, center = hole)
                drawCircle(Color(0xFF111111), radius = HOLE_R * k, center = hole)
                val pole = hole + Offset(0f, -HOLE_R * k * 3.2f)
                drawLine(Color.White, hole, pole, strokeWidth = 2.dp.toPx())
                val wave = kotlin.math.sin(t * 6f) * 3.dp.toPx()
                val flag = androidx.compose.ui.graphics.Path().apply {
                    moveTo(pole.x, pole.y)
                    lineTo(pole.x + 22.dp.toPx(), pole.y + 7.dp.toPx() + wave)
                    lineTo(pole.x, pole.y + 14.dp.toPx())
                    close()
                }
                drawPath(flag, Color(0xFFD55E00))

                // Blocks with a 3D lip.
                for (b in session.blocks) {
                    val tl = w(b.x(t), b.y)
                    val sz = Size(b.w * k, b.h * k)
                    val lip = 4.dp.toPx()
                    drawRoundRect(Color.Black.copy(alpha = 0.25f), tl + Offset(lip, lip), sz, CornerRadius(lip * 1.5f))
                    drawRoundRect(BlockSide, tl + Offset(0f, lip * 0.6f), sz, CornerRadius(lip * 1.5f))
                    drawRoundRect(
                        Brush.verticalGradient(listOf(Color(0xFFFFC94D), BlockTop), startY = tl.y, endY = tl.y + sz.height),
                        tl, sz, CornerRadius(lip * 1.5f),
                    )
                }

                // Aim guide: dotted line in the shot direction, length by power.
                val ds = dragStart; val dn = dragNow
                val ball = w(session.ballX, session.ballY)
                if (ds != null && dn != null && !rolling && !timeUp) {
                    val pullX = (dn.x - ds.x) / k
                    val pullY = (dn.y - ds.y) / k
                    val (vx, vy) = session.aimVelocity(pullX, pullY)
                    val reach = 0.09f
                    val end = ball + Offset(vx * reach * k, vy * reach * k)
                    val power = min(1f, kotlin.math.hypot(pullX, pullY) / BankShotSession.MAX_PULL)
                    drawLine(
                        Color.White.copy(alpha = 0.85f), ball, end,
                        strokeWidth = 4.dp.toPx(), cap = StrokeCap.Round,
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(2.dp.toPx(), 10.dp.toPx())),
                    )
                    drawCircle(
                        Color(0xFFF0E442).copy(alpha = 0.35f + 0.5f * power),
                        radius = BALL_R * k * (1.4f + power),
                        center = ball, style = Stroke(width = 3.dp.toPx()),
                    )
                }

                // Ball: shadow, body, highlight.
                drawCircle(Color.Black.copy(alpha = 0.3f), radius = BALL_R * k, center = ball + Offset(3.dp.toPx(), 4.dp.toPx()))
                drawCircle(
                    Brush.radialGradient(
                        listOf(Color.White, Color(0xFFDDDDDD), Color(0xFF9E9E9E)),
                        center = ball + Offset(-BALL_R * k * 0.35f, -BALL_R * k * 0.35f),
                        radius = BALL_R * k * 1.3f,
                    ),
                    radius = BALL_R * k, center = ball,
                )
            }
        }
    }
}
