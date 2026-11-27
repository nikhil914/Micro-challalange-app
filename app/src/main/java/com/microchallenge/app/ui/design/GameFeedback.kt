package com.microchallenge.app.ui.design

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Optional shared feedback for games. Call these from a game screen to get the
 * app-wide edge flash, combo badge and haptic, for example:
 *
 * ```
 * val feedback = LocalGameFeedback.current
 * if (hit) feedback.correct() else feedback.wrong()
 * ```
 * Games that do their own feedback can ignore it; the default does nothing.
 */
interface GameFeedback {
    fun correct()
    fun wrong()
    /** Show a combo badge for a streak of [count] correct moves in a row. */
    fun combo(count: Int)
}

val LocalGameFeedback = staticCompositionLocalOf<GameFeedback> { NoGameFeedback }

private object NoGameFeedback : GameFeedback {
    override fun correct() = Unit
    override fun wrong() = Unit
    override fun combo(count: Int) = Unit
}

/** State behind [FeedbackOverlay]; create with [rememberGameFeedback]. */
@Stable
class GameFeedbackState internal constructor(
    private val scope: CoroutineScope,
    private val haptics: HapticFeedback,
) : GameFeedback {
    internal val flash = Animatable(0f)
    internal var flashColor by mutableStateOf(Palette.Correct)
    internal var comboCount by mutableIntStateOf(0)
    internal val comboPop = Animatable(0f)
    private var comboTicket = 0

    private fun pulse(color: Color) {
        flashColor = color
        scope.launch {
            flash.snapTo(1f)
            flash.animateTo(0f, tween(380))
        }
    }

    override fun correct() {
        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        pulse(Palette.Correct)
    }

    override fun wrong() {
        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        pulse(Palette.Wrong)
    }

    override fun combo(count: Int) {
        if (count < 2) return
        comboCount = count
        val ticket = ++comboTicket
        scope.launch {
            comboPop.snapTo(0.4f)
            comboPop.animateTo(1f, spring(dampingRatio = 0.4f, stiffness = Spring.StiffnessMedium))
            delay(900)
            if (ticket == comboTicket) comboPop.animateTo(0f, tween(200))
        }
    }
}

@Composable
fun rememberGameFeedback(haptics: HapticFeedback): GameFeedbackState {
    val scope = rememberCoroutineScope()
    return remember(haptics) { GameFeedbackState(scope, haptics) }
}

/** Draws the edge flash and the combo badge over the game area. Never takes touches. */
@Composable
fun BoxScope.FeedbackOverlay(state: GameFeedbackState) {
    val reduce = LocalReduceMotion.current
    if (state.flash.value > 0f) {
        Canvas(Modifier.matchParentSize()) {
            val a = state.flash.value * if (reduce) 0.35f else 0.75f
            val w = 10.dp.toPx()
            drawRect(state.flashColor.copy(alpha = a), Offset(w / 2, w / 2),
                androidx.compose.ui.geometry.Size(size.width - w, size.height - w), style = Stroke(w))
            drawRect(Brush.verticalGradient(listOf(state.flashColor.copy(alpha = a * 0.25f), Color.Transparent,
                state.flashColor.copy(alpha = a * 0.25f))))
        }
    }
    if (state.comboPop.value > 0f) {
        Box(Modifier.align(Alignment.TopCenter).padding(top = 8.dp).scale(state.comboPop.value)) {
            Text(
                "COMBO x${state.comboCount}",
                Modifier.clip(Shapes.pill).background(Brush.horizontalGradient(listOf(Palette.Xp, Palette.Streak)))
                    .padding(horizontal = 14.dp, vertical = 6.dp),
                color = Color(0xFF2A1C00), fontFamily = AppFonts.display, fontWeight = FontWeight.Black, fontSize = 18.sp,
            )
        }
    }
}
