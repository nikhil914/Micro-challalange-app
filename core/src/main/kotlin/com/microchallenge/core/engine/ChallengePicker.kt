package com.microchallenge.core.engine

import com.microchallenge.core.model.ChallengeType
import kotlin.random.Random

/**
 * Chooses the challenge shown when the app opens.
 * - never repeats the last 2 types (variety)
 * - favours types played least (balanced skill coverage)
 * - [dailyType] gives every user the same challenge for a given day
 */
object ChallengePicker {
    fun pick(
        recent: List<ChallengeType>,
        playCounts: Map<ChallengeType, Int>,
        random: Random,
        enabled: List<ChallengeType> = ChallengeType.entries,
    ): ChallengeType {
        require(enabled.isNotEmpty())
        val avoid = recent.takeLast(2).toSet()
        val pool = enabled.filterNot { it in avoid }.ifEmpty { enabled }
        val maxCount = pool.maxOf { playCounts[it] ?: 0 }
        // weight = 1 + how far behind the most-played type this one is
        val weights = pool.map { 1 + maxCount - (playCounts[it] ?: 0) }
        var roll = random.nextInt(weights.sum())
        for ((i, w) in weights.withIndex()) {
            if (roll < w) return pool[i]
            roll -= w
        }
        return pool.last()
    }

    /** Same type for everyone on a given epoch day. */
    fun dailyType(epochDay: Long, enabled: List<ChallengeType> = ChallengeType.entries): ChallengeType =
        enabled[Random(epochDay * 7919 + 17).nextInt(enabled.size)]

    /** Seed for the daily challenge's content, so all users get identical puzzles. */
    fun dailySeed(epochDay: Long): Long = epochDay * 1_000_003L + 42L
}
