package com.microchallenge.core.engine

/**
 * Game-style progression for a teen/adult audience: XP, ranks and a "hot run"
 * combo for back-to-back strong scores. Pure logic.
 */
enum class Rank(val title: String, val minXp: Int) {
    ROOKIE("Rookie", 0),
    CHALLENGER("Challenger", 500),
    SHARP("Sharp", 1_500),
    ELITE("Elite", 4_000),
    MASTERMIND("Mastermind", 9_000),
    LEGEND("Legend", 20_000),
}

data class XpGain(val base: Int, val streakBonus: Int, val comboBonus: Int, val bestBonus: Int) {
    val total: Int get() = base + streakBonus + comboBonus + bestBonus
}

object Progression {
    /** A score at or above this keeps the hot run going. */
    const val HOT_SCORE = 700

    fun rankFor(xp: Int): Rank = Rank.entries.last { xp >= it.minXp }

    fun nextRank(xp: Int): Rank? = Rank.entries.firstOrNull { it.minXp > xp }

    /** 0..1 progress from the current rank to the next; 1.0 at max rank. */
    fun rankProgress(xp: Int): Float {
        val cur = rankFor(xp)
        val next = nextRank(xp) ?: return 1f
        return (xp - cur.minXp).toFloat() / (next.minXp - cur.minXp)
    }

    /** New combo after a play: +1 on a hot score, otherwise reset. */
    fun nextCombo(combo: Int, score: Int): Int = if (score >= HOT_SCORE) combo + 1 else 0

    /**
     * XP for one play. Base = score / 10 (0..100). Streak adds up to +50 %,
     * a hot-run combo adds 10 XP per step (cap 50), a personal best adds 25.
     */
    fun xpFor(score: Int, streakDays: Int, combo: Int, isBest: Boolean): XpGain {
        val base = score / 10
        val streakBonus = base * streakDays.coerceIn(0, 10) / 20
        val comboBonus = (combo * 10).coerceAtMost(50)
        return XpGain(base, streakBonus, comboBonus, if (isBest) 25 else 0)
    }
}
