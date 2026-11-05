package com.microchallenge.core.engine

import com.microchallenge.core.model.CHALLENGE_DURATION_MS

/**
 * Pure countdown for the 10-second window. UI polls [remainingMs] each frame;
 * no threads or coroutines here so it is trivially testable.
 */
class Countdown(private val clock: Clock, val durationMs: Long = CHALLENGE_DURATION_MS) {
    private var startMs: Long = -1L
    private var pausedAt: Long = -1L
    private var pausedTotal: Long = 0L

    val started: Boolean get() = startMs >= 0

    fun start() {
        startMs = clock.monotonicMs(); pausedTotal = 0L; pausedAt = -1L
    }

    /** Pause when app goes to background, so the user is not penalised. */
    fun pause() { if (started && pausedAt < 0) pausedAt = clock.monotonicMs() }

    fun resume() {
        if (pausedAt >= 0) { pausedTotal += clock.monotonicMs() - pausedAt; pausedAt = -1L }
    }

    fun elapsedMs(): Long {
        if (!started) return 0L
        val now = if (pausedAt >= 0) pausedAt else clock.monotonicMs()
        return (now - startMs - pausedTotal).coerceIn(0L, durationMs)
    }

    fun remainingMs(): Long = durationMs - elapsedMs()

    fun isFinished(): Boolean = started && remainingMs() <= 0L

    /** 1.0 at start, 0.0 at end. Handy for progress bars. */
    fun fractionRemaining(): Float = remainingMs().toFloat() / durationMs
}
