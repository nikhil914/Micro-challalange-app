package com.microchallenge.core.games

import com.microchallenge.core.challenges.games.TwinScenesSession
import com.microchallenge.core.challenges.games.TwinScenesSession.DiffKind
import com.microchallenge.core.challenges.games.TwinScenesSession.Phase
import com.microchallenge.core.challenges.games.TwinScenesSession.TapResult
import com.microchallenge.core.engine.FakeClock
import com.microchallenge.core.model.CHALLENGE_DURATION_MS
import kotlin.random.Random
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.Test

class TwinScenesSessionTest {

    @Test
    fun deterministicForSeed() {
        val a = TwinScenesSession(5, Random(7), FakeClock())
        val b = TwinScenesSession(5, Random(7), FakeClock())
        assertEquals(a.round, b.round)
    }

    @Test
    fun scenesDifferInExactlyOneSlot() {
        for (seed in 0 until 200) {
            val s = TwinScenesSession(1 + seed % 10, Random(seed), FakeClock())
            val r = s.round
            assertEquals(r.top.size, r.bottom.size)
            val diffs = r.top.indices.filter { r.top[it] != r.bottom[it] }
            assertEquals(listOf(r.diffIndex), diffs, "seed $seed")
            if (r.kind == DiffKind.MISSING) assertNull(r.bottom[r.diffIndex])
        }
    }

    @Test
    fun easyLevelsOnlyUseObviousDifferences() {
        for (seed in 0 until 100) {
            val kind = TwinScenesSession(1, Random(seed), FakeClock()).round.kind
            assertTrue(kind == DiffKind.MISSING || kind == DiffKind.ICON)
        }
    }

    @Test
    fun findingDifferenceStartsNewRound() {
        val clock = FakeClock()
        val s = TwinScenesSession(3, Random(2), clock)
        val first = s.round
        assertEquals(TapResult.FOUND, s.tap(first.diffIndex))
        assertEquals(Phase.FOUND, s.phase)
        assertEquals(TapResult.IGNORED, s.tap(first.diffIndex))
        clock.advance(TwinScenesSession.FOUND_MS)
        assertEquals(Phase.PLAY, s.phase)
        assertNotEquals(first, s.round)
        assertEquals(1, s.found)
    }

    @Test
    fun wrongTapCountsOnce() {
        val s = TwinScenesSession(3, Random(5), FakeClock())
        val wrong = s.round.top.indices.first { it != s.round.diffIndex }
        assertEquals(TapResult.WRONG, s.tap(wrong))
        assertEquals(TapResult.IGNORED, s.tap(wrong))
        assertEquals(1, s.finish().mistakes)
    }

    @Test
    fun noInputScoresZero() {
        val clock = FakeClock()
        val s = TwinScenesSession(5, Random(1), clock)
        clock.advance(CHALLENGE_DURATION_MS)
        assertEquals(Phase.DONE, s.phase)
        val r = s.finish()
        assertEquals(0, r.score)
        assertEquals(0.0, r.performance)
    }

    @Test
    fun scoreInRange() {
        val clock = FakeClock()
        val s = TwinScenesSession(10, Random(9), clock)
        repeat(8) {
            s.tap(s.round.diffIndex)
            clock.advance(TwinScenesSession.FOUND_MS + 300)
        }
        val r = s.finish()
        assertTrue(r.score in 1..1000)
        assertTrue(r.performance in 0.0..1.0)
    }
}
