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
 * Pipe Flow: rotate pipe tiles so water runs from the source on the left edge
 * to the drain on the right edge.
 *
 * Each board hides one winding path between source and drain; the other tiles are decoys.
 * A tap turns a tile 90° clockwise. When water reaches the drain the board flows for a
 * moment, then the next board is dealt. Grid is 3x3 (4x4 from level 5); the number of
 * scrambled path tiles grows with difficulty.
 *
 * Openings are bit masks: [N]=1, [E]=2, [S]=4, [W]=8.
 */
class PipeFlowSession(
    difficulty: Int,
    private val random: Random,
    private val clock: Clock,
) : ChallengeSession {

    enum class Phase { PLAY, FLOW, DONE }

    enum class TapResult { ROTATED, SOLVED, IGNORED }

    override val type: ChallengeType = ChallengeType.PIPE_FLOW
    override val difficulty: Int = difficulty.coerceIn(MIN_DIFFICULTY, MAX_DIFFICULTY)

    private val startMs: Long = clock.monotonicMs()

    /** Tiles per side. */
    val size: Int = if (this.difficulty >= 5) 4 else 3

    val cellCount: Int get() = size * size

    /** Path tiles that start turned the wrong way. */
    val scrambled: Int = (2 + this.difficulty / 2).coerceAtMost(if (size == 3) 4 else 7)

    /** Row of the source on the left edge, and of the drain on the right edge. */
    var sourceRow: Int = 0
        private set
    var drainRow: Int = 0
        private set

    /** Counts dealt boards; the UI keys animations on it. */
    var boardId: Int = 0
        private set

    /** Clockwise taps that restore the hidden path, summed over scrambled tiles. */
    var par: Int = 0
        private set

    /** Taps on the current board, and over the whole run. */
    var moves: Int = 0
        private set
    var totalMoves: Int = 0
        private set

    var solvedCount: Int = 0
        private set

    /** Score units one board is worth: harder boards hide more scrambled tiles. */
    private val unitsPerBoard: Double = 1.0 + scrambled

    private var masks = IntArray(cellCount)
    private var solution = IntArray(cellCount)
    private var turns = IntArray(cellCount)
    private var path: List<Int> = emptyList()
    private var flowStartMs = -1L
    private var solvedValue = 0.0
    private var reach = Reach(IntArray(cellCount) { -1 }, IntArray(cellCount) { -1 }, false)

    /** Path tiles water reached when the board was dealt. */
    private var initialPathReached = 0

    init {
        deal()
    }

    private fun elapsed(): Long = clock.monotonicMs() - startMs

    /** Time-boxed stream of boards: never completes early. */
    override val isComplete: Boolean get() = false

    val isTimeUp: Boolean get() = elapsed() >= CHALLENGE_DURATION_MS

    val phase: Phase
        get() {
            if (isTimeUp) return Phase.DONE
            if (flowStartMs >= 0 && elapsed() - flowStartMs >= FLOW_MS) {
                flowStartMs = -1L
                deal()
            }
            return if (flowStartMs >= 0) Phase.FLOW else Phase.PLAY
        }

    /** 0..1 progress of the solved-board flow animation. */
    fun flowProgress(): Float =
        if (flowStartMs < 0) 0f else ((elapsed() - flowStartMs).toFloat() / FLOW_MS).coerceIn(0f, 1f)

    /** Current openings of [cell]. */
    fun mask(cell: Int): Int = if (cell in 0 until cellCount) masks[cell] else 0

    /** Quarter turns applied to [cell] on this board; only grows, so the UI can animate. */
    fun turns(cell: Int): Int = if (cell in 0 until cellCount) turns[cell] else 0

    /** Steps from the source along water, or -1 when [cell] is dry. */
    fun waterDistance(cell: Int): Int = if (cell in 0 until cellCount) reach.dist[cell] else -1

    /** Side (bit) water enters [cell] from, or -1 when dry. */
    fun waterFrom(cell: Int): Int = if (cell in 0 until cellCount) reach.from[cell] else -1

    /** Longest water run on the board, for animation timing. */
    val maxWaterDistance: Int get() = reach.dist.max()

    /** True when water reaches the drain. */
    val isFlowing: Boolean get() = reach.solved

    /** Taps that would turn [cell] back to its hidden-path orientation (0 for decoys). */
    fun tapsToFix(cell: Int): Int {
        if (cell !in path) return 0
        var m = masks[cell]
        for (k in 0..3) {
            if (m == solution[cell]) return k
            m = rotate(m)
        }
        return 0
    }

    /** Cells of the hidden path, from source to drain. */
    val pathCells: List<Int> get() = path

    fun tap(cell: Int): TapResult {
        if (phase != Phase.PLAY || cell !in 0 until cellCount) return TapResult.IGNORED
        masks[cell] = rotate(masks[cell])
        turns[cell]++
        moves++
        totalMoves++
        reach = flood()
        if (!reach.solved) return TapResult.ROTATED
        solvedCount++
        solvedValue += BASE_SHARE + (1.0 - BASE_SHARE) * (par.toDouble() / moves).coerceAtMost(1.0)
        flowStartMs = elapsed()
        return TapResult.SOLVED
    }

    private fun deal() {
        boardId++
        moves = 0
        turns = IntArray(cellCount)
        do {
            sourceRow = random.nextInt(size)
            drainRow = random.nextInt(size)
            path = randomPath()
            solution = IntArray(cellCount)
            for (i in path.indices) {
                val cell = path[i]
                val inSide = if (i == 0) W else dirBetween(cell, path[i - 1])
                val outSide = if (i == path.lastIndex) E else dirBetween(cell, path[i + 1])
                solution[cell] = inSide or outSide
            }
            for (cell in 0 until cellCount) if (cell !in path) solution[cell] = randomDecoy()
            masks = solution.copyOf()
            // Decoys spin freely; chosen path tiles are turned away from the answer.
            for (cell in 0 until cellCount) if (cell !in path) masks[cell] = rotateBy(masks[cell], random.nextInt(4))
            par = 0
            val candidates = path.shuffled(random).take(scrambled)
            for (cell in candidates) {
                val fix = if (isStraight(solution[cell])) 1 else 1 + random.nextInt(3)
                // Rotating by (4 - fix) clockwise means [fix] more clockwise taps restore it.
                masks[cell] = rotateBy(solution[cell], 4 - fix)
                par += fix
            }
            reach = flood()
        } while (reach.solved)
        initialPathReached = path.count { reach.dist[it] >= 0 }
    }

    /** Random self-avoiding walk from the source cell to the drain cell, long enough to be fun. */
    private fun randomPath(): List<Int> {
        val start = sourceRow * size
        val goal = drainRow * size + size - 1
        val minLen = if (size == 3) 5 else 7
        while (true) {
            val walk = ArrayList<Int>()
            val seen = BooleanArray(cellCount)
            if (dfs(start, goal, walk, seen, minLen)) return walk
        }
    }

    private fun dfs(cell: Int, goal: Int, walk: ArrayList<Int>, seen: BooleanArray, minLen: Int): Boolean {
        walk += cell
        seen[cell] = true
        if (cell == goal) {
            if (walk.size >= minLen) return true
        } else {
            for (d in listOf(N, E, S, W).shuffled(random)) {
                val next = neighbor(cell, d)
                if (next >= 0 && !seen[next] && dfs(next, goal, walk, seen, minLen)) return true
            }
        }
        walk.removeAt(walk.lastIndex)
        seen[cell] = false
        return false
    }

    private fun randomDecoy(): Int = when (random.nextInt(10)) {
        in 0..3 -> N or E // corner
        in 4..6 -> N or S // straight
        in 7..8 -> N or E or S // tee
        else -> N // dead end
    }

    private class Reach(val dist: IntArray, val from: IntArray, val solved: Boolean)

    /** Breadth-first flood from the source through mutually open sides. */
    private fun flood(): Reach {
        val dist = IntArray(cellCount) { -1 }
        val from = IntArray(cellCount) { -1 }
        val start = sourceRow * size
        if (masks[start] and W == 0) return Reach(dist, from, false)
        dist[start] = 0
        from[start] = W
        val queue = ArrayDeque<Int>()
        queue += start
        while (queue.isNotEmpty()) {
            val cell = queue.removeFirst()
            for (d in intArrayOf(N, E, S, W)) {
                if (masks[cell] and d == 0) continue
                val next = neighbor(cell, d)
                if (next < 0 || dist[next] >= 0 || masks[next] and opposite(d) == 0) continue
                dist[next] = dist[cell] + 1
                from[next] = opposite(d)
                queue += next
            }
        }
        val drain = drainRow * size + size - 1
        return Reach(dist, from, dist[drain] >= 0 && masks[drain] and E != 0)
    }

    private fun neighbor(cell: Int, d: Int): Int {
        val r = cell / size
        val c = cell % size
        return when (d) {
            N -> if (r > 0) cell - size else -1
            S -> if (r < size - 1) cell + size else -1
            E -> if (c < size - 1) cell + 1 else -1
            W -> if (c > 0) cell - 1 else -1
            else -> -1
        }
    }

    private fun dirBetween(from: Int, to: Int): Int = when (to - from) {
        -size -> N
        size -> S
        1 -> E
        else -> W
    }

    override fun finish(): ChallengeResult {
        val duration = elapsed().coerceIn(0L, CHALLENGE_DURATION_MS)
        // Partial credit for pushing water further along an unfinished board.
        val reachedNow = path.count { reach.dist[it] >= 0 }
        val partial = if (moves > 0 && !reach.solved && reachedNow > initialPathReached) {
            PARTIAL_SHARE * (reachedNow - initialPathReached) / (path.size - initialPathReached)
        } else {
            0.0
        }
        val units = (solvedValue + partial) * unitsPerBoard
        val raw = units * POINTS_PER_UNIT * Scoring.difficultyBonus(difficulty)
        val performance = (units / GOOD_UNITS).coerceIn(0.0, 1.0)
        val detail = when {
            totalMoves == 0 -> "No taps"
            solvedCount == 0 -> "Water got $reachedNow of ${path.size} pipes"
            else -> "$solvedCount ${if (solvedCount == 1) "board" else "boards"} flowing · $totalMoves turns"
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
        const val N = 1
        const val E = 2
        const val S = 4
        const val W = 8

        /** How long a solved board shows its flow before the next one, ms. */
        const val FLOW_MS = 700L

        /** Share of a board's value paid for solving it at all; the rest rewards few turns. */
        const val BASE_SHARE = 0.7
        /** Largest share of a board's value an unfinished board can earn. */
        const val PARTIAL_SHARE = 0.4
        /** Points per unit (before the difficulty bonus). */
        const val POINTS_PER_UNIT = 75.0
        /** Units in 10 s that count as a full-performance run. */
        const val GOOD_UNITS = 10.0

        /** One quarter turn clockwise. */
        fun rotate(m: Int): Int = ((m shl 1) or (m shr 3)) and 15

        fun rotateBy(m: Int, k: Int): Int {
            var r = m
            repeat(((k % 4) + 4) % 4) { r = rotate(r) }
            return r
        }

        fun opposite(d: Int): Int = rotateBy(d, 2)

        fun isStraight(m: Int): Boolean = m == (N or S) || m == (E or W)
    }
}
