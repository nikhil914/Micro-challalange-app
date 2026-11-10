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
 * Chimp Order: numbers 1..N sit on random cells of a grid. They show briefly, then turn
 * into blank tiles; tap the tiles in ascending order. A wrong tile ends the run.
 *
 * Grid is 4 columns by 4 rows (5 rows from level 6). N is 4 + level/2, capped at 9.
 * Show time is 1.2 s + 0.25 s per number, cut by 4% per level above 1. Tapping "1" early
 * hides the numbers at once. Clear the board with time left and a second board with
 * N + 1 numbers starts.
 */
class ChimpOrderSession(
    difficulty: Int,
    private val random: Random,
    private val clock: Clock,
) : ChallengeSession {

    enum class Phase { SHOW, INPUT, DONE }

    /** Outcome of a [tap]. */
    enum class TapResult { CORRECT, BOARD_CLEARED, COMPLETED, WRONG, IGNORED }

    override val type: ChallengeType = ChallengeType.CHIMP_ORDER
    override val difficulty: Int = difficulty.coerceIn(MIN_DIFFICULTY, MAX_DIFFICULTY)

    private val startMs: Long = clock.monotonicMs()

    val cols: Int = 4
    val rows: Int = if (this.difficulty >= 6) 5 else 4

    val cellCount: Int get() = cols * rows

    /** Numbers on the first board. */
    val firstCount: Int = (4 + this.difficulty / 2).coerceAtMost(MAX_FIRST_COUNT)

    /** 0 for the first board, 1 for the bonus board. */
    var boardIndex: Int = 0
        private set

    /** Numbers on the current board (N). */
    var count: Int = firstCount
        private set

    /** How long the current board's numbers stay visible, ms. */
    var showMs: Long = showMsFor(firstCount)
        private set

    /** Next number the player must tap (1..[count]); [count] + 1 once the board is cleared. */
    var nextExpected: Int = 1
        private set

    /** Cells tapped correctly on the current board. */
    val revealed: Set<Int> get() = revealedCells

    /** Cell tapped by mistake, or -1. */
    var wrongCell: Int = -1
        private set

    /** Numbers tapped in order on the first board. */
    var firstProgress: Int = 0
        private set

    /** Numbers tapped in order on the bonus board. */
    var secondProgress: Int = 0
        private set

    private val revealedCells = LinkedHashSet<Int>()
    private var numbers = IntArray(cellCount)
    private var boardStartMs = 0L // session-relative
    private var hiddenEarly = false
    private var ended = false

    init {
        deal()
    }

    private fun elapsed(): Long = clock.monotonicMs() - startMs

    /** Show time for a board with [n] numbers at this difficulty. */
    fun showMsFor(n: Int): Long =
        ((BASE_SHOW_MS + PER_NUMBER_SHOW_MS * n) * (1.0 - (difficulty - 1) * 0.04)).toLong()

    private fun deal() {
        numbers = IntArray(cellCount)
        val cells = (0 until cellCount).toMutableList()
        for (n in 1..count) {
            numbers[cells.removeAt(random.nextInt(cells.size))] = n
        }
    }

    val phase: Phase
        get() {
            val t = elapsed()
            return when {
                ended || t >= CHALLENGE_DURATION_MS -> Phase.DONE
                !hiddenEarly && t < boardStartMs + showMs -> Phase.SHOW
                else -> Phase.INPUT
            }
        }

    override val isComplete: Boolean get() = ended

    /** Number on [cell] (1..[count]), or 0 for an empty cell. */
    fun numberAt(cell: Int): Int = if (cell in 0 until cellCount) numbers[cell] else 0

    /** True when the UI should print the number on [cell] instead of a blank tile. */
    fun isNumberVisible(cell: Int): Boolean =
        numberAt(cell) != 0 && (phase != Phase.INPUT || cell in revealedCells)

    /** Player taps [cell]. Empty cells and already cleared tiles are ignored. */
    fun tap(cell: Int): TapResult {
        val current = phase
        if (current == Phase.DONE || cell !in 0 until cellCount) return TapResult.IGNORED
        val n = numbers[cell]
        if (n == 0 || cell in revealedCells) return TapResult.IGNORED
        if (current == Phase.SHOW) {
            // Like the original test: starting early is allowed, but only on the 1.
            if (n != 1) return TapResult.IGNORED
            hiddenEarly = true
        }
        if (n != nextExpected) {
            wrongCell = cell
            ended = true
            return TapResult.WRONG
        }
        revealedCells.add(cell)
        nextExpected++
        if (boardIndex == 0) firstProgress++ else secondProgress++
        if (nextExpected <= count) return TapResult.CORRECT
        if (boardIndex == 1) {
            ended = true
            return TapResult.COMPLETED
        }
        // Bonus board: one more number, fresh layout, numbers on show again.
        boardIndex = 1
        count = firstCount + 1
        showMs = showMsFor(count)
        nextExpected = 1
        revealedCells.clear()
        hiddenEarly = false
        boardStartMs = elapsed()
        deal()
        return TapResult.BOARD_CLEARED
    }

    override fun finish(): ChallengeResult {
        val duration = elapsed().coerceIn(0L, CHALLENGE_DURATION_MS)
        ended = true
        val firstFrac = firstProgress.toDouble() / firstCount
        val firstCleared = firstProgress == firstCount
        val secondFrac = secondProgress.toDouble() / (firstCount + 1)
        val raw = (600.0 * firstFrac + (if (firstCleared) 150.0 else 0.0) + 250.0 * secondFrac) *
            Scoring.difficultyBonus(difficulty)
        val performance =
            (0.6 * firstFrac + (if (firstCleared) 0.15 else 0.0) + 0.25 * secondFrac).coerceIn(0.0, 1.0)
        val detail = when {
            secondProgress == firstCount + 1 -> "Both boards cleared"
            firstCleared -> "Board cleared · $secondProgress/${firstCount + 1} on the next"
            firstProgress == 0 && wrongCell < 0 -> "No taps"
            else -> "$firstProgress/$firstCount in order"
        }
        return ChallengeResult(
            type = type,
            score = Scoring.clampScore(raw),
            performance = performance,
            difficulty = difficulty,
            correct = firstProgress + secondProgress,
            mistakes = if (wrongCell >= 0) 1 else 0,
            durationMs = duration,
            detail = detail,
        )
    }

    companion object {
        const val MAX_FIRST_COUNT = 9
        const val BASE_SHOW_MS = 1_200L
        const val PER_NUMBER_SHOW_MS = 250L
    }
}
