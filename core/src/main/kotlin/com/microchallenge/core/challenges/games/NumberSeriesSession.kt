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

/** Pattern family behind a [SeriesPuzzle]. */
enum class SeriesRule {
    /** Constant positive step. */
    ARITHMETIC,
    /** Constant factor (×2 or ×3). */
    GEOMETRIC,
    /** Constant negative step. */
    DECREASING,
    /** Two steps taking turns (+a, +b, +a, ...). */
    ALTERNATING,
    /** Consecutive squares plus a constant offset. */
    SQUARES,
    /** Step grows by a constant each time (second differences are constant). */
    GROWING_STEP,
    /** Each term is the sum of the previous two. */
    FIBONACCI,
    /** Two arithmetic series taking turns. */
    INTERLEAVED,
    /** Each term is the previous one times a factor plus a constant (e.g. ×2 + 1). */
    MULTIPLY_ADD,
}

/**
 * One "what comes next" puzzle.
 *
 * @param terms the visible terms, in order.
 * @param choices 4 distinct non-negative candidates for the next term.
 * @param answerIndex index of the correct value inside [choices].
 */
data class SeriesPuzzle(
    val terms: List<Int>,
    val choices: List<Int>,
    val answerIndex: Int,
    val rule: SeriesRule,
) {
    val answer: Int get() = choices[answerIndex]
}

/**
 * Next in Line: a stream of number series; tap the term that comes next.
 * Every answer (right or wrong) moves on to a fresh puzzle.
 *
 * Difficulty: 1-3 arithmetic, 4-5 geometric and decreasing,
 * 6-7 alternating steps / squares / growing steps,
 * 8-10 Fibonacci-like, interleaved series and "×2 + 1" style rules.
 */
class NumberSeriesSession(
    difficulty: Int,
    private val random: Random,
    private val clock: Clock,
) : ChallengeSession {

    override val type: ChallengeType = ChallengeType.NUMBER_SERIES
    override val difficulty: Int = difficulty.coerceIn(MIN_DIFFICULTY, MAX_DIFFICULTY)

    private val startMs: Long = clock.monotonicMs()

    /** Puzzle currently on screen. */
    var current: SeriesPuzzle = generate()
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
            "$correct cracked · ${(accuracy * 100).toInt()}% accurate"
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

    private fun IntRange.pick(): Int = random.nextInt(first, last + 1)

    private fun generate(): SeriesPuzzle {
        val d = difficulty
        return when {
            d <= 3 -> arithmetic()
            d <= 5 -> if (random.nextBoolean()) geometric() else decreasing()
            d <= 7 -> when (random.nextInt(3)) {
                0 -> alternating()
                1 -> squares()
                else -> growingStep()
            }
            else -> when (random.nextInt(3)) {
                0 -> fibonacci()
                1 -> interleaved()
                else -> multiplyAdd()
            }
        }
    }

    private fun arithmetic(): SeriesPuzzle {
        val step = when (difficulty) { 1 -> (1..3).pick(); 2 -> (2..5).pick(); else -> (3..9).pick() }
        val start = if (difficulty == 1) (1..9).pick() else (1..20).pick()
        val seq = List(5) { start + it * step }
        return build(SeriesRule.ARITHMETIC, seq, listOf(seq[4] + step, seq[4] - step))
    }

    private fun geometric(): SeriesPuzzle {
        val factor = if (difficulty == 4 || random.nextBoolean()) 2 else 3
        val start = if (factor == 2) (1..5).pick() else (1..3).pick()
        val seq = mutableListOf(start)
        repeat(4) { seq += seq.last() * factor }
        val last = seq[3]
        return build(
            SeriesRule.GEOMETRIC, seq,
            listOf(last * (factor + 1), last * factor + start, last * factor - start, last + last / 2),
        )
    }

    private fun decreasing(): SeriesPuzzle {
        val step = if (difficulty == 4) (2..5).pick() else (3..9).pick()
        val start = step * 5 + (1..15).pick()
        val seq = List(if (difficulty == 4) 5 else 6) { start - it * step }
        val answer = seq.last()
        return build(SeriesRule.DECREASING, seq, listOf(answer + step, answer + 2 * step, answer - step))
    }

    /** +a, +b, +a, +b, then +a again. */
    private fun alternating(): SeriesPuzzle {
        val a = (1..6).pick()
        var b = (2..9).pick()
        if (b == a) b++
        val seq = mutableListOf((1..12).pick())
        repeat(5) { i -> seq += seq.last() + if (i % 2 == 0) a else b }
        val last = seq[4]
        return build(SeriesRule.ALTERNATING, seq, listOf(last + b, last + a + b, last + b - a))
    }

    private fun squares(): SeriesPuzzle {
        val offset = if (difficulty == 6) 0 else (0..3).pick()
        val n0 = (1..5).pick()
        val seq = List(6) { (n0 + it) * (n0 + it) + offset }
        val last = seq[4]
        val lastStep = last - seq[3]
        return build(SeriesRule.SQUARES, seq, listOf(last + lastStep, last + lastStep + 1, last + lastStep + 4))
    }

    private fun growingStep(): SeriesPuzzle {
        val inc = if (difficulty == 6) (1..2).pick() else (1..3).pick()
        val first = (1..4).pick()
        val seq = mutableListOf((1..10).pick())
        repeat(5) { i -> seq += seq.last() + first + i * inc }
        val last = seq[4]
        val lastStep = last - seq[3]
        return build(
            SeriesRule.GROWING_STEP, seq,
            listOf(last + lastStep, last + lastStep + inc + 1, last + lastStep + 2 * inc),
        )
    }

    private fun fibonacci(): SeriesPuzzle {
        val seq = mutableListOf((1..5).pick(), (1..6).pick())
        repeat(4) { seq += seq[seq.size - 1] + seq[seq.size - 2] }
        val last = seq[4]
        return build(SeriesRule.FIBONACCI, seq, listOf(last + (last - seq[3]), last * 2, last + seq[2]))
    }

    /** a1 b1 a2 b2 a3 b3 -> a4: six visible terms so both series show three terms. */
    private fun interleaved(): SeriesPuzzle {
        val stepA = (1..5).pick()
        var stepB = (2..9).pick()
        if (stepB == stepA) stepB++
        val startA = (1..9).pick()
        val startB = (10..30).pick()
        val downB = difficulty >= 9 && random.nextBoolean()
        val seq = List(7) { i ->
            val k = i / 2
            when {
                i % 2 == 0 -> startA + k * stepA
                downB -> startB + (2 - k) * stepB
                else -> startB + k * stepB
            }
        }
        val lastB = seq[5]
        return build(
            SeriesRule.INTERLEAVED, seq,
            listOf(lastB + stepB, lastB + stepA, seq[4] + stepB, seq[4] + 2 * stepA, (lastB - stepB).coerceAtLeast(0)),
        )
    }

    private fun multiplyAdd(): SeriesPuzzle {
        val factor = if (difficulty >= 10 && random.nextInt(3) == 0) 3 else 2
        val add = if (difficulty == 8) 1 else listOf(1, -1, 2, 3)[random.nextInt(4)]
        val seq = mutableListOf(if (factor == 3) (1..3).pick() else (2..5).pick())
        repeat(4) { seq += seq.last() * factor + add }
        val last = seq[3]
        return build(
            SeriesRule.MULTIPLY_ADD, seq,
            listOf(last * factor, last * factor - add, last + (last - seq[2]), last * factor + add + factor),
        )
    }

    /**
     * [seq] holds the visible terms followed by the answer. [ruleDistractors] are the
     * tempting wrong continuations for this rule; near misses fill up the rest.
     */
    private fun build(rule: SeriesRule, seq: List<Int>, ruleDistractors: List<Int>): SeriesPuzzle {
        val terms = seq.dropLast(1)
        val answer = seq.last()
        val valid = { v: Int -> v >= 0 && v != answer && v != terms.last() }
        // Rule-based traps first (at most two), then near misses, so every puzzle has both kinds.
        val traps = ruleDistractors.filter(valid).distinct().shuffled(random).take(2)
        val near = listOf(answer + 1, answer - 1, answer + 2, answer - 2, answer + 10)
            .filter(valid).filter { it !in traps }.shuffled(random)
        val picked = (traps + near).take(CHOICES - 1).toMutableList()
        var k = 3
        while (picked.size < CHOICES - 1) { // fallback, guarantees distinct values
            val v = answer + k
            if (v !in picked) picked += v
            k++
        }
        picked.shuffle(random)
        val answerIndex = random.nextInt(CHOICES)
        val choices = picked.apply { add(answerIndex, answer) }
        return SeriesPuzzle(terms, choices, answerIndex, rule)
    }

    companion object {
        const val CHOICES = 4
        const val POINTS_CORRECT = 120.0
        const val POINTS_MISTAKE = 60.0
        /** Correct answers in 10 s that count as a full-performance run. */
        const val TARGET_CORRECT = 7.0
    }
}
