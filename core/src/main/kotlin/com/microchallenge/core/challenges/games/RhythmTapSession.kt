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
 * Rhythm Tap: rings shrink onto a target circle on a steady beat. Tap the moment a ring
 * lands. Each beat is judged PERFECT, GOOD or MISS; taps far from any beat count as stray.
 *
 * Difficulty: the tempo rises (640 ms down to 415 ms between beats), rings approach faster,
 * the hit windows narrow, and from level 4 the pattern gets rests and off-beat notes.
 */
class RhythmTapSession(
    difficulty: Int,
    private val random: Random,
    private val clock: Clock,
) : ChallengeSession {

    enum class Grade { PERFECT, GOOD, MISS }

    /** Outcome of one tap or one missed beat. [offsetMs] is signed (positive = late), null for stray taps / misses. */
    data class Hit(val beatIndex: Int, val grade: Grade, val offsetMs: Long?, val atMs: Long)

    override val type: ChallengeType = ChallengeType.RHYTHM_TAP
    override val difficulty: Int = difficulty.coerceIn(MIN_DIFFICULTY, MAX_DIFFICULTY)

    private val startMs: Long = clock.monotonicMs()

    /** Base gap between beats in ms. */
    val beatIntervalMs: Long = 640L - (this.difficulty - 1) * 25L

    /** How long a ring is visible before it lands. */
    val approachMs: Long = 1100L - (this.difficulty - 1) * 40L

    /** |offset| up to this is PERFECT. */
    val perfectWindowMs: Long = 70L - (this.difficulty - 1) * 3L

    /** |offset| up to this is GOOD; beyond it the beat can no longer be hit. */
    val goodWindowMs: Long = 150L - (this.difficulty - 1) * 6L

    /** Beat landing times in ms since start, strictly increasing. */
    val beats: List<Long> = generateBeats()

    private val grades = arrayOfNulls<Grade>(beats.size)
    private val hitList = mutableListOf<Hit>()
    private var strays = 0
    private var combo = 0
    private var bestCombo = 0

    /** Every judgement so far, oldest first (hits, misses and stray taps with beatIndex -1). */
    val hits: List<Hit> get() { sync(); return hitList.toList() }

    val currentCombo: Int get() { sync(); return combo }
    val maxCombo: Int get() { sync(); return bestCombo }
    val strayTaps: Int get() = strays

    fun gradeOf(index: Int): Grade? { sync(); return grades[index] }

    fun count(grade: Grade): Int { sync(); return grades.count { it == grade } }

    /** Ms since the start, capped at the challenge length. */
    fun elapsedMs(): Long = (clock.monotonicMs() - startMs).coerceIn(0L, CHALLENGE_DURATION_MS)

    /** Ends once the last beat has been judged and its feedback had a moment on screen. */
    override val isComplete: Boolean
        get() = clock.monotonicMs() - startMs >= beats.last() + goodWindowMs + END_HOLD_MS

    /** Marks beats whose window has passed as MISS. */
    private fun sync() {
        val now = elapsedMs()
        for (i in beats.indices) {
            if (grades[i] == null && now > beats[i] + goodWindowMs) {
                grades[i] = Grade.MISS
                combo = 0
                hitList += Hit(i, Grade.MISS, null, beats[i] + goodWindowMs)
            }
        }
    }

    /** Register a tap. Returns the judgement, or null once time is up. */
    fun tap(): Hit? {
        if (clock.monotonicMs() - startMs >= CHALLENGE_DURATION_MS) return null
        sync()
        val now = elapsedMs()
        val index = beats.indices.firstOrNull { grades[it] == null && abs(now - beats[it]) <= goodWindowMs }
        val hit = if (index == null) {
            strays++
            combo = 0
            Hit(-1, Grade.MISS, null, now)
        } else {
            val offset = now - beats[index]
            val grade = if (abs(offset) <= perfectWindowMs) Grade.PERFECT else Grade.GOOD
            grades[index] = grade
            combo++
            bestCombo = maxOf(bestCombo, combo)
            Hit(index, grade, offset, now)
        }
        hitList += hit
        return hit
    }

    override fun finish(): ChallengeResult {
        sync()
        val perfect = grades.count { it == Grade.PERFECT }
        val good = grades.count { it == Grade.GOOD }
        val missed = beats.size - perfect - good
        val raw = (perfect + GOOD_CREDIT * good) / beats.size - STRAY_PENALTY * strays
        val quality = raw.coerceIn(0.0, 1.0)
        val detail = if (perfect + good == 0) "No beats hit" else
            "$perfect perfect, $good good, best combo $bestCombo"
        return ChallengeResult(
            type = type,
            score = Scoring.clampScore(quality * 1000.0),
            performance = quality,
            difficulty = difficulty,
            correct = perfect + good,
            mistakes = missed + strays,
            durationMs = elapsedMs(),
            detail = detail,
            timestampMs = clock.wallMs(),
        )
    }

    // ---------------------------------------------------------------- generation

    private fun generateBeats(): List<Long> {
        val out = mutableListOf<Long>()
        val restChance = if (difficulty >= 4) 0.08 + (difficulty - 4) * 0.02 else 0.0
        val offBeatChance = if (difficulty >= 6) 0.10 + (difficulty - 6) * 0.04 else 0.0
        var t = FIRST_BEAT_MS
        while (t <= LAST_BEAT_MS) {
            // Never rest on the first two beats, so the tempo is clear.
            if (out.size < 2 || random.nextDouble() >= restChance) out += t
            val half = t + beatIntervalMs / 2
            if (out.size >= 2 && half <= LAST_BEAT_MS && random.nextDouble() < offBeatChance) out += half
            t += beatIntervalMs
        }
        return out
    }

    companion object {
        /** First ring lands here, leaving time to see it approach. */
        const val FIRST_BEAT_MS = 1400L
        const val LAST_BEAT_MS = 9300L
        const val END_HOLD_MS = 350L
        const val GOOD_CREDIT = 0.6
        const val STRAY_PENALTY = 0.05
    }
}
