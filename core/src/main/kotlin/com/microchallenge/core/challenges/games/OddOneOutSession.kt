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
 * One grid tile, described UI-agnostically.
 *
 * @param shape one of the `SHAPE_*` constants in [OddOneOutSession].
 * @param rotationDeg clockwise rotation in degrees.
 * @param colorIndex index into the app palette (GameColors.entries; 0..5).
 * @param scale relative size, 1.0 = full tile.
 */
data class OddTile(
    val shape: Int,
    val rotationDeg: Int,
    val colorIndex: Int,
    val scale: Float,
)

/** How the odd tile differs from the others this round. */
enum class OddDifference { SHAPE, ROTATION, COLOR, SIZE }

/**
 * Odd One Out: a grid of identical tiles except one. Tap the odd one.
 * Correct tap: next round immediately. Wrong tap: mistake, same round stays.
 *
 * Difficulty grows the grid (3x3 to 6x6) and shrinks the difference:
 * different shape, then rotation, then slight color or size.
 * Color differences always come with a small size change too, so no round
 * depends on color vision alone.
 */
class OddOneOutSession(
    difficulty: Int,
    private val random: Random,
    private val clock: Clock,
) : ChallengeSession {

    override val type: ChallengeType = ChallengeType.ODD_ONE_OUT
    override val difficulty: Int = difficulty.coerceIn(MIN_DIFFICULTY, MAX_DIFFICULTY)

    private val startMs: Long = clock.monotonicMs()

    /** Grid side length (tiles per row and per column). */
    val gridSize: Int = when (this.difficulty) {
        1, 2 -> 3
        3, 4 -> 4
        5, 6, 7 -> 5
        else -> 6
    }

    var tiles: List<OddTile> = emptyList()
        private set

    /** Index of the odd tile in [tiles]. Exposed for tests and debug; the UI must not reveal it. */
    var oddIndex: Int = 0
        private set

    var difference: OddDifference = OddDifference.SHAPE
        private set

    /** Rounds solved so far (also the 0-based number of the current round). */
    var solved: Int = 0
        private set
    var mistakes: Int = 0
        private set

    var lastTapCorrect: Boolean? = null
        private set

    override val isComplete: Boolean get() = false

    init { newRound() }

    private fun elapsedMs(): Long = (clock.monotonicMs() - startMs).coerceIn(0L, CHALLENGE_DURATION_MS)

    /** Tap tile [index]. Returns true when it was the odd tile. Ignored after 10 s. */
    fun tap(index: Int): Boolean {
        if (elapsedMs() >= CHALLENGE_DURATION_MS) return false
        if (index !in tiles.indices) return false
        val ok = index == oddIndex
        lastTapCorrect = ok
        if (ok) {
            solved++
            newRound()
        } else {
            mistakes++
        }
        return ok
    }

    override fun finish(): ChallengeResult {
        val raw = (solved * POINTS_ROUND - mistakes * POINTS_MISTAKE) * Scoring.difficultyBonus(difficulty)
        val accuracy = Scoring.accuracy(solved, mistakes)
        val performance = ((solved / TARGET_ROUNDS) * accuracy).coerceIn(0.0, 1.0)
        val detail = when {
            solved + mistakes == 0 -> "No taps"
            mistakes == 0 -> "$solved found · no misses"
            else -> "$solved found · $mistakes ${if (mistakes == 1) "miss" else "misses"}"
        }
        return ChallengeResult(
            type = type,
            score = Scoring.clampScore(raw),
            performance = performance,
            difficulty = difficulty,
            correct = solved,
            mistakes = mistakes,
            durationMs = elapsedMs(),
            detail = detail,
            timestampMs = clock.wallMs(),
        )
    }

    // ---------------------------------------------------------------- generation

    private fun newRound() {
        val d = difficulty
        difference = when {
            d <= 3 -> OddDifference.SHAPE
            d <= 6 -> OddDifference.ROTATION
            d == 7 -> if (random.nextBoolean()) OddDifference.COLOR else OddDifference.SIZE
            else -> listOf(OddDifference.SIZE, OddDifference.ROTATION, OddDifference.COLOR)[random.nextInt(3)]
        }
        val color = random.nextInt(PALETTE_SIZE)
        val base: OddTile
        val odd: OddTile
        when (difference) {
            OddDifference.SHAPE -> {
                val pairs = if (d <= 2) DISTINCT_SHAPE_PAIRS else SIMILAR_SHAPE_PAIRS
                val pair = pairs[random.nextInt(pairs.size)]
                val (a, b) = if (random.nextBoolean()) pair else pair.second to pair.first
                base = OddTile(a, 0, color, 1f)
                odd = OddTile(b, 0, color, 1f)
            }
            OddDifference.ROTATION -> {
                val delta = when {
                    d <= 4 -> 180
                    d == 5 -> 90
                    d == 6 -> 45
                    d <= 8 -> 30
                    else -> 20
                }
                val shape = ASYMMETRIC_SHAPES[random.nextInt(ASYMMETRIC_SHAPES.size)]
                val rot = random.nextInt(8) * 45
                val sign = if (random.nextBoolean()) 1 else -1
                base = OddTile(shape, rot, color, 1f)
                odd = OddTile(shape, ((rot + sign * delta) % 360 + 360) % 360, color, 1f)
            }
            OddDifference.COLOR -> {
                val pair = SIMILAR_COLOR_PAIRS[random.nextInt(SIMILAR_COLOR_PAIRS.size)]
                val (a, b) = if (random.nextBoolean()) pair else pair.second to pair.first
                val shape = random.nextInt(SHAPE_COUNT)
                val sizeCue = if (d <= 8) 0.85f else 0.9f
                base = OddTile(shape, 0, a, 1f)
                odd = OddTile(shape, 0, b, sizeCue)
            }
            OddDifference.SIZE -> {
                val scale = when {
                    d <= 7 -> 0.75f
                    d == 8 -> 0.8f
                    d == 9 -> 0.84f
                    else -> 0.87f
                }
                val shape = random.nextInt(SHAPE_COUNT)
                val shrink = random.nextBoolean()
                base = OddTile(shape, 0, color, if (shrink) 1f else scale)
                odd = OddTile(shape, 0, color, if (shrink) scale else 1f)
            }
        }
        val n = gridSize * gridSize
        val newOdd = random.nextInt(n)
        oddIndex = newOdd
        tiles = List(n) { if (it == newOdd) odd else base }
    }

    companion object {
        const val SHAPE_CIRCLE = 0
        const val SHAPE_SQUARE = 1
        const val SHAPE_TRIANGLE = 2
        const val SHAPE_DIAMOND = 3
        const val SHAPE_STAR = 4
        const val SHAPE_HEXAGON = 5
        /** Arrow pointing up at rotation 0. Asymmetric, so any rotation is visible. */
        const val SHAPE_ARROW = 6
        /** Letter-L-like block. Asymmetric. */
        const val SHAPE_L = 7
        const val SHAPE_COUNT = 8

        /** Size of the app palette (GameColors.entries). */
        const val PALETTE_SIZE = 6

        const val POINTS_ROUND = 110.0
        const val POINTS_MISTAKE = 60.0
        /** Rounds in 10 s that count as a full-performance run. */
        const val TARGET_ROUNDS = 8.0

        private val DISTINCT_SHAPE_PAIRS = listOf(
            SHAPE_CIRCLE to SHAPE_TRIANGLE,
            SHAPE_SQUARE to SHAPE_STAR,
            SHAPE_TRIANGLE to SHAPE_SQUARE,
            SHAPE_CIRCLE to SHAPE_ARROW,
            SHAPE_STAR to SHAPE_CIRCLE,
        )
        private val SIMILAR_SHAPE_PAIRS = listOf(
            SHAPE_CIRCLE to SHAPE_HEXAGON,
            SHAPE_SQUARE to SHAPE_DIAMOND,
            SHAPE_DIAMOND to SHAPE_TRIANGLE,
            SHAPE_HEXAGON to SHAPE_STAR,
            SHAPE_SQUARE to SHAPE_L,
        )
        /** Shapes whose rotation is visible for every delta used (no 20°..180° symmetry). */
        private val ASYMMETRIC_SHAPES = listOf(SHAPE_ARROW, SHAPE_L, SHAPE_TRIANGLE)
        /** Palette index pairs that look alike (RED/ORANGE, ORANGE/YELLOW, BLUE/PURPLE, GREEN/BLUE). */
        private val SIMILAR_COLOR_PAIRS = listOf(0 to 5, 5 to 3, 2 to 4, 1 to 2)
    }
}
