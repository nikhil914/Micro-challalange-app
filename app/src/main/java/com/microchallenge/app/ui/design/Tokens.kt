package com.microchallenge.app.ui.design

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.microchallenge.core.model.Skill

/**
 * Design tokens for the "neon arcade, softened" look. Every color has one job:
 * flame orange is always streaks, gold is always XP and rewards, mint is always
 * correct and coral is always a mistake. In-game colors stay in [com.microchallenge.app.ui.GameColors].
 */
object Palette {
    val Night = Color(0xFF0F0C24)
    val Surface1 = Color(0xFF1A1638)
    val Surface2 = Color(0xFF241F4A)
    val Surface3 = Color(0xFF2F295C)
    val Outline = Color(0x26FFFFFF)
    val Highlight = Color(0x14FFFFFF)

    val Text = Color(0xFFF4F2FF)
    val TextDim = Color(0xFFBDB8DE)
    val TextFaint = Color(0xFF8A84B5)

    val Primary = Color(0xFF7C5CFF)
    val PrimaryEdge = Color(0xFF4E33C9)
    val Accent = Color(0xFF22D3EE)

    val Streak = Color(0xFFFF8A3D)
    val StreakHot = Color(0xFFFF4D2E)
    val Xp = Color(0xFFFFC83D)
    val XpEdge = Color(0xFFC98A00)
    val Correct = Color(0xFF34E0A1)
    val CorrectEdge = Color(0xFF119A6A)
    val Wrong = Color(0xFFFF6B6B)

    val Gold = Color(0xFFFFC83D)
    val Silver = Color(0xFFD5DBEA)
    val Bronze = Color(0xFFE09A5B)
}

/** One gradient per skill, used on cards, chips, headers and the in-game timer. */
data class SkillStyle(val label: String, val start: Color, val end: Color) {
    val brush: Brush get() = Brush.linearGradient(listOf(start, end))
    val horizontal: Brush get() = Brush.horizontalGradient(listOf(start, end))
}

fun Skill.style(): SkillStyle = when (this) {
    Skill.MEMORY -> SkillStyle("Memory", Color(0xFF8B5CF6), Color(0xFFEC4899))
    Skill.SPEED -> SkillStyle("Speed", Color(0xFFFB923C), Color(0xFFEF4444))
    Skill.ATTENTION -> SkillStyle("Attention", Color(0xFF22D3EE), Color(0xFF14B8A6))
    Skill.LOGIC -> SkillStyle("Logic", Color(0xFFA3E635), Color(0xFF22C55E))
    Skill.MATH -> SkillStyle("Math", Color(0xFFFBBF24), Color(0xFFF59E0B))
}

object Shapes {
    val card = RoundedCornerShape(24.dp)
    val tile = RoundedCornerShape(20.dp)
    val button = RoundedCornerShape(18.dp)
    val pill = RoundedCornerShape(50)
}

/** Medal for a game's best score. Thresholds match the star tiers on the result screen. */
enum class Medal(val minScore: Int, val label: String, val color: Color) {
    NONE(0, "No medal", Color.Transparent),
    BRONZE(300, "Bronze", Palette.Bronze),
    SILVER(600, "Silver", Palette.Silver),
    GOLD(850, "Gold", Palette.Gold);

    companion object {
        fun forScore(score: Int): Medal = entries.last { score >= it.minScore }
    }
}

/** 0 to 3 stars for one play, on the same tiers as [Medal]. */
fun starsFor(score: Int): Int = Medal.forScore(score).ordinal

/** True when the user asked for less motion: confetti, drifting glows and loops are skipped. */
val LocalReduceMotion = staticCompositionLocalOf { false }
