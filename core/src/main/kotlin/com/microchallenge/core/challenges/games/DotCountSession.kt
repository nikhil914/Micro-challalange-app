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
 * One dot of a flash, in a unit square.
 *
 * @param x centre, 0..1 from the left edge.
 * @param y centre, 0..1 from the top edge.
 * @param radius in the same unit (fraction of the square's side).
 * @param isTarget true for dots that count; false for decoys (drawn as a different shape and color).
 */
data class FlashDot(
    val x: Float,
    val y: Float,
    val radius: Float,
    val isTarget: Boolean,
)

/**
 * Dot Flash: dots flash briefly, then pick how many there were.
 * Any answer starts the next round right away.
 *
 * Difficulty: more dots (3-6 up to 9-16), shorter flash (900 ms down to 350 ms),
 * and from level 6 decoy shapes that must not be counted.
 */
class DotCountSession(
    difficulty: Int,
    private val random: Random,
    private val clock: Clock,
) : ChallengeSession {

    /** SHOW: dots visible, no input. ANSWER: dots hidden, choices live. */
    enum class Phase { SHOW, ANSWER }

    override val type: ChallengeType = ChallengeType.DOT_COUNT
    override val difficulty: Int = difficulty.coerceIn(MIN_DIFFICULTY, MAX_DIFFICULTY)

    private val startMs: Long = clock.monotonicMs()
    private var roundStartMs: Long = startMs

    /** How long the dots stay visible each round. */
    val showMs: Long = 900L - (this.difficulty - 1) * 550L / 9L

    /** Smallest and largest target count at this difficulty. */
    val minCount: Int = 3 + (this.difficulty - 1) * 2 / 3
    val maxCount: Int = 6 + (this.difficulty - 1) * 10 / 9

    /** True when decoy shapes are mixed in. */
    val hasDecoys: Boolean = this.difficulty >= 6

    /** All dots of the current round, decoys included. */
    var dots: List<FlashDot> = emptyList()
        private set

    /** Number of target dots this round (the right answer). */
    var targetCount: Int = 0
        private set

    /** Four distinct consecutive counts, ascending. */
    var choices: List<Int> = emptyList()
        private set

    /** Index of [targetCount] in [choices]. Exposed for tests; the UI must not reveal it. */
    var answerIndex: Int = 0
        private set

    var correct: Int = 0
        private set
    var mistakes: Int = 0
        private set

    var lastAnswerCorrect: Boolean? = null
        private set

    /** The right count of the round just answered, for feedback. */
    var lastTargetCount: Int = 0
        private set

    /** Increments on every answer; also the 0-based number of the current round. */
    var answeredCount: Int = 0
        private set

    override val isComplete: Boolean get() = false

    init { newRound() }

    private fun elapsedMs(): Long = (clock.monotonicMs() - startMs).coerceIn(0L, CHALLENGE_DURATION_MS)

    fun phase(): Phase = if (clock.monotonicMs() - roundStartMs < showMs) Phase.SHOW else Phase.ANSWER

    /**
     * Submit the choice at [choiceIndex]. Returns true when correct.
     * Ignored (returns false) while the dots are still showing, after 10 s, or for a bad index.
     */
    fun answer(choiceIndex: Int): Boolean {
        if (elapsedMs() >= CHALLENGE_DURATION_MS) return false
        if (phase() != Phase.ANSWER) return false
        if (choiceIndex !in choices.indices) return false
        val ok = choiceIndex == answerIndex
        if (ok) correct++ else mistakes++
        lastAnswerCorrect = ok
        lastTargetCount = targetCount
        answeredCount++
        roundStartMs = clock.monotonicMs()
        newRound()
        return ok
    }

    override fun finish(): ChallengeResult {
        val raw = (correct * POINTS_CORRECT - mistakes * POINTS_MISTAKE) * Scoring.difficultyBonus(difficulty)
        val accuracy = Scoring.accuracy(correct, mistakes)
        val performance = ((correct / TARGET_CORRECT) * accuracy).coerceIn(0.0, 1.0)
        val detail = when {
            correct + mistakes == 0 -> "No answers"
            mistakes == 0 -> "$correct counted · no misses"
            else -> "$correct counted · $mistakes ${if (mistakes == 1) "miss" else "misses"}"
        }
        return ChallengeResult(
            type = type,
            score = Scoring.clampScore(raw),
            performance = performance,
            difficulty = difficulty,
            correct = correct,
            mistakes = mistakes,
            durationMs = elapsedMs(),
            detail = detail,
            timestampMs = clock.wallMs(),
        )
    }

    // ---------------------------------------------------------------- generation

    private fun newRound() {
        val n = random.nextInt(minCount, maxCount + 1)
        val decoys = if (hasDecoys) random.nextInt(2, 2 + (difficulty - 4)) else 0
        val radius = if (n + decoys > 12) 0.04f else 0.05f
        val placed = mutableListOf<FlashDot>()
        var gap = radius * 2.5f
        var attempts = 0
        while (placed.size < n + decoys) {
            val x = radius + 0.01f + random.nextFloat() * (1f - 2f * radius - 0.02f)
            val y = radius + 0.01f + random.nextFloat() * (1f - 2f * radius - 0.02f)
            val free = placed.all { p ->
                val dx = p.x - x; val dy = p.y - y
                dx * dx + dy * dy >= gap * gap
            }
            if (free) placed += FlashDot(x, y, radius, isTarget = true)
            // Practically never needed at these densities; keeps the loop bounded regardless.
            if (++attempts % 2000 == 0 && gap > radius * 2.05f) gap = radius * 2.05f
        }
        // Positions are already random, so the decoys are simply the last ones placed, then shuffled in.
        dots = placed.mapIndexed { i, dot -> if (i < n) dot else dot.copy(isTarget = false) }.shuffled(random)
        targetCount = n
        val first = (n - random.nextInt(CHOICES)).coerceAtLeast(1)
        choices = List(CHOICES) { first + it }
        answerIndex = n - first
    }

    companion object {
        const val CHOICES = 4
        const val POINTS_CORRECT = 150.0
        const val POINTS_MISTAKE = 70.0
        /** Correct answers in 10 s that count as a full-performance run. */
        const val TARGET_CORRECT = 6.0
    }
}
