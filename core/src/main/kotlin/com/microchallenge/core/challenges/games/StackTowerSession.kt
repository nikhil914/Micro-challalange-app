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
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

/**
 * Stack Tower (timing stacker): a block slides back and forth above the tower.
 * Tap to drop it. Whatever hangs over the edge of the block below is sliced off,
 * so the tower gets narrower. A drop that misses the tower ends the run.
 *
 * A drop within [perfectTolerance] of the block below snaps onto it as a "perfect".
 * From the third perfect in a row the block grows back a little.
 *
 * Coordinates are fractions of the play width: 0.0 is the left edge, 1.0 the right edge.
 * The moving block's centre sweeps from 0.0 to 1.0 and back.
 *
 * Difficulty: faster sweep (1150 ms down to 610 ms per pass), narrower base
 * (0.60 down to 0.42) and a tighter perfect window.
 */
class StackTowerSession(
    difficulty: Int,
    private val random: Random,
    private val clock: Clock,
) : ChallengeSession {

    /** One placed block, as left and right edges. */
    data class Block(val left: Double, val right: Double) {
        val width: Double get() = right - left
        val center: Double get() = (left + right) / 2.0
    }

    /** What the latest drop did. [cut] is the sliced-off piece (null on a perfect or a miss). */
    data class Drop(
        val index: Int,
        val atMs: Long,
        val perfect: Boolean,
        val missed: Boolean,
        val placed: Block?,
        val cut: Block?,
        val combo: Int,
    )

    override val type: ChallengeType = ChallengeType.STACK_TOWER
    override val difficulty: Int = difficulty.coerceIn(MIN_DIFFICULTY, MAX_DIFFICULTY)

    private val startMs: Long = clock.monotonicMs()

    val baseWidth: Double = BASE_WIDTH_L1 - (this.difficulty - 1) * BASE_WIDTH_STEP

    /** Centre offset that still counts as a perfect drop. */
    val perfectTolerance: Double = PERFECT_TOL_L1 - (this.difficulty - 1) * PERFECT_TOL_STEP

    /** Time for one pass across the field at the start of the run. */
    val baseSweepMs: Long = SWEEP_L1_MS - (this.difficulty - 1) * SWEEP_STEP_MS

    private val blocks = mutableListOf(Block(0.5 - baseWidth / 2, 0.5 + baseWidth / 2))

    /** Placed blocks, base first. The base is not counted as a floor. */
    val tower: List<Block> get() = blocks.toList()

    /** Floors stacked on top of the base. */
    val height: Int get() = blocks.size - 1

    var perfects: Int = 0
        private set
    var combo: Int = 0
        private set
    var bestCombo: Int = 0
        private set
    var lastDrop: Drop? = null
        private set
    var missed: Boolean = false
        private set

    /** Sum of width kept per floor (1.0 = full base width), for scoring. */
    private var keptWidth = 0.0

    private var spawnMs: Long = 0L
    /** Random start side for the first block, then alternating. */
    private val firstFromLeft: Boolean = random.nextBoolean()

    /** Session-relative time, capped at the challenge length. */
    fun nowMs(): Long = (clock.monotonicMs() - startMs).coerceIn(0L, CHALLENGE_DURATION_MS)

    /** Pass time for the block now moving: 4% faster per floor, never below 70% of the start. */
    fun sweepMs(): Long = max((baseSweepMs * 0.7).toLong(), (baseSweepMs * Math.pow(SPEEDUP, height.toDouble())).toLong())

    private fun fromLeft(): Boolean = if (height % 2 == 0) firstFromLeft else !firstFromLeft

    /** Width of the block now moving: same as the top of the tower. */
    val movingWidth: Double get() = blocks.last().width

    /** Centre of the moving block (0..1), as a triangle wave starting at its entry side. */
    fun movingCenter(): Double {
        val sweep = sweepMs().toDouble()
        val t = (nowMs() - spawnMs).coerceAtLeast(0L) / sweep
        val phase = t % 2.0
        val x = if (phase <= 1.0) phase else 2.0 - phase
        return if (fromLeft()) x else 1.0 - x
    }

    /** False once the run is over: missed, or time is up. */
    fun canDrop(): Boolean = !missed && nowMs() < CHALLENGE_DURATION_MS

    override val isComplete: Boolean get() = missed

    /** Drops the moving block. Returns what happened, or null when ignored (after a miss or time up). */
    fun drop(): Drop? {
        if (!canDrop()) return null
        val now = nowMs()
        val top = blocks.last()
        val w = top.width
        val c = movingCenter()
        val left = c - w / 2
        val right = c + w / 2
        val index = blocks.size
        val drop: Drop
        if (abs(c - top.center) <= perfectTolerance) {
            combo++
            perfects++
            bestCombo = max(bestCombo, combo)
            val grow = if (combo >= GROW_FROM_COMBO) GROW_STEP else 0.0
            val newWidth = min(baseWidth, w + grow)
            val placed = Block(top.center - newWidth / 2, top.center + newWidth / 2)
            blocks += placed
            keptWidth += placed.width / baseWidth
            drop = Drop(index, now, perfect = true, missed = false, placed = placed, cut = null, combo = combo)
        } else {
            combo = 0
            val overlapLeft = max(left, top.left)
            val overlapRight = min(right, top.right)
            if (overlapRight - overlapLeft <= MIN_OVERLAP) {
                missed = true
                drop = Drop(index, now, perfect = false, missed = true, placed = null, cut = Block(left, right), combo = 0)
            } else {
                val placed = Block(overlapLeft, overlapRight)
                val cut = if (left < top.left) Block(left, top.left) else Block(top.right, right)
                blocks += placed
                keptWidth += placed.width / baseWidth
                drop = Drop(index, now, perfect = false, missed = false, placed = placed, cut = cut, combo = 0)
            }
        }
        spawnMs = now
        lastDrop = drop
        return drop
    }

    override fun finish(): ChallengeResult {
        // Each floor is worth the share of base width it kept, plus a bonus per perfect.
        val raw = keptWidth + perfects * PERFECT_BONUS
        val performance = (raw / TARGET_POINTS).coerceIn(0.0, 1.0)
        val detail = when {
            height == 0 && missed -> "Missed the tower"
            height == 0 -> "No floors"
            else -> "$height floors, $perfects perfect"
        }
        return ChallengeResult(
            type = type,
            score = Scoring.clampScore(performance * 1000.0),
            performance = performance,
            difficulty = difficulty,
            correct = height,
            mistakes = if (missed) 1 else 0,
            durationMs = nowMs(),
            detail = detail,
            timestampMs = clock.wallMs(),
        )
    }

    companion object {
        const val BASE_WIDTH_L1 = 0.60
        const val BASE_WIDTH_STEP = 0.02
        const val PERFECT_TOL_L1 = 0.022
        const val PERFECT_TOL_STEP = 0.0015
        const val SWEEP_L1_MS = 1150L
        const val SWEEP_STEP_MS = 60L
        const val SPEEDUP = 0.96
        const val GROW_FROM_COMBO = 3
        const val GROW_STEP = 0.03
        const val PERFECT_BONUS = 0.5
        /** Points for a full score: about 9 clean floors or 7 perfects. */
        const val TARGET_POINTS = 10.0
        /** Overlap thinner than this counts as a miss. */
        const val MIN_OVERLAP = 0.004
    }
}
