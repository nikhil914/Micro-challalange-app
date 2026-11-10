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
 * Lights Out: tapping a tile flips it and its orthogonal neighbours. Switch every light off.
 *
 * Grid is 3x3 (4x4 from level 7). Each board is made by applying K distinct random taps to
 * the dark board, K = 2 + level/2, so it can always be cleared in K moves or fewer.
 * Clearing a board deals the next one. Boards cleared in few moves score more, and lights
 * switched off on the last, unfinished board earn partial credit.
 */
class LightsOutSession(
    difficulty: Int,
    private val random: Random,
    private val clock: Clock,
) : ChallengeSession {

    /** Outcome of a [tap]. */
    enum class TapResult { TOGGLED, SOLVED, IGNORED }

    override val type: ChallengeType = ChallengeType.LIGHTS_OUT
    override val difficulty: Int = difficulty.coerceIn(MIN_DIFFICULTY, MAX_DIFFICULTY)

    private val startMs: Long = clock.monotonicMs()

    /** Tiles per side. */
    val size: Int = if (this.difficulty >= 7) 4 else 3

    val cellCount: Int get() = size * size

    /** Taps used to scramble each board (K): the board is solvable in this many moves or fewer. */
    val par: Int = 2 + this.difficulty / 2

    /** Taps made on the current board. */
    var moves: Int = 0
        private set

    /** Taps made over the whole run. */
    var totalMoves: Int = 0
        private set

    /** Boards cleared so far. */
    var solvedCount: Int = 0
        private set

    /** Lights that were on when the current board was dealt. */
    var initialOnCount: Int = 0
        private set

    private var lights = BooleanArray(cellCount)
    private val pending = LinkedHashSet<Int>()
    private var solvedValue = 0.0 // sum of move-efficiency weights of cleared boards

    init {
        deal()
    }

    private fun elapsed(): Long = clock.monotonicMs() - startMs

    /** Time-boxed stream of boards: never completes early. */
    override val isComplete: Boolean get() = false

    val isTimeUp: Boolean get() = elapsed() >= CHALLENGE_DURATION_MS

    fun isOn(cell: Int): Boolean = cell in 0 until cellCount && lights[cell]

    /** Lights currently on. */
    val onCount: Int get() = lights.count { it }

    /**
     * One set of cells that clears the current board when each is tapped once, in any order:
     * the scramble taps, adjusted for every tap the player has made. Never larger than
     * [par] before the first move.
     */
    val solutionCells: Set<Int> get() = pending.toSet()

    private fun flip(cell: Int) {
        val r = cell / size
        val c = cell % size
        lights[cell] = !lights[cell]
        if (r > 0) lights[cell - size] = !lights[cell - size]
        if (r < size - 1) lights[cell + size] = !lights[cell + size]
        if (c > 0) lights[cell - 1] = !lights[cell - 1]
        if (c < size - 1) lights[cell + 1] = !lights[cell + 1]
    }

    private fun deal() {
        // On 4x4 some tap sets cancel out completely; redraw until at least one light is on.
        do {
            lights = BooleanArray(cellCount)
            pending.clear()
            val cells = (0 until cellCount).toMutableList()
            repeat(par) {
                val cell = cells.removeAt(random.nextInt(cells.size))
                pending.add(cell)
                flip(cell)
            }
        } while (lights.none { it })
        moves = 0
        initialOnCount = onCount
    }

    /** Player taps [cell]: flips it and its orthogonal neighbours. */
    fun tap(cell: Int): TapResult {
        if (isTimeUp || cell !in 0 until cellCount) return TapResult.IGNORED
        flip(cell)
        if (!pending.remove(cell)) pending.add(cell)
        moves++
        totalMoves++
        if (lights.any { it }) return TapResult.TOGGLED
        solvedCount++
        solvedValue += BASE_SHARE + (1.0 - BASE_SHARE) * (par.toDouble() / moves).coerceAtMost(1.0)
        deal()
        return TapResult.SOLVED
    }

    override fun finish(): ChallengeResult {
        val duration = elapsed().coerceIn(0L, CHALLENGE_DURATION_MS)
        // Partial credit only for real progress on a board the player actually touched.
        val partial = if (moves > 0 && onCount < initialOnCount) {
            PARTIAL_SHARE * (initialOnCount - onCount) / initialOnCount
        } else {
            0.0
        }
        val units = (solvedValue + partial) * par
        val raw = units * POINTS_PER_UNIT * Scoring.difficultyBonus(difficulty)
        val performance = (units / GOOD_UNITS).coerceIn(0.0, 1.0)
        val detail = when {
            totalMoves == 0 -> "No taps"
            solvedCount == 0 -> "$onCount ${if (onCount == 1) "light" else "lights"} left on"
            else -> "$solvedCount ${if (solvedCount == 1) "board" else "boards"} cleared · $totalMoves moves"
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
        )
    }

    companion object {
        /** Share of a board's value paid for clearing it at all; the rest rewards few moves. */
        const val BASE_SHARE = 0.7
        /** Largest share of a board's value an unfinished board can earn. */
        const val PARTIAL_SHARE = 0.4
        /** Points per scramble tap undone (before the difficulty bonus). */
        const val POINTS_PER_UNIT = 95.0
        /** Scramble taps undone in 10 s that count as a full-performance run. */
        const val GOOD_UNITS = 10.0
    }
}
