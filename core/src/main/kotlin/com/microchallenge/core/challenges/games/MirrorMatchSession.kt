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

/** One unit square of a polyomino; x grows right, y grows down, both start at 0. */
data class ShapeCell(val x: Int, val y: Int)

/**
 * One same-or-mirror trial.
 *
 * @param left reference shape, cells shifted so the bounding box starts at (0, 0), sorted.
 * @param right [left] turned by [rotationDeg] clockwise and, when [isSame] is false, flipped first.
 * @param isSame true when [right] is a pure rotation of [left].
 * @param leftColorIndex index into the app palette (GameColors.entries; 0..5).
 * @param rightScale relative drawing size of the right shape, 1.0 = same as the left.
 */
data class MirrorTrial(
    val left: List<ShapeCell>,
    val right: List<ShapeCell>,
    val isSame: Boolean,
    val rotationDeg: Int,
    val leftColorIndex: Int,
    val rightColorIndex: Int,
    val rightScale: Float,
)

/**
 * Mirror Match: two block shapes; is the right one the left one rotated (SAME)
 * or its mirror image (MIRROR)? Every answer moves on to a fresh pair.
 *
 * All shapes are chiral: the mirror image can never be reached by rotating.
 * Difficulty: more cells (4 to 6), half-turns from level 4,
 * and from level 7 the two shapes differ in color and size.
 */
class MirrorMatchSession(
    difficulty: Int,
    private val random: Random,
    private val clock: Clock,
) : ChallengeSession {

    override val type: ChallengeType = ChallengeType.MIRROR_MATCH
    override val difficulty: Int = difficulty.coerceIn(MIN_DIFFICULTY, MAX_DIFFICULTY)

    private val startMs: Long = clock.monotonicMs()

    /** Cells per shape at this difficulty. */
    val cellCount: Int = when {
        this.difficulty <= 2 -> 4
        this.difficulty <= 6 -> 5
        else -> 6
    }

    /** True when the two shapes are drawn in different colors and sizes. */
    val variedLook: Boolean = this.difficulty >= 7

    /** Pair currently on screen. */
    var current: MirrorTrial = generate()
        private set

    var correct: Int = 0
        private set
    var mistakes: Int = 0
        private set

    /** Result of the most recent answer, null before the first one. For UI feedback. */
    var lastAnswerCorrect: Boolean? = null
        private set

    /** Increments on every answer, so the UI can key animations on it. */
    var answeredCount: Int = 0
        private set

    override val isComplete: Boolean get() = false

    private fun elapsedMs(): Long = (clock.monotonicMs() - startMs).coerceIn(0L, CHALLENGE_DURATION_MS)

    /**
     * Answer the current pair: [same] = true for "rotated only", false for "mirror image".
     * Returns true when correct. Ignored (returns false) once the 10 s window is over.
     */
    fun answer(same: Boolean): Boolean {
        if (elapsedMs() >= CHALLENGE_DURATION_MS) return false
        val ok = same == current.isSame
        if (ok) correct++ else mistakes++
        lastAnswerCorrect = ok
        answeredCount++
        current = generate()
        return ok
    }

    override fun finish(): ChallengeResult {
        val raw = (correct * POINTS_CORRECT - mistakes * POINTS_MISTAKE) * Scoring.difficultyBonus(difficulty)
        val accuracy = Scoring.accuracy(correct, mistakes)
        val performance = ((correct / TARGET_CORRECT) * accuracy).coerceIn(0.0, 1.0)
        val detail = if (correct + mistakes == 0) "No answers" else
            "$correct matched · ${(accuracy * 100).toInt()}% accurate"
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

    private fun generate(): MirrorTrial {
        var shape = randomPolyomino(cellCount)
        while (!isChiral(shape)) shape = randomPolyomino(cellCount)
        val left = normalize(shape)
        val isSame = random.nextBoolean()
        val quarterTurns = if (difficulty <= 3) (if (random.nextBoolean()) 1 else 3) else random.nextInt(1, 4)
        var right = if (isSame) left else mirror(left)
        repeat(quarterTurns) { right = rotateCw(right) }
        val leftColor = random.nextInt(PALETTE_SIZE)
        val rightColor = if (variedLook) (leftColor + 1 + random.nextInt(PALETTE_SIZE - 1)) % PALETTE_SIZE else leftColor
        val rightScale = if (variedLook) SCALES[random.nextInt(SCALES.size)] else 1f
        return MirrorTrial(left, right, isSame, quarterTurns * 90, leftColor, rightColor, rightScale)
    }

    /** Grows a connected shape cell by cell from a random frontier. */
    private fun randomPolyomino(n: Int): List<ShapeCell> {
        val cells = mutableListOf(ShapeCell(0, 0))
        while (cells.size < n) {
            val from = cells[random.nextInt(cells.size)]
            val next = when (random.nextInt(4)) {
                0 -> ShapeCell(from.x + 1, from.y)
                1 -> ShapeCell(from.x - 1, from.y)
                2 -> ShapeCell(from.x, from.y + 1)
                else -> ShapeCell(from.x, from.y - 1)
            }
            if (next !in cells) cells += next
        }
        return cells
    }

    companion object {
        /** Size of the app palette (GameColors.entries). */
        const val PALETTE_SIZE = 6

        const val POINTS_CORRECT = 110.0
        /** As costly as a hit is valuable: coin-flipping scores nothing. */
        const val POINTS_MISTAKE = 110.0
        /** Correct answers in 10 s that count as a full-performance run. */
        const val TARGET_CORRECT = 8.0

        private val SCALES = floatArrayOf(0.7f, 0.8f, 0.9f)

        /** Shifts the cells so the bounding box starts at (0, 0) and sorts them (canonical form for one orientation). */
        fun normalize(cells: List<ShapeCell>): List<ShapeCell> {
            val minX = cells.minOf { it.x }
            val minY = cells.minOf { it.y }
            return cells.map { ShapeCell(it.x - minX, it.y - minY) }.sortedWith(compareBy({ it.y }, { it.x }))
        }

        /** Quarter turn clockwise (y axis points down), normalized. */
        fun rotateCw(cells: List<ShapeCell>): List<ShapeCell> = normalize(cells.map { ShapeCell(-it.y, it.x) })

        /** Left-right flip, normalized. */
        fun mirror(cells: List<ShapeCell>): List<ShapeCell> = normalize(cells.map { ShapeCell(-it.x, it.y) })

        /** The four rotations of [cells] (0, 90, 180, 270 degrees), each normalized. */
        fun rotations(cells: List<ShapeCell>): List<List<ShapeCell>> {
            val out = mutableListOf(normalize(cells))
            repeat(3) { out += rotateCw(out.last()) }
            return out
        }

        /** True when the mirror image cannot be produced by rotating [cells]. */
        fun isChiral(cells: List<ShapeCell>): Boolean = mirror(cells) !in rotations(cells)
    }
}
