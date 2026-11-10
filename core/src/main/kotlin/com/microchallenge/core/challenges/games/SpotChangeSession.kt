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
 * @param colorIndex index into the app palette (GameColors.entries; 0..5).
 * @param shape one of the `SHAPE_*` constants in [SpotChangeSession].
 */
data class ChangeTile(
    val colorIndex: Int,
    val shape: Int,
)

/** What changed on the one altered tile. */
enum class ChangeKind { COLOR_AND_SHAPE, COLOR, SHAPE, SUBTLE_SHAPE }

/**
 * Spot the Change: study a grid, it blinks, one tile comes back different. Tap it.
 * Any tap ends the round (right: solved, wrong: mistake) and the next grid appears.
 *
 * Difficulty grows the grid (3x3 to 5x5) and shrinks the change:
 * color and shape together, then only one of them, then a look-alike shape.
 */
class SpotChangeSession(
    difficulty: Int,
    private val random: Random,
    private val clock: Clock,
) : ChallengeSession {

    /** STUDY: original grid. MASK: blank. FIND: grid with one tile changed, taps live. */
    enum class Phase { STUDY, MASK, FIND }

    override val type: ChallengeType = ChallengeType.SPOT_CHANGE
    override val difficulty: Int = difficulty.coerceIn(MIN_DIFFICULTY, MAX_DIFFICULTY)

    private val startMs: Long = clock.monotonicMs()
    private var roundStartMs: Long = startMs

    /** Grid side length (tiles per row and per column). */
    val gridSize: Int = when (this.difficulty) {
        1, 2, 3 -> 3
        4, 5, 6, 7 -> 4
        else -> 5
    }

    val studyMs: Long = STUDY_MS
    val maskMs: Long = MASK_MS

    /** Grid as first shown. */
    var originalTiles: List<ChangeTile> = emptyList()
        private set

    /** Grid after the blink: equal to [originalTiles] except at [changedIndex]. */
    var changedTiles: List<ChangeTile> = emptyList()
        private set

    /** Index of the altered tile. Exposed for tests and debug; the UI must not reveal it. */
    var changedIndex: Int = 0
        private set

    var changeKind: ChangeKind = ChangeKind.COLOR_AND_SHAPE
        private set

    /** Rounds solved so far. */
    var solved: Int = 0
        private set
    var mistakes: Int = 0
        private set

    var lastTapCorrect: Boolean? = null
        private set

    /** Rounds finished (solved + mistakes); also the 0-based number of the current round. */
    val roundsPlayed: Int get() = solved + mistakes

    override val isComplete: Boolean get() = false

    init { newRound() }

    private fun elapsedMs(): Long = (clock.monotonicMs() - startMs).coerceIn(0L, CHALLENGE_DURATION_MS)

    fun phase(): Phase {
        val t = clock.monotonicMs() - roundStartMs
        return when {
            t < STUDY_MS -> Phase.STUDY
            t < STUDY_MS + MASK_MS -> Phase.MASK
            else -> Phase.FIND
        }
    }

    /** Tiles to draw right now: the original grid, nothing during the mask, then the changed grid. */
    fun tiles(): List<ChangeTile> = when (phase()) {
        Phase.STUDY -> originalTiles
        Phase.MASK -> emptyList()
        Phase.FIND -> changedTiles
    }

    /**
     * Tap tile [index]. Returns true when it was the changed tile.
     * Ignored (returns false) before the changed grid is up, after 10 s, or for a bad index.
     */
    fun tap(index: Int): Boolean {
        if (elapsedMs() >= CHALLENGE_DURATION_MS) return false
        if (phase() != Phase.FIND) return false
        if (index !in changedTiles.indices) return false
        val ok = index == changedIndex
        lastTapCorrect = ok
        if (ok) solved++ else mistakes++
        roundStartMs = clock.monotonicMs()
        newRound()
        return ok
    }

    override fun finish(): ChallengeResult {
        val raw = (solved * POINTS_ROUND - mistakes * POINTS_MISTAKE) * Scoring.difficultyBonus(difficulty)
        val accuracy = Scoring.accuracy(solved, mistakes)
        val performance = ((solved / TARGET_ROUNDS) * accuracy).coerceIn(0.0, 1.0)
        val detail = when {
            solved + mistakes == 0 -> "No taps"
            mistakes == 0 -> "$solved spotted · no misses"
            else -> "$solved spotted · $mistakes ${if (mistakes == 1) "miss" else "misses"}"
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
        val n = gridSize * gridSize
        val original = List(n) { ChangeTile(random.nextInt(PALETTE_SIZE), random.nextInt(SHAPE_COUNT)) }
        val index = random.nextInt(n)
        val old = original[index]
        val kind = when {
            d <= 3 -> ChangeKind.COLOR_AND_SHAPE
            d <= 7 -> if (random.nextBoolean()) ChangeKind.COLOR else ChangeKind.SHAPE
            else -> when (random.nextInt(4)) {
                0 -> ChangeKind.COLOR
                1 -> ChangeKind.SHAPE
                else -> ChangeKind.SUBTLE_SHAPE
            }
        }
        val otherColor = (old.colorIndex + 1 + random.nextInt(PALETTE_SIZE - 1)) % PALETTE_SIZE
        val otherShape = (old.shape + 1 + random.nextInt(SHAPE_COUNT - 1)) % SHAPE_COUNT
        val new = when (kind) {
            ChangeKind.COLOR_AND_SHAPE -> ChangeTile(otherColor, otherShape)
            ChangeKind.COLOR -> old.copy(colorIndex = otherColor)
            ChangeKind.SHAPE -> old.copy(shape = otherShape)
            ChangeKind.SUBTLE_SHAPE -> old.copy(shape = LOOKALIKE[old.shape])
        }
        originalTiles = original
        changedTiles = original.toMutableList().also { it[index] = new }
        changedIndex = index
        changeKind = kind
    }

    companion object {
        const val SHAPE_CIRCLE = 0
        const val SHAPE_SQUARE = 1
        const val SHAPE_TRIANGLE = 2
        const val SHAPE_DIAMOND = 3
        const val SHAPE_STAR = 4
        const val SHAPE_HEXAGON = 5
        const val SHAPE_COUNT = 6

        /** Size of the app palette (GameColors.entries). */
        const val PALETTE_SIZE = 6

        const val STUDY_MS = 1000L
        const val MASK_MS = 300L

        const val POINTS_ROUND = 200.0
        const val POINTS_MISTAKE = 80.0
        /** Rounds in 10 s that count as a full-performance run. */
        const val TARGET_ROUNDS = 4.5

        /** Shape index -> the shape it is most easily confused with. */
        private val LOOKALIKE = intArrayOf(
            SHAPE_HEXAGON,  // circle
            SHAPE_DIAMOND,  // square
            SHAPE_DIAMOND,  // triangle
            SHAPE_SQUARE,   // diamond
            SHAPE_HEXAGON,  // star
            SHAPE_CIRCLE,   // hexagon
        )
    }
}
