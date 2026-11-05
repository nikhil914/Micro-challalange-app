package com.microchallenge.core.engine

/**
 * Time source abstraction. Android uses SystemClock.elapsedRealtimeNanos()
 * (monotonic, not affected by wall-clock changes); tests use [FakeClock].
 */
interface Clock {
    /** Monotonic time in milliseconds, for measuring durations. */
    fun monotonicMs(): Long

    /** Wall-clock epoch millis, for dates (streaks, daily challenge). */
    fun wallMs(): Long
}

object SystemClockJvm : Clock {
    override fun monotonicMs(): Long = System.nanoTime() / 1_000_000
    override fun wallMs(): Long = System.currentTimeMillis()
}

class FakeClock(var mono: Long = 0L, var wall: Long = 0L) : Clock {
    override fun monotonicMs() = mono
    override fun wallMs() = wall
    fun advance(ms: Long) { mono += ms; wall += ms }
}
