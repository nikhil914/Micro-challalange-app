package com.microchallenge.core.challenges.games

import com.microchallenge.core.challenges.ChallengeSession
import com.microchallenge.core.challenges.MAX_DIFFICULTY
import com.microchallenge.core.challenges.MIN_DIFFICULTY
import com.microchallenge.core.challenges.Scoring
import com.microchallenge.core.engine.Clock
import com.microchallenge.core.model.CHALLENGE_DURATION_MS
import com.microchallenge.core.model.ChallengeResult
import com.microchallenge.core.model.ChallengeType
import kotlin.math.hypot
import kotlin.random.Random

/**
 * One bubble on the field. Position is normalised (0..1) so the UI can scale it to any
 * canvas. [colorIndex] indexes the palette the UI draws with (0 until [ColorRushSession.colorCount]).
 */
data class RushBubble(
    val id: Int,
    val colorIndex: Int,
    val x: Float,
    val y: Float,
    val radius: Float,
    val spawnMs: Long,
    val despawnMs: Long,
)

/** One stretch of the run with a fixed target color. */
data class RushPhase(val colorIndex: Int, val startMs: Long, val endMs: Long)

/** What a [ColorRushSession.tap] did. */
enum class RushTapOutcome { HIT, WRONG_COLOR, IGNORED }

/**
 * Color Rush: bubbles of several colors pop up on the field and fade away. Tap only the
 * bubbles that match the current target color; the target switches a few times per run.
 * Consecutive hits build a combo that multiplies points. The whole spawn schedule and
 * phase list are precomputed from [random], so a seed always gives the same run.
 */
class ColorRushSession(
    difficulty: Int,
    random: Random,
    private val clock: Clock,
) : ChallengeSession {

    override val type: ChallengeType = ChallengeType.COLOR_RUSH
    override val difficulty: Int = difficulty.coerceIn(MIN_DIFFICULTY, MAX_DIFFICULTY)

    private val startMs: Long = clock.monotonicMs()

    /** Number of distinct colors in play: 3 at level 1, up to 6. */
    val colorCount: Int = (3 + (this.difficulty - 1) / 3).coerceAtMost(6)

    /** Gap between spawns, ms. */
    val spawnGapMs: Long = 420L - this.difficulty * 22L

    /** How long a bubble stays on the field, ms. */
    val lifeMs: Long = 1_700L - this.difficulty * 80L

    val phases: List<RushPhase> = buildPhases(random)
    val bubbles: List<RushBubble> = buildBubbles(random)

    private val popped = BooleanArray(bubbles.size)

    var hits: Int = 0
        private set
    var wrongTaps: Int = 0
        private set
    var combo: Int = 0
        private set
    var bestCombo: Int = 0
        private set
    var points: Int = 0
        private set

    /** Targets that existed and expired untapped, as of now. */
    val missedTargets: Int
        get() {
            val t = elapsedMs()
            return bubbles.count { !popped[it.id] && t >= it.despawnMs && isTarget(it) }
        }

    val totalTargets: Int = bubbles.count { isTarget(it) }

    fun elapsedMs(): Long = (clock.monotonicMs() - startMs).coerceIn(0L, CHALLENGE_DURATION_MS)

    override val isComplete: Boolean get() = elapsedMs() >= CHALLENGE_DURATION_MS

    /** Target phase active at [t]. */
    fun phaseAt(t: Long = elapsedMs()): RushPhase = phases.lastOrNull { t >= it.startMs } ?: phases.first()

    fun targetColor(): Int = phaseAt().colorIndex

    /** A bubble is a target when its color matches the phase active when it spawned. */
    fun isTarget(b: RushBubble): Boolean = b.colorIndex == phaseAt(b.spawnMs).colorIndex

    fun wasPopped(id: Int): Boolean = id in popped.indices && popped[id]

    /** Bubbles on the field right now (not popped, not expired). */
    fun visible(): List<RushBubble> {
        val t = elapsedMs()
        return bubbles.filter { t >= it.spawnMs && t < it.despawnMs && !popped[it.id] }
    }

    /** Tap at normalised point ([x], [y]). Hits the topmost (newest) visible bubble under it. */
    fun tapAt(x: Float, y: Float): Pair<RushTapOutcome, RushBubble?> {
        if (elapsedMs() >= CHALLENGE_DURATION_MS) return RushTapOutcome.IGNORED to null
        val b = visible().lastOrNull { hypot(it.x - x, it.y - y) <= it.radius * TOUCH_SLOP }
            ?: return RushTapOutcome.IGNORED to null
        return tap(b.id) to b
    }

    /** Tap the bubble with [id]. */
    fun tap(id: Int): RushTapOutcome {
        val t = elapsedMs()
        val b = bubbles.getOrNull(id) ?: return RushTapOutcome.IGNORED
        if (t >= CHALLENGE_DURATION_MS || popped[id] || t < b.spawnMs || t >= b.despawnMs) return RushTapOutcome.IGNORED
        popped[id] = true
        return if (b.colorIndex == targetColor()) {
            hits++
            combo++
            if (combo > bestCombo) bestCombo = combo
            points += 10 * comboMultiplier()
            RushTapOutcome.HIT
        } else {
            wrongTaps++
            combo = 0
            RushTapOutcome.WRONG_COLOR
        }
    }

    /** 1x, then +1 for every 3 hits in a row, capped at 4x. */
    fun comboMultiplier(): Int = (1 + combo / 3).coerceAtMost(4)

    override fun finish(): ChallengeResult {
        val acc = Scoring.accuracy(hits, wrongTaps)
        val coverage = if (totalTargets == 0) 0.0 else hits.toDouble() / totalTargets
        val raw = (coverage * 600 + acc * 250 + bestCombo * 12) * Scoring.difficultyBonus(difficulty)
        val score = if (hits == 0) 0 else Scoring.clampScore(raw)
        val performance = if (hits == 0) 0.0 else (coverage * acc).coerceIn(0.0, 1.0)
        val detail = if (hits == 0) "No pops" else buildString {
            append("$hits/$totalTargets popped")
            if (bestCombo >= 3) append(" · best combo x$bestCombo")
            if (wrongTaps > 0) append(" · $wrongTaps wrong")
        }
        return ChallengeResult(
            type = type,
            score = score,
            performance = performance,
            difficulty = difficulty,
            correct = hits,
            mistakes = wrongTaps + missedTargets,
            durationMs = elapsedMs(),
            detail = detail,
            timestampMs = clock.wallMs(),
        )
    }

    private fun buildPhases(random: Random): List<RushPhase> {
        val count = if (difficulty >= 6) 4 else 3
        val len = CHALLENGE_DURATION_MS / count
        val out = mutableListOf<RushPhase>()
        var prev = -1
        for (i in 0 until count) {
            var c = random.nextInt(colorCount)
            if (c == prev) c = (c + 1 + random.nextInt(colorCount - 1)) % colorCount
            prev = c
            val end = if (i == count - 1) CHALLENGE_DURATION_MS else (i + 1) * len
            out += RushPhase(c, i * len, end)
        }
        return out
    }

    private fun buildBubbles(random: Random): List<RushBubble> {
        val out = mutableListOf<RushBubble>()
        var t = LEAD_IN_MS
        while (t < CHALLENGE_DURATION_MS - 300) {
            val target = phaseAt(t).colorIndex
            // About 40 % targets so there is always something to chase.
            val color = if (random.nextDouble() < TARGET_SHARE) target else {
                val o = random.nextInt(colorCount - 1)
                if (o >= target) o + 1 else o
            }
            val r = 0.075f + random.nextFloat() * 0.035f
            // Avoid stacking on a bubble that is still alive.
            var x = 0f
            var y = 0f
            for (attempt in 0 until 8) {
                x = r + random.nextFloat() * (1 - 2 * r)
                y = r + random.nextFloat() * (1 - 2 * r)
                val clash = out.any { t < it.despawnMs && hypot(it.x - x, it.y - y) < it.radius + r }
                if (!clash) break
            }
            out += RushBubble(out.size, color, x, y, r, t, minOf(t + lifeMs, CHALLENGE_DURATION_MS))
            t += (spawnGapMs * (0.8 + random.nextDouble() * 0.4)).toLong()
        }
        return out
    }

    companion object {
        const val LEAD_IN_MS = 400L
        const val TARGET_SHARE = 0.4
        const val TOUCH_SLOP = 1.15f
    }
}
