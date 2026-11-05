package com.microchallenge.core

import com.microchallenge.core.engine.ChallengePicker
import com.microchallenge.core.engine.Countdown
import com.microchallenge.core.engine.DifficultyRating
import com.microchallenge.core.engine.FakeClock
import com.microchallenge.core.engine.PausableClock
import com.microchallenge.core.engine.StreakState
import com.microchallenge.core.engine.StreakTracker
import com.microchallenge.core.model.ChallengeType
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class EngineTest {

    @Test fun countdownRunsTenSeconds() {
        val clock = FakeClock()
        val cd = Countdown(clock)
        assertFalse(cd.isFinished())
        cd.start()
        clock.advance(4_000)
        assertEquals(6_000, cd.remainingMs())
        clock.advance(7_000)
        assertTrue(cd.isFinished())
        assertEquals(0, cd.remainingMs())
    }

    @Test fun countdownPauseExcludesBackgroundTime() {
        val clock = FakeClock()
        val cd = Countdown(clock)
        cd.start(); clock.advance(2_000)
        cd.pause(); clock.advance(60_000); cd.resume()
        clock.advance(1_000)
        assertEquals(3_000, cd.elapsedMs())
    }

    @Test fun pausableClockFreezesTime() {
        val base = FakeClock(mono = 1_000)
        val pc = PausableClock(base)
        val t0 = pc.monotonicMs()
        pc.pause(); base.advance(5_000)
        assertEquals(t0, pc.monotonicMs())
        pc.resume(); base.advance(100)
        assertEquals(t0 + 100, pc.monotonicMs())
    }

    @Test fun ratingRisesOnGoodPlayAndFallsOnBad() {
        val up = DifficultyRating.update(5.0, 1.0)
        val down = DifficultyRating.update(5.0, 0.0)
        val flat = DifficultyRating.update(5.0, DifficultyRating.TARGET)
        assertTrue(up > 5.0); assertTrue(down < 5.0); assertEquals(5.0, flat, 1e-9)
        assertEquals(10.0, (1..50).fold(5.0) { r, _ -> DifficultyRating.update(r, 1.0) })
        assertEquals(1.0, (1..50).fold(5.0) { r, _ -> DifficultyRating.update(r, 0.0) })
    }

    @Test fun streakCountsConsecutiveDays() {
        var s = StreakState()
        s = StreakTracker.onPlay(s, 100)
        s = StreakTracker.onPlay(s, 100) // same day: unchanged
        s = StreakTracker.onPlay(s, 101)
        assertEquals(2, s.current)
        s = StreakTracker.onPlay(s, 105)
        assertEquals(1, s.current)
        assertEquals(2, s.best)
    }

    @Test fun streakFreezeCoversOneMissedDay() {
        var s = StreakState()
        for (d in 1L..7L) s = StreakTracker.onPlay(s, d)
        assertEquals(1, s.freezes)
        assertEquals(7, StreakTracker.displayed(s, 9)) // missed day 8, freeze pending
        s = StreakTracker.onPlay(s, 9)
        assertEquals(8, s.current)
        assertEquals(0, s.freezes)
        assertEquals(0, StreakTracker.displayed(s, 12))
    }

    @Test fun pickerAvoidsLastTwoTypes() {
        val recent = listOf(ChallengeType.MATH_SPRINT, ChallengeType.COLOR_WORD)
        repeat(200) { seed ->
            val t = ChallengePicker.pick(recent, emptyMap(), Random(seed))
            assertTrue(t !in recent)
        }
    }

    @Test fun pickerFavoursUnderplayedTypes() {
        val counts = ChallengeType.entries.associateWith { 50 } + (ChallengeType.GO_NO_GO to 0)
        val rnd = Random(1)
        val hits = (1..1000).count { ChallengePicker.pick(emptyList(), counts, rnd) == ChallengeType.GO_NO_GO }
        assertTrue(hits > 500, "expected underplayed type to dominate, got $hits")
    }

    @Test fun dailyTypeIsStablePerDay() {
        assertEquals(ChallengePicker.dailyType(20_000), ChallengePicker.dailyType(20_000))
        val distinct = (20_000L..20_060L).map { ChallengePicker.dailyType(it) }.toSet()
        assertTrue(distinct.size > 3)
    }
}
