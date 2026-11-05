package com.microchallenge.core.games

import com.microchallenge.core.challenges.games.ColorWordSession
import com.microchallenge.core.challenges.games.ColorWordSession.AnswerResult
import com.microchallenge.core.engine.FakeClock
import com.microchallenge.core.model.CHALLENGE_DURATION_MS
import com.microchallenge.core.model.ChallengeType
import kotlin.random.Random
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.Test

class ColorWordSessionTest {

    private fun session(d: Int = 1, seed: Int = 1, clock: FakeClock = FakeClock(0)) =
        ColorWordSession(d, Random(seed), clock)

    @Test
    fun `color names match game palette order`() {
        assertEquals(listOf("Red", "Green", "Blue", "Yellow", "Purple", "Orange"), ColorWordSession.COLOR_NAMES)
    }

    @Test
    fun `color count and congruence scale with difficulty`() {
        assertEquals(4, session(d = 1).colorCount)
        assertEquals(5, session(d = 5).colorCount)
        assertEquals(6, session(d = 10).colorCount)
        assertEquals(0.5, session(d = 1).congruentProbability, 1e-9)
        assertEquals(0.1, session(d = 10).congruentProbability, 1e-9)
    }

    private fun congruentRate(d: Int): Double {
        val s = session(d = d, seed = 11)
        var cong = 0
        val n = 4000
        repeat(n) {
            if (s.isCongruent) cong++
            assertTrue(s.ink in 0 until s.colorCount && s.word in 0 until s.colorCount)
            s.answer(s.ink)
        }
        return cong.toDouble() / n
    }

    @Test
    fun `observed congruent rate follows probability`() {
        assertEquals(0.5, congruentRate(1), 0.05)
        assertEquals(0.1, congruentRate(10), 0.03)
    }

    @Test
    fun `deterministic trials for same seed`() {
        fun trace(): List<Pair<Int, Int>> {
            val s = session(d = 7, seed = 99)
            return List(30) { (s.word to s.ink).also { s.answer(s.ink) } }
        }
        assertEquals(trace(), trace())
    }

    @Test
    fun `ink never repeats twice in a row`() {
        val s = session(d = 3, seed = 5)
        var prev = -1
        repeat(500) { assertTrue(s.ink != prev); prev = s.ink; s.answer(0) }
    }

    @Test
    fun `answers count and advance trials`() {
        val s = session()
        val wrong = (s.ink + 1) % s.colorCount
        assertEquals(AnswerResult.WRONG, s.answer(wrong))
        assertEquals(AnswerResult.CORRECT, s.answer(s.ink))
        assertEquals(AnswerResult.IGNORED, s.answer(7))
        assertEquals(1, s.correct)
        assertEquals(1, s.mistakes)
        assertEquals(2, s.trialIndex)
        assertFalse(s.isComplete)
    }

    @Test
    fun `good player around 14 correct scores well and near target`() {
        val clock = FakeClock(0)
        val s = session(d = 2, clock = clock)
        repeat(14) { clock.advance(650); s.answer(s.ink) }
        s.answer((s.ink + 1) % s.colorCount)
        val r = s.finish()
        assertEquals(14, r.correct)
        assertTrue(r.score in 750..1000, "score ${r.score}")
        assertTrue(r.performance in 0.6..0.85, "perf ${r.performance}")
        assertEquals("14 correct · 1 miss", r.detail)
        assertEquals(ChallengeType.COLOR_WORD, r.type)
    }

    @Test
    fun `random spamming scores zero`() {
        val s = session(d = 1, seed = 3)
        val rnd = Random(123)
        repeat(40) { s.answer(rnd.nextInt(s.colorCount)) }
        assertEquals(0, s.finish().score)
    }

    @Test
    fun `difficulty bonus rewards higher levels`() {
        fun score(d: Int): Int {
            val s = session(d = d)
            repeat(10) { s.answer(s.ink) }
            return s.finish().score
        }
        assertTrue(score(10) > score(1))
    }

    @Test
    fun `answers after time up are ignored and no input gives zero`() {
        val clock = FakeClock(0)
        val s = session(clock = clock)
        clock.advance(CHALLENGE_DURATION_MS)
        assertEquals(AnswerResult.IGNORED, s.answer(s.ink))
        val r = s.finish()
        assertEquals(0, r.score)
        assertEquals(0.0, r.performance)
        assertEquals(CHALLENGE_DURATION_MS, r.durationMs)
    }

    @Test
    fun `scores clamp to bounds`() {
        val s = session(d = 10)
        repeat(60) { s.answer(s.ink) }
        val r = s.finish()
        assertEquals(1000, r.score)
        assertEquals(1.0, r.performance)
    }
}
