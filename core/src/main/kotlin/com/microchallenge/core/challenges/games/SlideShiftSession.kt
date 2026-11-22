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

/** One row or column shift. [line] is the row (for [Axis.ROW]) or column index. */
data class Shift(val axis: SlideShiftSession.Axis, val line: Int, val forward: Boolean)

/**
 * Slide Shift: a small grid of colored tiles. Swiping a row slides it one step left or
 * right, swiping a column slides it up or down; tiles wrap around the edge. Make the grid
 * match the target pattern. Mechanic adapted from "Sixteen" in Simon Tatham's Portable
 * Puzzle Collection (MIT licence); no code was copied. See docs/CREDITS.md.
 *
 * Each board is the target with K random shifts applied (K grows with difficulty), so it
 * can always be solved in K moves or fewer. Grid is 3x3, 4x4 from level 9.
 */
class SlideShiftSession(
    difficulty: Int,
    private val random: Random,
    private val clock: Clock,
) : ChallengeSession {

    enum class Axis { ROW, COLUMN }

    /** Outcome of a [shift]. */
    enum class MoveResult { MOVED, SOLVED, IGNORED }

    override val type: ChallengeType = ChallengeType.SLIDE_SHIFT
    override val difficulty: Int = difficulty.coerceIn(MIN_DIFFICULTY, MAX_DIFFICULTY)

    private val startMs: Long = clock.monotonicMs()

    val size: Int = if (this.difficulty >= 9) 4 else 3
    val cellCount: Int get() = size * size

    /** Shifts used to scramble each board. */
    val par: Int = when (this.difficulty) {
        1, 2 -> 1
        3, 4, 5 -> 2
        else -> 3
    }

    /** Pattern to reach. Values are palette indexes 0 until [PALETTE_SIZE]. */
    var target: List<Int> = emptyList()
        private set

    private var tiles = IntArray(cellCount)
    private val history = ArrayList<Shift>()
    private var scramble: List<Shift> = emptyList()

    var moves: Int = 0
        private set
    var totalMoves: Int = 0
        private set
    var solvedCount: Int = 0
        private set
    private var solvedValue = 0.0

    init {
        deal()
    }

    private fun elapsed(): Long = clock.monotonicMs() - startMs

    override val isComplete: Boolean get() = false

    val isTimeUp: Boolean get() = elapsed() >= CHALLENGE_DURATION_MS

    fun tileAt(cell: Int): Int = tiles.getOrElse(cell) { 0 }

    val tilesNow: List<Int> get() = tiles.toList()

    /** Tiles already in their target color. */
    val correctCount: Int get() = (0 until cellCount).count { tiles[it] == target[it] }

    /**
     * Moves that solve the current board from here: undo the player's moves, then the
     * scramble. Not always the shortest path. For tests and debug; the UI must not reveal it.
     */
    val solution: List<Shift> get() = (scramble + history).reversed().map { it.copy(forward = !it.forward) }

    /**
     * Slide one line. For a row, [forward] means right; for a column, down. Tiles wrap.
     */
    fun shift(axis: Axis, line: Int, forward: Boolean): MoveResult {
        if (isTimeUp || line !in 0 until size) return MoveResult.IGNORED
        apply(Shift(axis, line, forward))
        history.add(Shift(axis, line, forward))
        moves++
        totalMoves++
        if (!isSolved()) return MoveResult.MOVED
        solvedCount++
        solvedValue += BASE_SHARE + (1.0 - BASE_SHARE) * (par.toDouble() / moves).coerceAtMost(1.0)
        deal()
        return MoveResult.SOLVED
    }

    private fun isSolved(): Boolean = (0 until cellCount).all { tiles[it] == target[it] }

    private fun apply(s: Shift) {
        val idx = IntArray(size) { i -> if (s.axis == Axis.ROW) s.line * size + i else i * size + s.line }
        val vals = IntArray(size) { tiles[idx[it]] }
        for (i in 0 until size) {
            val from = if (s.forward) (i - 1 + size) % size else (i + 1) % size
            tiles[idx[i]] = vals[from]
        }
    }

    private fun deal() {
        // Target: several colors, and no row or column that is one solid color, so every shift shows.
        do {
            target = List(cellCount) { random.nextInt(PALETTE_SIZE) }
        } while (!linesAllMixed(target))
        do {
            tiles = target.toIntArray()
            history.clear()
            scramble = List(par) {
                Shift(
                    if (random.nextBoolean()) Axis.ROW else Axis.COLUMN,
                    random.nextInt(size),
                    random.nextBoolean(),
                )
            }
            scramble.forEach { apply(it) }
        } while (isSolved())
        moves = 0
    }

    private fun linesAllMixed(grid: List<Int>): Boolean {
        for (l in 0 until size) {
            val row = (0 until size).map { grid[l * size + it] }
            val col = (0 until size).map { grid[it * size + l] }
            if (row.distinct().size == 1 || col.distinct().size == 1) return false
        }
        return true
    }

    override fun finish(): ChallengeResult {
        val duration = elapsed().coerceIn(0L, CHALLENGE_DURATION_MS)
        val units = solvedValue * par
        val ratio = units / GOOD_UNITS
        val performance = (ratio * GOOD_PERFORMANCE).coerceIn(0.0, 1.0)
        val raw = ratio * POINTS_AT_GOOD * Scoring.difficultyBonus(difficulty)
        val detail = when {
            totalMoves == 0 -> "No moves"
            solvedCount == 0 -> "$correctCount/$cellCount tiles in place"
            else -> "$solvedCount ${if (solvedCount == 1) "board" else "boards"} · $totalMoves moves"
        }
        return ChallengeResult(
            type = type,
            score = Scoring.clampScore(raw),
            performance = performance,
            difficulty = difficulty,
            correct = solvedCount,
            mistakes = 0,
            durationMs = duration,
            detail = detail,
            timestampMs = clock.wallMs(),
        )
    }

    companion object {
        /** Size of the app palette (GameColors.entries). */
        const val PALETTE_SIZE = 6

        const val BASE_SHARE = 0.7
        /** Scramble shifts undone in 10 s that count as a "good" run. */
        const val GOOD_UNITS = 5.0
        const val GOOD_PERFORMANCE = 0.7
        const val POINTS_AT_GOOD = 650.0
    }
}
