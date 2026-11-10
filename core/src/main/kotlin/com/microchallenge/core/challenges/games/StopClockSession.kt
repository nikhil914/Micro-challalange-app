package com.microchallenge.core.challenges.games

import com.microchallenge.core.challenges.ChallengeSession
import com.microchallenge.core.challenges.MAX_DIFFICULTY
import com.microchallenge.core.challenges.MIN_DIFFICULTY
import com.microchallenge.core.challenges.Scoring
import com.microchallenge.core.engine.Clock
import com.microchallenge.core.model.CHALLENGE_DURATION_MS
import com.microchallenge.core.model.ChallengeResult
import com.microchallenge.core.model.ChallengeType
import kotlin.math.abs
import kotlin.random.Random

/**
 * Stop the Clock: a stopwatch runs up from 0.00; stop it as close to the target as you can.
 * Three rounds; the next one starts [GAP_MS] after a stop.
 *
 * Difficulty: from level 3 the readout fades out part-way (after 80% of the target,
 * down to after 0.5 s at level 10) so the rest must be timed blind,
 * and the error that still earns points shrinks (600 ms down to 330 ms).
 */
class StopClockSession(
    difficulty: Int,
    private val random: Random,
    private val clock: Clock,
) : ChallengeSession {

    override val type: ChallengeType = ChallengeType.STOP_CLOCK
    override val difficulty: Int = difficulty.coerceIn(MIN_DIFFICULTY, MAX_DIFFICULTY)

    private val startMs: Long = clock.monotonicMs()

    /** Targets for all rounds, fixed up front so three rounds always fit into the window. */
    val targets: List<Long> = pickTargets()

    /** Mean error (ms) at or above which the score is 0. */
    val zeroErrorMs: Long = 600L - (this.difficulty - 1) * 30L

    private var roundStartMs: Long = startMs
    private var running: Boolean = true
    private var stoppedAtMs: Long = 0L
    private var frozenMs: Long = 0L
    private val errors = mutableListOf<Long>()

    /** Signed errors of the finished rounds: positive = stopped late, negative = early. */
    val errorsMs: List<Long> get() = errors.toList()

    /** Rounds finished so far (0..[ROUNDS]). */
    val roundsDone: Int get() = errors.size

    /** Signed error of the latest stop in ms, null before the first one. */
    val lastErrorMs: Long? get() = errors.lastOrNull()

    /** Target of the round on screen (the one just stopped while its result is showing). */
    val targetMs: Long
        get() {
            sync()
            val index = if (running) errors.size else errors.size - 1
            return targets[index.coerceIn(0, ROUNDS - 1)]
        }

    /** Ends shortly after the last stop, so its result can still be read. */
    override val isComplete: Boolean
        get() = errors.size >= ROUNDS && clock.monotonicMs() - stoppedAtMs >= GAP_MS

    private fun now(): Long = clock.monotonicMs().coerceAtMost(startMs + CHALLENGE_DURATION_MS)

    private fun elapsedMs(): Long = (clock.monotonicMs() - startMs).coerceIn(0L, CHALLENGE_DURATION_MS)

    /** Starts the next round once the pause after a stop is over. */
    private fun sync() {
        if (!running && errors.size < ROUNDS && clock.monotonicMs() >= stoppedAtMs + GAP_MS) {
            roundStartMs = stoppedAtMs + GAP_MS
            running = true
        }
    }

    /** True while the stopwatch is counting. False in the pause after a stop and when all rounds are done. */
    fun isRunning(): Boolean { sync(); return running }

    /** Stopwatch reading in ms: live while running, frozen at the stopped value during the pause. */
    fun runningMs(): Long {
        sync()
        return if (running) (now() - roundStartMs).coerceAtLeast(0L) else frozenMs
    }

    /** Reading after which the display hides this round, or null when it never hides. */
    fun hideAfterMs(): Long? {
        val fraction = HIDE_FRACTION[difficulty - 1]
        return if (fraction < 0.0) null else (targetMs * fraction).toLong().coerceAtLeast(MIN_HIDE_MS)
    }

    /** False once the running readout has started to hide. Always true while a result is showing. */
    fun displayVisible(): Boolean {
        sync()
        if (!running) return true
        val hideAt = hideAfterMs() ?: return true
        return runningMs() < hideAt
    }

    /** Readout opacity 0..1: fades over [FADE_MS] once [displayVisible] turns false. */
    fun displayAlpha(): Float {
        sync()
        if (!running) return 1f
        val hideAt = hideAfterMs() ?: return 1f
        val over = runningMs() - hideAt
        return (1f - over.toFloat() / FADE_MS).coerceIn(0f, 1f)
    }

    /**
     * Stop the running stopwatch. Returns the signed error in ms (positive = late),
     * or null when ignored: during the pause, after the last round, or after 10 s.
     */
    fun stop(): Long? {
        if (elapsedMs() >= CHALLENGE_DURATION_MS) return null
        sync()
        if (!running) return null
        val nowMs = clock.monotonicMs()
        val reading = nowMs - roundStartMs
        val error = reading - targets[errors.size]
        errors += error
        frozenMs = reading
        stoppedAtMs = nowMs
        running = false
        return error
    }

    /** Mean absolute error over the finished rounds, 0 when none. */
    fun meanAbsErrorMs(): Double = if (errors.isEmpty()) 0.0 else errors.sumOf { abs(it) }.toDouble() / errors.size

    override fun finish(): ChallengeResult {
        val done = errors.size
        // 1.0 at <= 30 ms mean error, falling linearly to 0 at zeroErrorMs; skipped rounds scale it down.
        val precision = if (done == 0) 0.0 else
            (1.0 - (meanAbsErrorMs() - PERFECT_ERROR_MS) / (zeroErrorMs - PERFECT_ERROR_MS)).coerceIn(0.0, 1.0)
        val quality = precision * done / ROUNDS
        val good = errors.count { abs(it) <= GOOD_ERROR_MS }
        val detail = if (done == 0) "No stops" else "Avg off by ${Math.round(meanAbsErrorMs())} ms"
        return ChallengeResult(
            type = type,
            score = Scoring.clampScore(quality * 1000.0),
            performance = quality.coerceIn(0.0, 1.0),
            difficulty = difficulty,
            correct = good,
            mistakes = done - good,
            durationMs = elapsedMs(),
            detail = detail,
            timestampMs = clock.wallMs(),
        )
    }

    // ---------------------------------------------------------------- generation

    /** Targets on a 0.1 s raster in 1.5..3.0 s whose sum leaves room for the pauses and some overshoot. */
    private fun pickTargets(): List<Long> {
        while (true) {
            val picked = List(ROUNDS) { MIN_TARGET_MS + random.nextInt(TARGET_STEPS + 1) * TARGET_STEP_MS }
            if (picked.sum() <= MAX_TARGET_SUM_MS) return picked
        }
    }

    companion object {
        const val ROUNDS = 3
        /** Pause between a stop and the next round. */
        const val GAP_MS = 500L
        const val MIN_TARGET_MS = 1500L
        const val MAX_TARGET_MS = 3000L
        const val TARGET_STEP_MS = 100L
        private const val TARGET_STEPS = 15
        /** 3 targets + 2 pauses stay under 8.2 s, leaving 1.8 s for late stops. */
        const val MAX_TARGET_SUM_MS = 7200L
        /** Mean error up to which the score is a full 1000. */
        const val PERFECT_ERROR_MS = 30L
        /** A stop this close counts as "correct" in the result. */
        const val GOOD_ERROR_MS = 150L
        const val MIN_HIDE_MS = 500L
        const val FADE_MS = 250L

        /** Fraction of the target after which the readout hides, per level; negative = never. */
        private val HIDE_FRACTION = doubleArrayOf(-1.0, -1.0, 0.8, 0.7, 0.6, 0.5, 0.4, 0.33, 0.25, 0.0)
    }
}
