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

/**
 * Number Memory: a row of digits is shown for a short time, then hidden; the
 * player types it back. Digit count grows from 3 (level 1) to 10 (level 10) and the
 * time per digit shrinks.
 *
 * Input can be built with [typeDigit]/[backspace] and sent with [submit], or sent
 * in one go with [submit] (answer). If time runs out, whatever was typed is scored.
 */
class NumberMemorySession(
    difficulty: Int,
    random: Random,
    private val clock: Clock,
) : ChallengeSession {

    enum class Phase { SHOW, INPUT, DONE }

    override val type: ChallengeType = ChallengeType.NUMBER_MEMORY
    override val difficulty: Int = difficulty.coerceIn(MIN_DIFFICULTY, MAX_DIFFICULTY)

    private val startMs: Long = clock.monotonicMs()

    /** Number of digits to remember. */
    val length: Int = digitCount(this.difficulty)

    /** The digits to memorise, as a string of '0'..'9'. */
    val digits: String = buildString {
        var prev = -1
        var run = 0
        while (length < this@NumberMemorySession.length) {
            var d = random.nextInt(10)
            // Avoid three identical digits in a row (too easy to chunk).
            if (d == prev && run >= 2) d = (d + 1 + random.nextInt(9)) % 10
            run = if (d == prev) run + 1 else 1
            prev = d
            append(('0' + d))
        }
    }

    /** How long the digits stay visible, ms. 2600 at level 1, 4000 at level 10. */
    val showMs: Long = 2000L + length * 200L

    private var inputStartedEarlyAt: Long = -1L
    private var submitted: Boolean = false

    /** Digits typed so far (the player's answer). */
    var input: String = ""
        private set

    /** Number of digits in the right position, valid once [phase] is DONE. */
    var correctDigits: Int = 0
        private set

    private fun elapsed(): Long = clock.monotonicMs() - startMs

    private fun showEndsAt(): Long = if (inputStartedEarlyAt >= 0) inputStartedEarlyAt else showMs

    val phase: Phase
        get() {
            val t = elapsed()
            return when {
                submitted || t >= CHALLENGE_DURATION_MS -> Phase.DONE
                t < showEndsAt() -> Phase.SHOW
                else -> Phase.INPUT
            }
        }

    override val isComplete: Boolean get() = submitted

    /** Ms the digits remain visible, 0 once hidden. */
    fun showRemainingMs(): Long =
        if (phase != Phase.SHOW) 0L else (showEndsAt() - elapsed()).coerceAtLeast(0L)

    /** Player says "I've got it": hide the digits now and start input. */
    fun startInput() {
        if (phase == Phase.SHOW) inputStartedEarlyAt = elapsed()
    }

    /** Append one digit 0..9. Ignored outside the input phase or when full. */
    fun typeDigit(digit: Int): Boolean {
        if (phase != Phase.INPUT || digit !in 0..9 || input.length >= length) return false
        input += ('0' + digit)
        return true
    }

    fun backspace(): Boolean {
        if (phase != Phase.INPUT || input.isEmpty()) return false
        input = input.dropLast(1)
        return true
    }

    /** Submit what was typed with [typeDigit]. Returns digits correct, or -1 if ignored. */
    fun submit(): Int = submit(input)

    /**
     * Submit an answer; non-digits are ignored, extra digits beyond [length] are dropped.
     * Allowed only in the input phase. Returns digits correct in position, or -1 if ignored.
     */
    fun submit(answer: String): Int {
        if (phase != Phase.INPUT) return -1
        input = answer.filter { it in '0'..'9' }.take(length)
        evaluate()
        submitted = true
        return correctDigits
    }

    private fun evaluate() {
        correctDigits = input.indices.count { input[it] == digits[it] }
    }

    override fun finish(): ChallengeResult {
        if (!submitted) evaluate() // time ran out: score whatever was typed
        submitted = true
        val duration = elapsed().coerceIn(0L, CHALLENGE_DURATION_MS)
        val frac = correctDigits.toDouble() / length
        val perfect = correctDigits == length
        val raw = (650.0 * frac + if (perfect) 150.0 else 0.0) * Scoring.difficultyBonus(difficulty)
        val performance = (0.85 * frac + if (perfect) 0.15 else 0.0).coerceIn(0.0, 1.0)
        val detail = when {
            perfect -> "Perfect: $length digits"
            input.isEmpty() -> "No answer"
            else -> "$correctDigits/$length digits"
        }
        return ChallengeResult(
            type = type,
            score = if (correctDigits == 0) 0 else Scoring.clampScore(raw),
            performance = if (correctDigits == 0) 0.0 else performance,
            difficulty = difficulty,
            correct = correctDigits,
            mistakes = if (input.isEmpty()) 0 else length - correctDigits,
            durationMs = duration,
            detail = detail,
        )
    }

    companion object {
        /** 3 digits at level 1 up to 10 at level 10. */
        fun digitCount(difficulty: Int): Int =
            (3 + ((difficulty.coerceIn(MIN_DIFFICULTY, MAX_DIFFICULTY) - 1) * 7 / 9.0).roundToInt())
                .coerceAtMost(10)
    }
}
