package com.microchallenge.app.games

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
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
import androidx.compose.ui.unit.sp
import com.microchallenge.app.ui.GameColors
import com.microchallenge.core.challenges.games.WordScrambleSession
import com.microchallenge.core.challenges.games.WordScrambleSession.Phase
import com.microchallenge.core.challenges.games.WordScrambleSession.TapResult
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

private val TileTop = Color(0xFFFFE7B0)
private val TileBottom = Color(0xFFF2B866)
private val TileEdge = Color(0xFFB9772E)
private val TileInk = Color(0xFF4A2A0C)
private val BoardTop = Color(0xFF1B1F3B)
private val BoardBottom = Color(0xFF0B0D1E)
private val SparkColor = Color(0xFFFFE08A)

private enum class SlotLook { EMPTY, FILLED, SOLVED, WRONG }

@Composable
fun WordScrambleScreen(session: WordScrambleSession, elapsedMs: Long, onInput: () -> Unit) {
    val haptics = LocalHapticFeedback.current
    val currentOnInput by rememberUpdatedState(onInput)

    // elapsedMs ticks every frame; reading it keeps feedback animations and phase changes live.
    val tick = elapsedMs
    val phase = session.phase
    val round = session.round
    val picked = session.picked
    val feedback = session.feedbackProgress()

    // Shake the answer row on a wrong spelling.
    val shake = remember(session) { Animatable(0f) }
    LaunchedEffect(session.mistakes - session.skips) {
        if (session.mistakes - session.skips > 0) {
            for (x in listOf(20f, -16f, 12f, -8f, 4f, 0f)) shake.animateTo(x, tween(40))
        }
    }

    fun onLetter(index: Int) {
        when (session.tapLetter(index)) {
            TapResult.PLACED, TapResult.SOLVED -> haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            TapResult.WRONG -> haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            TapResult.IGNORED -> Unit
        }
        currentOnInput()
    }

    val (title, subtitle) = when (phase) {
        Phase.PLAY -> "Unscramble" to "Word ${round.number + 1} · ${session.wordsSolved} solved"
        Phase.SOLVED -> "Nice!" to session.spelled()
        Phase.WRONG -> "Not quite" to "Try another order"
        Phase.DONE -> "Time's up" to "It was ${round.target}"
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 8.dp),
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
        Spacer(Modifier.height(12.dp))

        Box(
            Modifier
                .fillMaxWidth()
                .weight(1f)
                .clip(RoundedCornerShape(28.dp))
                .background(Brush.verticalGradient(listOf(BoardTop, BoardBottom))),
            contentAlignment = Alignment.Center,
        ) {
            Sparkles(tick)
            Column(
                Modifier.fillMaxWidth().padding(14.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(28.dp),
            ) {
                // Answer slots.
                Box(contentAlignment = Alignment.Center) {
                    Row(
                        Modifier.fillMaxWidth().graphicsLayer { translationX = shake.value },
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        for (slot in round.letters.indices) {
                            val letter = picked.getOrNull(slot)?.let { round.letters[it] }
                            val look = when {
                                letter == null -> SlotLook.EMPTY
                                phase == Phase.SOLVED -> SlotLook.SOLVED
                                phase == Phase.WRONG -> SlotLook.WRONG
                                else -> SlotLook.FILLED
                            }
                            // Celebration wave: each slot hops a little after the one before it.
                            val hop = if (look == SlotLook.SOLVED) {
                                val t = (feedback * 1.6f - slot * 0.12f).coerceIn(0f, 1f)
                                -sin(t * PI).toFloat() * 18f
                            } else 0f
                            AnswerSlot(
                                letter = letter,
                                look = look,
                                hop = hop,
                                label = "Slot ${slot + 1}",
                                enabled = phase == Phase.PLAY && letter != null,
                                onTap = {
                                    if (session.tapSlot(slot)) {
                                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    }
                                    currentOnInput()
                                },
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                    if (phase == Phase.SOLVED) Confetti(feedback, Modifier.matchParentSize())
                }

                // Letter tray. Tiles drop in, staggered, every new round.
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    for (i in round.letters.indices) {
                        LetterTile(
                            letter = round.letters[i],
                            used = i in picked,
                            enabled = phase == Phase.PLAY && i !in picked,
                            dealKey = round,
                            dealDelayMs = i * 55,
                            onTap = { onLetter(i) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(
                onClick = {
                    if (session.tapSlot(0)) currentOnInput()
                },
                enabled = phase == Phase.PLAY && picked.isNotEmpty(),
                modifier = Modifier.weight(1f).heightIn(min = 64.dp),
            ) { Text("Clear", fontSize = 18.sp) }
            OutlinedButton(
                onClick = {
                    if (session.skip()) haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    currentOnInput()
                },
                enabled = phase == Phase.PLAY,
                modifier = Modifier.weight(1f).heightIn(min = 64.dp),
            ) { Text("Skip", fontSize = 18.sp) }
        }
    }
}

/** A wooden-style letter tile with a raised edge, drawn entirely in code. */
@Composable
private fun LetterTile(
    letter: Char,
    used: Boolean,
    enabled: Boolean,
    dealKey: Any,
    dealDelayMs: Int,
    onTap: () -> Unit,
    modifier: Modifier,
) {
    // Drop-in when a new word is dealt.
    val drop = remember(dealKey) { Animatable(-260f) }
    LaunchedEffect(dealKey) {
        drop.snapTo(-260f)
        kotlinx.coroutines.delay(dealDelayMs.toLong())
        drop.animateTo(0f, spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessMediumLow))
    }
    // Used tiles sink into a faint ghost so the tray keeps its shape.
    val presence by animateFloatAsState(
        targetValue = if (used) 0f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "presence",
    )
    val currentEnabled by rememberUpdatedState(enabled)
    val currentOnTap by rememberUpdatedState(onTap)
    val shape = RoundedCornerShape(12.dp)
    Box(
        modifier
            .aspectRatio(0.86f)
            .graphicsLayer {
                translationY = drop.value
                val s = 0.55f + 0.45f * presence
                scaleX = s
                scaleY = s
                alpha = 0.22f + 0.78f * presence
            }
            .pointerInput(dealKey) {
                detectTapGestures(onPress = { if (currentEnabled) currentOnTap() })
            }
            .clearAndSetSemantics {
                role = Role.Button
                contentDescription = "Letter $letter"
                stateDescription = if (used) "used" else ""
                if (!enabled) disabled()
                onClick(label = "Place letter") {
                    if (currentEnabled) currentOnTap()
                    currentEnabled
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val r = CornerRadius(12.dp.toPx())
            val edge = 5.dp.toPx()
            // Drop shadow + raised edge.
            drawRoundRect(Color.Black.copy(alpha = 0.35f), topLeft = Offset(0f, edge + 2.dp.toPx()), size = Size(size.width, size.height - edge), cornerRadius = r)
            drawRoundRect(TileEdge, topLeft = Offset(0f, edge), size = Size(size.width, size.height - edge), cornerRadius = r)
            // Face.
            drawRoundRect(
                Brush.verticalGradient(listOf(TileTop, TileBottom), endY = size.height - edge),
                size = Size(size.width, size.height - edge),
                cornerRadius = r,
            )
            // Glossy highlight.
            drawRoundRect(
                Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.55f), Color.Transparent), endY = size.height * 0.45f),
                topLeft = Offset(size.width * 0.08f, size.height * 0.06f),
                size = Size(size.width * 0.84f, size.height * 0.34f),
                cornerRadius = r,
            )
        }
        Text(
            letter.toString(),
            fontSize = 30.sp,
            fontWeight = FontWeight.Black,
            color = TileInk,
            modifier = Modifier.padding(bottom = 5.dp).clip(shape),
        )
    }
}

@Composable
private fun AnswerSlot(
    letter: Char?,
    look: SlotLook,
    hop: Float,
    label: String,
    enabled: Boolean,
    onTap: () -> Unit,
    modifier: Modifier,
) {
    val pop by animateFloatAsState(
        targetValue = if (letter != null) 1f else 0.9f,
        animationSpec = spring(dampingRatio = 0.45f, stiffness = Spring.StiffnessMedium),
        label = "slotPop",
    )
    val shape = RoundedCornerShape(12.dp)
    val (fill, glow) = when (look) {
        SlotLook.EMPTY -> Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.06f), Color.White.copy(alpha = 0.02f))) to Color.Transparent
        SlotLook.FILLED -> Brush.verticalGradient(listOf(TileTop, TileBottom)) to SparkColor
        SlotLook.SOLVED -> Brush.verticalGradient(listOf(Color(0xFF9CFFC8), GameColors.GREEN.color)) to GameColors.GREEN.color
        SlotLook.WRONG -> Brush.verticalGradient(listOf(Color(0xFFFFB08A), GameColors.RED.color)) to GameColors.RED.color
    }
    val currentEnabled by rememberUpdatedState(enabled)
    val currentOnTap by rememberUpdatedState(onTap)
    Box(
        modifier
            .aspectRatio(0.86f)
            .graphicsLayer {
                translationY = hop * density
                scaleX = pop
                scaleY = pop
            }
            .shadow(if (letter != null) 10.dp else 0.dp, shape, ambientColor = glow, spotColor = glow)
            .clip(shape)
            .background(fill)
            .border(
                if (letter == null) 2.dp else 1.dp,
                Color.White.copy(alpha = if (letter == null) 0.25f else 0.5f),
                shape,
            )
            .pointerInput(Unit) {
                detectTapGestures(onPress = { if (currentEnabled) currentOnTap() })
            }
            .clearAndSetSemantics {
                role = Role.Button
                contentDescription = label
                stateDescription = when (look) {
                    SlotLook.EMPTY -> "empty"
                    SlotLook.FILLED -> "$letter"
                    SlotLook.SOLVED -> "$letter, correct"
                    SlotLook.WRONG -> "$letter, wrong"
                }
                if (!enabled) disabled()
                onClick(label = "Return letter") {
                    if (currentEnabled) currentOnTap()
                    currentEnabled
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        if (letter != null) {
            Text(
                letter.toString(),
                fontSize = 28.sp,
                fontWeight = FontWeight.Black,
                color = if (look == SlotLook.FILLED) TileInk else Color.Black.copy(alpha = 0.78f),
            )
        } else {
            // Underscore marker on empty slots.
            Box(
                Modifier
                    .padding(top = 22.dp)
                    .fillMaxWidth(0.45f)
                    .height(3.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Color.White.copy(alpha = 0.35f)),
            )
        }
    }
}

/** Slow twinkling stars behind the board. Positions are fixed; only brightness moves. */
@Composable
private fun Sparkles(tickMs: Long) {
    Canvas(Modifier.fillMaxSize().clearAndSetSemantics { }) {
        for (i in 0 until 28) {
            val x = ((i * 0.6180339f) % 1f) * size.width
            val y = ((i * 0.3819660f + i * i * 0.013f) % 1f) * size.height
            val phase = (tickMs / 900.0 + i * 0.37) * 2 * PI
            val a = (0.15f + 0.35f * ((sin(phase) + 1) / 2).toFloat())
            drawCircle(SparkColor.copy(alpha = a), radius = (1.2f + (i % 3)) * density, center = Offset(x, y))
        }
    }
}

/** Radial confetti burst from the answer row, driven by feedback progress 0..1. */
@Composable
private fun Confetti(progress: Float, modifier: Modifier) {
    val colors = listOf(
        GameColors.YELLOW.color, GameColors.GREEN.color, GameColors.BLUE.color,
        GameColors.PURPLE.color, GameColors.ORANGE.color, Color.White,
    )
    Canvas(modifier.clearAndSetSemantics { }) {
        val center = Offset(size.width / 2, size.height / 2)
        val reach = size.width * 0.6f
        val fade = (1f - progress).coerceIn(0f, 1f)
        for (i in 0 until 36) {
            val angle = i * (2 * PI / 36) + (i % 5) * 0.11
            val dist = reach * progress * (0.55f + (i % 4) * 0.15f)
            val gravity = progress * progress * size.height * 0.9f
            val p = Offset(
                center.x + cos(angle).toFloat() * dist,
                center.y + sin(angle).toFloat() * dist * 0.6f + gravity,
            )
            val w = (5 + i % 3 * 2) * density
            drawRoundRect(
                colors[i % colors.size].copy(alpha = fade),
                topLeft = Offset(p.x - w / 2, p.y - w / 4),
                size = Size(w, w / 2),
                cornerRadius = CornerRadius(w / 4),
            )
        }
    }
}
