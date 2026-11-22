package com.microchallenge.app.games

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.microchallenge.core.challenges.games.NeonDashSession
import com.microchallenge.core.challenges.games.NeonDashSession.Kind
import com.microchallenge.core.challenges.games.NeonDashSession.Outcome
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

private val SkyTop = Color(0xFF070420)
private val SkyMid = Color(0xFF2B0B4D)
private val SkyHorizon = Color(0xFFB0246E)
private val SunTop = Color(0xFFFFE66D)
private val SunBottom = Color(0xFFFF3D8B)
private val GridCyan = Color(0xFF39F3FF)
private val GridPink = Color(0xFFFF4FD8)
private val BarrierHot = Color(0xFFFF3B5C)
private val BarrierCore = Color(0xFFFFB3C1)
private val OrbGlow = Color(0xFF6DFFB3)
private val ShipBody = Color(0xFFE8F7FF)
private val ShipAccent = Color(0xFF39F3FF)
private val Flame = Color(0xFFFF9F40)

/** Horizon line as a fraction of height, and where the ship rides. */
private const val HORIZON = 0.36f
private const val PLAYER_LINE = 0.84f

private class Particle(
    var x: Float, var y: Float,
    var vx: Float, var vy: Float,
    var life: Float, val maxLife: Float,
    val color: Color, val size: Float,
)

/** Mutable per-run effect state. Not Compose state: the host recomposes every frame anyway. */
private class DashFx {
    val particles = ArrayList<Particle>(256)
    val rng = Random(42)
    var lastFrameMs = -1L
    var seenCrashes = 0
    var seenOrbs = 0
    var distance = 0f
    val stars = List(70) { Triple(rng.nextFloat(), rng.nextFloat() * HORIZON, rng.nextFloat()) }
    val skyline = List(24) { rng.nextFloat() * 0.6f + 0.25f }
}

@Composable
fun NeonDashScreen(session: NeonDashSession, elapsedMs: Long, onInput: () -> Unit) {
    val haptics = LocalHapticFeedback.current
    val currentOnInput by rememberUpdatedState(onInput)
    val fx = remember(session) { DashFx() }

    session.step()
    val now = session.nowMs()

    val laneAnim by animateFloatAsState(
        targetValue = session.lane.toFloat(),
        animationSpec = spring(dampingRatio = 0.62f, stiffness = Spring.StiffnessMediumLow),
        label = "lane",
    )

    // Crash: shake + red flash. Orb: green pulse.
    val shake = remember(session) { Animatable(0f) }
    val flash = remember(session) { Animatable(0f) }
    val orbPulse = remember(session) { Animatable(0f) }
    LaunchedEffect(session.crashes) {
        if (session.crashes > 0) {
            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            flash.snapTo(1f)
            for (x in listOf(22f, -18f, 13f, -8f, 4f, 0f)) shake.animateTo(x, tween(40))
        }
    }
    LaunchedEffect(session.crashes) {
        if (session.crashes > 0) flash.animateTo(0f, tween(420))
    }
    LaunchedEffect(session.orbs) {
        if (session.orbs > 0) {
            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            orbPulse.snapTo(1f)
            orbPulse.animateTo(0f, tween(380))
        }
    }

    fun move(dir: Int) {
        if (session.move(dir)) haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        currentOnInput()
    }

    // Advance particles and scroll distance by frame time.
    val dt = if (fx.lastFrameMs < 0) 0f else ((elapsedMs - fx.lastFrameMs).coerceIn(0L, 50L)) / 1000f
    fx.lastFrameMs = elapsedMs
    val speed = session.speedFactor(now)
    if (!session.isComplete) fx.distance += dt * speed

    Column(Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 4.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                (0 until NeonDashSession.MAX_LIVES).joinToString(" ") { if (it < session.lives) "♥" else "♡" },
                color = BarrierHot,
                fontSize = 22.sp,
                modifier = Modifier.semantics { contentDescription = "${session.lives} lives left" },
            )
            Spacer(Modifier.weight(1f))
            Text(
                "◆ ${session.orbs}   ✓ ${session.dodged}",
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.semantics {
                    contentDescription = "${session.orbs} orbs, ${session.dodged} dodged"
                    liveRegion = LiveRegionMode.Polite
                },
            )
        }
        Spacer(Modifier.height(8.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .weight(1f)
                .graphicsLayer { translationX = shake.value }
                .clip(RoundedCornerShape(24.dp))
                .pointerInput(session) {
                    val swipe = 36.dp.toPx()
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        var moved = false
                        val startX = down.position.x
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            val dx = change.position.x - startX
                            if (!moved && abs(dx) > swipe) {
                                moved = true
                                move(if (dx > 0) 1 else -1)
                                change.consume()
                            }
                            if (!change.pressed) break
                        }
                        // A tap (no swipe) steers toward the tapped half.
                        if (!moved) move(if (startX < size.width / 2f) -1 else 1)
                    }
                }
                .semantics {
                    contentDescription = "Lane ${session.lane + 1} of ${NeonDashSession.LANES}"
                    customActions = listOf(
                        CustomAccessibilityAction("Move left") { move(-1); true },
                        CustomAccessibilityAction("Move right") { move(1); true },
                    )
                },
        ) {
            Canvas(Modifier.fillMaxSize()) {
                val w = size.width
                val h = size.height
                val horizonY = h * HORIZON
                val playerY = h * PLAYER_LINE
                val vanishX = w / 2f
                // Camera leans with the ship: far layers move less than near ones.
                val lean = (laneAnim - 1f)

                drawSky(fx, horizonY, lean, now)
                drawRoad(horizonY, vanishX, lean, fx.distance)

                fun depth(p: Float): Float = p * p
                fun roadHalf(p: Float): Float = w * (0.06f + 0.5f * depth(p))
                fun laneX(lane: Float, p: Float): Float =
                    vanishX - lean * w * 0.06f * depth(p) + (lane - 1f) * roadHalf(p) * 2f / 3f
                fun yAt(p: Float): Float = horizonY + (playerY - horizonY) * depth(p)

                // Items, far to near.
                val visible = session.items
                    .map { it to session.progressOf(it, now) }
                    .filter { (item, p) ->
                        val o = session.outcomeOf(item)
                        p in 0f..1.25f && o != Outcome.CRASHED && o != Outcome.COLLECTED
                    }
                    .sortedBy { it.second }
                for ((item, p) in visible) {
                    val x = laneX(item.lane.toFloat(), p)
                    val y = yAt(p)
                    val scale = 0.12f + 0.88f * depth(p)
                    val fade = if (p > 1f) (1f - (p - 1f) / 0.25f).coerceIn(0f, 1f) else (p / 0.15f).coerceIn(0f, 1f)
                    when (item.kind) {
                        Kind.BARRIER -> drawBarrier(x, y, roadHalf(p) * 2f / 3f * 0.82f, 54.dp.toPx() * scale, fade, now)
                        Kind.ORB -> drawOrb(x, y - 22.dp.toPx() * scale, 15.dp.toPx() * scale, fade, now)
                    }
                }

                // Ship.
                val shipX = laneX(laneAnim, 1f)
                val tilt = (laneAnim - session.lane) * -28f
                val bob = sin(now / 140f) * 2.dp.toPx()
                val shipW = 46.dp.toPx()

                // Spawn effects.
                val r = fx.rng
                if (!session.isComplete) {
                    repeat(3) {
                        fx.particles += Particle(
                            shipX + (r.nextFloat() - 0.5f) * shipW * 0.35f, playerY + shipW * 0.3f + bob,
                            (r.nextFloat() - 0.5f) * 40f, 180f + r.nextFloat() * 160f,
                            0f, 0.25f + r.nextFloat() * 0.2f,
                            if (r.nextBoolean()) Flame else SunTop, 3.dp.toPx() + r.nextFloat() * 3.dp.toPx(),
                        )
                    }
                    if (r.nextFloat() < 0.5f * speed) {
                        // Speed streaks along the road edges.
                        val side = if (r.nextBoolean()) -1f else 1f
                        fx.particles += Particle(
                            vanishX + side * w * 0.08f, horizonY + 4f,
                            side * w * 0.9f, h * 1.1f,
                            0f, 0.6f, GridCyan.copy(alpha = 0.7f), 2.dp.toPx(),
                        )
                    }
                }
                if (session.crashes > fx.seenCrashes) {
                    fx.seenCrashes = session.crashes
                    repeat(46) {
                        val a = r.nextFloat() * 2f * PI.toFloat()
                        val v = 160f + r.nextFloat() * 520f
                        fx.particles += Particle(
                            shipX, playerY - shipW * 0.2f, cos(a) * v, sin(a) * v - 120f,
                            0f, 0.45f + r.nextFloat() * 0.4f,
                            listOf(BarrierHot, Flame, SunTop, Color.White)[r.nextInt(4)], 3.dp.toPx() + r.nextFloat() * 4.dp.toPx(),
                        )
                    }
                }
                if (session.orbs > fx.seenOrbs) {
                    fx.seenOrbs = session.orbs
                    repeat(28) {
                        val a = r.nextFloat() * 2f * PI.toFloat()
                        val v = 90f + r.nextFloat() * 300f
                        fx.particles += Particle(
                            shipX, playerY - shipW * 0.5f, cos(a) * v, sin(a) * v - 200f,
                            0f, 0.4f + r.nextFloat() * 0.3f,
                            if (r.nextBoolean()) OrbGlow else Color.White, 2.dp.toPx() + r.nextFloat() * 3.dp.toPx(),
                        )
                    }
                }
                drawParticles(fx, dt)

                drawShip(shipX, playerY + bob, shipW, tilt, orbPulse.value, session.lives > 0)

                // Full-screen hit flash and orb pulse vignette.
                if (flash.value > 0f) drawRect(BarrierHot.copy(alpha = 0.35f * flash.value))
                if (orbPulse.value > 0f) {
                    drawRect(
                        Brush.radialGradient(
                            listOf(Color.Transparent, OrbGlow.copy(alpha = 0.25f * orbPulse.value)),
                            center = Offset(w / 2f, h / 2f), radius = maxOf(w, h) * 0.75f,
                        ),
                    )
                }
            }
            if (session.isComplete && session.lives <= 0) {
                Text(
                    "CRASHED",
                    fontSize = 40.sp,
                    fontWeight = FontWeight.Black,
                    color = Color.White,
                    modifier = Modifier.align(Alignment.Center),
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("◀ tap or swipe", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
            Text("tap or swipe ▶", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
        }
    }
}

private fun DrawScope.drawSky(fx: DashFx, horizonY: Float, lean: Float, now: Long) {
    val w = size.width
    drawRect(
        Brush.verticalGradient(listOf(SkyTop, SkyMid, SkyHorizon), startY = 0f, endY = horizonY),
        size = Size(w, horizonY + 1f),
    )
    // Layer 1: twinkling stars, barely move.
    for ((sx, sy, phase) in fx.stars) {
        val tw = 0.4f + 0.6f * ((sin(now / 300f + phase * 20f) + 1f) / 2f)
        drawCircle(
            Color.White.copy(alpha = tw * (1f - sy / HORIZON * 0.8f)),
            radius = (0.6f + phase * 1.4f).dp.toPx(),
            center = Offset(((sx * w - lean * w * 0.01f) % w + w) % w, sy * size.height),
        )
    }
    // Layer 2: striped neon sun.
    val sunR = w * 0.2f
    val sunC = Offset(w / 2f - lean * w * 0.02f, horizonY - sunR * 0.15f)
    clipRect(bottom = horizonY) {
        drawCircle(Brush.radialGradient(listOf(SunBottom.copy(alpha = 0.55f), Color.Transparent), sunC, sunR * 1.9f), sunR * 1.9f, sunC)
        drawCircle(Brush.verticalGradient(listOf(SunTop, SunBottom), sunC.y - sunR, sunC.y + sunR), sunR, sunC)
        for (i in 0 until 6) {
            val gap = (i + 1) * sunR * 0.06f
            val y = sunC.y + sunR * 0.05f + i * sunR * 0.17f
            drawRect(SkyHorizon, Offset(sunC.x - sunR, y), Size(sunR * 2f, gap * 0.5f))
        }
    }
    // Layer 3: city skyline silhouette, moves more than the sun.
    val n = fx.skyline.size
    val bw = w * 1.2f / n
    val shift = -lean * w * 0.05f - w * 0.1f
    for (i in 0 until n) {
        val bh = horizonY * 0.28f * fx.skyline[i]
        val x = shift + i * bw
        drawRect(Color(0xFF14082B), Offset(x, horizonY - bh), Size(bw * 0.92f, bh))
        // Window lights.
        if (i % 3 == 0) drawRect(GridPink.copy(alpha = 0.55f), Offset(x + bw * 0.3f, horizonY - bh * 0.7f), Size(bw * 0.15f, bw * 0.15f))
        if (i % 4 == 1) drawRect(GridCyan.copy(alpha = 0.5f), Offset(x + bw * 0.55f, horizonY - bh * 0.45f), Size(bw * 0.15f, bw * 0.15f))
    }
    drawLine(GridPink, Offset(0f, horizonY), Offset(w, horizonY), 2.dp.toPx())
}

private fun DrawScope.drawRoad(horizonY: Float, vanishX: Float, lean: Float, distance: Float) {
    val w = size.width
    val h = size.height
    // Ground.
    drawRect(
        Brush.verticalGradient(listOf(Color(0xFF1A0633), Color(0xFF05020D)), startY = horizonY, endY = h),
        topLeft = Offset(0f, horizonY), size = Size(w, h - horizonY),
    )
    val bottomShift = -lean * w * 0.06f * 1.4f
    // Road surface as a trapezoid.
    val road = Path().apply {
        moveTo(vanishX - w * 0.06f, horizonY)
        lineTo(vanishX + w * 0.06f, horizonY)
        lineTo(vanishX + bottomShift + w * 0.8f, h)
        lineTo(vanishX + bottomShift - w * 0.8f, h)
        close()
    }
    drawPath(road, Brush.verticalGradient(listOf(Color(0xFF2A0C52), Color(0xFF0D0420)), horizonY, h))
    // Horizontal grid lines rushing toward the viewer.
    val lines = 12
    val phase = (distance * 2.2f) % 1f
    for (i in 0 until lines) {
        val t = (i + phase) / lines
        val y = horizonY + (h - horizonY) * t * t
        drawLine(GridPink.copy(alpha = 0.15f + 0.6f * t), Offset(0f, y), Offset(w, y), (0.5f + 2.5f * t).dp.toPx())
    }
    // Vertical grid rays across the ground plane.
    for (k in -8..8) {
        val bx = vanishX + bottomShift + k * w * 0.2f
        drawLine(GridCyan.copy(alpha = 0.18f), Offset(vanishX + k * w * 0.015f, horizonY), Offset(bx, h), 1.dp.toPx())
    }
    // Road edges and lane dividers (glow pass + core pass).
    for (edge in listOf(-3, -1, 1, 3)) {
        val top = Offset(vanishX + edge * w * 0.02f, horizonY)
        val bottom = Offset(vanishX + bottomShift + edge * w * 0.8f / 3f, h)
        val isEdge = abs(edge) == 3
        val c = if (isEdge) GridCyan else GridCyan.copy(alpha = 0.6f)
        drawLine(c.copy(alpha = 0.25f), top, bottom, (if (isEdge) 10f else 6f).dp.toPx(), StrokeCap.Round)
        drawLine(c, top, bottom, (if (isEdge) 2.5f else 1.5f).dp.toPx(), StrokeCap.Round)
    }
}

private fun DrawScope.drawBarrier(cx: Float, baseY: Float, halfW: Float, height: Float, alpha: Float, now: Long) {
    val pulse = 0.75f + 0.25f * sin(now / 90f)
    val left = cx - halfW
    val top = baseY - height
    val corner = CornerRadius(height * 0.18f)
    // Outer glow.
    drawRoundRect(
        BarrierHot.copy(alpha = 0.28f * alpha * pulse),
        Offset(left - height * 0.18f, top - height * 0.18f),
        Size(halfW * 2f + height * 0.36f, height * 1.36f),
        CornerRadius(height * 0.35f),
    )
    drawRoundRect(
        Brush.verticalGradient(listOf(BarrierCore, BarrierHot, Color(0xFF7A0E2A)), top, baseY),
        Offset(left, top), Size(halfW * 2f, height), corner, alpha = alpha,
    )
    // Hazard stripes.
    clipRect(left, top, left + halfW * 2f, baseY) {
        val stripe = height * 0.5f
        var x = left - height
        while (x < left + halfW * 2f) {
            val p = Path().apply {
                moveTo(x, baseY); lineTo(x + stripe * 0.5f, baseY)
                lineTo(x + stripe * 0.5f + height, top); lineTo(x + height, top); close()
            }
            drawPath(p, Color.Black.copy(alpha = 0.22f * alpha))
            x += stripe
        }
    }
    drawRoundRect(Color.White.copy(alpha = 0.8f * alpha), Offset(left, top), Size(halfW * 2f, height), corner,
        style = Stroke(1.5.dp.toPx()))
}

private fun DrawScope.drawOrb(cx: Float, cy: Float, r: Float, alpha: Float, now: Long) {
    val pulse = 1f + 0.12f * sin(now / 110f)
    val c = Offset(cx, cy)
    drawCircle(Brush.radialGradient(listOf(OrbGlow.copy(alpha = 0.55f * alpha), Color.Transparent), c, r * 2.6f * pulse), r * 2.6f * pulse, c)
    drawCircle(Brush.radialGradient(listOf(Color.White, OrbGlow, Color(0xFF0E8F5A)), Offset(cx - r * 0.3f, cy - r * 0.3f), r * 1.2f), r * pulse, c, alpha = alpha)
    // Orbiting spark.
    val a = now / 160f
    drawCircle(Color.White.copy(alpha = alpha), r * 0.18f, Offset(cx + cos(a) * r * 1.5f, cy + sin(a) * r * 0.6f))
}

private fun DrawScope.drawParticles(fx: DashFx, dt: Float) {
    val it = fx.particles.iterator()
    while (it.hasNext()) {
        val p = it.next()
        p.life += dt
        if (p.life >= p.maxLife || fx.particles.size > 400) { it.remove(); continue }
        p.x += p.vx * dt
        p.y += p.vy * dt
        p.vy += 260f * dt
        val k = 1f - p.life / p.maxLife
        drawCircle(p.color.copy(alpha = p.color.alpha * k), p.size * (0.4f + 0.6f * k), Offset(p.x, p.y), blendMode = BlendMode.Plus)
    }
}

private fun DrawScope.drawShip(cx: Float, cy: Float, w: Float, tilt: Float, orbPulse: Float, alive: Boolean) {
    if (!alive) return
    val h = w * 0.9f
    // Ground glow under the hull.
    drawOval(
        Brush.radialGradient(listOf(ShipAccent.copy(alpha = 0.45f + 0.4f * orbPulse), Color.Transparent), Offset(cx, cy + h * 0.35f), w),
        Offset(cx - w, cy + h * 0.1f), Size(w * 2f, h * 0.5f),
    )
    translate(cx, cy) {
        rotate(tilt, Offset.Zero) {
            val hull = Path().apply {
                moveTo(0f, -h * 0.75f)
                lineTo(w * 0.5f, h * 0.3f)
                lineTo(w * 0.18f, h * 0.18f)
                lineTo(0f, h * 0.3f)
                lineTo(-w * 0.18f, h * 0.18f)
                lineTo(-w * 0.5f, h * 0.3f)
                close()
            }
            drawPath(hull, ShipAccent.copy(alpha = 0.35f), style = Stroke(8.dp.toPx()))
            drawPath(hull, Brush.verticalGradient(listOf(Color.White, ShipBody, Color(0xFF7A8BB5)), -h * 0.75f, h * 0.3f))
            drawPath(hull, ShipAccent, style = Stroke(1.5.dp.toPx()))
            // Cockpit.
            drawOval(
                Brush.verticalGradient(listOf(GridPink, Color(0xFF5B1A8C)), -h * 0.3f, h * 0.05f),
                Offset(-w * 0.09f, -h * 0.3f), Size(w * 0.18f, h * 0.35f),
            )
            // Wing-tip lights.
            drawCircle(GridPink, 2.5.dp.toPx(), Offset(-w * 0.47f, h * 0.27f))
            drawCircle(GridCyan, 2.5.dp.toPx(), Offset(w * 0.47f, h * 0.27f))
        }
    }
}
