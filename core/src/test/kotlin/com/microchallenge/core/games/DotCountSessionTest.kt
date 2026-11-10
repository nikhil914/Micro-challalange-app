package com.microchallenge.core.games

import com.microchallenge.core.challenges.games.DotCountSession
import com.microchallenge.core.challenges.games.DotCountSession.Phase
import com.microchallenge.core.engine.FakeClock
import com.microchallenge.core.model.CHALLENGE_DURATION_MS
import kotlin.math.sqrt
import kotlin.random.Random
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.Test

class DotCountSessionTest {

    private fun session(d: Int, seed: Long = 1L, clock: FakeClock = FakeClock()) =
        DotCountSession(d, Random(seed), clock)

    /** Waits out the flash, then answers. */
    private fun DotCountSession.play(clock: FakeClock, index: Int = answerIndex): Boolean {
        clock.advance(showMs)
        return answer(index)
    }

    @Test
    fun deterministicForSeed() {
        val ca = FakeClock(); val cb = FakeClock()
        val a = session(8, 99, ca); val b = session(8, 99, cb)
        repeat(15) {
            assertEquals(a.dots, b.dots); assertEquals(a.choices, b.choices)
            assertEquals(a.answerIndex, b.answerIndex)
            a.play(ca); b.play(cb)
        }
    }

    @Test
    fun dotsAreInsideSeparateAndCountedRight() {
        for (d in 1..10) for (seed in 1L..40L) {
            val clock = FakeClock()
            val s = session(d, seed, clock)
            repeat(6) {
                val targets = s.dots.count { it.isTarget }
                assertEquals(s.targetCount, targets)
                assertTrue(targets in s.minCount..s.maxCount, "d=$d count $targets")
                val decoys = s.dots.size - targets
                if (d >= 6) assertTrue(decoys >= 2, "d=$d needs decoys") else assertEquals(0, decoys)
                for (dot in s.dots) {
                    assertTrue(dot.radius > 0f)
                    assertTrue(dot.x - dot.radius >= 0f && dot.x + dot.radius <= 1f, "x out of box $dot")
                    assertTrue(dot.y - dot.radius >= 0f && dot.y + dot.radius <= 1f, "y out of box $dot")
                }
                for (i in s.dots.indices) for (j in i + 1 until s.dots.size) {
                    val p = s.dots[i]; val q = s.dots[j]
                    val dist = sqrt((p.x - q.x) * (p.x - q.x) + (p.y - q.y) * (p.y - q.y))
                    assertTrue(dist > p.radius + q.radius, "overlap $p $q")
                }
                assertEquals(4, s.choices.size)
                assertEquals(s.choices, List(4) { s.choices[0] + it }, "choices must be adjacent")
                assertTrue(s.choices[0] >= 1)
                assertEquals(s.targetCount, s.choices[s.answerIndex])
                assertTrue(s.play(clock))
            }
        }
    }

    @Test
    fun phasesFollowTheClock() {
        val clock = FakeClock()
        val s = session(1, clock = clock)
        assertEquals(900L, s.showMs)
        assertEquals(Phase.SHOW, s.phase())
        assertFalse(s.answer(s.answerIndex), "no answers while dots show")
        assertEquals(0, s.answeredCount)
        clock.advance(899); assertEquals(Phase.SHOW, s.phase())
        clock.advance(1); assertEquals(Phase.ANSWER, s.phase())
        assertTrue(s.answer(s.answerIndex))
        assertEquals(Phase.SHOW, s.phase(), "next round starts right away")
        assertEquals(1, s.correct)
        clock.advance(900)
        val expected = s.targetCount
        assertFalse(s.answer((s.answerIndex + 1) % 4))
        assertEquals(1, s.mistakes); assertEquals(false, s.lastAnswerCorrect)
        assertEquals(expected, s.lastTargetCount)
    }

    @Test
    fun difficultyScalesCountFlashAndDecoys() {
        assertEquals(3..6, session(1).minCount..session(1).maxCount)
        assertEquals(9..16, session(10).minCount..session(10).maxCount)
        assertEquals(350L, session(10).showMs)
        for (d in 1..9) {
            assertTrue(session(d + 1).showMs < session(d).showMs)
            assertTrue(session(d + 1).maxCount >= session(d).maxCount)
            assertTrue(session(d + 1).minCount >= session(d).minCount)
        }
        assertFalse(session(5).hasDecoys); assertTrue(session(6).hasDecoys)
    }

    @Test
    fun noInputFinishGivesZero() {
        val clock = FakeClock()
        val s = session(6, clock = clock)
        clock.advance(CHALLENGE_DURATION_MS)
        val r = s.finish()
        assertEquals(0, r.score); assertEquals(0.0, r.performance)
        assertEquals(CHALLENGE_DURATION_MS, r.durationMs)
        assertFalse(s.isComplete)
    }

    @Test
    fun scoreBounds() {
        for (d in 1..10) {
            val cg = FakeClock(); val good = session(d, clock = cg)
            repeat(7) { good.play(cg) }
            val r = good.finish()
            assertEquals(1000, r.score); assertEquals(1.0, r.performance)
            val cb = FakeClock(); val bad = session(d, clock = cb)
            repeat(7) { bad.play(cb, (bad.answerIndex + 1) % 4) }
            assertEquals(0, bad.finish().score)
        }
        val clock = FakeClock(); val mid = session(5, clock = clock)
        repeat(4) { assertTrue(mid.play(clock)); clock.advance(1000) }
        val r = mid.finish()
        assertTrue(r.score in 650..800, "score ${r.score}")
        assertTrue(r.performance in 0.6..0.8, "performance ${r.performance}")
    }

    @Test
    fun inputIgnoredAfterTimeUpOrBadIndex() {
        val clock = FakeClock()
        val s = session(2, clock = clock)
        clock.advance(s.showMs)
        assertFalse(s.answer(-1)); assertFalse(s.answer(4))
        assertEquals(0, s.answeredCount)
        clock.advance(CHALLENGE_DURATION_MS)
        assertFalse(s.answer(s.answerIndex))
        assertEquals(0, s.correct)
    }
}
