package com.microchallenge.app.ui.design

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

private class Piece(
    val angle: Float, val speed: Float, val spin: Float, val color: Color,
    val w: Float, val h: Float, val wobble: Float,
)

private val confettiColors = listOf(
    Palette.Xp, Palette.Streak, Color(0xFFEC4899), Palette.Primary, Palette.Accent, Palette.Correct, Color.White,
)

/**
 * One-shot confetti burst from the upper middle of its bounds, drawn on a Canvas
 * (no image assets). Changing [key] fires it again. Draws nothing when motion is reduced.
 */
@Composable
fun ConfettiBurst(key: Any, modifier: Modifier = Modifier, pieces: Int = 90, durationMs: Long = 2_400L) {
    if (LocalReduceMotion.current) return
    val parts = remember(key) {
        val rnd = Random(key.hashCode())
        List(pieces) {
            Piece(
                angle = (-PI / 2 + (rnd.nextFloat() - 0.5f) * PI * 1.1f).toFloat(),
                speed = 0.55f + rnd.nextFloat() * 0.9f,
                spin = (rnd.nextFloat() - 0.5f) * 900f,
                color = confettiColors[rnd.nextInt(confettiColors.size)],
                w = 6f + rnd.nextFloat() * 6f,
                h = 10f + rnd.nextFloat() * 8f,
                wobble = rnd.nextFloat() * 6f,
            )
        }
    }
    var t by remember(key) { mutableFloatStateOf(0f) }
    LaunchedEffect(key) {
        val start = withFrameNanos { it }
        while (t < 1f) {
            withFrameNanos { now -> t = ((now - start) / 1_000_000f / durationMs).coerceAtMost(1f) }
        }
    }
    if (t >= 1f) return
    Canvas(modifier.fillMaxSize()) {
        val origin = Offset(size.width / 2f, size.height * 0.32f)
        val secs = t * durationMs / 1000f
        val gravity = size.height * 0.9f
        val fade = if (t > 0.7f) (1f - t) / 0.3f else 1f
        for (p in parts) {
            val v = size.height * 0.9f * p.speed
            val x = origin.x + cos(p.angle) * v * secs + sin(secs * p.wobble) * 12f
            val y = origin.y + sin(p.angle) * v * secs + gravity * secs * secs * 0.6f
            val density = this.density
            rotate(p.spin * secs, Offset(x, y)) {
                drawRect(
                    p.color.copy(alpha = fade),
                    topLeft = Offset(x - p.w * density / 2, y - p.h * density / 2),
                    size = Size(p.w * density, p.h * density * (0.4f + 0.6f * kotlin.math.abs(cos(secs * 8f + p.wobble)))),
                )
            }
        }
    }
}
