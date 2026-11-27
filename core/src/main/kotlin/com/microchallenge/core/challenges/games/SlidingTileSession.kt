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
 * Slide Puzzle: a classic sliding tile puzzle. Tiles 1..n-1 sit on an n x n board with one
 * gap; tap a tile in the gap's row or column to slide it (and any tiles between) into the gap.
 * Put every tile back in order, gap bottom-right.
 *
 * Board is 3x3 (4x4 from level 8). Each board is made by walking the gap [par] random steps
 * from the solved board without ever revisiting a layout, so it can always be solved in
 * [par] single-tile moves or fewer. Solving deals the next board. Boards solved in few moves
 * score more, and tiles moved closer to home on the last, unfinished board earn partial credit.
 */
class SlidingTileSession(
    difficulty: Int,
    private val random: Random,
    private val clock: Clock,
) : ChallengeSession {

    /** Outcome of a [tap]. */
    enum class TapResult { MOVED, SOLVED, IGNORED }

    override val type: ChallengeType = ChallengeType.SLIDING_TILE
    override val difficulty: Int = difficulty.coerceIn(MIN_DIFFICULTY, MAX_DIFFICULTY)

    private val startMs: Long = clock.monotonicMs()

    /** Tiles per side. */
    val size: Int = if (this.difficulty >= 8) 4 else 3

    val cellCount: Int get() = size * size

    /** Scramble steps (single-tile moves) per board: the board is solvable in this many or fewer. */
    val par: Int = 2 + (this.difficulty + 1) / 2

    /** Single-tile moves made on the current board. */
    var moves: Int = 0
        private set

    /** Single-tile moves made over the whole run. */
    var totalMoves: Int = 0
        private set

    /** Boards solved so far. */
    var solvedCount: Int = 0
        private set

    /** Sum of tile distances from home when the current board was dealt. */
    var initialDistance: Int = 0
        private set

    // tiles[cell] = tile number, 0 for the gap.
    private var tiles = IntArray(cellCount)
    private var solvedValue = 0.0 // sum of move-efficiency weights of solved boards

    init {
        deal()
    }

    private fun elapsed(): Long = clock.monotonicMs() - startMs

    /** Time-boxed stream of boards: never completes early. */
    override val isComplete: Boolean get() = false

    val isTimeUp: Boolean get() = elapsed() >= CHALLENGE_DURATION_MS

    /** Tile number at [cell], 0 for the gap or an out-of-range cell. */
    fun tileAt(cell: Int): Int = if (cell in 0 until cellCount) tiles[cell] else 0

    /** Cell holding the gap. */
    val blankCell: Int get() = tiles.indexOf(0)

    /** Cell where [tile] belongs on the solved board (the gap belongs in the last cell). */
    fun homeCell(tile: Int): Int = if (tile == 0) cellCount - 1 else tile - 1

    /** True when [cell] holds the tile that belongs there. */
    fun isHome(cell: Int): Boolean = cell in 0 until cellCount && tiles[cell] != 0 && homeCell(tiles[cell]) == cell

    /** Sum over tiles of row + column distance from home (0 when solved). */
    val distance: Int get() = (0 until cellCount).sumOf { cell ->
        val t = tiles[cell]
        if (t == 0) 0 else {
            val home = homeCell(t)
            abs(home / size - cell / size) + abs(home % size - cell % size)
        }
    }

    val isSolved: Boolean get() = (0 until cellCount - 1).all { tiles[it] == it + 1 }

    /** True when tapping [cell] would slide tiles: it shares a row or column with the gap. */
    fun canSlide(cell: Int): Boolean {
        if (isTimeUp || cell !in 0 until cellCount) return false
        val b = blankCell
        return cell != b && (cell / size == b / size || cell % size == b % size)
    }

    private fun swapWithBlank(cell: Int) {
        val b = blankCell
        tiles[b] = tiles[cell]
        tiles[cell] = 0
    }

    private fun neighbours(cell: Int): List<Int> {
        // Local copy: inside buildList, `size` would mean the list's size.
        val n = size
        val r = cell / n
        val c = cell % n
        return buildList {
            if (r > 0) add(cell - n)
            if (r < n - 1) add(cell + n)
            if (c > 0) add(cell - 1)
            if (c < n - 1) add(cell + 1)
        }
    }

    private fun deal() {
        // Self-avoiding random walk of the gap; restart in the rare case it boxes itself in.
        while (true) {
            tiles = IntArray(cellCount) { if (it == cellCount - 1) 0 else it + 1 }
            val seen = hashSetOf(tiles.toList())
            var stuck = false
            for (step in 0 until par) {
                val options = neighbours(blankCell).filter { next ->
                    val copy = tiles.copyOf()
                    copy[blankCell] = copy[next]
                    copy[next] = 0
                    copy.toList() !in seen
                }
                if (options.isEmpty()) { stuck = true; break }
                swapWithBlank(options[random.nextInt(options.size)])
                seen.add(tiles.toList())
            }
            if (!stuck && !isSolved) break
        }
        moves = 0
        initialDistance = distance
    }

    /**
     * Player taps [cell]. If it shares a row or column with the gap, every tile between it
     * and the gap slides one step toward the gap (each tile shifted counts as one move).
     */
    fun tap(cell: Int): TapResult {
        if (!canSlide(cell)) return TapResult.IGNORED
        val b = blankCell
        val step = when {
            cell / size == b / size -> if (cell > b) 1 else -1
            else -> if (cell > b) size else -size
        }
        var gap = b
        while (gap != cell) {
            swapWithBlank(gap + step)
            gap += step
            moves++
            totalMoves++
        }
        if (!isSolved) return TapResult.MOVED
        solvedCount++
        solvedValue += BASE_SHARE + (1.0 - BASE_SHARE) * (par.toDouble() / moves).coerceAtMost(1.0)
        deal()
        return TapResult.SOLVED
    }

    override fun finish(): ChallengeResult {
        val duration = elapsed().coerceIn(0L, CHALLENGE_DURATION_MS)
        // Partial credit only for real progress on a board the player actually touched.
        val now = distance
        val partial = if (moves > 0 && now < initialDistance) {
            PARTIAL_SHARE * (initialDistance - now) / initialDistance
        } else {
            0.0
        }
        val units = (solvedValue + partial) * par
        val raw = units * POINTS_PER_UNIT * Scoring.difficultyBonus(difficulty)
        val performance = (units / GOOD_UNITS).coerceIn(0.0, 1.0)
        val misplaced = (0 until cellCount).count { tiles[it] != 0 && !isHome(it) }
        val detail = when {
            totalMoves == 0 -> "No moves"
            solvedCount == 0 -> "$misplaced ${if (misplaced == 1) "tile" else "tiles"} out of place"
            else -> "$solvedCount ${if (solvedCount == 1) "puzzle" else "puzzles"} solved · $totalMoves moves"
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
        /** Share of a board's value paid for solving it at all; the rest rewards few moves. */
        const val BASE_SHARE = 0.7
        /** Largest share of a board's value an unfinished board can earn. */
        const val PARTIAL_SHARE = 0.4
        /** Points per scramble step undone (before the difficulty bonus). */
        const val POINTS_PER_UNIT = 95.0
        /** Scramble steps undone in 10 s that count as a full-performance run. */
        const val GOOD_UNITS = 10.0
    }
}
