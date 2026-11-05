package com.microchallenge.core.store

import com.microchallenge.core.engine.DifficultyRating
import com.microchallenge.core.engine.Progression
import com.microchallenge.core.engine.XpGain
import com.microchallenge.core.engine.StreakState
import com.microchallenge.core.engine.StreakTracker
import com.microchallenge.core.model.ChallengeResult
import com.microchallenge.core.model.ChallengeType

/** Summary of a challenge type for the stats screen. */
data class TypeStats(
    val type: ChallengeType,
    val plays: Int,
    val best: Int,
    val average: Int,
    val rating: Double,
)

/** One stored history row. */
data class HistoryEntry(val type: ChallengeType, val score: Int, val day: Long)

/**
 * All persistence goes through here, on top of a TinyDB-style [KeyValueStore].
 * Keys are flat strings, so the same layout works in App Inventor's TinyDB.
 */
class ProgressRepository(private val store: KeyValueStore) {

    companion object {
        const val HISTORY_LIMIT = 60
        private const val K_HISTORY = "history"
        private const val K_RECENT = "recent_types"
        private const val K_TOTAL = "total_plays"
        private const val K_STREAK_CUR = "streak_current"
        private const val K_STREAK_BEST = "streak_best"
        private const val K_STREAK_DAY = "streak_last_day"
        private const val K_STREAK_FREEZE = "streak_freezes"
        private const val K_DAILY_DONE = "daily_done_day"
        private const val K_DAILY_SCORE = "daily_score"
        private const val K_XP = "xp_total"
        private const val K_COMBO = "hot_combo"
        private fun kBest(t: ChallengeType) = "best_${t.name}"
        private fun kPlays(t: ChallengeType) = "plays_${t.name}"
        private fun kSum(t: ChallengeType) = "sum_${t.name}"
        private fun kRating(t: ChallengeType) = "rating_x100_${t.name}"
    }

    fun totalPlays(): Int = store.getInt(K_TOTAL)

    fun xp(): Int = store.getInt(K_XP)

    /** Current hot-run combo (back-to-back scores >= [Progression.HOT_SCORE]). */
    fun combo(): Int = store.getInt(K_COMBO)

    /** XP earned by the most recent [record] call. */
    var lastXpGain: XpGain = XpGain(0, 0, 0, 0)
        private set

    fun best(type: ChallengeType): Int = store.getInt(kBest(type))

    fun plays(type: ChallengeType): Int = store.getInt(kPlays(type))

    fun playCounts(): Map<ChallengeType, Int> = ChallengeType.entries.associateWith { plays(it) }

    /** Ratings stored as int x100 so TinyDB-style stores need no float support. */
    fun rating(type: ChallengeType): Double {
        val raw = store.getInt(kRating(type), -1)
        return if (raw < 0) DifficultyRating.START else raw / 100.0
    }

    fun nextDifficulty(type: ChallengeType): Int = DifficultyRating.level(rating(type))

    fun streak(): StreakState {
        val day = store.getLong(K_STREAK_DAY, Long.MIN_VALUE)
        return StreakState(
            current = store.getInt(K_STREAK_CUR),
            best = store.getInt(K_STREAK_BEST),
            lastPlayedDay = day,
            freezes = store.getInt(K_STREAK_FREEZE),
        )
    }

    fun recentTypes(): List<ChallengeType> =
        store.getString(K_RECENT).split(',').mapNotNull { n -> ChallengeType.entries.find { it.name == n } }

    fun history(): List<HistoryEntry> =
        store.getString(K_HISTORY).split(';').mapNotNull { row ->
            val p = row.split(':')
            if (p.size != 3) return@mapNotNull null
            val type = ChallengeType.entries.find { it.name == p[0] } ?: return@mapNotNull null
            val score = p[1].toIntOrNull() ?: return@mapNotNull null
            val day = p[2].toLongOrNull() ?: return@mapNotNull null
            HistoryEntry(type, score, day)
        }

    fun isDailyDone(today: Long): Boolean = store.getLong(K_DAILY_DONE, Long.MIN_VALUE) == today

    fun dailyScore(today: Long): Int? = if (isDailyDone(today)) store.getInt(K_DAILY_SCORE) else null

    fun stats(type: ChallengeType): TypeStats {
        val plays = plays(type)
        val avg = if (plays == 0) 0 else (store.getLong(kSum(type)) / plays).toInt()
        return TypeStats(type, plays, best(type), avg, rating(type))
    }

    /**
     * Record a finished play. Returns true when it is a new personal best.
     * @param today local epoch day.
     */
    fun record(result: ChallengeResult, today: Long, isDaily: Boolean = false): Boolean {
        val t = result.type
        val isBest = result.score > best(t)
        if (isBest) store.putInt(kBest(t), result.score)
        store.putInt(kPlays(t), plays(t) + 1)
        store.putLong(kSum(t), store.getLong(kSum(t)) + result.score)
        store.putInt(K_TOTAL, totalPlays() + 1)

        val newRating = DifficultyRating.update(rating(t), result.performance)
        store.putInt(kRating(t), Math.round(newRating * 100).toInt())

        val s = StreakTracker.onPlay(streak(), today)
        store.putInt(K_STREAK_CUR, s.current)
        store.putInt(K_STREAK_BEST, s.best)
        store.putLong(K_STREAK_DAY, s.lastPlayedDay)
        store.putInt(K_STREAK_FREEZE, s.freezes)

        val recent = (recentTypes() + t).takeLast(5)
        store.putString(K_RECENT, recent.joinToString(",") { it.name })

        val hist = (history() + HistoryEntry(t, result.score, today)).takeLast(HISTORY_LIMIT)
        store.putString(K_HISTORY, hist.joinToString(";") { "${it.type.name}:${it.score}:${it.day}" })

        val combo = Progression.nextCombo(combo(), result.score)
        store.putInt(K_COMBO, combo)
        lastXpGain = Progression.xpFor(result.score, s.current, combo, isBest)
        store.putInt(K_XP, xp() + lastXpGain.total)

        if (isDaily && !isDailyDone(today)) {
            store.putLong(K_DAILY_DONE, today)
            store.putInt(K_DAILY_SCORE, result.score)
        }
        return isBest
    }

    fun reset() = store.clear()
}
