package com.microchallenge.app.games

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.microchallenge.core.challenges.games.StackTowerSession
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

/** Sky colours (top, bottom) for low, mid and high towers. */
private val StackSkyLow = listOf(Color(0xFF3A8DDE), Color(0xFFFFD6A5))
private val StackSkyMid = listOf(Color(0xFF41207A), Color(0xFFFF8FB1))
private val StackSkyHigh = listOf(Color(0xFF070A24), Color(0xFF2C3A7A))
private val StackHillFar = Color(0xFF6C8FC7)
private val StackHillNear = Color(0xFF3F6BA8)
private val StackGold = Color(0xFFFFE27A)

/** Floors climbed before the sky reaches night. */
private const val NIGHT_AT_FLOOR = 18f

private class StackPiece(
    val left: Float, val right: Float,
    var y: Float, var vy: Float,
    var rot: Float, val spin: Float,
    val color: Color,
)

private class StackSpark(
    var x: Float, var y: Float,
    var vx: Float, var vy: Float,
    var life: Float, val maxLife: Float,
    val color: Color, val size: Float,
)

/** Per-run effect state. Not Compose state: the host recomposes every frame anyway. */
private class StackFx {
    val rng = Random(7)
    val pieces = ArrayList<StackPiece>()
    val sparks = ArrayList<StackSpark>(200)
    var lastFrameMs = -1L
    var seenDrops = 0
    var camera = 0f
    var perfectAtMs = -10_000L
    var landAtMs = -10_000L
    val stars = List(60) { Triple(rng.nextFloat(), rng.nextFloat(), rng.nextFloat()) }
    val clouds = List(6) { Triple(rng.nextFloat(), rng.nextFloat(), 0.6f + rng.nextFloat() * 0.8f) }
    val hueStart = rng.nextFloat() * 360f
}

/** Block colour for floor [index]: hue walks round the wheel as the tower grows. */
private fun stackColor(fx: StackFx, index: Int): Color =
    Color.hsv((fx.hueStart + index * 11f) % 360f, 0.58f, 0.96f)

@Composable
fun StackTowerScreen(session: StackTowerSession, elapsedMs: Long, onInput: () -> Unit) {
    val haptics = LocalHapticFeedback.current
    val currentOnInput by rememberUpdatedState(onInput)
    val fx = remember(session) { StackFx() }

    val shake = remember(session) { Animatable(0f) }
    val perfectPop = remember(session) { Animatable(0f) }
    LaunchedEffect(session.missed) {
        if (session.missed) {
            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            for (x in listOf(20f, -16f, 11f, -6f, 3f, 0f)) shake.animateTo(x, tween(40))
        }
    }
    LaunchedEffect(session.perfects) {
        if (session.perfects > 0) {
            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            perfectPop.snapTo(1f)
            perfectPop.animateTo(0f, tween(700))
        }
    }

    val handleDrop: () -> Unit = {
        session.drop()
        currentOnInput()
    }
    val dropHandler by rememberUpdatedState(handleDrop)

    val dt = if (fx.lastFrameMs < 0) 0f else (elapsedMs - fx.lastFrameMs).coerceIn(0L, 50L) / 1000f
    fx.lastFrameMs = elapsedMs
    val height = session.height
    val combo = session.combo
    val canDrop = session.canDrop()

    Box(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .graphicsLayer { translationX = shake.value }
            .clip(RoundedCornerShape(24.dp))
            .pointerInput(session) { detectTapGestures(onPress = { dropHandler() }) }
            .semantics {
                role = Role.Button
                contentDescription = "Tower $height floors. Tap to drop the block."
                onClick(label = "Drop block") { dropHandler(); true }
            },
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val bh = min(w * 0.085f, h / 13f)
            val depth = bh * 0.55f
            val baseBottom = h - bh * 1.4f

            // Camera keeps the top of the tower in the lower middle of the view.
            val camTarget = ((height - 4).coerceAtLeast(0)) * bh
            fx.camera += (camTarget - fx.camera) * min(1f, dt * 7f)
            val cam = fx.camera
            fun floorBottom(i: Int): Float = baseBottom - i * bh + cam

            // New drops: spawn sliced pieces and sparks.
            val drop = session.lastDrop
            if (drop != null && drop.index != fx.seenDrops) {
                fx.seenDrops = drop.index
                val color = stackColor(fx, drop.index)
                drop.cut?.let { cut ->
                    val goesRight = cut.center > (drop.placed?.center ?: 0.5)
                    fx.pieces += StackPiece(
                        (cut.left * w).toFloat(), (cut.right * w).toFloat(),
                        floorBottom(drop.index) - cam, 0f, 0f,
                        if (goesRight) 140f else -140f, color,
                    )
                }
                if (drop.placed != null) fx.landAtMs = elapsedMs
                val placed = drop.placed
                if (drop.perfect && placed != null) {
                    fx.perfectAtMs = elapsedMs
                    val y = floorBottom(drop.index) - bh
                    repeat(26) {
                        val fromLeft = it % 2 == 0
                        val x = ((if (fromLeft) placed.left else placed.right) * w).toFloat()
                        fx.sparks += StackSpark(
                            x, y, (if (fromLeft) -1f else 1f) * (80f + fx.rng.nextFloat() * 260f),
                            -120f - fx.rng.nextFloat() * 360f, 0f, 0.5f + fx.rng.nextFloat() * 0.4f,
                            if (it % 3 == 0) Color.White else StackGold, 2.dp.toPx() + fx.rng.nextFloat() * 3.dp.toPx(),
                        )
                    }
                }
            }

            drawStackSky(fx, height, cam, bh, elapsedMs)

            // Hills sink out of view as the camera climbs.
            drawHills(w, h, cam * 0.5f)

            // Ground slab under the base.
            drawRect(
                brush = Brush.verticalGradient(listOf(Color(0xFF2B3550), Color(0xFF151A2A)), startY = baseBottom + cam, endY = h + cam),
                topLeft = Offset(0f, baseBottom + cam),
                size = Size(w, bh * 3f),
            )

            // Tower, bottom to top, skipping floors far below the view.
            val tower = session.tower
            for (i in tower.indices) {
                val bottom = floorBottom(i)
                if (bottom - bh > h + depth) continue
                val b = tower[i]
                val isTop = i == tower.lastIndex
                val land = if (isTop && i > 0) (1f - (elapsedMs - fx.landAtMs) / 220f).coerceIn(0f, 1f) else 0f
                drawBlock3d(
                    (b.left * w).toFloat(), (b.right * w).toFloat(), bottom - bh, bh, depth,
                    stackColor(fx, i), flash = land * 0.6f,
                )
            }

            // Perfect ring around the top block.
            val perfectT = (elapsedMs - fx.perfectAtMs) / 520f
            if (perfectT in 0f..1f && tower.size > 1) {
                val b = tower.last()
                val top = floorBottom(tower.lastIndex) - bh
                val grow = 4.dp.toPx() + perfectT * 22.dp.toPx()
                drawRoundRect(
                    color = Color.White.copy(alpha = 0.9f * (1f - perfectT)),
                    topLeft = Offset((b.left * w).toFloat() - grow, top - grow),
                    size = Size((b.width * w).toFloat() + grow * 2, bh + grow * 2),
                    cornerRadius = CornerRadius(8.dp.toPx()),
                    style = Stroke(width = 3.dp.toPx() * (1f - perfectT) + 1f),
                )
            }

            // Moving block, with its shadow on the tower top to help aim.
            if (canDrop) {
                val cx = session.movingCenter()
                val mw = session.movingWidth
                val l = ((cx - mw / 2) * w).toFloat()
                val r = ((cx + mw / 2) * w).toFloat()
                val topBlock = tower.last()
                val topY = floorBottom(tower.lastIndex) - bh
                val sl = maxOf(l, (topBlock.left * w).toFloat())
                val sr = minOf(r, (topBlock.right * w).toFloat())
                if (sr > sl) {
                    drawPath(
                        topFace(sl, sr, topY, depth),
                        color = Color.Black.copy(alpha = 0.22f),
                    )
                }
                // Hovers a fifth of a block above the tower top, bobbing slightly.
                val hover = bh * 1.2f + sin(elapsedMs / 160f) * 1.5f
                drawBlock3d(l, r, topY - hover, bh, depth, stackColor(fx, tower.size))
            }

            // Falling sliced pieces.
            val itPieces = fx.pieces.iterator()
            while (itPieces.hasNext()) {
                val p = itPieces.next()
                p.vy += 2600f * dt
                p.y += p.vy * dt
                p.rot += p.spin * dt
                val screenY = p.y + cam
                if (screenY - bh > h + 200f) { itPieces.remove(); continue }
                val cx = (p.left + p.right) / 2f
                rotate(p.rot, pivot = Offset(cx, screenY - bh / 2f)) {
                    drawBlock3d(p.left, p.right, screenY - bh, bh, depth, p.color, alpha = 0.95f)
                }
            }

            // Sparks.
            val itSparks = fx.sparks.iterator()
            while (itSparks.hasNext()) {
                val s = itSparks.next()
                s.life += dt
                if (s.life >= s.maxLife) { itSparks.remove(); continue }
                s.vy += 900f * dt
                s.x += s.vx * dt
                s.y += s.vy * dt
                val fade = 1f - s.life / s.maxLife
                drawGlow(Offset(s.x, s.y), s.size * 3f, s.color, 0.5f * fade)
                drawCircle(s.color.copy(alpha = fade), radius = s.size * fade + 1f, center = Offset(s.x, s.y))
            }
        }

        // HUD: floor count, combo and hints.
        Column(
            Modifier.align(Alignment.TopCenter).padding(top = 18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                "$height",
                style = TextStyle(
                    fontSize = 60.sp,
                    fontWeight = FontWeight.Black,
                    color = Color.White,
                    shadow = Shadow(Color.Black.copy(alpha = 0.45f), Offset(0f, 6f), 12f),
                ),
                modifier = Modifier.semantics {
                    contentDescription = "$height floors"
                    liveRegion = LiveRegionMode.Polite
                },
            )
            val pop = perfectPop.value
            Text(
                if (pop > 0f) (if (combo >= 2) "PERFECT x$combo" else "PERFECT") else " ",
                style = TextStyle(
                    fontSize = 24.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = StackGold.copy(alpha = min(1f, pop * 2f)),
                    shadow = Shadow(Color.Black.copy(alpha = 0.4f * pop), Offset(0f, 3f), 8f),
                ),
                modifier = Modifier.graphicsLayer {
                    val s = 1f + 0.35f * easeOutBack(pop)
                    scaleX = s; scaleY = s
                },
            )
        }
        val hint = when {
            session.missed -> "Missed! Tower: $height"
            height == 0 && canDrop -> "Tap to drop"
            else -> null
        }
        if (hint != null) {
            Text(
                hint,
                modifier = Modifier.align(Alignment.Center).padding(top = 80.dp),
                style = TextStyle(
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    shadow = Shadow(Color.Black.copy(alpha = 0.5f), Offset(0f, 3f), 8f),
                ),
            )
        }
    }
}

/** Top face of a block: a parallelogram leaning up and to the right. */
private fun topFace(left: Float, right: Float, top: Float, depth: Float): Path = Path().apply {
    val dx = depth * 0.55f
    moveTo(left, top)
    lineTo(right, top)
    lineTo(right + dx, top - depth * 0.5f)
    lineTo(left + dx, top - depth * 0.5f)
    close()
}

/** A block with a lit top, gradient front and shaded right side, so it reads as 3D. */
private fun DrawScope.drawBlock3d(
    left: Float, right: Float, top: Float, bh: Float, depth: Float,
    color: Color, alpha: Float = 1f, flash: Float = 0f,
) {
    if (right <= left) return
    val dx = depth * 0.55f
    val dy = depth * 0.5f
    val base = lerp(color, Color.White, flash.coerceIn(0f, 1f))
    // Right side.
    val side = Path().apply {
        moveTo(right, top)
        lineTo(right + dx, top - dy)
        lineTo(right + dx, top + bh - dy)
        lineTo(right, top + bh)
        close()
    }
    drawPath(side, brush = Brush.verticalGradient(listOf(lerp(base, Color.Black, 0.3f), lerp(base, Color.Black, 0.5f)), startY = top - dy, endY = top + bh), alpha = alpha)
    // Front.
    drawRect(
        brush = Brush.verticalGradient(listOf(lerp(base, Color.White, 0.12f), lerp(base, Color.Black, 0.22f)), startY = top, endY = top + bh),
        topLeft = Offset(left, top),
        size = Size(right - left, bh),
        alpha = alpha,
    )
    // Thin highlight under the top edge and a dark line at the bottom.
    drawRect(Color.White.copy(alpha = 0.25f * alpha), Offset(left, top), Size(right - left, bh * 0.08f))
    drawRect(Color.Black.copy(alpha = 0.18f * alpha), Offset(left, top + bh * 0.92f), Size(right - left, bh * 0.08f))
    // Top.
    drawPath(
        topFace(left, right, top, depth),
        brush = Brush.horizontalGradient(listOf(lerp(base, Color.White, 0.42f), lerp(base, Color.White, 0.22f)), startX = left, endX = right + dx),
        alpha = alpha,
    )
}

/** Sky that turns from day to dusk to night as the tower grows, with drifting clouds and stars. */
private fun DrawScope.drawStackSky(fx: StackFx, height: Int, cam: Float, bh: Float, timeMs: Long) {
    val w = size.width
    val h = size.height
    val p = (cam / bh / NIGHT_AT_FLOOR).coerceIn(0f, 1f)
    val (topC, botC) = if (p < 0.5f) {
        val t = p / 0.5f
        lerp(StackSkyLow[0], StackSkyMid[0], t) to lerp(StackSkyLow[1], StackSkyMid[1], t)
    } else {
        val t = (p - 0.5f) / 0.5f
        lerp(StackSkyMid[0], StackSkyHigh[0], t) to lerp(StackSkyMid[1], StackSkyHigh[1], t)
    }
    drawRect(Brush.verticalGradient(listOf(topC, botC)))

    // Sun sinking behind the hills early on.
    val sunY = h * 0.55f + cam * 0.35f
    if (sunY < h + 80f) {
        drawGlow(Offset(w * 0.72f, sunY), w * 0.45f, Color(0xFFFFF1C1), 0.5f * (1f - p))
        drawCircle(Color(0xFFFFF4D6).copy(alpha = 1f - p), radius = w * 0.09f, center = Offset(w * 0.72f, sunY))
    }

    // Stars fade in with height and twinkle.
    val starAlpha = ((p - 0.35f) / 0.5f).coerceIn(0f, 1f)
    if (starAlpha > 0f) {
        for ((sx, sy, tw) in fx.stars) {
            val y = (sy * h + cam * 0.08f) % h
            val a = starAlpha * (0.5f + 0.5f * sin(timeMs / 300f + tw * 20f))
            drawCircle(Color.White.copy(alpha = a), radius = 1.2.dp.toPx() + tw * 1.5.dp.toPx(), center = Offset(sx * w, y))
        }
    }

    // Clouds drift sideways and move down slowly as the camera climbs (parallax).
    val cloudAlpha = 0.75f * (1f - p * 0.7f)
    for ((cx, cy, scale) in fx.clouds) {
        val span = w * 1.4f
        val x = ((cx * span + timeMs / 1000f * 14f * scale) % span) - w * 0.2f
        val y = ((cy * h * 0.6f + cam * 0.3f * scale) % (h * 1.2f)) - h * 0.1f
        val r = w * 0.07f * scale
        val c = Color.White.copy(alpha = cloudAlpha)
        drawCircle(c, r, Offset(x, y))
        drawCircle(c, r * 0.8f, Offset(x - r, y + r * 0.25f))
        drawCircle(c, r * 0.85f, Offset(x + r * 1.05f, y + r * 0.2f))
        drawRoundRect(c, Offset(x - r * 1.6f, y + r * 0.1f), Size(r * 3.4f, r * 0.85f), CornerRadius(r * 0.4f))
    }
}

/** Two layers of rolling hills along the bottom, pushed down by [offset]. */
private fun DrawScope.drawHills(w: Float, h: Float, offset: Float) {
    if (offset > h * 0.5f) return
    fun hills(baseY: Float, amp: Float, freq: Float, color: Color) {
        val path = Path().apply {
            moveTo(0f, h + offset)
            var x = 0f
            while (x <= w) {
                lineTo(x, baseY + offset + sin(x / w * freq) * amp)
                x += w / 24f
            }
            lineTo(w, h + offset)
            close()
        }
        drawPath(path, color)
    }
    hills(h * 0.72f, h * 0.035f, 7f, StackHillFar)
    hills(h * 0.8f, h * 0.03f, 11f, StackHillNear)
}
