package com.microchallenge.core.games

import com.microchallenge.core.challenges.games.NumberMemorySession
import com.microchallenge.core.challenges.games.NumberMemorySession.Phase
import com.microchallenge.core.engine.FakeClock
import com.microchallenge.core.model.CHALLENGE_DURATION_MS
import com.microchallenge.core.model.ChallengeType
import kotlin.random.Random
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import org.junit.Test

class NumberMemorySessionTest {

    private fun session(d: Int = 1, seed: Int = 1, clock: FakeClock = FakeClock(500)) =
        NumberMemorySession(d, Random(seed), clock)

    private fun toInput(s: NumberMemorySession, clock: FakeClock) {
        clock.advance(s.showMs)
        assertEquals(Phase.INPUT, s.phase)
    }

    @Test
    fun `digit count scales with difficulty from 3 to 10`() {
        assertEquals(3, session(d = 1).digits.length)
        assertEquals(10, session(d = 10).digits.length)
        val counts = (1..10).map { session(d = it).length }
        assertEquals(counts.sorted(), counts)
        assertTrue(session(d = 10).showMs > session(d = 1).showMs)
        // less time per digit at higher levels
        assertTrue(session(d = 10).showMs / 10 < session(d = 1).showMs / 3)
    }

    @Test
    fun `deterministic digits for same seed`() {
        assertEquals(session(d = 6, seed = 9).digits, session(d = 6, seed = 9).digits)
        assertNotEquals(session(d = 10, seed = 1).digits, session(d = 10, seed = 2).digits)
    }

    @Test
    fun `digits are numeric and never three identical in a row`() {
        for (seed in 0 until 200) {
            val d = session(d = 10, seed = seed).digits
            assertTrue(d.all { it in '0'..'9' })
            assertFalse(Regex("(.)\\1\\1").containsMatchIn(d), d)
        }
    }

    @Test
    fun `show phase then input phase with countdown`() {
        val clock = FakeClock(500)
        val s = session(clock = clock)
        assertEquals(Phase.SHOW, s.phase)
        assertEquals(s.showMs, s.showRemainingMs())
        clock.advance(1_000)
        assertEquals(s.showMs - 1_000, s.showRemainingMs())
        assertFalse(s.typeDigit(1), "typing during show is ignored")
        assertEquals(-1, s.submit("123"))
        clock.advance(s.showMs)
        assertEquals(Phase.INPUT, s.phase)
        assertEquals(0L, s.showRemainingMs())
    }

    @Test
    fun `start input early hides digits`() {
        val clock = FakeClock(500)
        val s = session(clock = clock)
        clock.advance(300)
        s.startInput()
        assertEquals(Phase.INPUT, s.phase)
    }

    @Test
    fun `perfect answer completes with top score`() {
        val clock = FakeClock(500)
        val s = session(d = 5, clock = clock)
        toInput(s, clock)
        assertEquals(s.length, s.submit(s.digits))
        assertTrue(s.isComplete)
        assertEquals(Phase.DONE, s.phase)
        val r = s.finish()
        assertEquals(960, r.score) // 800 base x 1.2 level-5 bonus
        assertEquals(1.0, r.performance)
        assertEquals(0, r.mistakes)
        assertTrue(r.detail.startsWith("Perfect"))
        assertEquals(ChallengeType.NUMBER_MEMORY, r.type)
    }

    @Test
    fun `partial answer scored by digits in position`() {
        val clock = FakeClock(500)
        val s = session(d = 10, seed = 3, clock = clock)
        toInput(s, clock)
        val wrongLast = s.digits.dropLast(2) + s.digits.takeLast(2).map { '0' + ((it - '0' + 1) % 10) }.joinToString("")
        assertEquals(8, s.submit(wrongLast))
        val r = s.finish()
        assertEquals("8/10 digits", r.detail)
        assertEquals(2, r.mistakes)
        assertTrue(r.score in 400..999)
        assertTrue(r.performance in 0.6..0.75)
    }

    @Test
    fun `perfect bonus makes perfect clearly better than one wrong`() {
        val ca = FakeClock(0)
        val a = NumberMemorySession(1, Random(4), ca)
        ca.advance(a.showMs)
        a.submit(a.digits)
        val cb = FakeClock(0)
        val b = NumberMemorySession(1, Random(4), cb)
        cb.advance(b.showMs)
        b.submit(b.digits.dropLast(1) + ((b.digits.last() - '0' + 1) % 10))
        assertTrue(a.finish().score - b.finish().score > 300)
    }

    @Test
    fun `keypad typing and backspace and non digit filtering`() {
        val clock = FakeClock(500)
        val s = session(d = 1, clock = clock)
        toInput(s, clock)
        assertTrue(s.typeDigit(4)); assertTrue(s.typeDigit(5))
        assertTrue(s.backspace())
        assertEquals("4", s.input)
        assertFalse(s.typeDigit(12))
        repeat(5) { s.typeDigit(1) }
        assertEquals(s.length, s.input.length, "input capped at length")
        val clock2 = FakeClock(0)
        val t = NumberMemorySession(1, Random(1), clock2)
        clock2.advance(t.showMs)
        t.submit(" ${t.digits[0]}-${t.digits.drop(1)}99 ")
        assertEquals(t.digits, t.input)
    }

    @Test
    fun `no input finish gives zero`() {
        val clock = FakeClock(500)
        val s = session(d = 7, clock = clock)
        clock.advance(CHALLENGE_DURATION_MS)
        assertEquals(Phase.DONE, s.phase)
        val r = s.finish()
        assertEquals(0, r.score)
        assertEquals(0.0, r.performance)
        assertEquals(0, r.mistakes)
        assertEquals("No answer", r.detail)
        assertEquals(CHALLENGE_DURATION_MS, r.durationMs)
    }

    @Test
    fun `typed but unsubmitted input is scored at timeout`() {
        val clock = FakeClock(500)
        val s = session(d = 1, clock = clock)
        toInput(s, clock)
        s.typeDigit(s.digits[0] - '0')
        clock.advance(CHALLENGE_DURATION_MS)
        val r = s.finish()
        assertEquals(1, r.correct)
        assertTrue(r.score > 0)
    }

    @Test
    fun `scores stay in bounds for all levels`() {
        for (d in 1..10) {
            val clock = FakeClock(0)
            val s = NumberMemorySession(d, Random(d), clock)
            clock.advance(s.showMs)
            s.submit(s.digits)
            val r = s.finish()
            assertTrue(r.score in 0..1000 && r.performance in 0.0..1.0)
        }
    }
}
