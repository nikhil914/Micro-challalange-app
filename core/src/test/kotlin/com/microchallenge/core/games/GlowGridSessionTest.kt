package com.microchallenge.core.games

import com.microchallenge.core.challenges.games.GlowGridSession
import com.microchallenge.core.challenges.games.GlowGridSession.Phase
import com.microchallenge.core.challenges.games.GlowGridSession.TapResult
import com.microchallenge.core.engine.FakeClock
import com.microchallenge.core.model.CHALLENGE_DURATION_MS
import kotlin.random.Random
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.Test

class GlowGridSessionTest {

    private fun GlowGridSession.toInput(clock: FakeClock) {
        clock.advance(GlowGridSession.LEAD_IN_MS + round.studyMs)
    }

    @Test
    fun deterministicForSeed() {
        val a = GlowGridSession(5, Random(7), FakeClock())
        val b = GlowGridSession(5, Random(7), FakeClock())
        assertEquals(a.round, b.round)
    }

    @Test
    fun showsThenAcceptsInput() {
        val clock = FakeClock()
        val s = GlowGridSession(1, Random(1), clock)
        assertEquals(Phase.SHOW, s.phase)
        assertEquals(TapResult.IGNORED, s.tap(s.round.pattern.first()))
        s.toInput(clock)
        assertEquals(Phase.INPUT, s.phase)
    }

    @Test
    fun clearingRoundGrowsPattern() {
        val clock = FakeClock()
        val s = GlowGridSession(1, Random(3), clock)
        val first = s.round.pattern.size
        s.toInput(clock)
        val results = s.round.pattern.map { s.tap(it) }
        assertEquals(TapResult.ROUND_CLEARED, results.last())
        assertEquals(Phase.FEEDBACK, s.phase)
        clock.advance(GlowGridSession.FEEDBACK_MS)
        assertEquals(Phase.SHOW, s.phase)
        assertEquals(first + 1, s.round.pattern.size)
    }

    @Test
    fun twoMissesFailRound() {
        val clock = FakeClock()
        val s = GlowGridSession(1, Random(4), clock)
        s.toInput(clock)
        val blanks = (0 until s.round.gridSize * s.round.gridSize).filter { it !in s.round.pattern }
        assertEquals(TapResult.MISS, s.tap(blanks[0]))
        assertEquals(TapResult.ROUND_FAILED, s.tap(blanks[1]))
        assertEquals(2, s.finish().mistakes)
    }

    @Test
    fun scoreInRangeAndDoneAfterTimeout() {
        val clock = FakeClock()
        val s = GlowGridSession(10, Random(9), clock)
        s.toInput(clock)
        s.round.pattern.forEach { s.tap(it) }
        clock.advance(CHALLENGE_DURATION_MS)
        assertEquals(Phase.DONE, s.phase)
        val r = s.finish()
        assertTrue(r.score in 1..1000)
    }
}
