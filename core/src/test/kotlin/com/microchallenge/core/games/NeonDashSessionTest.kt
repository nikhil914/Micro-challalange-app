package com.microchallenge.core.games

import com.microchallenge.core.challenges.games.NeonDashSession
import com.microchallenge.core.challenges.games.NeonDashSession.Kind
import com.microchallenge.core.challenges.games.NeonDashSession.Outcome
import com.microchallenge.core.engine.FakeClock
import com.microchallenge.core.model.CHALLENGE_DURATION_MS
import kotlin.random.Random
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.Test

class NeonDashSessionTest {

    @Test
    fun deterministicForSeed() {
        val a = NeonDashSession(5, Random(7), FakeClock())
        val b = NeonDashSession(5, Random(7), FakeClock())
        assertEquals(a.items, b.items)
    }

    @Test
    fun everyWaveLeavesAnOpenLane() {
        for (seed in 0 until 50) {
            val s = NeonDashSession(10, Random(seed), FakeClock())
            s.items.filter { it.kind == Kind.BARRIER }.groupBy { it.spawnMs }.values.forEach { wave ->
                assertTrue(wave.size < NeonDashSession.LANES)
            }
            assertTrue(s.items.all { it.arriveMs <= CHALLENGE_DURATION_MS })
        }
    }

    @Test
    fun barrierInLaneCrashes() {
        val clock = FakeClock()
        val s = NeonDashSession(1, Random(3), clock)
        val first = s.items.filter { it.kind == Kind.BARRIER }.minBy { it.arriveMs }
        while (s.lane != first.lane) s.move(if (first.lane > s.lane) 1 else -1)
        clock.advance(first.arriveMs)
        s.step()
        assertEquals(Outcome.CRASHED, s.outcomeOf(first))
        assertEquals(NeonDashSession.MAX_LIVES - 1, s.lives)
        assertEquals(first.lane, s.lastCrashLane)
    }

    @Test
    fun perfectPilotDodgesEverything() {
        val clock = FakeClock()
        val s = NeonDashSession(4, Random(11), clock)
        val waves = s.items.filter { it.kind == Kind.BARRIER }.groupBy { it.arriveMs }.toSortedMap()
        for ((arrive, wave) in waves) {
            val blocked = wave.map { it.lane }.toSet()
            val safe = (0 until NeonDashSession.LANES).first { it !in blocked }
            while (s.lane != safe) s.move(if (safe > s.lane) 1 else -1)
            clock.advance(arrive - s.nowMs())
            s.step()
        }
        clock.advance(CHALLENGE_DURATION_MS)
        val r = s.finish()
        assertEquals(0, r.mistakes)
        assertTrue(s.dodged > 0)
        assertTrue(r.score in 1..1000)
    }

    @Test
    fun threeCrashesEndRun() {
        val clock = FakeClock()
        val s = NeonDashSession(10, Random(5), clock)
        var t = 0L
        while (!s.isComplete && t < CHALLENGE_DURATION_MS) {
            val next = s.items.filter { it.kind == Kind.BARRIER && s.outcomeOf(it) == Outcome.PENDING }
                .minByOrNull { it.arriveMs } ?: break
            while (s.lane != next.lane) s.move(if (next.lane > s.lane) 1 else -1)
            clock.advance(next.arriveMs - s.nowMs())
            t = s.nowMs()
            s.step()
        }
        assertTrue(s.isComplete)
        assertEquals(0, s.lives)
        assertFalse(s.move(1))
        assertEquals(3, s.finish().mistakes)
    }

    @Test
    fun movesClampAtEdges() {
        val s = NeonDashSession(1, Random(1), FakeClock())
        assertTrue(s.move(-1))
        assertFalse(s.move(-1))
        assertEquals(0, s.lane)
    }
}
