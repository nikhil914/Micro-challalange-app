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
 * One arithmetic problem with multiple-choice answers.
 *
 * @param prompt human-readable expression without "= ?", e.g. "7 + 5" (uses × ÷ − glyphs).
 * @param choices 3..4 distinct non-negative candidate answers.
 * @param answerIndex index of the correct value inside [choices].
 */
data class MathProblem(
    val prompt: String,
    val choices: List<Int>,
    val answerIndex: Int,
) {
    val answer: Int get() = choices[answerIndex]
}

/**
 * Math Sprint: a stream of arithmetic problems; tap the right answer.
 * Every answer (right or wrong) moves on to a fresh problem.
 *
 * Difficulty: 1-3 single-digit + / −, 4-6 two-digit + / − and small ×,
 * 7-10 mixed including ×, exact ÷ and two-step expressions.
 */
class MathSprintSession(
    difficulty: Int,
    private val random: Random,
    private val clock: Clock,
) : ChallengeSession {

    override val type: ChallengeType = ChallengeType.MATH_SPRINT
    override val difficulty: Int = difficulty.coerceIn(MIN_DIFFICULTY, MAX_DIFFICULTY)

    private val startMs: Long = clock.monotonicMs()

    /** Problem currently on screen. */
    var current: MathProblem = generate()
        private set

    var correct: Int = 0
        private set
    var mistakes: Int = 0
        private set

    /** Result of the most recent answer, null before the first one. For UI feedback. */
    var lastAnswerCorrect: Boolean? = null
        private set

    /** Increments on every answer, so the UI can key animations on it. */
    var answeredCount: Int = 0
        private set

    override val isComplete: Boolean get() = false

    private fun elapsedMs(): Long = (clock.monotonicMs() - startMs).coerceIn(0L, CHALLENGE_DURATION_MS)

    /**
     * Submit the choice at [choiceIndex]. Returns true when correct.
     * Ignored (returns false) once the 10 s window is over or for an out-of-range index.
     */
    fun answer(choiceIndex: Int): Boolean {
        if (elapsedMs() >= CHALLENGE_DURATION_MS) return false
        if (choiceIndex !in current.choices.indices) return false
        val ok = choiceIndex == current.answerIndex
        if (ok) correct++ else mistakes++
        lastAnswerCorrect = ok
        answeredCount++
        current = generate()
        return ok
    }

    override fun finish(): ChallengeResult {
        val raw = (correct * POINTS_CORRECT - mistakes * POINTS_MISTAKE) * Scoring.difficultyBonus(difficulty)
        val accuracy = Scoring.accuracy(correct, mistakes)
        val performance = ((correct / TARGET_CORRECT) * accuracy).coerceIn(0.0, 1.0)
        val detail = if (correct + mistakes == 0) "No answers" else
            "$correct solved · ${(accuracy * 100).toInt()}% accurate"
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

    private enum class Op(val glyph: String) { ADD("+"), SUB("−"), MUL("×"), DIV("÷") }

    private fun generate(): MathProblem {
        val d = difficulty
        return when {
            d <= 3 -> {
                val maxA = if (d == 1) 5 else 9
                simple(if (d == 1 || random.nextBoolean()) Op.ADD else Op.SUB, 1..maxA, 1..9)
            }
            d <= 6 -> {
                val roll = random.nextInt(10)
                when {
                    d >= 5 && roll < 3 -> simple(Op.MUL, 2..9, 2..(if (d == 5) 5 else 9))
                    roll < 7 -> simple(Op.ADD, if (d == 4) 10..49 else 10..99, if (d == 4) 1..9 else 10..99)
                    else -> simple(Op.SUB, if (d == 4) 10..49 else 10..99, if (d == 4) 1..9 else 10..99)
                }
            }
            else -> {
                val twoStepShare = (d - 6) * 2 // 2..8 out of 10
                val roll = random.nextInt(10)
                if (roll < twoStepShare / 2 + 1) twoStep() else when (random.nextInt(4)) {
                    0 -> simple(Op.ADD, 10..99, 10..99)
                    1 -> simple(Op.SUB, 20..99, 10..99)
                    2 -> simple(Op.MUL, 2..(if (d >= 9) 15 else 12), 2..12)
                    else -> division()
                }
            }
        }
    }

    private fun IntRange.pick(): Int = random.nextInt(first, last + 1)

    private fun simple(op: Op, aRange: IntRange, bRange: IntRange): MathProblem {
        var a = aRange.pick()
        var b = bRange.pick()
        if (op == Op.SUB && b > a) { val t = a; a = b; b = t }
        val answer = apply(op, a, b)
        val distractors = mutableListOf(answer + 1, answer - 1, answer + 2, answer - 2)
        if (answer >= 10) distractors += listOf(answer + 10, answer - 10)
        when (op) {
            Op.ADD -> { distractors += a - b; distractors += a * b }
            Op.SUB -> { distractors += a + b }
            Op.MUL -> { distractors += a + b; distractors += a * (b + 1); distractors += (a - 1) * b }
            Op.DIV -> {}
        }
        return build("$a ${op.glyph} $b", answer, distractors)
    }

    private fun division(): MathProblem {
        val divisor = (2..12).pick()
        val quotient = (2..12).pick()
        val dividend = divisor * quotient
        val distractors = mutableListOf(quotient + 1, quotient - 1, quotient + 2, divisor, quotient * 2)
        return build("$dividend ${Op.DIV.glyph} $divisor", quotient, distractors)
    }

    /** "a × b + c" or "a + b − c" style. */
    private fun twoStep(): MathProblem {
        return if (random.nextBoolean()) {
            val a = (2..9).pick(); val b = (2..9).pick(); val c = (1..20).pick()
            val plus = random.nextBoolean()
            val answer = if (plus) a * b + c else a * b - c
            if (answer < 0) return twoStep()
            val wrongOrder = if (plus) a * (b + c) else a * (b - c)
            val distractors = mutableListOf(answer + 1, answer - 1, answer + 10, answer - 10, wrongOrder, a + b + c)
            build("$a ${Op.MUL.glyph} $b ${if (plus) Op.ADD.glyph else Op.SUB.glyph} $c", answer, distractors)
        } else {
            val a = (10..60).pick(); val b = (5..40).pick(); val c = (1..(a + b - 1).coerceAtMost(40)).pick()
            val answer = a + b - c
            val distractors = mutableListOf(answer + 1, answer - 1, answer + 10, answer - 10, a + b + c, a - b + c)
            build("$a ${Op.ADD.glyph} $b ${Op.SUB.glyph} $c", answer, distractors)
        }
    }

    private fun apply(op: Op, a: Int, b: Int): Int = when (op) {
        Op.ADD -> a + b
        Op.SUB -> a - b
        Op.MUL -> a * b
        Op.DIV -> a / b
    }

    private fun build(prompt: String, answer: Int, candidates: List<Int>): MathProblem {
        val count = if (difficulty <= 3) 3 else 4
        val pool = candidates.filter { it >= 0 && it != answer }.distinct().shuffled(random).toMutableList()
        val picked = pool.take(count - 1).toMutableList()
        var k = 3
        while (picked.size < count - 1) { // fallback, guarantees distinct values
            val v = answer + k
            if (v != answer && v !in picked) picked += v
            k++
        }
        val answerIndex = random.nextInt(count)
        val choices = picked.toMutableList().apply { add(answerIndex, answer) }
        return MathProblem(prompt, choices, answerIndex)
    }

    companion object {
        const val POINTS_CORRECT = 75.0
        const val POINTS_MISTAKE = 40.0
        /** Correct answers in 10 s that count as a full-performance run. */
        const val TARGET_CORRECT = 11.0
    }
}
