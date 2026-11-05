package com.microchallenge.core.engine

/**
 * Wraps a [Clock] so that time stops while paused (app in background).
 * The host passes this to sessions and the countdown, so a phone call
 * mid-challenge does not eat the user's 10 seconds or skew reaction times.
 */
class PausableClock(private val base: Clock) : Clock {
    private var pausedAt = -1L
    private var pausedTotal = 0L

    val isPaused: Boolean get() = pausedAt >= 0

    fun pause() { if (pausedAt < 0) pausedAt = base.monotonicMs() }

    fun resume() {
        if (pausedAt >= 0) { pausedTotal += base.monotonicMs() - pausedAt; pausedAt = -1L }
    }

    override fun monotonicMs(): Long =
        (if (pausedAt >= 0) pausedAt else base.monotonicMs()) - pausedTotal

    override fun wallMs(): Long = base.wallMs()
}
