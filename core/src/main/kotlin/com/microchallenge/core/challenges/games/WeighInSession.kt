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
 * One balance scale. [left] and [right] are weight ids (0 until [WeighInSession.WEIGHT_KINDS]).
 * The scale tips down on the heavier side.
 */
data class Scale(val left: Int, val right: Int, val leftHeavier: Boolean) {
    val heavier: Int get() = if (leftHeavier) left else right
    val lighter: Int get() = if (leftHeavier) right else left
}

/**
 * Weigh In: a few balance scales each compare two weights. Combine them and tap the
 * heaviest weight (or, from level 5, sometimes the lightest).
 *
 * The scales link the weights in a chain (A > B, B > C, ...), shown shuffled and with
 * random sides, so the answer always follows from the scales and needs transitive
 * reasoning once there are more than two weights.
 */
class WeighInSession(
    difficulty: Int,
    private val random: Random,
    private val clock: Clock,
) : ChallengeSession {

    enum class Ask { HEAVIEST, LIGHTEST }

    override val type: ChallengeType = ChallengeType.WEIGH_IN
    override val difficulty: Int = difficulty.coerceIn(MIN_DIFFICULTY, MAX_DIFFICULTY)

    private val startMs: Long = clock.monotonicMs()

    /** Weights per round. */
    val weightCount: Int = when (this.difficulty) {
        1, 2, 3 -> 3
        4, 5, 6, 7 -> 4
        else -> 5
    }

    /** Weight ids in this round, in the order the UI lays out the answer buttons. */
    var weights: List<Int> = emptyList()
        private set

    var scales: List<Scale> = emptyList()
        private set

    var ask: Ask = Ask.HEAVIEST
        private set

    /** The right answer. For tests and debug; the UI must not reveal it. */
    var answer: Int = 0
        private set

    var correct: Int = 0
        private set
    var mistakes: Int = 0
        private set

    var lastCorrect: Boolean? = null
        private set

    val round: Int get() = correct + mistakes

    init {
        deal()
    }

    private fun elapsed(): Long = clock.monotonicMs() - startMs

    override val isComplete: Boolean get() = false

    val isTimeUp: Boolean get() = elapsed() >= CHALLENGE_DURATION_MS

    /** Tap weight [id] as the answer. Returns true when right. Any valid tap deals the next round. */
    fun choose(id: Int): Boolean {
        if (isTimeUp || id !in weights) return false
        val ok = id == answer
        lastCorrect = ok
        if (ok) correct++ else mistakes++
        deal()
        return ok
    }

    private fun deal() {
        // order[0] is the heaviest, order.last() the lightest.
        val order = (0 until WEIGHT_KINDS).shuffled(random).take(weightCount)
        scales = (0 until weightCount - 1).map { i ->
            val heavy = order[i]
            val light = order[i + 1]
            if (random.nextBoolean()) Scale(heavy, light, true) else Scale(light, heavy, false)
        }.shuffled(random)
        ask = if (difficulty >= LIGHTEST_FROM && random.nextBoolean()) Ask.LIGHTEST else Ask.HEAVIEST
        answer = if (ask == Ask.HEAVIEST) order.first() else order.last()
        weights = order.shuffled(random)
    }

    override fun finish(): ChallengeResult {
        val duration = elapsed().coerceIn(0L, CHALLENGE_DURATION_MS)
        val good = GOOD_EASY - (difficulty - 1) * GOOD_STEP
        val units = correct - MISTAKE_WEIGHT * mistakes
        val ratio = (units / good).coerceAtLeast(0.0)
        val performance = (ratio * GOOD_PERFORMANCE).coerceIn(0.0, 1.0)
        val raw = ratio * POINTS_AT_GOOD * Scoring.difficultyBonus(difficulty)
        val detail = when {
            correct + mistakes == 0 -> "No answers"
            mistakes == 0 -> "$correct right · no misses"
            else -> "$correct right · $mistakes wrong"
        }
        return ChallengeResult(
            type = type,
            score = Scoring.clampScore(raw),
            performance = performance,
            difficulty = difficulty,
            correct = correct,
            mistakes = mistakes,
            durationMs = duration,
            detail = detail,
            timestampMs = clock.wallMs(),
        )
    }

    companion object {
        /** Distinct weights the UI can draw. */
        const val WEIGHT_KINDS = 6
        const val LIGHTEST_FROM = 5

        /** Right answers in 10 s that count as a "good" run at level 1. */
        const val GOOD_EASY = 5.0
        const val GOOD_STEP = 0.25
        const val MISTAKE_WEIGHT = 1.0
        const val GOOD_PERFORMANCE = 0.7
        const val POINTS_AT_GOOD = 650.0
    }
}
