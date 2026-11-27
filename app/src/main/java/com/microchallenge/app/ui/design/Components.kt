package com.microchallenge.app.ui.design

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Makes any element tappable with a springy squash on press and a light haptic tick.
 * Use it for cards and tiles; [ChunkyButton] has its own press effect.
 */
@Composable
fun Modifier.pressable(
    onClick: () -> Unit,
    enabled: Boolean = true,
    role: Role = Role.Button,
    pressedScale: Float = 0.95f,
): Modifier {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(
        if (pressed && enabled) pressedScale else 1f,
        spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "press",
    )
    val haptics = LocalHapticFeedback.current
    return this
        .scale(scale)
        .clickable(source, indication = null, enabled = enabled, role = role) {
            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            onClick()
        }
}

/**
 * The main game button: a bright face sitting on a darker edge, which sinks
 * into the edge while pressed. Fills the width it is given.
 */
@Composable
fun ChunkyButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    face: Color = Palette.Primary,
    edge: Color = Palette.PrimaryEdge,
    textColor: Color = Color.White,
    enabled: Boolean = true,
    height: Dp = 60.dp,
    fontSize: TextUnit = 20.sp,
    leading: String? = null,
) {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val depth = 5.dp
    val sink by animateDpAsState(
        if (pressed && enabled) depth else 0.dp,
        spring(stiffness = Spring.StiffnessHigh),
        label = "sink",
    )
    val haptics = LocalHapticFeedback.current
    Box(
        modifier
            .height(height + depth)
            .alpha(if (enabled) 1f else 0.45f)
            .clickable(source, indication = null, enabled = enabled, role = Role.Button) {
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                onClick()
            },
    ) {
        Box(
            Modifier.fillMaxWidth().height(height).align(Alignment.BottomCenter)
                .clip(Shapes.button).background(edge),
        )
        Box(
            Modifier.fillMaxWidth().height(height).offset(y = sink)
                .clip(Shapes.button)
                .background(Brush.verticalGradient(listOf(lerp(face, Color.White, 0.18f), face)))
                .border(1.dp, Color.White.copy(alpha = 0.22f), Shapes.button),
            contentAlignment = Alignment.Center,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (leading != null) {
                    Text(leading, color = textColor, fontSize = fontSize, modifier = Modifier.padding(end = 8.dp))
                }
                Text(
                    text.uppercase(),
                    color = textColor,
                    fontSize = fontSize,
                    fontFamily = AppFonts.display,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 1.sp,
                )
            }
        }
    }
}

/** Quieter secondary button: dark face, light text, same press-in effect. */
@Composable
fun SoftButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    ChunkyButton(
        text, onClick, modifier,
        face = Palette.Surface3, edge = Color(0xFF16122E), textColor = Palette.Text,
        enabled = enabled, height = 52.dp, fontSize = 16.sp,
    )
}

/** Rounded panel with a faint top highlight, used for every grouped block of content. */
@Composable
fun Panel(
    modifier: Modifier = Modifier,
    color: Color = Palette.Surface1,
    shape: Shape = Shapes.card,
    padding: PaddingValues = PaddingValues(16.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier
            .clip(shape)
            .background(color)
            .border(1.dp, Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.14f), Color.Transparent)), shape)
            .padding(padding),
        content = content,
    )
}

/** Section header with an optional action on the right. */
@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier, action: String? = null, onAction: () -> Unit = {}) {
    Row(modifier.fillMaxWidth().padding(top = 20.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(text, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
        if (action != null) {
            Text(
                action, style = MaterialTheme.typography.labelLarge, color = Palette.Accent,
                modifier = Modifier.clip(Shapes.pill).pressable(onAction).padding(horizontal = 8.dp, vertical = 4.dp),
            )
        }
    }
}

/** Small gradient pill naming a skill. */
@Composable
fun SkillChip(
    style: SkillStyle,
    modifier: Modifier = Modifier,
    selected: Boolean = true,
    onClick: (() -> Unit)? = null,
) {
    val base = modifier.clip(Shapes.pill)
        .then(if (selected) Modifier.background(style.horizontal) else Modifier.background(Palette.Surface2))
        .then(if (onClick != null) Modifier.pressable(onClick, role = Role.Tab) else Modifier)
        .padding(horizontal = 14.dp, vertical = 7.dp)
    Text(
        style.label.uppercase(), base,
        color = if (selected) Color(0xFF14102B) else Palette.TextDim,
        style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold,
    )
}

/** Round medal with a ribbon; an empty ring when there is no medal yet. */
@Composable
fun MedalBadge(medal: Medal, modifier: Modifier = Modifier, size: Dp = 22.dp) {
    Canvas(modifier.size(size).semantics { contentDescription = "${medal.label} medal" }) {
        val r = this.size.minDimension / 2f
        val c = Offset(r, r)
        if (medal == Medal.NONE) {
            drawCircle(Palette.TextFaint.copy(alpha = 0.5f), r * 0.8f, c, style = Stroke(r * 0.16f))
            return@Canvas
        }
        drawCircle(lerp(medal.color, Color.Black, 0.35f), r, c)
        drawCircle(Brush.linearGradient(listOf(lerp(medal.color, Color.White, 0.45f), medal.color), Offset.Zero,
            Offset(r * 2, r * 2)), r * 0.82f, c)
        // Five-point star in the middle.
        val star = Path()
        for (i in 0 until 10) {
            val a = -PI / 2 + i * PI / 5
            val rr = if (i % 2 == 0) r * 0.5f else r * 0.22f
            val p = Offset(c.x + (cos(a) * rr).toFloat(), c.y + (sin(a) * rr).toFloat())
            if (i == 0) star.moveTo(p.x, p.y) else star.lineTo(p.x, p.y)
        }
        star.close()
        drawPath(star, lerp(medal.color, Color.Black, 0.3f))
    }
}

/** Row of 0 to 3 stars. Earned stars pop in one after another when [animate] is true. */
@Composable
fun StarRow(stars: Int, modifier: Modifier = Modifier, size: Dp = 40.dp, animate: Boolean = true) {
    val reduce = LocalReduceMotion.current
    Row(modifier.semantics { contentDescription = "$stars of 3 stars" }, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        for (i in 0 until 3) {
            val earned = i < stars
            val pop = remember { Animatable(if (animate && earned && !reduce) 0f else 1f) }
            LaunchedEffect(earned) {
                if (animate && earned && !reduce) {
                    kotlinx.coroutines.delay(350L + i * 220L)
                    pop.animateTo(1f, spring(dampingRatio = 0.45f, stiffness = Spring.StiffnessMediumLow))
                }
            }
            val lift = if (i == 1) (-6).dp else 0.dp
            Canvas(Modifier.size(size).offset(y = lift).scale(pop.value)) {
                val r = this.size.minDimension / 2f
                val c = Offset(r, r)
                val path = Path()
                for (k in 0 until 10) {
                    val a = -PI / 2 + k * PI / 5
                    val rr = if (k % 2 == 0) r else r * 0.45f
                    val p = Offset(c.x + (cos(a) * rr).toFloat(), c.y + (sin(a) * rr).toFloat())
                    if (k == 0) path.moveTo(p.x, p.y) else path.lineTo(p.x, p.y)
                }
                path.close()
                if (earned) {
                    drawCircle(Palette.Gold.copy(alpha = 0.25f), r * 1.1f, c)
                    drawPath(path, Brush.verticalGradient(listOf(Color(0xFFFFE58A), Palette.Gold, Palette.XpEdge)))
                } else {
                    drawPath(path, Palette.Surface3)
                }
            }
        }
    }
}

/** Hand-drawn flame. Lit flames flicker gently unless motion is reduced. */
@Composable
fun Flame(modifier: Modifier = Modifier, lit: Boolean = true, size: Dp = 22.dp) {
    val reduce = LocalReduceMotion.current
    val flicker = if (lit && !reduce) {
        val t = rememberInfiniteTransition(label = "flame")
        t.animateFloat(0.94f, 1.06f, infiniteRepeatable(tween(520, easing = FastOutSlowInEasing), RepeatMode.Reverse),
            label = "flicker").value
    } else 1f
    Canvas(modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        fun flame(scaleX: Float, scaleY: Float): Path = Path().apply {
            val cx = w / 2f
            val bottom = h * 0.96f
            val top = h * (1f - 0.92f * scaleY)
            val half = w * 0.40f * scaleX
            moveTo(cx, top)
            cubicTo(cx + half * 0.35f, top + h * 0.22f, cx + half * 1.25f, bottom - h * 0.42f, cx + half * 0.95f, bottom - h * 0.22f)
            cubicTo(cx + half * 0.75f, bottom - h * 0.02f, cx - half * 0.75f, bottom - h * 0.02f, cx - half * 0.95f, bottom - h * 0.22f)
            cubicTo(cx - half * 1.2f, bottom - h * 0.48f, cx - half * 0.2f, top + h * 0.28f, cx, top)
            close()
        }
        if (lit) {
            drawPath(flame(1f, flicker), Brush.verticalGradient(listOf(Color(0xFFFFC14D), Palette.Streak, Palette.StreakHot)))
            drawPath(flame(0.5f, 0.5f * (2f - flicker)), Brush.verticalGradient(listOf(Color(0xFFFFF3C4), Color(0xFFFFD36B))))
        } else {
            drawPath(flame(1f, 1f), Palette.TextFaint.copy(alpha = 0.55f))
        }
    }
}

/** Streak counter: flame plus number. The flame is grey until today's round is played. */
@Composable
fun StreakPill(days: Int, playedToday: Boolean, modifier: Modifier = Modifier) {
    Row(
        modifier.clip(Shapes.pill)
            .background(if (playedToday) Palette.Streak.copy(alpha = 0.16f) else Palette.Surface2)
            .border(1.dp, if (playedToday) Palette.Streak.copy(alpha = 0.5f) else Palette.Outline, Shapes.pill)
            .padding(start = 10.dp, end = 14.dp, top = 6.dp, bottom = 6.dp)
            .semantics(mergeDescendants = true) {
                contentDescription = if (playedToday) "$days day streak" else "$days day streak, play today to keep it"
            },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Flame(lit = playedToday || days > 0, size = 22.dp)
        Spacer(Modifier.width(6.dp))
        Text("$days", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black,
            color = if (playedToday) Palette.Streak else Palette.Text)
    }
}

/** Circular progress ring with a gradient sweep; [content] sits in the middle. */
@Composable
fun ProgressRing(
    progress: Float,
    modifier: Modifier = Modifier,
    size: Dp = 56.dp,
    stroke: Dp = 6.dp,
    colors: List<Color> = listOf(Palette.Xp, Palette.Streak),
    track: Color = Palette.Surface3,
    content: @Composable BoxScope.() -> Unit = {},
) {
    val animated by animateFloatAsState(progress.coerceIn(0f, 1f), tween(900, easing = FastOutSlowInEasing), label = "ring")
    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val s = stroke.toPx()
            val arcSize = Size(this.size.width - s, this.size.height - s)
            val topLeft = Offset(s / 2, s / 2)
            drawArc(track, 0f, 360f, false, topLeft, arcSize, style = Stroke(s))
            drawArc(
                Brush.sweepGradient(colors + colors.first()), -90f, 360f * animated, false, topLeft, arcSize,
                style = Stroke(s, cap = StrokeCap.Round),
            )
        }
        content()
    }
}

/** Rank badge: a gem-like hexagon with the rank's first letter, wrapped in an XP ring. */
@Composable
fun RankBadge(title: String, progress: Float, modifier: Modifier = Modifier, size: Dp = 56.dp) {
    ProgressRing(progress, modifier.semantics { contentDescription = "Rank $title" }, size = size, stroke = (size.value / 10f).dp) {
        Canvas(Modifier.size(size * 0.62f)) {
            val r = this.size.minDimension / 2f
            val c = Offset(r, r)
            val hex = Path()
            for (i in 0 until 6) {
                val a = -PI / 2 + i * PI / 3
                val p = Offset(c.x + (cos(a) * r).toFloat(), c.y + (sin(a) * r).toFloat())
                if (i == 0) hex.moveTo(p.x, p.y) else hex.lineTo(p.x, p.y)
            }
            hex.close()
            drawPath(hex, Brush.linearGradient(listOf(Palette.Primary, Color(0xFFEC4899)), Offset.Zero, Offset(r * 2, r * 2)))
        }
        Text(title.take(1), fontFamily = AppFonts.display, fontWeight = FontWeight.Black,
            fontSize = (size.value * 0.3f).sp, color = Color.White)
    }
}

/** Thick rounded progress bar with a gradient fill that animates to [progress]. */
@Composable
fun GradientBar(
    progress: Float,
    modifier: Modifier = Modifier,
    brush: Brush = Brush.horizontalGradient(listOf(Palette.Xp, Palette.Streak)),
    height: Dp = 12.dp,
    animateFrom: Float? = null,
    durationMs: Int = 900,
) {
    val anim = remember { Animatable(animateFrom ?: progress) }
    LaunchedEffect(progress) { anim.animateTo(progress.coerceIn(0f, 1f), tween(durationMs, easing = FastOutSlowInEasing)) }
    Canvas(modifier.fillMaxWidth().height(height)) {
        val r = CornerRadius(this.size.height / 2, this.size.height / 2)
        drawRoundRect(Palette.Surface3, cornerRadius = r)
        val w = this.size.width * anim.value.coerceIn(0f, 1f)
        if (w > 0f) {
            drawRoundRect(brush, size = Size(w.coerceAtLeast(this.size.height), this.size.height), cornerRadius = r)
            drawRoundRect(
                Color.White.copy(alpha = 0.25f), topLeft = Offset(this.size.height / 3, this.size.height * 0.18f),
                size = Size((w - this.size.height * 2 / 3).coerceAtLeast(0f), this.size.height * 0.22f), cornerRadius = r,
            )
        }
    }
}

/** Big number with a label under it. */
@Composable
fun StatTile(value: String, label: String, modifier: Modifier = Modifier, color: Color = Palette.Text) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.headlineSmall, color = color, fontWeight = FontWeight.Black,
            textAlign = TextAlign.Center, maxLines = 1)
        Text(label.uppercase(), style = MaterialTheme.typography.labelSmall, color = Palette.TextFaint)
    }
}

/**
 * Slow drifting color glows behind the whole app. Static when motion is reduced.
 * [accent] tints the main glow, e.g. with the current game's skill color.
 */
@Composable
fun GlowBackground(modifier: Modifier = Modifier, accent: Color = Palette.Primary) {
    val reduce = LocalReduceMotion.current
    val phase = if (!reduce) {
        val t = rememberInfiniteTransition(label = "glow")
        t.animateFloat(0f, (2 * PI).toFloat(), infiniteRepeatable(tween(24_000, easing = LinearEasing)), label = "phase").value
    } else 0.8f
    val tint by animateColor(accent)
    Canvas(modifier.fillMaxSize().background(Palette.Night)) {
        val w = size.width
        val h = size.height
        fun glow(cx: Float, cy: Float, radius: Float, color: Color, alpha: Float) {
            drawCircle(
                Brush.radialGradient(listOf(color.copy(alpha = alpha), Color.Transparent), Offset(cx, cy), radius),
                radius, Offset(cx, cy),
            )
        }
        glow(w * (0.2f + 0.12f * cos(phase)), h * (0.12f + 0.05f * sin(phase)), w * 0.9f, tint, 0.32f)
        glow(w * (0.9f + 0.08f * sin(phase * 2)), h * (0.55f + 0.08f * cos(phase)), w * 0.8f, Color(0xFFEC4899), 0.14f)
        glow(w * (0.3f + 0.1f * sin(phase)), h * (1.0f + 0.04f * cos(phase * 2)), w * 0.9f, Palette.Accent, 0.12f)
    }
}

@Composable
private fun animateColor(target: Color) = androidx.compose.animation.animateColorAsState(target, tween(800), label = "tint")
