package com.microchallenge.core.engine

/**
 * Day-based streak using epoch-day numbers (days since 1970-01-01 in the
 * user's local zone; the caller converts). Pure logic, easy to test.
 *
 * One "streak freeze" is earned every 7 days of streak and is used
 * automatically to cover a single missed day. Gentle, not punishing.
 */
data class StreakState(
    val current: Int = 0,
    val best: Int = 0,
    val lastPlayedDay: Long = Long.MIN_VALUE,
    val freezes: Int = 0,
)

object StreakTracker {
    const val MAX_FREEZES = 2

    fun onPlay(state: StreakState, today: Long): StreakState {
        if (state.lastPlayedDay == today) return state
        val gap = if (state.lastPlayedDay == Long.MIN_VALUE) Long.MAX_VALUE else today - state.lastPlayedDay
        var freezes = state.freezes
        val current = when {
            gap == 1L -> state.current + 1
            gap == 2L && freezes > 0 -> { freezes--; state.current + 1 }
            gap < 0L -> return state // clock went backwards; ignore
            else -> 1
        }
        if (current > 0 && current % 7 == 0) freezes = (freezes + 1).coerceAtMost(MAX_FREEZES)
        return StreakState(current, maxOf(state.best, current), today, freezes)
    }

    /** Streak shown on the home screen: 0 if the user already missed it beyond rescue. */
    fun displayed(state: StreakState, today: Long): Int {
        if (state.lastPlayedDay == Long.MIN_VALUE) return 0
        val gap = today - state.lastPlayedDay
        return when {
            gap <= 1L -> state.current
            gap == 2L && state.freezes > 0 -> state.current
            else -> 0
        }
    }
}
