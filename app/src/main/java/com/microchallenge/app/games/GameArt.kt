package com.microchallenge.app.games

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Hand-built vector art shared by the arcade games (Pair Up, Bubble Sum, Star Catcher,
 * Slide Shift, Weigh In). Everything is drawn with Canvas paths and gradients, so it stays
 * sharp at any size and adds no image assets to the APK. Original art, no third-party assets.
 */
object GameArt {
    const val ICON_STAR = 0
    const val ICON_HEART = 1
    const val ICON_MOON = 2
    const val ICON_BOLT = 3
    const val ICON_GEM = 4
    const val ICON_DROP = 5
    const val ICON_CROWN = 6
    const val ICON_LEAF = 7

    /** One vivid color per icon, readable on the dark arcade background. */
    val iconColors: List<Color> = listOf(
        Color(0xFFFFD740), // star: gold
        Color(0xFFFF5370), // heart: neon red
        Color(0xFFB388FF), // moon: violet
        Color(0xFF00E5FF), // bolt: cyan
        Color(0xFF64FFDA), // gem: aqua
        Color(0xFF448AFF), // drop: blue
        Color(0xFFFFAB40), // crown: orange
        Color(0xFF69F0AE), // leaf: green
    )

    /** Spoken names, so TalkBack users never depend on the drawing. */
    val iconNames: List<String> = listOf("star", "heart", "moon", "bolt", "gem", "drop", "crown", "leaf")

    fun color(icon: Int): Color = iconColors[Math.floorMod(icon, iconColors.size)]
    fun name(icon: Int): String = iconNames[Math.floorMod(icon, iconNames.size)]
}

/** Builds a closed polygon from unit-space points (x, y in -1..1) around [c] with radius [r]. */
private fun polygon(c: Offset, r: Float, vararg pts: Float): Path = Path().apply {
    moveTo(c.x + pts[0] * r, c.y + pts[1] * r)
    var i = 2
    while (i < pts.size) {
        lineTo(c.x + pts[i] * r, c.y + pts[i + 1] * r)
        i += 2
    }
    close()
}

/** Outline of [icon] centred on [c], fitting a circle of radius [r]. */
fun iconPath(icon: Int, c: Offset, r: Float): Path = when (Math.floorMod(icon, GameArt.iconColors.size)) {
    GameArt.ICON_STAR -> Path().apply {
        for (i in 0 until 10) {
            val a = -PI / 2 + i * PI / 5
            val rad = if (i % 2 == 0) r else r * 0.45f
            val x = c.x + (cos(a) * rad).toFloat()
            val y = c.y + (sin(a) * rad).toFloat()
            if (i == 0) moveTo(x, y) else lineTo(x, y)
        }
        close()
    }
    GameArt.ICON_HEART -> Path().apply {
        moveTo(c.x, c.y + 0.85f * r)
        cubicTo(c.x - 1.35f * r, c.y - 0.05f * r, c.x - 0.85f * r, c.y - 1.05f * r, c.x, c.y - 0.42f * r)
        cubicTo(c.x + 0.85f * r, c.y - 1.05f * r, c.x + 1.35f * r, c.y - 0.05f * r, c.x, c.y + 0.85f * r)
        close()
    }
    GameArt.ICON_MOON -> {
        val outer = Path().apply { addOval(Rect(c, r * 0.9f)) }
        val bite = Path().apply { addOval(Rect(Offset(c.x + r * 0.42f, c.y - r * 0.28f), r * 0.75f)) }
        Path().apply { op(outer, bite, PathOperation.Difference) }
    }
    GameArt.ICON_BOLT -> polygon(c, r, 0.2f, -1f, -0.6f, 0.12f, -0.05f, 0.12f, -0.25f, 1f, 0.6f, -0.18f, 0.05f, -0.18f)
    GameArt.ICON_GEM -> polygon(c, r, -0.9f, -0.3f, -0.45f, -0.82f, 0.45f, -0.82f, 0.9f, -0.3f, 0f, 0.95f)
    GameArt.ICON_DROP -> Path().apply {
        moveTo(c.x, c.y - r)
        cubicTo(c.x + 0.25f * r, c.y - 0.5f * r, c.x + 0.78f * r, c.y, c.x + 0.78f * r, c.y + 0.35f * r)
        cubicTo(c.x + 0.78f * r, c.y + 0.8f * r, c.x + 0.4f * r, c.y + r, c.x, c.y + r)
        cubicTo(c.x - 0.4f * r, c.y + r, c.x - 0.78f * r, c.y + 0.8f * r, c.x - 0.78f * r, c.y + 0.35f * r)
        cubicTo(c.x - 0.78f * r, c.y, c.x - 0.25f * r, c.y - 0.5f * r, c.x, c.y - r)
        close()
    }
    GameArt.ICON_CROWN -> polygon(c, r, -0.9f, 0.65f, -0.95f, -0.5f, -0.45f, 0.02f, 0f, -0.82f, 0.45f, 0.02f, 0.95f, -0.5f, 0.9f, 0.65f)
    else -> Path().apply { // leaf
        moveTo(c.x - 0.8f * r, c.y + 0.8f * r)
        cubicTo(c.x - 0.95f * r, c.y - 0.45f * r, c.x - 0.2f * r, c.y - 0.95f * r, c.x + 0.9f * r, c.y - 0.9f * r)
        cubicTo(c.x + 0.95f * r, c.y + 0.2f * r, c.x + 0.45f * r, c.y + 0.95f * r, c.x - 0.8f * r, c.y + 0.8f * r)
        close()
    }
}

/** Draws [icon] with a top-lit gradient fill and a dark rim, so it reads like a glossy sticker. */
fun DrawScope.drawIcon(icon: Int, center: Offset, radius: Float, color: Color = GameArt.color(icon), alpha: Float = 1f) {
    val path = iconPath(icon, center, radius)
    val light = lerp(color, Color.White, 0.55f)
    val dark = lerp(color, Color.Black, 0.35f)
    drawPath(
        path,
        brush = Brush.verticalGradient(listOf(light, color, dark), startY = center.y - radius, endY = center.y + radius),
        alpha = alpha,
    )
    drawPath(path, color = lerp(color, Color.Black, 0.6f), alpha = alpha, style = Stroke(width = radius * 0.09f))
}

/** Soft radial glow, for highlights, selected items and catches. */
fun DrawScope.drawGlow(center: Offset, radius: Float, color: Color, strength: Float = 0.6f) {
    if (radius <= 0f || strength <= 0f) return
    drawCircle(
        brush = Brush.radialGradient(
            listOf(color.copy(alpha = strength.coerceIn(0f, 1f)), Color.Transparent),
            center = center,
            radius = radius,
        ),
        radius = radius,
        center = center,
    )
}

/** Particle burst: a ring and sparks flying out. [progress] runs 0..1. */
fun DrawScope.drawBurst(center: Offset, progress: Float, color: Color, radius: Float, sparks: Int = 12) {
    val p = progress.coerceIn(0f, 1f)
    if (p >= 1f) return
    val fade = 1f - p
    drawCircle(
        color = color.copy(alpha = 0.7f * fade),
        radius = radius * (0.5f + 0.9f * p),
        center = center,
        style = Stroke(width = radius * 0.12f * fade + 1f),
    )
    for (i in 0 until sparks) {
        val a = 2 * PI * i / sparks + p * 0.6
        val dist = radius * (0.35f + 1.25f * p)
        val pos = Offset(center.x + (cos(a) * dist).toFloat(), center.y + (sin(a) * dist).toFloat())
        drawCircle(color = lerp(color, Color.White, 0.4f).copy(alpha = fade), radius = radius * 0.11f * fade + 1f, center = pos)
    }
}

/** Rounded glossy tile: diagonal gradient body, light top sheen and a thin rim. */
fun DrawScope.drawGlossyTile(
    topLeft: Offset,
    size: Size,
    color: Color,
    corner: Float,
    rim: Color = Color.White.copy(alpha = 0.18f),
    rimWidth: Float = 2f,
) {
    val radius = CornerRadius(corner, corner)
    drawRoundRect(
        brush = Brush.linearGradient(
            listOf(lerp(color, Color.White, 0.25f), color, lerp(color, Color.Black, 0.3f)),
            start = topLeft,
            end = Offset(topLeft.x + size.width, topLeft.y + size.height),
        ),
        topLeft = topLeft,
        size = size,
        cornerRadius = radius,
    )
    drawRoundRect(
        brush = Brush.verticalGradient(
            listOf(Color.White.copy(alpha = 0.22f), Color.Transparent),
            startY = topLeft.y,
            endY = topLeft.y + size.height * 0.5f,
        ),
        topLeft = Offset(topLeft.x + size.width * 0.06f, topLeft.y + size.height * 0.04f),
        size = Size(size.width * 0.88f, size.height * 0.42f),
        cornerRadius = radius,
    )
    drawRoundRect(color = rim, topLeft = topLeft, size = size, cornerRadius = radius, style = Stroke(width = rimWidth))
}

/** Ease-out with a small overshoot, for pop-in animations. Input and output roughly 0..1. */
fun easeOutBack(x: Float): Float {
    val t = x.coerceIn(0f, 1f) - 1f
    val s = 1.70158f
    return 1f + t * t * ((s + 1f) * t + s)
}
