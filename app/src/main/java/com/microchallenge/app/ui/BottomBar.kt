package com.microchallenge.app.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.microchallenge.app.Tab
import com.microchallenge.app.ui.design.Palette
import com.microchallenge.app.ui.design.Shapes
import com.microchallenge.app.ui.design.pressable

/** Floating bottom bar for Play, Games and Progress. Icons are drawn, so no icon library is needed. */
@Composable
fun BottomBar(current: Tab, onSelect: (Tab) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
            .clip(Shapes.card).background(Palette.Surface1.copy(alpha = 0.96f))
            .border(1.dp, Palette.Outline, Shapes.card)
            .padding(6.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        Tab.entries.forEach { t ->
            val on = t == current
            val tint by animateColorAsState(if (on) Color.White else Palette.TextFaint, label = "tab")
            val bg by animateColorAsState(if (on) Palette.Primary else Color.Transparent, label = "tabBg")
            Column(
                Modifier.weight(1f).clip(Shapes.tile).background(bg)
                    .pressable({ onSelect(t) }, role = Role.Tab)
                    .semantics { selected = on }
                    .padding(vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                TabIcon(t, tint)
                Spacer(Modifier.height(2.dp))
                Text(
                    when (t) { Tab.PLAY -> "Play"; Tab.GAMES -> "Games"; Tab.PROGRESS -> "Progress" },
                    style = MaterialTheme.typography.labelMedium, color = tint,
                    fontWeight = if (on) FontWeight.Bold else FontWeight.Medium,
                )
            }
        }
    }
}

@Composable
private fun TabIcon(tab: Tab, tint: Color) {
    Canvas(Modifier.size(22.dp)) {
        val w = size.width
        val h = size.height
        when (tab) {
            Tab.PLAY -> {
                val p = Path().apply {
                    moveTo(w * 0.28f, h * 0.16f); lineTo(w * 0.84f, h * 0.5f); lineTo(w * 0.28f, h * 0.84f); close()
                }
                drawPath(p, tint)
            }
            Tab.GAMES -> {
                val s = w * 0.42f
                val r = CornerRadius(w * 0.1f)
                for (x in 0..1) for (y in 0..1) {
                    drawRoundRect(tint, Offset(x * (w - s), y * (h - s)), Size(s, s), r)
                }
            }
            Tab.PROGRESS -> {
                val bw = w * 0.22f
                val r = CornerRadius(w * 0.08f)
                listOf(0.45f, 0.75f, 1f).forEachIndexed { i, f ->
                    val x = i * (w - bw) / 2f
                    drawRoundRect(tint, Offset(x, h * (1f - f)), Size(bw, h * f), r)
                }
            }
        }
    }
}
