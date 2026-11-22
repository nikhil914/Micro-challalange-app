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
 * One floating bubble. [lane] (0..1, left to right) and [phase] (0..1) only drive the
 * drawing: the UI bobs each bubble around its lane, so every bubble moves differently.
 */
data class SumBubble(
    val id: Int,
    val value: Int,
    val lane: Float,
    val phase: Float,
)

/**
 * Bubble Sum: a target number and a cluster of numbered bubbles. Pop bubbles whose values
 * add up to exactly the target. Going over the target is a bust: the selection clears and
 * counts as a mistake. Hitting it exactly deals the next round.
 *
 * Difficulty adds bubbles, bigger numbers and, from level 5, three-part sums.
 */
class BubbleSumSession(
    difficulty: Int,
    private val random: Random,
    private val clock: Clock,
) : ChallengeSession {

    /** Outcome of a [pop]. */
    enum class PopResult { SELECTED, DESELECTED, SOLVED, BUST, IGNORED }

    override val type: ChallengeType = ChallengeType.BUBBLE_SUM
    override val difficulty: Int = difficulty.coerceIn(MIN_DIFFICULTY, MAX_DIFFICULTY)

    private val startMs: Long = clock.monotonicMs()

    val bubbleCount: Int = when (this.difficulty) {
        1, 2, 3 -> 5
        4, 5, 6 -> 6
        else -> 7
    }

    /** Values in the planted solution. */
    val parts: Int = if (this.difficulty >= 5) 3 else 2

    /** Largest bubble value. */
    val maxValue: Int = when (this.difficulty) {
        1, 2 -> 9
        3, 4, 5, 6 -> 12
        else -> 15
    }

    var target: Int = 0
        private set

    var bubbles: List<SumBubble> = emptyList()
        private set

    /** Ids of the bubbles in one exact solution, for tests and debug. The UI must not reveal it. */
    var solutionIds: Set<Int> = emptySet()
        private set

    private val selected = LinkedHashSet<Int>()
    private var nextId = 0

    var solved: Int = 0
        private set
    var busts: Int = 0
        private set

    /** Rounds played so far, also the 0-based index of the current round. */
    val round: Int get() = solved

    init {
        deal()
    }

    private fun elapsed(): Long = clock.monotonicMs() - startMs

    override val isComplete: Boolean get() = false

    val isTimeUp: Boolean get() = elapsed() >= CHALLENGE_DURATION_MS

    fun isSelected(id: Int): Boolean = id in selected

    val selectedIds: Set<Int> get() = selected.toSet()

    val selectedSum: Int get() = bubbles.filter { it.id in selected }.sumOf { it.value }

    /** Tap bubble [id]: selects it, or deselects it when already selected. */
    fun pop(id: Int): PopResult {
        if (isTimeUp || bubbles.none { it.id == id }) return PopResult.IGNORED
        if (!selected.add(id)) {
            selected.remove(id)
            return PopResult.DESELECTED
        }
        val sum = selectedSum
        return when {
            sum == target -> {
                solved++
                deal()
                PopResult.SOLVED
            }
            sum > target -> {
                busts++
                selected.clear()
                PopResult.BUST
            }
            else -> PopResult.SELECTED
        }
    }

    private fun deal() {
        selected.clear()
        val solution = List(parts) { 1 + random.nextInt(maxValue) }
        target = solution.sum()
        val values = solution + List(bubbleCount - parts) { 1 + random.nextInt(maxValue) }
        val lanes = List(bubbleCount) { (it + 0.5f) / bubbleCount }.shuffled(random)
        val made = values.mapIndexed { i, v -> SumBubble(nextId + i, v, lanes[i], random.nextFloat()) }
        nextId += bubbleCount
        solutionIds = made.take(parts).map { it.id }.toSet()
        bubbles = made.shuffled(random)
    }

    override fun finish(): ChallengeResult {
        val duration = elapsed().coerceIn(0L, CHALLENGE_DURATION_MS)
        val units = solved * parts - BUST_WEIGHT * busts
        val ratio = (units / GOOD_UNITS).coerceAtLeast(0.0)
        val performance = (ratio * GOOD_PERFORMANCE).coerceIn(0.0, 1.0)
        val raw = ratio * POINTS_AT_GOOD * Scoring.difficultyBonus(difficulty)
        val detail = when {
            solved + busts == 0 -> "No sums"
            busts == 0 -> "$solved ${if (solved == 1) "sum" else "sums"} · no busts"
            else -> "$solved ${if (solved == 1) "sum" else "sums"} · $busts ${if (busts == 1) "bust" else "busts"}"
        }
        return ChallengeResult(
            type = type,
            score = Scoring.clampScore(raw),
            performance = performance,
            difficulty = difficulty,
            correct = solved,
            mistakes = busts,
            durationMs = duration,
            detail = detail,
            timestampMs = clock.wallMs(),
        )
    }

    companion object {
        /** Bubbles popped into exact sums in 10 s that count as a "good" run. */
        const val GOOD_UNITS = 8.0
        const val BUST_WEIGHT = 1.0
        const val GOOD_PERFORMANCE = 0.7
        const val POINTS_AT_GOOD = 650.0
    }
}
