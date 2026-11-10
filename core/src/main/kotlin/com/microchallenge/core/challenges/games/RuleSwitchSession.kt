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
 * Rule Switch (task switching): every trial shows a number 1..9 (never 5) and a rule cue.
 * "ODD or EVEN?" judges parity, "LOW or HIGH?" judges below/above 5. The two answer
 * buttons are relabelled to fit the rule; the first button is always ODD / LOW / CYAN.
 *
 * The chance that the rule changes between trials climbs from 20% at level 1 to 60% at
 * level 10. From level 6 a third rule asks for the number's tint ("CYAN or GOLD?"); each
 * tint has its own badge shape so it never depends on color vision. From level 8 the cue
 * is only shown for [cueMs] at the start of each trial.
 */
class RuleSwitchSession(
    difficulty: Int,
    private val random: Random,
    private val clock: Clock,
) : ChallengeSession {

    /** The question asked about the number. [first] / [second] label the two answer buttons. */
    enum class Rule(val prompt: String, val first: String, val second: String) {
        PARITY("ODD or EVEN?", "ODD", "EVEN"),
        MAGNITUDE("LOW or HIGH?", "LOW", "HIGH"),
        TINT("CYAN or GOLD?", "CYAN", "GOLD"),
    }

    /** Tint of the number. [shape] names the badge drawn around it, as a non-color cue. */
    enum class Tint(val label: String, val shape: String) {
        NONE("Plain", "none"),
        CYAN("Cyan", "circle"),
        GOLD("Gold", "square"),
    }

    /** Outcome of an [answer]. */
    enum class AnswerResult { CORRECT, WRONG, IGNORED }

    override val type: ChallengeType = ChallengeType.RULE_SWITCH
    override val difficulty: Int = difficulty.coerceIn(MIN_DIFFICULTY, MAX_DIFFICULTY)

    private val startMs: Long = clock.monotonicMs()

    /** Rules in play: two, or three from level 6. */
    val rules: List<Rule> =
        if (this.difficulty >= 6) listOf(Rule.PARITY, Rule.MAGNITUDE, Rule.TINT) else listOf(Rule.PARITY, Rule.MAGNITUDE)

    /** Chance the rule changes on the next trial: 0.20 at level 1 up to 0.60 at level 10. */
    val switchProbability: Double = 0.20 + (this.difficulty - 1) * (0.40 / 9.0)

    /** How long the cue stays up each trial, ms; [Long.MAX_VALUE] when it never hides. */
    val cueMs: Long = if (this.difficulty >= 8) 900L - (this.difficulty - 8) * 150L else Long.MAX_VALUE

    /** Rule for the current trial. */
    var rule: Rule = Rule.PARITY
        private set

    /** The number on screen: 1..9, never 5. */
    var stimulus: Int = 1
        private set

    /** Tint of the number; [Tint.NONE] while the tint rule is not in play. */
    var tint: Tint = Tint.NONE
        private set

    /** True when this trial's rule differs from the previous trial's. */
    var isSwitch: Boolean = false
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

    private var trialStartMs = 0L // session-relative

    init {
        rule = rules[random.nextInt(2)]
        deal()
    }

    private fun elapsed(): Long = clock.monotonicMs() - startMs

    /** Time-boxed stream: never completes early. */
    override val isComplete: Boolean get() = false

    val isTimeUp: Boolean get() = elapsed() >= CHALLENGE_DURATION_MS

    /** Label of the first (left) answer button under the current rule. */
    val firstLabel: String get() = rule.first

    /** Label of the second (right) answer button under the current rule. */
    val secondLabel: String get() = rule.second

    /** False once the brief cue of levels 8+ has timed out for this trial. */
    val cueVisible: Boolean get() = elapsed() - trialStartMs < cueMs

    /** True when the first button is the right answer for the current trial. */
    val firstIsCorrect: Boolean
        get() = when (rule) {
            Rule.PARITY -> stimulus % 2 == 1
            Rule.MAGNITUDE -> stimulus < 5
            Rule.TINT -> tint == Tint.CYAN
        }

    private fun deal() {
        // Never the same number twice in a row, so every trial visibly changes.
        var n = NUMBERS[random.nextInt(NUMBERS.size)]
        if (n == stimulus && trialIndex > 0) {
            n = NUMBERS[(NUMBERS.indexOf(n) + 1 + random.nextInt(NUMBERS.size - 1)) % NUMBERS.size]
        }
        stimulus = n
        tint = if (Rule.TINT in rules) {
            if (random.nextBoolean()) Tint.CYAN else Tint.GOLD
        } else {
            Tint.NONE
        }
        trialStartMs = elapsed()
    }

    private fun nextTrial() {
        isSwitch = random.nextDouble() < switchProbability
        if (isSwitch) {
            val others = rules.filter { it != rule }
            rule = others[random.nextInt(others.size)]
        }
        deal()
    }

    /** Player presses the first ([first] = true: ODD / LOW / CYAN) or second button. */
    fun answer(first: Boolean): AnswerResult {
        if (isTimeUp) return AnswerResult.IGNORED
        val result = if (first == firstIsCorrect) {
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
        /** Every digit except 5, which is neither low nor high. */
        val NUMBERS: List<Int> = listOf(1, 2, 3, 4, 6, 7, 8, 9)
        const val POINTS_CORRECT = 75.0
        /** Two-way choice: a miss costs a full hit, so blind mashing nets nothing. */
        const val POINTS_MISTAKE = 75.0
        /** Correct answers in 10 s that count as a full-rate run for performance. */
        const val GOOD_CORRECT = 15.0
    }
}
