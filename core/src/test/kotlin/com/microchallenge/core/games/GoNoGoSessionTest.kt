package com.microchallenge.core.games

import com.microchallenge.core.challenges.games.GoNoGoSession
import com.microchallenge.core.challenges.games.GoNoGoStimulus
import com.microchallenge.core.challenges.games.GoNoGoTapOutcome
import com.microchallenge.core.engine.FakeClock
import com.microchallenge.core.model.CHALLENGE_DURATION_MS
import kotlin.random.Random
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.Test

class GoNoGoSessionTest {

    private fun session(d: Int, seed: Long = 1L, clock: FakeClock = FakeClock()) =
        GoNoGoSession(d, Random(seed), clock)

    /** Plays the whole run; taps GO after [rt] ms, taps NO-GO too when [tapNoGo]. */
    private fun play(s: GoNoGoSession, clock: FakeClock, rt: Long, tapNoGo: Boolean = false) {
        for (st in s.schedule) {
            if (st.type == GoNoGoStimulus.GO || tapNoGo) {
                clock.mono = st.onsetMs + rt
                s.tap()
            }
        }
        clock.mono = CHALLENGE_DURATION_MS
    }

    @Test
    fun scheduleDeterministicAndWellFormed() {
        assertEquals(session(5, 77).schedule, session(5, 77).schedule)
        for (d in 1..10) {
            val s = session(d, d * 13L)
            val sch = s.schedule
            assertTrue(sch.size >= 8, "d=$d only ${sch.size}")
            assertEquals(GoNoGoStimulus.GO, sch.first().type)
            val noGoShare = s.noGoCount.toDouble() / sch.size
            assertTrue(noGoShare in 0.15..0.35, "no-go share $noGoShare")
            sch.zipWithNext().forEach { (a, b) ->
                assertTrue(a.hideMs <= b.onsetMs); assertEquals(a.responseEndMs, b.onsetMs)
            }
            assertTrue(sch.last().responseEndMs <= CHALLENGE_DURATION_MS)
        }
    }

    @Test
    fun harderMeansFasterStimuli() {
        assertTrue(session(10).intervalMs < session(1).intervalMs)
        assertTrue(session(10).schedule.size > session(1).schedule.size)
        assertEquals(860L, session(1).intervalMs)
        assertEquals(500L, session(10).intervalMs)
    }

    @Test
    fun currentStimulusFollowsClock() {
        val clock = FakeClock()
        val s = session(4, clock = clock)
        assertNull(s.currentStimulus())
        val first = s.schedule.first()
        clock.mono = first.onsetMs
        assertEquals(GoNoGoStimulus.GO, s.currentStimulus())
        clock.mono = first.hideMs
        assertNull(s.currentStimulus())
    }

    @Test
    fun tapOutcomesAndReactionTime() {
        val clock = FakeClock()
        val s = session(3, clock = clock)
        assertEquals(GoNoGoTapOutcome.STRAY, s.tap()) // before first stimulus
        val go = s.schedule.first()
        clock.mono = go.onsetMs + 320
        assertEquals(GoNoGoTapOutcome.HIT, s.tap())
        assertEquals(GoNoGoTapOutcome.STRAY, s.tap()) // double tap
        assertEquals(320L, s.meanReactionMs)
        val noGo = s.schedule.first { it.type == GoNoGoStimulus.NO_GO }
        clock.mono = noGo.onsetMs + 100
        assertEquals(GoNoGoTapOutcome.FALSE_ALARM, s.tap())
        assertEquals(1, s.hits); assertEquals(1, s.falseAlarms); assertEquals(2, s.strayTaps)
    }

    @Test
    fun omissionsCounted() {
        val clock = FakeClock()
        val s = session(5, clock = clock)
        clock.mono = CHALLENGE_DURATION_MS
        assertEquals(s.goCount, s.omissions)
        assertTrue(s.isComplete)
    }

    @Test
    fun noInputFinishGivesZero() {
        val clock = FakeClock()
        val s = session(5, clock = clock)
        clock.advance(CHALLENGE_DURATION_MS)
        val r = s.finish()
        assertEquals(0, r.score); assertEquals(0.0, r.performance)
        assertEquals(s.goCount, r.mistakes)
    }

    @Test
    fun scoringRewardsSpeedAndPenalisesFalseAlarms() {
        fun run(rt: Long, tapNoGo: Boolean): Int {
            val clock = FakeClock()
            val s = session(5, seed = 9, clock = clock)
            play(s, clock, rt, tapNoGo)
            return s.finish().score.also { assertTrue(it in 0..1000) }
        }
        val fast = run(260, false)
        val slow = run(450, false)
        val spammer = run(260, true)
        assertTrue(fast > slow, "fast $fast slow $slow")
        assertTrue(fast > spammer, "fast $fast spammer $spammer")
        assertTrue(fast >= 900)
    }

    @Test
    fun goodRunPerformanceNearTarget() {
        val clock = FakeClock()
        val s = session(5, clock = clock)
        play(s, clock, 400)
        val p = s.finish().performance
        assertTrue(p in 0.6..0.95, "perf $p")
    }
}
