package com.microchallenge.core.games

import com.microchallenge.core.challenges.games.MathSprintSession
import com.microchallenge.core.engine.FakeClock
import com.microchallenge.core.model.CHALLENGE_DURATION_MS
import kotlin.random.Random
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.Test

class MathSprintSessionTest {

    private fun session(d: Int, seed: Long = 1L, clock: FakeClock = FakeClock()) =
        MathSprintSession(d, Random(seed), clock)

    /** Evaluates the prompt the way a player would: × ÷ before + −, left to right. */
    private fun eval(prompt: String): Int {
        val tokens = prompt.split(" ")
        val nums = mutableListOf(tokens[0].toInt())
        val ops = mutableListOf<String>()
        var i = 1
        while (i < tokens.size) {
            val op = tokens[i]; val n = tokens[i + 1].toInt()
            when (op) {
                "×" -> nums[nums.lastIndex] = nums.last() * n
                "÷" -> { assertEquals(0, nums.last() % n, "inexact division in $prompt"); nums[nums.lastIndex] = nums.last() / n }
                else -> { ops += op; nums += n }
            }
            i += 2
        }
        var acc = nums[0]
        ops.forEachIndexed { k, op -> acc = if (op == "+") acc + nums[k + 1] else acc - nums[k + 1] }
        return acc
    }

    @Test
    fun sameSeedGivesSameProblems() {
        val a = session(6, seed = 42); val b = session(6, seed = 42)
        repeat(20) {
            assertEquals(a.current, b.current)
            a.answer(0); b.answer(0)
        }
    }

    @Test
    fun answersAreCorrectAndChoicesDistinct() {
        for (d in 1..10) {
            val s = session(d, seed = d * 31L)
            repeat(200) {
                val p = s.current
                assertEquals(eval(p.prompt), p.answer, "d=$d ${p.prompt}")
                assertEquals(p.choices.size, p.choices.distinct().size, "duplicate choices ${p.choices}")
                assertEquals(if (d <= 3) 3 else 4, p.choices.size)
                assertTrue(p.choices.all { it >= 0 }, "negative choice ${p.choices}")
                s.answer(p.answerIndex)
            }
        }
    }

    @Test
    fun difficultyScalesOperatorsAndOperands() {
        fun prompts(d: Int): List<String> {
            val s = session(d, seed = 7)
            return List(300) { s.current.prompt.also { s.answer(0) } }
        }
        val easy = prompts(1)
        assertTrue(easy.all { p -> p.split(" ").filterIndexed { i, _ -> i % 2 == 0 }.all { it.length == 1 } })
        assertTrue(easy.none { "×" in it || "÷" in it })
        assertTrue(prompts(5).any { "×" in it })
        val hard = prompts(10)
        assertTrue(hard.any { "÷" in it })
        assertTrue(hard.any { it.split(" ").size == 5 }, "expected two-step problems")
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
    fun eightCorrectAtMidDifficultyIsAbout700() {
        val clock = FakeClock()
        val s = session(5, clock = clock)
        repeat(8) { clock.advance(1000); assertTrue(s.answer(s.current.answerIndex)) }
        val r = s.finish()
        assertTrue(r.score in 650..750, "score ${r.score}")
        assertEquals(8, r.correct)
        assertTrue(r.performance in 0.6..0.8)
    }

    @Test
    fun mistakesCountAndLowerScoreWithinBounds() {
        val clock = FakeClock()
        val s = session(10, clock = clock)
        repeat(30) { s.answer((s.current.answerIndex + 1) % s.current.choices.size) }
        assertEquals(30, s.mistakes)
        assertEquals(0, s.finish().score)
        val perfect = session(10)
        repeat(100) { perfect.answer(perfect.current.answerIndex) }
        val r = perfect.finish()
        assertEquals(1000, r.score); assertEquals(1.0, r.performance)
    }

    @Test
    fun inputIgnoredAfterTimeUp() {
        val clock = FakeClock()
        val s = session(3, clock = clock)
        clock.advance(CHALLENGE_DURATION_MS + 5)
        assertFalse(s.answer(s.current.answerIndex))
        assertEquals(0, s.correct)
    }
}
