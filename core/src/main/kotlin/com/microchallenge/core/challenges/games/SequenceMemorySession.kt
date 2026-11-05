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
 * Pattern Recall (Simon-style): tiles on a grid light up one after another, then the
 * player taps them back in the same order. A wrong tap ends the run.
 *
 * Grid is 3x3, or 4x4 from level 7. Sequence length is 3 + level/2 (3..8) and each
 * tile is lit for 600 - 30*level ms.
 */
class SequenceMemorySession(
    difficulty: Int,
    random: Random,
    private val clock: Clock,
) : ChallengeSession {

    enum class Phase { PLAYBACK, INPUT, DONE }

    /** Outcome of a [tap]. */
    enum class TapResult { CORRECT, COMPLETED, WRONG, IGNORED }

    override val type: ChallengeType = ChallengeType.SEQUENCE_MEMORY
    override val difficulty: Int = difficulty.coerceIn(MIN_DIFFICULTY, MAX_DIFFICULTY)

    private val startMs: Long = clock.monotonicMs()

    /** Tiles per side. */
    val gridSize: Int = if (this.difficulty >= 7) 4 else 3

    val tileCount: Int get() = gridSize * gridSize

    /** Tile indices (row-major, 0 until [tileCount]) in the order they light up. */
    val sequence: List<Int> = buildList {
        val len = 3 + this@SequenceMemorySession.difficulty / 2
        while (size < len) {
            val t = random.nextInt(gridSize * gridSize)
            if (t != lastOrNull()) add(t) // no immediate repeats: they are hard to see
        }
    }

    /** How long each tile stays lit, ms. */
    val litMs: Long = 600L - this.difficulty * 30L

    /** Dark gap between two lit tiles, ms. */
    val gapMs: Long = 200L - this.difficulty * 10L

    /** Session-relative time when playback ends and input begins. */
    val playbackEndMs: Long = LEAD_IN_MS + sequence.size * (litMs + gapMs)

    /** Tiles tapped correctly so far. */
    var progress: Int = 0
        private set

    /** Tile tapped by mistake, or -1. */
    var wrongTile: Int = -1
        private set

    /** Last correctly tapped tile, or -1 (handy for a flash in the UI). */
    var lastCorrectTile: Int = -1
        private set

    private var ended = false

    private fun elapsed(): Long = clock.monotonicMs() - startMs

    val phase: Phase
        get() {
            val t = elapsed()
            return when {
                ended || t >= CHALLENGE_DURATION_MS -> Phase.DONE
                t < playbackEndMs -> Phase.PLAYBACK
                else -> Phase.INPUT
            }
        }

    override val isComplete: Boolean get() = ended

    /** The tile lit right now during playback, or -1 (between tiles or after playback). */
    fun litTile(): Int {
        val t = elapsed() - LEAD_IN_MS
        if (phase != Phase.PLAYBACK || t < 0) return -1
        val slot = litMs + gapMs
        val i = (t / slot).toInt()
        if (i >= sequence.size) return -1
        return if (t % slot < litMs) sequence[i] else -1
    }

    /** Position (0-based) of the tile now lit in playback, or -1. */
    fun litStep(): Int {
        val tile = litTile()
        if (tile < 0) return -1
        return ((elapsed() - LEAD_IN_MS) / (litMs + gapMs)).toInt()
    }

    fun tap(index: Int): TapResult {
        if (phase != Phase.INPUT || index !in 0 until tileCount) return TapResult.IGNORED
        return if (index == sequence[progress]) {
            progress++
            lastCorrectTile = index
            if (progress == sequence.size) {
                ended = true
                TapResult.COMPLETED
            } else {
                TapResult.CORRECT
            }
        } else {
            wrongTile = index
            ended = true
            TapResult.WRONG
        }
    }

    override fun finish(): ChallengeResult {
        ended = true
        val duration = elapsed().coerceIn(0L, CHALLENGE_DURATION_MS)
        val len = sequence.size
        val frac = progress.toDouble() / len
        val perfect = progress == len
        val raw = (650.0 * frac + if (perfect) 150.0 else 0.0) * Scoring.difficultyBonus(difficulty)
        val performance = (0.85 * frac + if (perfect) 0.15 else 0.0).coerceIn(0.0, 1.0)
        val detail = when {
            perfect -> "Perfect: $len/$len tiles"
            progress == 0 && wrongTile < 0 -> "No taps"
            else -> "$progress/$len tiles"
        }
        return ChallengeResult(
            type = type,
            score = Scoring.clampScore(raw),
            performance = performance,
            difficulty = difficulty,
            correct = progress,
            mistakes = if (wrongTile >= 0) 1 else 0,
            durationMs = duration,
            detail = detail,
        )
    }

    companion object {
        /** Pause before the first tile lights, ms. */
        const val LEAD_IN_MS = 500L
    }
}
