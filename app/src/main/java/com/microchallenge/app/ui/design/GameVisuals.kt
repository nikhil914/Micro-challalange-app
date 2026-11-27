package com.microchallenge.app.ui.design

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.microchallenge.app.games.GameArt
import com.microchallenge.app.games.drawIcon
import com.microchallenge.core.model.ChallengeType

/** What sits in the middle of a game's tile: one of the [GameArt] icons, or a short bold label. */
sealed interface GameGlyph {
    data class Art(val icon: Int) : GameGlyph
    data class Label(val text: String) : GameGlyph
}

/**
 * Menu art for each game. A game that is not listed here still gets a tile:
 * its skill's gradient with a default glyph, so new games need no UI work.
 * To give a new game its own look, add one line to [glyphs].
 */
object GameVisuals {
    private val glyphs: Map<ChallengeType, GameGlyph> = buildMap {
        fun art(t: String, icon: Int) { ChallengeType.entries.find { it.name == t }?.let { put(it, GameGlyph.Art(icon)) } }
        fun label(t: String, text: String) { ChallengeType.entries.find { it.name == t }?.let { put(it, GameGlyph.Label(text)) } }
        // Looked up by name so this file never breaks when a game is renamed or removed.
        art("REACTION_TAP", GameArt.ICON_BOLT)
        label("NUMBER_MEMORY", "123")
        art("SEQUENCE_MEMORY", GameArt.ICON_GEM)
        label("COLOR_WORD", "Aa")
        label("MATH_SPRINT", "+-")
        label("ODD_ONE_OUT", "1/9")
        label("GO_NO_GO", "GO")
        label("QUICK_COMPARE", "9>6")
        label("FLANKER", ">>")
        label("CHIMP_ORDER", "1.2.3")
        label("RULE_SWITCH", "A|B")
        art("LIGHTS_OUT", GameArt.ICON_MOON)
        label("NUMBER_SERIES", "2,4,?")
        label("DOT_COUNT", "...")
        art("SPOT_CHANGE", GameArt.ICON_LEAF)
        label("STOP_CLOCK", "0.00")
        label("MIRROR_MATCH", "R|R")
        art("COLOR_RUSH", GameArt.ICON_DROP)
        art("PAIR_UP", GameArt.ICON_HEART)
        label("BUBBLE_SUM", "=10")
        art("STAR_CATCHER", GameArt.ICON_STAR)
        label("SLIDE_SHIFT", "<>")
        art("WEIGH_IN", GameArt.ICON_CROWN)
        label("BANK_SHOT", "O")
        art("GLOW_GRID", GameArt.ICON_GEM)
        label("WORD_SCRAMBLE", "ABC")
        label("PIPE_FLOW", "+")
        art("NEON_DASH", GameArt.ICON_BOLT)
        label("MATH_BLITZ", "x2")
        label("SLIDING_TILE", "15")
        art("RHYTHM_TAP", GameArt.ICON_DROP)
        label("STACK_TOWER", "|||")
        art("TWIN_SCENES", GameArt.ICON_STAR)
    }

    fun glyph(type: ChallengeType): GameGlyph = glyphs[type] ?: GameGlyph.Art(GameArt.ICON_STAR)
    fun style(type: ChallengeType): SkillStyle = type.skill.style()
}

/** Square gradient tile with the game's glyph. Decorative: callers give the game's name to TalkBack. */
@Composable
fun GameIcon(type: ChallengeType, modifier: Modifier = Modifier, size: Dp = 56.dp) {
    val style = GameVisuals.style(type)
    val shape = androidx.compose.foundation.shape.RoundedCornerShape(size * 0.3f)
    Box(
        modifier.size(size).clip(shape).background(style.brush)
            .border(1.dp, Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.5f), Color.Transparent)), shape),
        contentAlignment = Alignment.Center,
    ) {
        // Soft shine in the top-left corner.
        Canvas(Modifier.fillMaxSize()) {
            drawCircle(
                Brush.radialGradient(listOf(Color.White.copy(alpha = 0.35f), Color.Transparent),
                    Offset(this.size.width * 0.25f, this.size.height * 0.2f), this.size.width * 0.7f),
                this.size.width * 0.7f, Offset(this.size.width * 0.25f, this.size.height * 0.2f),
            )
        }
        when (val g = GameVisuals.glyph(type)) {
            is GameGlyph.Art -> Canvas(Modifier.size(size * 0.62f)) {
                val r = this.size.minDimension / 2f
                drawIcon(g.icon, Offset(r, r + r * 0.06f), r * 0.86f, Color.Black.copy(alpha = 0.22f))
                drawIcon(g.icon, Offset(r, r), r * 0.86f, Color.White)
            }
            is GameGlyph.Label -> Text(
                g.text, color = Color.White, fontFamily = AppFonts.display, fontWeight = FontWeight.Black,
                fontSize = (size.value * if (g.text.length <= 2) 0.4f else if (g.text.length <= 3) 0.3f else 0.22f).sp,
                maxLines = 1,
            )
        }
    }
}

/** Library tile: art, name, skill and the medal for the best score so far. */
@Composable
fun GameCard(
    type: ChallengeType,
    best: Int,
    plays: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val style = GameVisuals.style(type)
    val medal = Medal.forScore(best)
    Column(
        modifier
            .pressable(onClick)
            .clip(Shapes.tile)
            .background(Palette.Surface1)
            .border(1.dp, Brush.verticalGradient(listOf(style.start.copy(alpha = 0.55f), Palette.Outline)), Shapes.tile)
            .semantics(mergeDescendants = true) {
                contentDescription = buildString {
                    append(type.title).append(", ").append(style.label)
                    if (plays == 0) append(", new") else append(", best $best, ${medal.label} medal")
                }
            },
    ) {
        Box(
            Modifier.fillMaxWidth().height(88.dp)
                .background(Brush.verticalGradient(listOf(style.start.copy(alpha = 0.35f), Color.Transparent))),
            contentAlignment = Alignment.Center,
        ) {
            GameIcon(type, size = 60.dp)
            if (plays == 0) {
                Text(
                    "NEW", Modifier.align(Alignment.TopEnd).padding(8.dp).clip(Shapes.pill).background(Palette.Accent)
                        .padding(horizontal = 8.dp, vertical = 2.dp),
                    color = Color(0xFF00262D), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Black,
                )
            }
        }
        Column(Modifier.padding(start = 12.dp, end = 12.dp, bottom = 12.dp)) {
            Text(type.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(style.label, style = MaterialTheme.typography.labelMedium, color = style.start,
                    modifier = Modifier.weight(1f))
                if (plays > 0) {
                    Text("$best", style = MaterialTheme.typography.labelLarge, color = Palette.TextDim)
                    Spacer(Modifier.width(4.dp))
                }
                MedalBadge(medal, size = 18.dp)
            }
        }
    }
}

/** Compact horizontal-row card for carousels on the home screen. */
@Composable
fun MiniGameCard(type: ChallengeType, best: Int, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier.width(104.dp).pressable(onClick)
            .semantics(mergeDescendants = true) { contentDescription = "${type.title}, best $best" },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box {
            GameIcon(type, size = 80.dp)
            val medal = Medal.forScore(best)
            if (medal != Medal.NONE) MedalBadge(medal, Modifier.align(Alignment.BottomEnd).padding(2.dp), size = 22.dp)
        }
        Spacer(Modifier.height(8.dp))
        Text(type.title, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis,
            color = Palette.Text)
    }
}
