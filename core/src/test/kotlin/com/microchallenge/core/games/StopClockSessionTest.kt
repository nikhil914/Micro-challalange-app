package com.microchallenge.core.games

import com.microchallenge.core.challenges.games.StopClockSession
import com.microchallenge.core.engine.FakeClock
import com.microchallenge.core.model.CHALLENGE_DURATION_MS
import kotlin.random.Random
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.Test

class StopClockSessionTest {

    private fun session(d: Int, seed: Long = 1L, clock: FakeClock = FakeClock()) =
        StopClockSession(d, Random(seed), clock)

    /** Plays every round, stopping [offBy] ms after (or before) each target. */
    private fun playAll(d: Int, offBy: Long, seed: Long = 1L): StopClockSession {
        val clock = FakeClock()
        val s = session(d, seed, clock)
        repeat(StopClockSession.ROUNDS) {
            clock.advance(s.targetMs + offBy)
            assertEquals(offBy, s.stop())
            clock.advance(StopClockSession.GAP_MS)
        }
        return s
    }

    @Test
    fun deterministicTargetsThatFitTheWindow() {
        for (seed in 1L..200L) {
            val t = session(5, seed).targets
            assertEquals(t, session(5, seed).targets)
            assertEquals(StopClockSession.ROUNDS, t.size)
            assertTrue(t.all { it in 1500L..3000L && it % 100L == 0L }, "targets $t")
            val needed = t.sum() + (StopClockSession.ROUNDS - 1) * StopClockSession.GAP_MS
            assertTrue(needed <= CHALLENGE_DURATION_MS - 1500, "no room for 3 rounds: $t")
        }
        assertTrue((1L..20L).map { session(5, it).targets }.distinct().size > 10)
    }

    @Test
    fun stopwatchRunsStopsAndRestartsAfterGap() {
        val clock = FakeClock(mono = 5_000)
        val s = session(1, clock = clock)
        val first = s.targets[0]
        assertEquals(0L, s.runningMs()); assertTrue(s.isRunning())
        assertEquals(first, s.targetMs)
        assertNull(s.lastErrorMs)
        clock.advance(first - 40)
        assertEquals(first - 40, s.runningMs())
        assertEquals(-40L, s.stop())
        assertEquals(-40L, s.lastErrorMs); assertEquals(1, s.roundsDone)
        assertFalse(s.isRunning())
        clock.advance(499)
        assertEquals(first - 40, s.runningMs(), "reading stays frozen during the pause")
        assertEquals(first, s.targetMs)
        assertNull(s.stop(), "stop is ignored during the pause")
        assertEquals(1, s.roundsDone)
        clock.advance(1)
        assertTrue(s.isRunning()); assertEquals(0L, s.runningMs())
        assertEquals(s.targets[1], s.targetMs)
        clock.advance(s.targets[1] + 70)
        assertEquals(70L, s.stop())
        assertEquals(listOf(-40L, 70L), s.errorsMs)
    }

    @Test
    fun completesShortlyAfterThirdStop() {
        val clock = FakeClock()
        val s = session(4, clock = clock)
        repeat(3) {
            assertFalse(s.isComplete)
            clock.advance(s.targetMs); s.stop()
            if (it < 2) clock.advance(StopClockSession.GAP_MS)
        }
        assertEquals(3, s.roundsDone)
        assertFalse(s.isComplete, "last result stays up for a moment")
        clock.advance(StopClockSession.GAP_MS)
        assertTrue(s.isComplete)
        assertFalse(s.isRunning())
        assertNull(s.stop())
        assertEquals(3, s.roundsDone)
    }

    @Test
    fun displayHidesEarlierAtHigherDifficulty() {
        for (d in 1..2) {
            val clock = FakeClock(); val s = session(d, clock = clock)
            assertNull(s.hideAfterMs())
            clock.advance(s.targetMs + 500)
            assertTrue(s.displayVisible()); assertEquals(1f, s.displayAlpha())
        }
        var previous = Long.MAX_VALUE
        for (d in 3..10) {
            val clock = FakeClock(); val s = session(d, seed = 4, clock = clock)
            val hideAt = s.hideAfterMs()!!
            assertTrue(hideAt <= previous, "d=$d hides at $hideAt, after level below ($previous)")
            assertTrue(hideAt >= 500 && hideAt < s.targetMs)
            previous = hideAt
            clock.advance(hideAt - 1)
            assertTrue(s.displayVisible()); assertEquals(1f, s.displayAlpha())
            clock.advance(1)
            assertFalse(s.displayVisible())
            clock.advance(StopClockSession.FADE_MS / 2)
            assertTrue(s.displayAlpha() > 0f && s.displayAlpha() < 1f)
            clock.advance(StopClockSession.FADE_MS)
            assertEquals(0f, s.displayAlpha())
            s.stop()
            assertTrue(s.displayVisible(), "result is always shown")
            assertEquals(1f, s.displayAlpha())
        }
        assertEquals(500L, session(10).hideAfterMs())
    }

    @Test
    fun scoreFromMeanAbsoluteError() {
        for (d in 1..10) {
            assertEquals(1000, playAll(d, 30).finish().score)
            assertEquals(1000, playAll(d, -20).finish().score)
            assertEquals(0, playAll(d, 600).finish().score)
            assertEquals(0, playAll(d, -700).finish().score)
            val r = playAll(d, 150).finish()
            assertTrue(r.score in 1..999); assertTrue(r.performance in 0.0..1.0)
        }
        val r = playAll(1, 84).finish()
        assertEquals("Avg off by 84 ms", r.detail)
        assertEquals(3, r.correct); assertEquals(0, r.mistakes)
        assertTrue(r.score in 880..930, "score ${r.score}")
        // Early and late errors must not cancel out.
        val clock = FakeClock(); val s = session(1, clock = clock)
        clock.advance(s.targetMs - 200); s.stop(); clock.advance(500)
        clock.advance(s.targetMs + 200); s.stop()
        assertEquals(200.0, s.meanAbsErrorMs())
    }

    @Test
    fun toleranceTightensWithDifficulty() {
        val scores = (1..10).map { playAll(it, 200).finish().score }
        for (i in 0 until 9) assertTrue(scores[i + 1] < scores[i], "scores $scores")
        assertTrue(session(10).zeroErrorMs < session(1).zeroErrorMs)
    }

    @Test
    fun skippedRoundsCostPoints() {
        val clock = FakeClock()
        val s = session(3, clock = clock)
        clock.advance(s.targetMs); assertEquals(0L, s.stop())
        clock.advance(CHALLENGE_DURATION_MS)
        val r = s.finish()
        assertEquals(333, r.score)
        assertTrue(r.performance < 0.34)
        assertFalse(s.isComplete)
    }

    @Test
    fun noInputFinishGivesZero() {
        val clock = FakeClock()
        val s = session(5, clock = clock)
        clock.advance(CHALLENGE_DURATION_MS)
        val r = s.finish()
        assertEquals(0, r.score); assertEquals(0.0, r.performance)
        assertEquals(CHALLENGE_DURATION_MS, r.durationMs)
        assertEquals("No stops", r.detail)
        assertFalse(s.isComplete)
    }

    @Test
    fun stopIgnoredAfterTimeUp() {
        val clock = FakeClock()
        val s = session(2, clock = clock)
        clock.advance(CHALLENGE_DURATION_MS)
        assertNull(s.stop())
        assertEquals(0, s.roundsDone)
        assertTrue(s.runningMs() <= CHALLENGE_DURATION_MS)
    }
}
