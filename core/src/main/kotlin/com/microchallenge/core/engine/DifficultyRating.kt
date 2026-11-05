package com.microchallenge.core.engine

import com.microchallenge.core.challenges.MAX_DIFFICULTY
import com.microchallenge.core.challenges.MIN_DIFFICULTY

/**
 * Adaptive difficulty per challenge type, using a 1-up/1-down staircase on a
 * continuous rating. Target performance ~0.7 keeps the user in "flow":
 * hard enough to be interesting, easy enough to win most of the time.
 */
object DifficultyRating {
    const val TARGET = 0.7
    const val STEP = 1.2
    const val START = 2.0

    /** New rating after a play with [performance] in 0..1. */
    fun update(rating: Double, performance: Double): Double {
        val delta = (performance.coerceIn(0.0, 1.0) - TARGET) * STEP * 2
        return (rating + delta).coerceIn(MIN_DIFFICULTY.toDouble(), MAX_DIFFICULTY.toDouble())
    }

    /** Integer level used to generate the next challenge. */
    fun level(rating: Double): Int =
        Math.round(rating).toInt().coerceIn(MIN_DIFFICULTY, MAX_DIFFICULTY)
}
