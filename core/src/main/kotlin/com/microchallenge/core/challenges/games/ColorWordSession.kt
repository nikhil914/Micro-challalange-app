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
 * Color Clash (Stroop): a color word is drawn in some ink color; tap the INK.
 *
 * Color indices refer to [COLOR_NAMES], which matches the order of the app's
 * `GameColors` enum. Level 1 uses 4 colors with 50% congruent trials; level 10 uses
 * 6 colors with 10% congruent trials. A new trial appears immediately after each answer.
 */
class ColorWordSession(
    difficulty: Int,
    private val random: Random,
    private val clock: Clock,
) : ChallengeSession {

    /** Outcome of an [answer]. */
    enum class AnswerResult { CORRECT, WRONG, IGNORED }

    override val type: ChallengeType = ChallengeType.COLOR_WORD
    override val difficulty: Int = difficulty.coerceIn(MIN_DIFFICULTY, MAX_DIFFICULTY)

    private val startMs: Long = clock.monotonicMs()

    /** How many colors (and answer buttons) are in play: indices 0 until colorCount. */
    val colorCount: Int = when {
        this.difficulty >= 8 -> 6
        this.difficulty >= 4 -> 5
        else -> 4
    }

    /** Chance a trial's word matches its ink: 0.5 at level 1 down to 0.1 at level 10. */
    val congruentProbability: Double = 0.5 - (this.difficulty - 1) * (0.4 / 9.0)

    /** Index of the color NAME shown as text. */
    var word: Int = 0
        private set

    /** Index of the INK color the word is drawn in (the right answer). */
    var ink: Int = 0
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

    private var lastInk = -1

    init {
        nextTrial()
    }

    val isCongruent: Boolean get() = word == ink

    private fun elapsed(): Long = clock.monotonicMs() - startMs

    /** Time-boxed stream: never completes early. */
    override val isComplete: Boolean get() = false

    val isTimeUp: Boolean get() = elapsed() >= CHALLENGE_DURATION_MS

    private fun nextTrial() {
        // Avoid the same ink twice in a row so a repeated tap is never "free".
        var newInk = random.nextInt(colorCount)
        if (newInk == lastInk) newInk = (newInk + 1 + random.nextInt(colorCount - 1)) % colorCount
        ink = newInk
        lastInk = newInk
        word = if (random.nextDouble() < congruentProbability) {
            newInk
        } else {
            (newInk + 1 + random.nextInt(colorCount - 1)) % colorCount
        }
    }

    /** Player picks [colorIndex] as the ink color. */
    fun answer(colorIndex: Int): AnswerResult {
        if (isTimeUp || colorIndex !in 0 until colorCount) return AnswerResult.IGNORED
        val result = if (colorIndex == ink) {
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
        /** Same order as `com.microchallenge.app.ui.GameColors`. */
        val COLOR_NAMES: List<String> = listOf("Red", "Green", "Blue", "Yellow", "Purple", "Orange")
        const val POINTS_CORRECT = 62.0
        const val POINTS_MISTAKE = 45.0
        /** Correct answers in 10 s that count as a full-rate run for performance. */
        const val GOOD_CORRECT = 17.0
    }
}
