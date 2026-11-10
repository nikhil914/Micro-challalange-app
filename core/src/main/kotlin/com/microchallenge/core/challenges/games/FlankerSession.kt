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
 * Arrow Focus (Eriksen flanker): a row of arrows appears; answer which way the MIDDLE
 * arrow points and ignore the flankers around it.
 *
 * The row has 5 glyphs (7 from level 7). The share of congruent rows (all arrows agree)
 * drops from 70% at level 1 to 25% at level 10. From level 5 the non-congruent rows also
 * come as neutral (dashes) or mixed (flankers disagree with each other). From level 8 the
 * row jumps to a new [offsetSlot] every trial. A new trial appears right after each answer.
 */
class FlankerSession(
    difficulty: Int,
    private val random: Random,
    private val clock: Clock,
) : ChallengeSession {

    /** One symbol in the row. */
    enum class Glyph { LEFT, RIGHT, DASH }

    /** How the flankers relate to the middle arrow. */
    enum class TrialKind { CONGRUENT, INCONGRUENT, NEUTRAL, MIXED }

    /** Outcome of an [answer]. */
    enum class AnswerResult { CORRECT, WRONG, IGNORED }

    override val type: ChallengeType = ChallengeType.FLANKER
    override val difficulty: Int = difficulty.coerceIn(MIN_DIFFICULTY, MAX_DIFFICULTY)

    private val startMs: Long = clock.monotonicMs()

    /** Glyphs per row (odd, so there is one middle). */
    val arrowCount: Int = if (this.difficulty >= 7) 7 else 5

    /** Index of the target glyph inside [row]. */
    val middleIndex: Int get() = arrowCount / 2

    /** Chance that all flankers agree with the target: 0.70 at level 1 down to 0.25 at level 10. */
    val congruentProbability: Double = 0.70 - (this.difficulty - 1) * (0.45 / 9.0)

    /** True when neutral and mixed rows are in play. */
    val hasTrickyFlankers: Boolean = this.difficulty >= 5

    /** True when the row moves to a new slot each trial. */
    val hasJitter: Boolean = this.difficulty >= 8

    /** The glyphs on screen, left to right. The middle one is always an arrow. */
    var row: List<Glyph> = emptyList()
        private set

    /** Direction of the middle arrow (the right answer). */
    var targetLeft: Boolean = false
        private set

    var kind: TrialKind = TrialKind.CONGRUENT
        private set

    /** Row position: 0 is centred, range -[MAX_OFFSET_SLOT]..[MAX_OFFSET_SLOT]. Always 0 below level 8. */
    var offsetSlot: Int = 0
        private set

    /** 0-based number of the current trial (increments after each answer). */
    var trialIndex: Int = 0
        private set

    var correct: Int = 0
        private set

    var mistakes: Int = 0
        private set

    /** Result of the latest answer; IGNORED before the first. */
    var lastResult: AnswerResult = AnswerResult.IGNORED
        private set

    init {
        nextTrial()
    }

    private fun elapsed(): Long = clock.monotonicMs() - startMs

    /** Time-boxed stream: never completes early. */
    override val isComplete: Boolean get() = false

    val isTimeUp: Boolean get() = elapsed() >= CHALLENGE_DURATION_MS

    private fun arrow(left: Boolean): Glyph = if (left) Glyph.LEFT else Glyph.RIGHT

    private fun nextTrial() {
        targetLeft = random.nextBoolean()
        kind = when {
            random.nextDouble() < congruentProbability -> TrialKind.CONGRUENT
            !hasTrickyFlankers -> TrialKind.INCONGRUENT
            else -> {
                val roll = random.nextDouble()
                when {
                    roll < 0.5 -> TrialKind.INCONGRUENT
                    roll < 0.8 -> TrialKind.MIXED
                    else -> TrialKind.NEUTRAL
                }
            }
        }
        val mid = middleIndex
        val flankers: List<Glyph> = when (kind) {
            TrialKind.CONGRUENT -> List(arrowCount - 1) { arrow(targetLeft) }
            TrialKind.INCONGRUENT -> List(arrowCount - 1) { arrow(!targetLeft) }
            TrialKind.NEUTRAL -> List(arrowCount - 1) { Glyph.DASH }
            TrialKind.MIXED -> {
                // Both directions on show, and the target's neighbours always point the wrong way.
                val list = MutableList(arrowCount - 1) { arrow(random.nextBoolean()) }
                list[mid - 1] = arrow(!targetLeft)
                list[mid] = arrow(!targetLeft)
                val outer = (0 until arrowCount - 1).filter { it != mid - 1 && it != mid }
                if (outer.none { list[it] == arrow(targetLeft) }) {
                    list[outer[random.nextInt(outer.size)]] = arrow(targetLeft)
                }
                list
            }
        }
        row = flankers.subList(0, mid) + arrow(targetLeft) + flankers.subList(mid, flankers.size)
        if (hasJitter) {
            // Always move, so the eyes have to find the middle again.
            val span = 2 * MAX_OFFSET_SLOT + 1
            val next = random.nextInt(span) - MAX_OFFSET_SLOT
            offsetSlot = if (next == offsetSlot) {
                (next + MAX_OFFSET_SLOT + 1 + random.nextInt(span - 1)) % span - MAX_OFFSET_SLOT
            } else {
                next
            }
        }
    }

    /** Player says the middle arrow points left ([left] = true) or right. */
    fun answer(left: Boolean): AnswerResult {
        if (isTimeUp) return AnswerResult.IGNORED
        val result = if (left == targetLeft) {
            correct++
            AnswerResult.CORRECT
        } else {
            mistakes++
            AnswerResult.WRONG
        }
        lastResult = result
        trialIndex++
        nextTrial()
        return result
    }

    override fun finish(): ChallengeResult {
        val duration = elapsed().coerceIn(0L, CHALLENGE_DURATION_MS)
        val raw = (correct * POINTS_CORRECT - mistakes * POINTS_MISTAKE) * Scoring.difficultyBonus(difficulty)
        val acc = Scoring.accuracy(correct, mistakes)
        val rate = (correct / GOOD_CORRECT).coerceAtMost(1.0)
        val performance = (rate * acc * acc).coerceIn(0.0, 1.0)
        val detail = when {
            correct + mistakes == 0 -> "No answers"
            mistakes == 0 -> "$correct correct"
            else -> "$correct correct · $mistakes ${if (mistakes == 1) "miss" else "misses"}"
        }
        return ChallengeResult(
            type = type, score = Scoring.clampScore(raw), performance = performance,
            difficulty = difficulty, correct = correct, mistakes = mistakes,
            durationMs = duration, detail = detail,
        )
    }

    companion object {
        const val POINTS_CORRECT = 64.0
        /** Two-way choice: a miss costs a full hit, so blind mashing nets nothing. */
        const val POINTS_MISTAKE = 64.0
        /** Correct answers in 10 s that count as a full-rate run for performance. */
        const val GOOD_CORRECT = 19.0
        /** Largest row shift in either direction. */
        const val MAX_OFFSET_SLOT = 2
    }
}
