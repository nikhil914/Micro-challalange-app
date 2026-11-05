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
import kotlin.random.Random

/** GO = green circle (tap), NO_GO = red square (do not tap). Shape differs as well as color. */
enum class GoNoGoStimulus { GO, NO_GO }

/**
 * One scheduled stimulus. It is visible during [onsetMs] until [hideMs]; a response
 * counts for it until [responseEndMs] (the next onset), so a fast tap just after it
 * disappears still counts.
 */
data class ScheduledStimulus(
    val index: Int,
    val type: GoNoGoStimulus,
    val onsetMs: Long,
    val hideMs: Long,
    val responseEndMs: Long,
)

/** What a [GoNoGoSession.tap] did. */
enum class GoNoGoTapOutcome {
    /** Tapped a GO stimulus. */
    HIT,
    /** Tapped a NO-GO stimulus (commission error). */
    FALSE_ALARM,
    /** Tap with no stimulus to answer (before the first one, or already answered). Small penalty. */
    STRAY,
    /** After the 10 s window: nothing recorded. */
    IGNORED,
}

/**
 * Go / No-Go: stimuli flash every ~(900 − 40·difficulty) ms. Tap on GO (75 %),
 * hold back on NO-GO (25 %). The whole schedule is precomputed from [random].
 */
class GoNoGoSession(
    difficulty: Int,
    random: Random,
    private val clock: Clock,
) : ChallengeSession {

    override val type: ChallengeType = ChallengeType.GO_NO_GO
    override val difficulty: Int = difficulty.coerceIn(MIN_DIFFICULTY, MAX_DIFFICULTY)

    private val startMs: Long = clock.monotonicMs()

    /** Nominal gap between onsets, ms. */
    val intervalMs: Long = 900L - this.difficulty * 40L

    /** How long each stimulus stays visible, ms. */
    val displayMs: Long = (intervalMs * DISPLAY_FRACTION).toLong()

    /** Full precomputed schedule, in onset order. */
    val schedule: List<ScheduledStimulus> = buildSchedule(random)

    val goCount: Int = schedule.count { it.type == GoNoGoStimulus.GO }
    val noGoCount: Int = schedule.size - goCount

    private val responded = BooleanArray(schedule.size)
    private val reactionTimes = mutableListOf<Long>()

    var hits: Int = 0
        private set
    var falseAlarms: Int = 0
        private set
    var strayTaps: Int = 0
        private set

    /** Reaction times of hits, ms from onset. */
    val hitReactionTimesMs: List<Long> get() = reactionTimes

    val meanReactionMs: Long?
        get() = if (reactionTimes.isEmpty()) null else reactionTimes.average().roundToInt().toLong()

    /** Last tap outcome, for UI feedback. */
    var lastOutcome: GoNoGoTapOutcome? = null
        private set

    /** Index (into [schedule]) of the stimulus the last tap answered, or -1. */
    var lastOutcomeIndex: Int = -1
        private set

    fun elapsedMs(): Long = (clock.monotonicMs() - startMs).coerceIn(0L, CHALLENGE_DURATION_MS)

    /** True once the last stimulus' response window has closed. */
    override val isComplete: Boolean
        get() = schedule.isEmpty() || elapsedMs() >= schedule.last().responseEndMs

    /** The visible stimulus, or null between stimuli. */
    fun currentStimulus(): GoNoGoStimulus? = visible()?.type

    /** The visible scheduled stimulus (with its index, for keying UI animations), or null. */
    fun visible(): ScheduledStimulus? {
        val t = elapsedMs()
        return schedule.firstOrNull { t >= it.onsetMs && t < it.hideMs }
    }

    /** Whether the stimulus at [index] was already answered by a tap. */
    fun wasResponded(index: Int): Boolean = index in responded.indices && responded[index]

    /** GO stimuli whose response window has closed without a tap, as of now. */
    val omissions: Int
        get() {
            val t = elapsedMs()
            return schedule.count { it.type == GoNoGoStimulus.GO && !responded[it.index] && t >= it.responseEndMs }
        }

    /** Register a tap (call on touch-down). */
    fun tap(): GoNoGoTapOutcome {
        val t = elapsedMs()
        if (t >= CHALLENGE_DURATION_MS) return record(GoNoGoTapOutcome.IGNORED, -1)
        val s = schedule.firstOrNull { t >= it.onsetMs && t < it.responseEndMs }
        if (s == null || responded[s.index]) {
            strayTaps++
            return record(GoNoGoTapOutcome.STRAY, s?.index ?: -1)
        }
        responded[s.index] = true
        return if (s.type == GoNoGoStimulus.GO) {
            hits++
            reactionTimes += t - s.onsetMs
            record(GoNoGoTapOutcome.HIT, s.index)
        } else {
            falseAlarms++
            record(GoNoGoTapOutcome.FALSE_ALARM, s.index)
        }
    }

    private fun record(o: GoNoGoTapOutcome, index: Int): GoNoGoTapOutcome {
        lastOutcome = o
        lastOutcomeIndex = index
        return o
    }

    override fun finish(): ChallengeResult {
        val hitRate = if (goCount == 0) 0.0 else hits.toDouble() / goCount
        val faRate = if (noGoCount == 0) 0.0 else falseAlarms.toDouble() / noGoCount
        val rtFactor = meanReactionMs?.let { ((RT_SLOW - it) / (RT_SLOW - RT_FAST)).coerceIn(0.0, 1.0) } ?: 0.0
        val raw = (hitRate * 550 + hitRate * rtFactor * 250 - faRate * 300 - strayTaps * 15) *
            Scoring.difficultyBonus(difficulty)
        val performance = (hitRate * (1 - faRate) * (0.6 + 0.4 * rtFactor) - strayTaps * 0.02).coerceIn(0.0, 1.0)
        val score = if (hits == 0) 0 else Scoring.clampScore(raw)
        val detail = if (hits + falseAlarms == 0) "No hits" else buildString {
            append("$hits/$goCount hits")
            meanReactionMs?.let { append(" · avg $it ms") }
            if (falseAlarms > 0) append(" · $falseAlarms on red")
        }
        return ChallengeResult(
            type = type,
            score = score,
            performance = if (hits == 0) 0.0 else performance,
            difficulty = difficulty,
            correct = hits,
            mistakes = falseAlarms + omissions,
            durationMs = elapsedMs(),
            detail = detail,
            timestampMs = clock.wallMs(),
        )
    }

    private fun buildSchedule(random: Random): List<ScheduledStimulus> {
        // Onsets with ±15 % jitter so the rhythm cannot be anticipated.
        val onsets = mutableListOf<Long>()
        var t = LEAD_IN_MS
        while (true) {
            val gap = (intervalMs * (0.85 + random.nextDouble() * 0.30)).toLong()
            if (t + gap > CHALLENGE_DURATION_MS) break
            onsets += t
            t += gap
        }
        val n = onsets.size
        val noGo = (n * NO_GO_SHARE).roundToInt()
        // First stimulus is always GO so the player learns what to do; the rest is shuffled.
        val types = (List(noGo) { GoNoGoStimulus.NO_GO } + List(n - 1 - noGo) { GoNoGoStimulus.GO })
            .shuffled(random)
        val all = listOf(GoNoGoStimulus.GO) + types
        return onsets.mapIndexed { i, onset ->
            val end = if (i + 1 < n) onsets[i + 1] else minOf(onset + intervalMs, CHALLENGE_DURATION_MS)
            ScheduledStimulus(i, all[i], onset, onset + minOf(displayMs, end - onset), end)
        }
    }

    companion object {
        const val LEAD_IN_MS = 600L
        const val NO_GO_SHARE = 0.25
        const val DISPLAY_FRACTION = 0.6
        const val RT_FAST = 250.0
        const val RT_SLOW = 650.0
    }
}
