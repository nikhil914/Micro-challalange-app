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
 * Math Blitz: equation bubbles fall down [LANES] lanes. A target number sits at the bottom.
 * Pop every bubble whose equation equals the target before it lands.
 *
 * - Right pop: points scaled by the running combo.
 * - Wrong pop: mistake, combo resets.
 * - A matching bubble that lands: miss, combo resets.
 * - Every [WAVE_SIZE] right pops start a new wave with a new target; the screen is cleared.
 *
 * Difficulty raises fall speed, spawn rate, and equation complexity
 * (1-3 + / −, 4-6 adds ×, 7-10 adds ÷ and two-step equations).
 *
 * All timing comes from [clock], so the session stays a pure state machine.
 */
class MathBlitzSession(
    difficulty: Int,
    private val random: Random,
    private val clock: Clock,
) : ChallengeSession {

    enum class TapResult { HIT, WAVE_CLEARED, WRONG, IGNORED }

    /** One falling equation. Times are session-relative ms. */
    data class Bubble(
        val id: Int,
        val lane: Int,
        val expression: String,
        val value: Int,
        val spawnMs: Long,
        val fallMs: Long,
    ) {
        /** 0 at the top edge, 1 when it touches the floor. */
        fun progress(nowMs: Long): Float = ((nowMs - spawnMs).toFloat() / fallMs).coerceIn(0f, 1f)
    }

    /** A short-lived visual event (pop, wrong tap, landed miss) for the UI to animate. */
    data class Effect(
        val kind: Kind,
        val lane: Int,
        val progress: Float,
        val atMs: Long,
        val points: Int,
    ) {
        enum class Kind { POP, WRONG, MISS }
    }

    override val type: ChallengeType = ChallengeType.MATH_BLITZ
    override val difficulty: Int = difficulty.coerceIn(MIN_DIFFICULTY, MAX_DIFFICULTY)

    private val startMs: Long = clock.monotonicMs()

    val fallMs: Long = (2_700L - this.difficulty * 130L).coerceAtLeast(1_300L)
    private val spawnEveryMs: Long = (760L - this.difficulty * 40L).coerceAtLeast(360L)

    var target: Int = newTarget()
        private set

    /** Increments on every new wave, so the UI can key a banner animation on it. */
    var wave: Int = 1
        private set

    /** Session-relative time the current wave started. */
    var waveStartMs: Long = 0L
        private set

    var hitsInWave: Int = 0
        private set

    private val live = mutableListOf<Bubble>()
    private val recentEffects = mutableListOf<Effect>()
    private var nextId = 0
    private var nextSpawnMs = FIRST_SPAWN_MS
    private var lastLane = -1
    private var sinceMatch = 0

    var combo: Int = 0
        private set
    var bestCombo: Int = 0
        private set
    var correct: Int = 0
        private set
    var mistakes: Int = 0
        private set
    var misses: Int = 0
        private set
    var points: Int = 0
        private set

    private var ended = false

    fun nowMs(): Long = (clock.monotonicMs() - startMs).coerceIn(0L, CHALLENGE_DURATION_MS)

    override val isComplete: Boolean get() = ended

    /** Bubbles currently on screen. Advances spawning and landing to the current time. */
    fun bubbles(): List<Bubble> {
        update()
        return live.toList()
    }

    /** Effects younger than [EFFECT_MS]. */
    fun effects(): List<Effect> {
        update()
        return recentEffects.toList()
    }

    /** Multiplier the next right pop earns. */
    fun multiplier(): Double = 1.0 + combo.coerceAtMost(MAX_COMBO_STEPS) * 0.25

    private fun update() {
        val now = nowMs()
        if (ended) return
        // Spawn on schedule. Bubbles spawn at their scheduled time, so a slow frame never bunches them.
        while (nextSpawnMs <= now && now < CHALLENGE_DURATION_MS) {
            live += spawn(nextSpawnMs)
            nextSpawnMs += spawnEveryMs
        }
        // Land bubbles that reached the floor.
        val landed = live.filter { now - it.spawnMs >= it.fallMs }
        for (b in landed) {
            live.remove(b)
            if (b.value == target) {
                misses++
                combo = 0
                recentEffects += Effect(Effect.Kind.MISS, b.lane, 1f, b.spawnMs + b.fallMs, 0)
            }
        }
        recentEffects.removeAll { now - it.atMs > EFFECT_MS }
    }

    fun tap(bubbleId: Int): TapResult {
        if (ended || nowMs() >= CHALLENGE_DURATION_MS) return TapResult.IGNORED
        update()
        val b = live.firstOrNull { it.id == bubbleId } ?: return TapResult.IGNORED
        val now = nowMs()
        live.remove(b)
        return if (b.value == target) {
            correct++
            combo++
            bestCombo = maxOf(bestCombo, combo)
            val gained = (BASE_POINTS * multiplier()).toInt()
            points += gained
            hitsInWave++
            recentEffects += Effect(Effect.Kind.POP, b.lane, b.progress(now), now, gained)
            if (hitsInWave >= WAVE_SIZE) {
                startWave(now)
                TapResult.WAVE_CLEARED
            } else {
                TapResult.HIT
            }
        } else {
            mistakes++
            combo = 0
            recentEffects += Effect(Effect.Kind.WRONG, b.lane, b.progress(now), now, 0)
            TapResult.WRONG
        }
    }

    private fun startWave(now: Long) {
        wave++
        waveStartMs = now
        hitsInWave = 0
        live.clear()
        val old = target
        do { target = newTarget() } while (target == old)
        nextSpawnMs = now + WAVE_PAUSE_MS
        sinceMatch = 0
    }

    // ---------------------------------------------------------------- generation

    private fun IntRange.pick(): Int = random.nextInt(first, last + 1)

    private fun newTarget(): Int = when {
        difficulty <= 3 -> (6..15).pick()
        difficulty <= 6 -> (12..36).pick()
        else -> (18..60).pick()
    }

    private fun spawn(atMs: Long): Bubble {
        val lane = (0 until LANES).filter { it != lastLane }.random(random)
        lastLane = lane
        // Roughly 45% matches, and never more than two decoys in a row.
        val match = sinceMatch >= 2 || random.nextInt(100) < 45
        sinceMatch = if (match) 0 else sinceMatch + 1
        val value = if (match) target else decoyValue()
        return Bubble(nextId++, lane, expressionFor(value), value, atMs, fallMs)
    }

    /** Near misses are what make it hard: mostly ±1..3, sometimes ±10. */
    private fun decoyValue(): Int {
        while (true) {
            val delta = when (random.nextInt(10)) {
                in 0..6 -> (1..3).pick()
                in 7..8 -> (4..6).pick()
                else -> 10
            }
            val v = if (random.nextBoolean()) target + delta else target - delta
            if (v >= 1 && v != target) return v
        }
    }

    /** Builds a random equation that evaluates to [value] (value >= 1). */
    fun expressionFor(value: Int): String {
        val d = difficulty
        val roll = random.nextInt(10)
        return when {
            d >= 7 && roll < 2 -> division(value)
            d >= 7 && roll < 5 -> twoStep(value)
            d >= 4 && roll < 7 -> multiply(value) ?: addition(value)
            roll < 5 -> addition(value)
            else -> subtraction(value)
        }
    }

    private fun addition(v: Int): String {
        if (v < 2) return subtraction(v)
        val a = (1 until v).pick()
        return "$a + ${v - a}"
    }

    private fun subtraction(v: Int): String {
        val b = if (difficulty <= 3) (1..9).pick() else (2..25).pick()
        return "${v + b} − $b"
    }

    private fun multiply(v: Int): String? {
        val pairs = (2..12).filter { v % it == 0 && v / it in 2..12 }
        if (pairs.isEmpty()) return null
        val a = pairs.random(random)
        return "$a × ${v / a}"
    }

    private fun division(v: Int): String {
        val b = (2..9).pick()
        return "${v * b} ÷ $b"
    }

    private fun twoStep(v: Int): String {
        val a = (2..6).pick()
        val maxB = (v / a).coerceAtMost(9)
        if (maxB < 2) return addition(v)
        val b = (2..maxB).pick()
        val rest = v - a * b
        return when {
            rest > 0 -> "$a × $b + $rest"
            rest == 0 -> "$a × $b"
            else -> "$a × $b − ${-rest}"
        }
    }

    override fun finish(): ChallengeResult {
        update()
        ended = true
        val raw = (points - mistakes * WRONG_PENALTY - misses * MISS_PENALTY) * Scoring.difficultyBonus(difficulty)
        val attempts = correct + mistakes + misses
        val accuracy = if (attempts == 0) 0.0 else correct.toDouble() / attempts
        val performance = ((correct / TARGET_CORRECT).coerceAtMost(1.0) * accuracy).coerceIn(0.0, 1.0)
        val detail = if (attempts == 0) "No pops" else "$correct popped · best combo x$bestCombo"
        return ChallengeResult(
            type = type,
            score = Scoring.clampScore(raw),
            performance = performance,
            difficulty = difficulty,
            correct = correct,
            mistakes = mistakes + misses,
            durationMs = nowMs(),
            detail = detail,
            timestampMs = clock.wallMs(),
        )
    }

    companion object {
        const val LANES = 3
        const val WAVE_SIZE = 3
        const val FIRST_SPAWN_MS = 250L
        const val WAVE_PAUSE_MS = 450L
        const val EFFECT_MS = 600L
        const val BASE_POINTS = 50
        const val MAX_COMBO_STEPS = 8
        const val WRONG_PENALTY = 30
        const val MISS_PENALTY = 15
        /** Right pops in 10 s that count as a full-performance run. */
        const val TARGET_CORRECT = 9.0
    }
}
