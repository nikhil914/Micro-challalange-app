package com.microchallenge.core.games

import com.microchallenge.core.challenges.games.RhythmTapSession
import com.microchallenge.core.challenges.games.RhythmTapSession.Grade
import com.microchallenge.core.engine.FakeClock
import com.microchallenge.core.model.CHALLENGE_DURATION_MS
import kotlin.random.Random
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.Test

class RhythmTapSessionTest {

    private fun session(d: Int, seed: Long = 1L, clock: FakeClock = FakeClock()) =
        RhythmTapSession(d, Random(seed), clock)

    /** Taps every beat [offBy] ms from its landing time. */
    private fun playAll(d: Int, offBy: Long, seed: Long = 1L): Pair<RhythmTapSession, FakeClock> {
        val clock = FakeClock()
        val s = session(d, seed, clock)
        var t = 0L
        for (beat in s.beats) {
            clock.advance(beat + offBy - t); t = beat + offBy
            s.tap()
        }
        return s to clock
    }

    @Test
    fun beatsAreDeterministicIncreasingAndFitTheWindow() {
        for (d in 1..10) for (seed in 1L..50L) {
            val b = session(d, seed).beats
            assertEquals(b, session(d, seed).beats)
            assertTrue(b.size >= 8, "d=$d too few beats: ${b.size}")
            assertTrue(b.zipWithNext().all { (a, c) -> c - a >= session(d).beatIntervalMs / 2 })
            assertTrue(b.first() >= 1000 && b.last() <= RhythmTapSession.LAST_BEAT_MS)
        }
        assertTrue((1L..20L).map { session(8, it).beats }.distinct().size > 5)
        assertEquals(session(1, 1).beats, session(1, 2).beats, "level 1 is a plain steady beat")
    }

    @Test
    fun perfectGoodAndStrayTaps() {
        val clock = FakeClock(mono = 3_000)
        val s = session(1, clock = clock)
        val b = s.beats
        clock.advance(b[0] - 20)
        val h1 = s.tap()!!
        assertEquals(Grade.PERFECT, h1.grade); assertEquals(-20L, h1.offsetMs); assertEquals(0, h1.beatIndex)
        clock.advance(b[1] - b[0] + 20 + 120)
        val h2 = s.tap()!!
        assertEquals(Grade.GOOD, h2.grade); assertEquals(120L, h2.offsetMs)
        assertEquals(2, s.currentCombo)
        // Half-way between beats: nothing to hit.
        clock.advance((b[2] - b[1]) / 2 - 120)
        val h3 = s.tap()!!
        assertEquals(Grade.MISS, h3.grade); assertEquals(-1, h3.beatIndex)
        assertEquals(0, s.currentCombo); assertEquals(2, s.maxCombo); assertEquals(1, s.strayTaps)
        assertNull(s.gradeOf(2))
    }

    @Test
    fun unTappedBeatsBecomeMisses() {
        val clock = FakeClock()
        val s = session(3, clock = clock)
        clock.advance(s.beats[0] + s.goodWindowMs)
        assertNull(s.gradeOf(0))
        clock.advance(1)
        assertEquals(Grade.MISS, s.gradeOf(0))
        assertEquals(1, s.count(Grade.MISS))
        assertEquals(Grade.MISS, s.hits.single().grade)
    }

    @Test
    fun perfectRunScores1000AndCompletesEarly() {
        for (d in 1..10) {
            val (s, clock) = playAll(d, 0)
            assertFalse(s.isComplete)
            clock.advance(s.goodWindowMs + RhythmTapSession.END_HOLD_MS)
            assertTrue(s.isComplete)
            val r = s.finish()
            assertEquals(1000, r.score); assertEquals(1.0, r.performance)
            assertEquals(s.beats.size, r.correct); assertEquals(0, r.mistakes)
            assertTrue(r.detail.startsWith("${s.beats.size} perfect"))
        }
    }

    @Test
    fun goodOnlyRunScoresLess() {
        val (s, _) = playAll(1, 100)
        val r = s.finish()
        assertEquals(600, r.score)
        assertEquals(s.beats.size, s.count(Grade.GOOD))
    }

    @Test
    fun windowsTightenWithDifficulty() {
        assertTrue(session(10).goodWindowMs < session(1).goodWindowMs)
        assertTrue(session(10).perfectWindowMs < session(1).perfectWindowMs)
        assertTrue(session(10).beatIntervalMs < session(1).beatIntervalMs)
        // 110 ms off is GOOD at level 1 but out of reach at level 10.
        assertTrue(playAll(1, 110).first.finish().score > 0)
        assertEquals(0, playAll(10, 110).first.finish().score)
    }

    @Test
    fun spammingDoesNotPay() {
        val clock = FakeClock()
        val s = session(5, clock = clock)
        repeat(200) { clock.advance(45); s.tap() }
        assertTrue(s.finish().score < 300, "spam score ${s.finish().score}")
    }

    @Test
    fun noInputFinishGivesZero() {
        val clock = FakeClock()
        val s = session(5, clock = clock)
        clock.advance(CHALLENGE_DURATION_MS)
        val r = s.finish()
        assertEquals(0, r.score); assertEquals(0.0, r.performance)
        assertEquals(CHALLENGE_DURATION_MS, r.durationMs)
        assertEquals("No beats hit", r.detail)
        assertNull(s.tap())
    }
}
