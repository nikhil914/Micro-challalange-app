package com.microchallenge.app.games

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
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
import com.microchallenge.core.challenges.games.Spawn
import com.microchallenge.core.challenges.games.StarCatcherSession
import com.microchallenge.core.challenges.games.StarCatcherSession.TapResult
import kotlin.math.sin

private const val CATCH_BURST_MS = 380L
private const val BOMB_FLASH_MS = 320L
private const val POP_IN_MS = 140L
private const val SHRINK_MS = 160L

/** Star Catcher: tap stars before they vanish, never tap a bomb. */
@Composable
fun StarCatcherScreen(session: StarCatcherSession, elapsedMs: Long, onInput: () -> Unit) {
    val haptics = LocalHapticFeedback.current
    val currentOnInput by rememberUpdatedState(onInput)
    val currentElapsed by rememberUpdatedState(elapsedMs)
    var bombAt by remember(session) { mutableLongStateOf(-1L) }

    val t = session.elapsedMs()
    val timeUp = session.isTimeUp
    val flash = if (bombAt >= 0 && elapsedMs - bombAt < BOMB_FLASH_MS) {
        1f - (elapsedMs - bombAt) / BOMB_FLASH_MS.toFloat()
    } else {
        0f
    }
    val lastHit: Spawn? = session.spawns.getOrNull(session.lastHitId)
    val sinceHit = if (session.lastHitAtMs >= 0) t - session.lastHitAtMs else Long.MAX_VALUE

    fun onPortalTap(cell: Int) {
        when (session.tap(cell)) {
            TapResult.CAUGHT -> haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            TapResult.BOMB -> {
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                bombAt = currentElapsed
            }
            TapResult.EMPTY, TapResult.IGNORED -> Unit
        }
        currentOnInput()
    }

    val scheme = MaterialTheme.colorScheme
    Column(
        modifier = Modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(20.dp))
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFF0B0D12), Color(0xFF1A1240), Color(0xFF0B0D12)),
                ),
            )
            .background(scheme.error.copy(alpha = 0.35f * flash))
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            when {
                timeUp -> "Time's up"
                flash > 0f -> "Boom!"
                else -> "Catch the stars"
            },
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = if (flash > 0f) scheme.error else scheme.onSurface,
            modifier = Modifier.semantics {
                heading()
                liveRegion = LiveRegionMode.Polite
            },
        )
        Text(
            "Stars ${session.caught} · Missed ${session.starsMissed} · Bombs ${session.bombsHit}",
            style = MaterialTheme.typography.titleMedium,
            color = scheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(16.dp))
        Box(
            modifier = Modifier.fillMaxWidth().weight(1f),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                Modifier.aspectRatio(1f),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                for (row in 0 until 3) {
                    Row(
                        Modifier.fillMaxWidth().weight(1f),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        for (col in 0 until 3) {
                            val cell = row * 3 + col
                            val item = session.itemAt(cell)
                            val burst = if (lastHit != null && lastHit.cell == cell && sinceHit < CATCH_BURST_MS) {
                                lastHit to sinceHit / CATCH_BURST_MS.toFloat()
                            } else {
                                null
                            }
                            Portal(
                                item = item,
                                nowMs = t,
                                burst = burst,
                                enabled = !timeUp,
                                label = "Portal row ${row + 1}, column ${col + 1}",
                                onTap = { onPortalTap(cell) },
                                tapKey = session,
                                modifier = Modifier.weight(1f).fillMaxSize(),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Portal(
    item: Spawn?,
    nowMs: Long,
    burst: Pair<Spawn, Float>?,
    enabled: Boolean,
    label: String,
    onTap: () -> Unit,
    tapKey: Any,
    modifier: Modifier,
) {
    val currentEnabled by rememberUpdatedState(enabled)
    val currentOnTap by rememberUpdatedState(onTap)
    val rim = MaterialTheme.colorScheme.secondary
    val cyan = MaterialTheme.colorScheme.primary
    val gold = GameArt.color(GameArt.ICON_STAR)
    val danger = MaterialTheme.colorScheme.error
    Box(
        modifier = modifier
            .pointerInput(tapKey) {
                detectTapGestures(onPress = { if (currentEnabled) currentOnTap() })
            }
            .clearAndSetSemantics {
                role = Role.Button
                contentDescription = label
                stateDescription = when {
                    item == null -> "empty"
                    item.isBomb -> "bomb"
                    else -> "star"
                }
                if (!enabled) disabled()
                onClick(label = "Tap portal") {
                    if (currentEnabled) currentOnTap()
                    currentEnabled
                }
            },
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val c = Offset(size.width / 2, size.height / 2)
            val r = size.minDimension / 2
            drawPortal(c, r, rim, cyan, nowMs)
            if (item != null) {
                val age = nowMs - item.startMs
                val left = item.endMs - nowMs
                val grow = easeOutBack(age / POP_IN_MS.toFloat())
                val shrink = (left / SHRINK_MS.toFloat()).coerceIn(0f, 1f)
                val s = (grow * shrink).coerceAtLeast(0f)
                if (item.isBomb) {
                    drawBomb(c, r * 0.55f * s, danger, nowMs)
                } else {
                    drawGlow(c, r * 1.0f * s, gold, 0.55f)
                    rotate(degrees = (nowMs % 3_000L) / 3_000f * 72f - 36f, pivot = c) {
                        drawIcon(GameArt.ICON_STAR, c, r * 0.62f * s)
                    }
                }
            }
            if (burst != null) {
                val (spawn, p) = burst
                drawBurst(c, p, if (spawn.isBomb) danger else gold, r * 0.7f, sparks = if (spawn.isBomb) 8 else 14)
            }
        }
    }
}

/** A glowing oval portal with a slowly pulsing rim. */
private fun DrawScope.drawPortal(c: Offset, r: Float, rim: Color, inner: Color, nowMs: Long) {
    val pulse = 0.5f + 0.5f * sin(nowMs / 420f)
    val ovalSize = Size(r * 1.7f, r * 0.62f)
    val topLeft = Offset(c.x - ovalSize.width / 2, c.y + r * 0.35f - ovalSize.height / 2)
    drawOval(
        brush = Brush.radialGradient(
            listOf(Color.Black, inner.copy(alpha = 0.25f + 0.15f * pulse), Color.Transparent),
            center = Offset(c.x, c.y + r * 0.35f),
            radius = ovalSize.width / 1.6f,
        ),
        topLeft = Offset(topLeft.x - r * 0.15f, topLeft.y - r * 0.12f),
        size = Size(ovalSize.width + r * 0.3f, ovalSize.height + r * 0.24f),
    )
    drawOval(color = Color(0xFF05060A), topLeft = topLeft, size = ovalSize)
    drawOval(
        color = rim.copy(alpha = 0.55f + 0.35f * pulse),
        topLeft = topLeft,
        size = ovalSize,
        style = Stroke(width = r * 0.06f),
    )
}

/** Round bomb with a highlight, a fuse and a flickering spark: shape alone tells it from a star. */
private fun DrawScope.drawBomb(c: Offset, r: Float, danger: Color, nowMs: Long) {
    if (r <= 0f) return
    drawGlow(c, r * 1.8f, danger, 0.35f)
    drawCircle(
        brush = Brush.radialGradient(
            listOf(Color(0xFF5A5F70), Color(0xFF1C1E26), Color.Black),
            center = Offset(c.x - r * 0.35f, c.y - r * 0.35f),
            radius = r * 1.4f,
        ),
        radius = r,
        center = c,
    )
    drawCircle(color = danger, radius = r, center = c, style = Stroke(width = r * 0.1f))
    // Cap and fuse.
    val capTop = Offset(c.x + r * 0.45f, c.y - r * 0.85f)
    drawLine(Color(0xFF8A8F9E), Offset(c.x + r * 0.3f, c.y - r * 0.7f), capTop, strokeWidth = r * 0.28f, cap = StrokeCap.Round)
    val fuseEnd = Offset(c.x + r * 0.85f, c.y - r * 1.25f)
    drawLine(Color(0xFFD7CCC8), capTop, fuseEnd, strokeWidth = r * 0.09f, cap = StrokeCap.Round)
    val flicker = 0.6f + 0.4f * sin(nowMs / 45f)
    drawGlow(fuseEnd, r * 0.55f * flicker, Color(0xFFFFAB40), 0.9f)
    drawCircle(Color(0xFFFFF59D), radius = r * 0.1f, center = fuseEnd)
    // A white cross marks it as "do not tap" without relying on color.
    val k = r * 0.32f
    drawLine(Color.White, Offset(c.x - k, c.y - k), Offset(c.x + k, c.y + k), strokeWidth = r * 0.14f, cap = StrokeCap.Round)
    drawLine(Color.White, Offset(c.x - k, c.y + k), Offset(c.x + k, c.y - k), strokeWidth = r * 0.14f, cap = StrokeCap.Round)
}
