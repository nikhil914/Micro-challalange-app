package com.microchallenge.core.challenges.games

import com.microchallenge.core.challenges.ChallengeSession
import com.microchallenge.core.challenges.MAX_DIFFICULTY
import com.microchallenge.core.challenges.MIN_DIFFICULTY
import com.microchallenge.core.challenges.Scoring
import com.microchallenge.core.engine.Clock
import com.microchallenge.core.model.CHALLENGE_DURATION_MS
import com.microchallenge.core.model.ChallengeResult
import com.microchallenge.core.model.ChallengeType
import kotlin.math.roundToInt
import kotlin.math.roundToLong
import kotlin.random.Random

/**
 * Reaction Tap: wait for the screen to turn green, then tap as fast as possible.
 *
 * Up to [MAX_ROUNDS] rounds fit in the 10 s window. Each round starts with a random
 * wait (shorter and less predictable at higher difficulty). From level 5 yellow
 * "decoy" flashes may appear during the wait; tapping one, or tapping while waiting,
 * is a false start: it counts as a mistake and the round's wait restarts.
 *
 * All timing is derived from [Clock.monotonicMs]; the schedule of the current round is
 * drawn from [random] whenever a round (re)starts, so a seed plus the same tap times
 * always gives the same run.
 */
class ReactionTapSession(
    difficulty: Int,
    private val random: Random,
    private val clock: Clock,
) : ChallengeSession {

    enum class Phase { WAITING, DECOY, GO, DONE }

    /** What a [tap] did, so the UI can give the right feedback. */
    enum class TapResult { REACTION, FALSE_START, IGNORED }

    /** One yellow flash inside a round's wait, relative to session start. */
    data class Decoy(val startMs: Long, val endMs: Long)

    override val type: ChallengeType = ChallengeType.REACTION_TAP
    override val difficulty: Int = difficulty.coerceIn(MIN_DIFFICULTY, MAX_DIFFICULTY)

    private val startMs: Long = clock.monotonicMs()

    /** Shortest wait before green, ms. 1500 at level 1, 1050 at level 10. */
    val minDelayMs: Long = 1500L - (this.difficulty - 1) * 50L

    /** Longest wait before green, ms. 4000 at level 1, 2650 at level 10. */
    val maxDelayMs: Long = 4000L - (this.difficulty - 1) * 150L

    private val reactions = mutableListOf<Long>()

    /** Reaction times of completed rounds, ms. */
    val reactionTimes: List<Long> get() = reactions

    /** Number of rounds with a valid reaction. */
    val roundsDone: Int get() = reactions.size

    /** Most recent valid reaction time in ms, or -1 when none yet. */
    val lastReactionMs: Long get() = reactions.lastOrNull() ?: -1L

    /** Taps during the wait or on a decoy (including anticipations faster than [MIN_HUMAN_MS]). */
    var falseStarts: Int = 0
        private set

    /** Result of the latest tap; [TapResult.IGNORED] before any tap. */
    var lastTapResult: TapResult = TapResult.IGNORED
        private set

    /** Session-relative time of the green onset of the current round. */
    var goAtMs: Long = 0L
        private set

    /** Decoy flashes scheduled in the current round's wait. */
    var decoys: List<Decoy> = emptyList()
        private set

    private var done = false

    init {
        scheduleRound(0L)
    }

    override val isComplete: Boolean get() = done

    private fun elapsed(): Long = clock.monotonicMs() - startMs

    /** Current phase, computed from the clock. */
    val phase: Phase
        get() {
            val t = elapsed()
            if (done || t >= CHALLENGE_DURATION_MS) return Phase.DONE
            return when {
                t >= goAtMs -> Phase.GO
                decoys.any { t >= it.startMs && t < it.endMs } -> Phase.DECOY
                else -> Phase.WAITING
            }
        }

    /** Register a tap (call on touch-down). */
    fun tap(): TapResult {
        val t = elapsed()
        if (done || t >= CHALLENGE_DURATION_MS) return TapResult.IGNORED.also { lastTapResult = it }
        val reaction = t - goAtMs
        val result = if (reaction >= MIN_HUMAN_MS) {
            reactions += reaction
            if (reactions.size >= MAX_ROUNDS) done = true
            TapResult.REACTION
        } else {
            // Tapped while waiting, on a decoy, or impossibly fast (anticipation).
            falseStarts++
            TapResult.FALSE_START
        }
        if (!done) scheduleRound(t)
        lastTapResult = result
        return result
    }

    private fun scheduleRound(roundStart: Long) {
        val drawn = minDelayMs + random.nextLong(maxDelayMs - minDelayMs + 1)
        // Keep the green onset early enough to react before time is up.
        val latestGo = CHALLENGE_DURATION_MS - LATE_GO_MARGIN_MS
        val delay = drawn.coerceAtMost((latestGo - roundStart).coerceAtLeast(MIN_SQUEEZED_DELAY_MS))
        goAtMs = roundStart + delay
        decoys = buildDecoys(roundStart, delay)
    }

    private fun buildDecoys(roundStart: Long, delay: Long): List<Decoy> {
        if (difficulty < DECOY_LEVEL) return emptyList()
        val chance = 0.25 + (difficulty - DECOY_LEVEL) * 0.13 // 0.25 at 5 .. 0.90 at 10
        if (random.nextDouble() >= chance) return emptyList()
        // Window: not right at round start, and well clear of the green onset.
        val earliest = DECOY_LEAD_MS
        val latest = delay - DECOY_DURATION_MS - DECOY_GAP_BEFORE_GO_MS
        if (latest <= earliest) return emptyList()
        val offset = earliest + random.nextLong(latest - earliest + 1)
        val s = roundStart + offset
        return listOf(Decoy(s, s + DECOY_DURATION_MS))
    }

    /** Mean of valid reactions in ms, or -1 when none. */
    fun meanReactionMs(): Long =
        if (reactions.isEmpty()) -1L else reactions.average().roundToLong()

    override fun finish(): ChallengeResult {
        done = true
        val duration = elapsed().coerceIn(0L, CHALLENGE_DURATION_MS)
        val rounds = reactions.size
        if (rounds == 0) {
            return ChallengeResult(
                type = type, score = 0, performance = 0.0, difficulty = difficulty,
                correct = 0, mistakes = falseStarts, durationMs = duration,
                detail = if (falseStarts > 0) plural(falseStarts, "false start") else "No reaction",
            )
        }
        val mean = reactions.average()
        val completion = rounds.toDouble() / MAX_ROUNDS
        // 200 ms -> 1.0, 500 ms -> 0.25, 600 ms+ -> 0.
        val speed = ((600.0 - mean) / 400.0).coerceIn(0.0, 1.0)
        val raw = 1000.0 * speed * (0.6 + 0.4 * completion) * Scoring.difficultyBonus(difficulty) -
            falseStarts * FALSE_START_PENALTY
        // ~300 ms with no false starts lands near the 0.7 target.
        val perfSpeed = ((550.0 - mean) / 350.0).coerceIn(0.0, 1.0)
        val performance = (perfSpeed * (0.7 + 0.3 * completion) - falseStarts * 0.1).coerceIn(0.0, 1.0)
        val detail = buildString {
            append("Avg ${mean.roundToInt()} ms")
            if (falseStarts > 0) append(" · ${plural(falseStarts, "false start")}")
        }
        return ChallengeResult(
            type = type, score = Scoring.clampScore(raw), performance = performance,
            difficulty = difficulty, correct = rounds, mistakes = falseStarts,
            durationMs = duration, detail = detail,
        )
    }

    private fun plural(n: Int, word: String) = if (n == 1) "1 $word" else "$n ${word}s"

    companion object {
        const val MAX_ROUNDS = 3
        /** Faster than this after green counts as an anticipation (false start). */
        const val MIN_HUMAN_MS = 100L
        const val DECOY_LEVEL = 5
        const val DECOY_DURATION_MS = 350L
        const val DECOY_LEAD_MS = 300L
        const val DECOY_GAP_BEFORE_GO_MS = 400L
        const val LATE_GO_MARGIN_MS = 900L
        const val MIN_SQUEEZED_DELAY_MS = 600L
        const val FALSE_START_PENALTY = 80.0
    }
}
