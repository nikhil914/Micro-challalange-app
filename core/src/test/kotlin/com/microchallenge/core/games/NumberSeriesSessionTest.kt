package com.microchallenge.core.games

import com.microchallenge.core.challenges.games.NumberSeriesSession
import com.microchallenge.core.challenges.games.SeriesPuzzle
import com.microchallenge.core.challenges.games.SeriesRule
import com.microchallenge.core.engine.FakeClock
import com.microchallenge.core.model.CHALLENGE_DURATION_MS
import kotlin.random.Random
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.Test

class NumberSeriesSessionTest {

    private fun session(d: Int, seed: Long = 1L, clock: FakeClock = FakeClock()) =
        NumberSeriesSession(d, Random(seed), clock)

    /** Checks that terms + answer really follow the declared rule, the way a player would reason. */
    private fun assertFollowsRule(p: SeriesPuzzle) {
        val s = p.terms + p.answer
        val diffs = s.zipWithNext { a, b -> b - a }
        val msg = "${p.rule} $s"
        when (p.rule) {
            SeriesRule.ARITHMETIC -> assertTrue(diffs.distinct().size == 1 && diffs[0] > 0, msg)
            SeriesRule.DECREASING -> assertTrue(diffs.distinct().size == 1 && diffs[0] < 0, msg)
            SeriesRule.GEOMETRIC -> {
                val f = s[1] / s[0]
                assertTrue(f == 2 || f == 3, msg)
                assertTrue(s.zipWithNext().all { (a, b) -> b == a * f }, msg)
            }
            SeriesRule.ALTERNATING -> {
                assertTrue(diffs.indices.all { diffs[it] == diffs[it % 2] }, msg)
                assertTrue(diffs[0] != diffs[1], msg)
            }
            SeriesRule.SQUARES -> {
                // Differences of consecutive squares are consecutive odd numbers.
                assertTrue(diffs.zipWithNext().all { (a, b) -> b - a == 2 } && diffs[0] % 2 == 1, msg)
            }
            SeriesRule.GROWING_STEP -> {
                val second = diffs.zipWithNext { a, b -> b - a }
                assertTrue(second.distinct().size == 1 && second[0] > 0, msg)
            }
            SeriesRule.FIBONACCI -> assertTrue((2 until s.size).all { s[it] == s[it - 1] + s[it - 2] }, msg)
            SeriesRule.INTERLEAVED -> {
                val even = s.filterIndexed { i, _ -> i % 2 == 0 }.zipWithNext { a, b -> b - a }
                val odd = s.filterIndexed { i, _ -> i % 2 == 1 }.zipWithNext { a, b -> b - a }
                assertTrue(even.size >= 3 && even.distinct().size == 1, msg)
                assertTrue(odd.size >= 2 && odd.distinct().size == 1, msg)
            }
            SeriesRule.MULTIPLY_ADD -> {
                val fits = (2..3).any { f -> s.zipWithNext { a, b -> b - a * f }.distinct().size == 1 }
                assertTrue(fits, msg)
            }
        }
    }

    @Test
    fun sameSeedGivesSamePuzzles() {
        for (d in listOf(1, 5, 7, 10)) {
            val a = session(d, seed = 42); val b = session(d, seed = 42)
            repeat(20) {
                assertEquals(a.current, b.current)
                a.answer(0); b.answer(0)
            }
        }
        assertFalse((1..5).all { session(9, it.toLong()).current == session(9, 77).current })
    }

    @Test
    fun answersFollowTheRuleAndChoicesAreDistinct() {
        for (d in 1..10) {
            val s = session(d, seed = d * 31L)
            repeat(300) {
                val p = s.current
                assertFollowsRule(p)
                assertEquals(4, p.choices.size)
                assertEquals(4, p.choices.distinct().size, "duplicate choices ${p.choices}")
                assertTrue(p.choices.all { it >= 0 }, "negative choice ${p.choices}")
                assertTrue(p.terms.all { it > 0 }, "non-positive term ${p.terms}")
                assertTrue(p.terms.size in 4..6, "terms ${p.terms}")
                assertEquals(p.answer, p.choices[p.answerIndex])
                assertTrue(s.answer(p.answerIndex))
            }
        }
    }

    @Test
    fun answerPositionVaries() {
        val s = session(5, seed = 3)
        val seen = mutableSetOf<Int>()
        repeat(100) { seen += s.current.answerIndex; s.answer(0) }
        assertEquals(setOf(0, 1, 2, 3), seen)
    }

    @Test
    fun difficultyScalesRules() {
        fun rules(d: Int): Set<SeriesRule> {
            val s = session(d, seed = 7)
            return (1..200).map { s.current.rule.also { s.answer(0) } }.toSet()
        }
        for (d in 1..3) assertEquals(setOf(SeriesRule.ARITHMETIC), rules(d))
        for (d in 4..5) assertEquals(setOf(SeriesRule.GEOMETRIC, SeriesRule.DECREASING), rules(d))
        for (d in 6..7) assertEquals(
            setOf(SeriesRule.ALTERNATING, SeriesRule.SQUARES, SeriesRule.GROWING_STEP), rules(d),
        )
        for (d in 8..10) assertEquals(
            setOf(SeriesRule.FIBONACCI, SeriesRule.INTERLEAVED, SeriesRule.MULTIPLY_ADD), rules(d),
        )
        // Steps grow inside the arithmetic band too.
        fun maxStep(d: Int): Int {
            val s = session(d, seed = 9)
            return (1..100).maxOf { (s.current.terms[1] - s.current.terms[0]).also { s.answer(0) } }
        }
        assertTrue(maxStep(1) <= 3)
        assertTrue(maxStep(3) > maxStep(1))
    }

    @Test
    fun noInputFinishGivesZero() {
        val clock = FakeClock()
        val s = session(5, clock = clock)
        clock.advance(CHALLENGE_DURATION_MS)
        val r = s.finish()
        assertEquals(0, r.score); assertEquals(0.0, r.performance)
        assertEquals(CHALLENGE_DURATION_MS, r.durationMs)
        assertFalse(s.isComplete)
    }

    @Test
    fun fiveCorrectAtMidDifficultyIsAGoodRun() {
        val clock = FakeClock()
        val s = session(5, clock = clock)
        repeat(5) { clock.advance(1800); assertTrue(s.answer(s.current.answerIndex)) }
        val r = s.finish()
        assertTrue(r.score in 650..800, "score ${r.score}")
        assertEquals(5, r.correct)
        assertTrue(r.performance in 0.6..0.8, "performance ${r.performance}")
    }

    @Test
    fun scoreBoundsAndMistakes() {
        for (d in 1..10) {
            val bad = session(d)
            repeat(30) { bad.answer((bad.current.answerIndex + 1) % 4) }
            assertEquals(30, bad.mistakes)
            assertEquals(0, bad.finish().score)
            val perfect = session(d)
            repeat(100) { perfect.answer(perfect.current.answerIndex) }
            val r = perfect.finish()
            assertEquals(1000, r.score); assertEquals(1.0, r.performance)
        }
    }

    @Test
    fun inputIgnoredAfterTimeUpOrBadIndex() {
        val clock = FakeClock()
        val s = session(3, clock = clock)
        assertFalse(s.answer(-1)); assertFalse(s.answer(4))
        assertEquals(0, s.answeredCount)
        clock.advance(CHALLENGE_DURATION_MS + 5)
        assertFalse(s.answer(s.current.answerIndex))
        assertEquals(0, s.correct)
    }
}
