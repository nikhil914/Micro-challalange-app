package com.microchallenge.core.challenges.games

import com.microchallenge.core.challenges.ChallengeSession
import com.microchallenge.core.challenges.MAX_DIFFICULTY
import com.microchallenge.core.challenges.MIN_DIFFICULTY
import com.microchallenge.core.challenges.Scoring
import com.microchallenge.core.engine.Clock
import com.microchallenge.core.model.CHALLENGE_DURATION_MS
import com.microchallenge.core.model.ChallengeResult
import com.microchallenge.core.model.ChallengeType
import kotlin.random.Random

/**
 * Glow Grid (visual pattern memory): several tiles glow at the same time, then flip dark.
 * The player taps every tile that glowed, in any order. Two wrong taps fail the round.
 *
 * Each solved round grows the pattern by one tile, and the grid grows with it
 * (3x3 → 4x4 → 5x5). Start size and study time scale with difficulty.
 */
class GlowGridSession(
    difficulty: Int,
    private val random: Random,
    private val clock: Clock,
) : ChallengeSession {

    enum class Phase { SHOW, INPUT, FEEDBACK, DONE }

    enum class TapResult { HIT, ROUND_CLEARED, MISS, ROUND_FAILED, IGNORED }

    /** One generated round. [pattern] holds row-major tile indices. */
    data class Round(val gridSize: Int, val pattern: Set<Int>, val studyMs: Long)

    override val type: ChallengeType = ChallengeType.GLOW_GRID
    override val difficulty: Int = difficulty.coerceIn(MIN_DIFFICULTY, MAX_DIFFICULTY)

    private val startMs: Long = clock.monotonicMs()

    /** Pattern size of the next round; grows on success, shrinks (min start) on failure. */
    private var level: Int = 3 + this.difficulty / 3

    var round: Round = newRound()
        private set

    /** Session-relative time the current round started. */
    private var roundStartMs: Long = 0L

    /** Session-relative time the current round was resolved, or -1 while open. */
    private var resolvedAtMs: Long = -1L

    /** Whether the last resolved round was cleared. */
    var lastRoundCleared: Boolean = false
        private set

    val found: MutableSet<Int> = mutableSetOf()
    val wrong: MutableSet<Int> = mutableSetOf()

    var roundsCleared: Int = 0
        private set
    var roundsPlayed: Int = 0
        private set
    var tilesFound: Int = 0
        private set
    var mistakes: Int = 0
        private set

    private var ended = false

    private fun elapsed(): Long = clock.monotonicMs() - startMs

    private fun newRound(): Round {
        val size = when {
            level <= 4 -> 3
            level <= 7 -> 4
            else -> 5
        }
        val count = level.coerceAtMost(size * size - 2)
        val pattern = (0 until size * size).shuffled(random).take(count).toSet()
        val study = (1300L - difficulty * 50L + count * 60L).coerceIn(600L, 1600L)
        return Round(size, pattern, study)
    }

    /** Moves to the next round once feedback has shown long enough. */
    private fun advanceIfNeeded() {
        if (resolvedAtMs >= 0 && elapsed() - resolvedAtMs >= FEEDBACK_MS) {
            roundStartMs = resolvedAtMs + FEEDBACK_MS
            resolvedAtMs = -1L
            found.clear()
            wrong.clear()
            round = newRound()
        }
    }

    val phase: Phase
        get() {
            if (ended || elapsed() >= CHALLENGE_DURATION_MS) return Phase.DONE
            advanceIfNeeded()
            val t = elapsed() - roundStartMs
            return when {
                resolvedAtMs >= 0 -> Phase.FEEDBACK
                t < LEAD_IN_MS + round.studyMs -> Phase.SHOW
                else -> Phase.INPUT
            }
        }

    /** True while the pattern is actually visible (after the short lead-in). */
    fun patternVisible(): Boolean {
        val t = elapsed() - roundStartMs
        return phase == Phase.SHOW && t >= LEAD_IN_MS
    }

    /** 0..1 progress through the study window, for a countdown ring. */
    fun studyProgress(): Float {
        val t = elapsed() - roundStartMs - LEAD_IN_MS
        return (t.toFloat() / round.studyMs).coerceIn(0f, 1f)
    }

    override val isComplete: Boolean get() = ended

    fun tap(index: Int): TapResult {
        if (phase != Phase.INPUT || index !in 0 until round.gridSize * round.gridSize) return TapResult.IGNORED
        if (index in found || index in wrong) return TapResult.IGNORED
        return if (index in round.pattern) {
            found += index
            tilesFound++
            if (found.size == round.pattern.size) {
                resolve(cleared = true)
                TapResult.ROUND_CLEARED
            } else {
                TapResult.HIT
            }
        } else {
            wrong += index
            mistakes++
            if (wrong.size >= MAX_WRONG) {
                resolve(cleared = false)
                TapResult.ROUND_FAILED
            } else {
                TapResult.MISS
            }
        }
    }

    private fun resolve(cleared: Boolean) {
        resolvedAtMs = elapsed()
        lastRoundCleared = cleared
        roundsPlayed++
        if (cleared) {
            roundsCleared++
            level++
        } else {
            level = (level - 1).coerceAtLeast(3)
        }
    }

    override fun finish(): ChallengeResult {
        ended = true
        val duration = elapsed().coerceIn(0L, CHALLENGE_DURATION_MS)
        val raw = (tilesFound * 45.0 + roundsCleared * 90.0 - mistakes * 20.0) * Scoring.difficultyBonus(difficulty)
        val acc = Scoring.accuracy(tilesFound, mistakes)
        val performance = (0.6 * acc + 0.4 * (roundsCleared / 3.0).coerceAtMost(1.0)).coerceIn(0.0, 1.0)
        val detail = when {
            tilesFound == 0 && mistakes == 0 -> "No taps"
            else -> "$roundsCleared rounds · $tilesFound tiles"
        }
        return ChallengeResult(
            type = type,
            score = Scoring.clampScore(raw),
            performance = performance,
            difficulty = difficulty,
            correct = tilesFound,
            mistakes = mistakes,
            durationMs = duration,
            detail = detail,
        )
    }

    companion object {
        /** Blank pause before the pattern glows, ms. */
        const val LEAD_IN_MS = 300L

        /** How long the round result is shown, ms. */
        const val FEEDBACK_MS = 650L

        /** Wrong taps that fail a round. */
        const val MAX_WRONG = 2
    }
}
