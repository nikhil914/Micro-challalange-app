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
 * One left/right pair. Values are exact rationals ([leftNum]/[leftDen]) so ties are impossible
 * and comparison never suffers from floating-point error.
 *
 * @param fontTrick when true the UI should draw the *smaller* value in the bigger font.
 */
data class ComparePair(
    val leftLabel: String,
    val rightLabel: String,
    val leftNum: Long,
    val leftDen: Long,
    val rightNum: Long,
    val rightDen: Long,
    val fontTrick: Boolean,
) {
    /** True when the left value is the larger one. */
    val leftIsLarger: Boolean get() = leftNum * rightDen > rightNum * leftDen

    /** Side the UI should draw in the larger font, or null for equal fonts. */
    val leftEmphasized: Boolean? get() = if (fontTrick) !leftIsLarger else null

    val leftValue: Double get() = leftNum.toDouble() / leftDen
    val rightValue: Double get() = rightNum.toDouble() / rightDen
}

/**
 * Quick Compare: tap the side with the bigger value. A new pair appears right after every answer.
 *
 * Difficulty: 1-2 far-apart integers, 3-4 close integers, 5-6 fractions / decimals,
 * 7-10 arithmetic expressions; from 6 on some pairs use the font-size trick
 * (the smaller value is drawn bigger).
 */
class QuickCompareSession(
    difficulty: Int,
    private val random: Random,
    private val clock: Clock,
) : ChallengeSession {

    override val type: ChallengeType = ChallengeType.QUICK_COMPARE
    override val difficulty: Int = difficulty.coerceIn(MIN_DIFFICULTY, MAX_DIFFICULTY)

    private val startMs: Long = clock.monotonicMs()

    var current: ComparePair = generate()
        private set

    /** Shortcut for the UI: which side gets the bigger font, null = no trick. */
    val leftEmphasized: Boolean? get() = current.leftEmphasized

    var correct: Int = 0
        private set
    var mistakes: Int = 0
        private set
    var lastAnswerCorrect: Boolean? = null
        private set
    var answeredCount: Int = 0
        private set

    override val isComplete: Boolean get() = false

    private fun elapsedMs(): Long = (clock.monotonicMs() - startMs).coerceIn(0L, CHALLENGE_DURATION_MS)

    /** Pick a side. Returns true if it held the larger value. Ignored after 10 s. */
    fun choose(left: Boolean): Boolean {
        if (elapsedMs() >= CHALLENGE_DURATION_MS) return false
        val ok = left == current.leftIsLarger
        if (ok) correct++ else mistakes++
        lastAnswerCorrect = ok
        answeredCount++
        current = generate()
        return ok
    }

    override fun finish(): ChallengeResult {
        val raw = (correct - mistakes) * POINTS * Scoring.difficultyBonus(difficulty)
        val accuracy = Scoring.accuracy(correct, mistakes)
        val performance = ((correct / TARGET_CORRECT) * accuracy * accuracy).coerceIn(0.0, 1.0)
        val detail = if (correct + mistakes == 0) "No answers" else
            "$correct right · ${(accuracy * 100).toInt()}% accurate"
        return ChallengeResult(
            type = type,
            score = Scoring.clampScore(raw),
            performance = performance,
            difficulty = difficulty,
            correct = correct,
            mistakes = mistakes,
            durationMs = elapsedMs(),
            detail = detail,
            timestampMs = clock.wallMs(),
        )
    }

    // ---------------------------------------------------------------- generation

    private class Value(val label: String, val num: Long, val den: Long = 1) {
        fun cmp(o: Value): Long = num * o.den - o.num * den
    }

    private fun IntRange.pick(): Int = random.nextInt(first, last + 1)

    private fun generate(): ComparePair {
        val d = difficulty
        var a: Value
        var b: Value
        do {
            when {
                d <= 2 -> { // far apart: at least 40 % bigger
                    val x = (1..60).pick()
                    val y = (x * 1.4).toInt() + 1 + (0..(if (d == 1) 30 else 15)).pick()
                    a = int(x); b = int(y)
                }
                d <= 4 -> { // close: same magnitude, differ by 1..5 (d3) or 1..2 (d4)
                    val x = if (d == 3) (10..99).pick() else (100..999).pick()
                    val delta = (1..(if (d == 3) 5 else 2)).pick()
                    a = int(x); b = int(x + delta)
                }
                d <= 6 -> {
                    val pair = when (random.nextInt(3)) {
                        0 -> fraction() to fraction()
                        1 -> decimal() to decimal()
                        else -> fraction() to decimal()
                    }
                    a = pair.first; b = pair.second
                }
                else -> {
                    val e = expression()
                    // Mostly expression vs. a nearby integer, sometimes expression vs. expression.
                    b = if (random.nextInt(10) < (d - 6) * 2) expression() else {
                        val v = e.num.toInt()
                        val spread = if (d >= 9) 2 else 4
                        int((v + (-spread..spread).pick()).coerceAtLeast(0))
                    }
                    a = e
                }
            }
        } while (a.cmp(b) == 0L || !closeEnough(a, b))
        if (random.nextBoolean()) { val t = a; a = b; b = t }
        val trickChance = when {
            d < 6 -> 0
            d < 8 -> 25
            d < 10 -> 45
            else -> 60
        }
        val trick = random.nextInt(100) < trickChance
        return ComparePair(a.label, b.label, a.num, a.den, b.num, b.den, trick)
    }

    /** Keeps fraction / decimal rounds from becoming trivially far apart at higher levels. */
    private fun closeEnough(a: Value, b: Value): Boolean {
        if (difficulty !in 5..6) return true
        val diff = abs(a.num.toDouble() / a.den - b.num.toDouble() / b.den)
        val max = if (difficulty == 5) 0.35 else 0.15
        return diff <= max && diff >= 0.01
    }

    private fun int(v: Int) = Value(v.toString(), v.toLong())

    private fun fraction(): Value {
        val den = (2..9).pick()
        val num = (1 until den).pick()
        return Value("$num/$den", num.toLong(), den.toLong())
    }

    private fun decimal(): Value {
        return if (random.nextBoolean()) {
            val tenths = (1..9).pick()
            Value("0.$tenths", tenths.toLong(), 10)
        } else {
            val hundredths = (5..95).pick()
            Value("0.${hundredths.toString().padStart(2, '0')}", hundredths.toLong(), 100)
        }
    }

    private fun expression(): Value {
        val d = difficulty
        return when (random.nextInt(if (d >= 8) 4 else 3)) {
            0 -> { val x = (2..9).pick(); val y = (2..9).pick(); Value("$x×$y", (x * y).toLong()) }
            1 -> { val x = (10..60).pick(); val y = (5..39).pick(); Value("$x+$y", (x + y).toLong()) }
            2 -> { val x = (30..99).pick(); val y = (5..29).pick(); Value("$x−$y", (x - y).toLong()) }
            else -> { val y = (2..9).pick(); val q = (2..12).pick(); Value("${y * q}÷$y", q.toLong()) }
        }
    }

    companion object {
        const val POINTS = 60.0
        /** Correct answers in 10 s that count as a full-performance run. */
        const val TARGET_CORRECT = 12.0
    }
}
