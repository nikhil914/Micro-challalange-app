package com.microchallenge.app.games

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.microchallenge.core.challenges.games.TwinScenesSession
import com.microchallenge.core.challenges.games.TwinScenesSession.Phase
import com.microchallenge.core.challenges.games.TwinScenesSession.Sticker
import com.microchallenge.core.challenges.games.TwinScenesSession.TapResult
import kotlin.math.PI
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.random.Random

private val TwinSkyTop = Color(0xFF1B1F4B)
private val TwinSkyBottom = Color(0xFF0A0C1F)
private val TwinFoundColor = Color(0xFF69F0AE)
private val TwinWrongColor = Color(0xFFFF5370)

/** Decorative dots and nebula blobs, generated once per round so both scenes match. */
private class TwinBackdrop(seed: Int) {
    private val rnd = Random(seed)
    val stars: List<Triple<Float, Float, Float>> = List(26) { Triple(rnd.nextFloat(), rnd.nextFloat(), 0.6f + rnd.nextFloat() * 1.6f) }
    val blobs: List<Triple<Float, Float, Int>> = List(3) { Triple(rnd.nextFloat(), rnd.nextFloat(), rnd.nextInt(GameArt.iconColors.size)) }
}

@Composable
fun TwinScenesScreen(session: TwinScenesSession, elapsedMs: Long, onInput: () -> Unit) {
    val haptics = LocalHapticFeedback.current
    val currentOnInput by rememberUpdatedState(onInput)

    // elapsedMs ticks every frame; reading it keeps the scenes animating.
    val tick = elapsedMs
    val phase = session.phase
    val round = session.round
    val now = session.nowMs()
    val backdrop = remember(round) { TwinBackdrop(round.backdropSeed) }

    // Shake both scenes on a wrong tap.
    val shake = remember(session) { Animatable(0f) }
    LaunchedEffect(session.mistakes) {
        if (session.mistakes > 0) {
            for (x in listOf(14f, -11f, 8f, -4f, 0f)) shake.animateTo(x, tween(40))
        }
    }

    fun onSceneTap(index: Int?) {
        if (index == null) return
        when (session.tap(index)) {
            TapResult.FOUND -> haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            TapResult.WRONG -> haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            TapResult.IGNORED -> Unit
        }
        currentOnInput()
    }

    val title = when (phase) {
        Phase.PLAY -> "Find the difference"
        Phase.FOUND -> "Found it!"
        Phase.DONE -> "Time's up"
    }

    // Upper and lower scene. The changed copy goes on top or bottom at random.
    val upper = if (round.swapped) round.bottom else round.top
    val lower = if (round.swapped) round.top else round.bottom
    val introT = ((now - session.roundStartMs) / 320f).coerceIn(0f, 1f)
    val foundP = if (phase == Phase.FOUND) session.foundProgress() else 0f
    val revealDiff = phase == Phase.FOUND || phase == Phase.DONE

    Column(Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            title,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.semantics {
                heading()
                liveRegion = LiveRegionMode.Polite
            },
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Found ${session.found}  ·  Misses ${session.mistakes}",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(8.dp))
        for ((slot, stickers) in listOf("A" to upper, "B" to lower)) {
            TwinScene(
                stickers = stickers,
                reference = round.top,
                diffIndex = round.diffIndex,
                wrong = session.wrongTaps,
                backdrop = backdrop,
                timeMs = tick,
                introT = introT,
                foundP = foundP,
                revealDiff = revealDiff,
                enabled = phase == Phase.PLAY,
                label = "Scene $slot, ${stickers.count { it != null }} stickers. Tap the one that differs from the other scene.",
                onTap = ::onSceneTap,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .graphicsLayer { translationX = shake.value },
            )
            if (slot == "A") Spacer(Modifier.height(10.dp))
        }
    }
}

@Composable
private fun TwinScene(
    stickers: List<Sticker?>,
    reference: List<Sticker?>,
    diffIndex: Int,
    wrong: List<Int>,
    backdrop: TwinBackdrop,
    timeMs: Long,
    introT: Float,
    foundP: Float,
    revealDiff: Boolean,
    enabled: Boolean,
    label: String,
    onTap: (Int?) -> Unit,
    modifier: Modifier,
) {
    val currentEnabled by rememberUpdatedState(enabled)
    val currentOnTap by rememberUpdatedState(onTap)
    // Hit-test against the unchanged scene so a missing sticker's empty spot can be tapped.
    val currentReference by rememberUpdatedState(reference)
    Canvas(
        modifier
            .clip(RoundedCornerShape(22.dp))
            .pointerInput(Unit) {
                detectTapGestures(onPress = { pos ->
                    if (!currentEnabled) return@detectTapGestures
                    val h = size.height.toFloat()
                    val w = size.width.toFloat()
                    var best: Int? = null
                    var bestDist = Float.MAX_VALUE
                    currentReference.forEachIndexed { i, s ->
                        if (s == null) return@forEachIndexed
                        val d = hypot(pos.x - s.x * w, pos.y - s.y * h)
                        if (d < s.size * h * 1.6f && d < bestDist) {
                            best = i
                            bestDist = d
                        }
                    }
                    currentOnTap(best)
                })
            }
            .clearAndSetSemantics { contentDescription = label },
    ) {
        drawTwinBackdrop(backdrop, timeMs)
        val pop = easeOutBack(introT)
        stickers.forEachIndexed { i, s ->
            if (s == null) return@forEachIndexed
            // Gentle bob, identical in both scenes so it never gives the answer away.
            val bob = sin(timeMs / 520.0 + i * 1.7).toFloat() * size.height * 0.008f
            val c = Offset(s.x * size.width, s.y * size.height + bob)
            val r = s.size * size.height
            if (revealDiff && i == diffIndex) drawGlow(c, r * 2.4f, TwinFoundColor, 0.55f * (1f - foundP * 0.5f))
            drawGlow(Offset(c.x, c.y + r * 0.9f), r * 0.9f, Color.Black, 0.35f)
            scale(pop, pivot = c) {
                rotate(s.rotation, pivot = c) {
                    drawIcon(s.icon, c, r, GameArt.color(s.tint))
                }
            }
        }
        // Mark the diff spot (even where the sticker is missing) with a ring and burst.
        reference.getOrNull(diffIndex)?.let { s ->
            if (!revealDiff) return@let
            val c = Offset(s.x * size.width, s.y * size.height)
            val r = s.size * size.height
            drawCircle(
                color = TwinFoundColor,
                radius = r * (1.35f + 0.15f * sin(foundP * PI * 2).toFloat()),
                center = c,
                style = Stroke(width = r * 0.14f),
            )
            if (foundP > 0f) drawBurst(c, foundP, TwinFoundColor, r * 1.9f, sparks = 14)
        }
        // Wrong taps: red cross over the sticker, in both scenes.
        for (i in wrong) {
            val s = reference.getOrNull(i) ?: continue
            val c = Offset(s.x * size.width, s.y * size.height)
            val arm = s.size * size.height * 0.75f
            val stroke = s.size * size.height * 0.2f
            drawLine(TwinWrongColor, Offset(c.x - arm, c.y - arm), Offset(c.x + arm, c.y + arm), stroke, StrokeCap.Round)
            drawLine(TwinWrongColor, Offset(c.x - arm, c.y + arm), Offset(c.x + arm, c.y - arm), stroke, StrokeCap.Round)
        }
        // Glassy frame.
        drawRoundRect(
            color = Color.White.copy(alpha = 0.16f),
            cornerRadius = CornerRadius(22.dp.toPx()),
            style = Stroke(width = 2.dp.toPx()),
        )
    }
}

private fun DrawScope.drawTwinBackdrop(b: TwinBackdrop, timeMs: Long) {
    drawRect(Brush.verticalGradient(listOf(TwinSkyTop, TwinSkyBottom)))
    for ((x, y, icon) in b.blobs) {
        drawGlow(Offset(x * size.width, y * size.height), size.height * 0.55f, GameArt.color(icon), 0.18f)
    }
    for ((i, star) in b.stars.withIndex()) {
        val (x, y, r) = star
        val twinkle = 0.35f + 0.35f * (1f + sin(timeMs / 400.0 + i).toFloat()) / 2f
        drawCircle(Color.White.copy(alpha = twinkle), radius = r * density, center = Offset(x * size.width, y * size.height))
    }
    // Soft ground band, for a sense of depth.
    drawRect(
        Brush.verticalGradient(listOf(Color.Transparent, Color(0x33000000)), startY = size.height * 0.7f, endY = size.height),
        topLeft = Offset(0f, size.height * 0.7f),
    )
}
