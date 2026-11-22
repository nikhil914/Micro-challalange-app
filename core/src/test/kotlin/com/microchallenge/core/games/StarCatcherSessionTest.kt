package com.microchallenge.core.games

import com.microchallenge.core.challenges.games.StarCatcherSession
import com.microchallenge.core.challenges.games.StarCatcherSession.TapResult
import com.microchallenge.core.engine.FakeClock
import com.microchallenge.core.model.CHALLENGE_DURATION_MS
import com.microchallenge.core.model.ChallengeType
import kotlin.random.Random
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.Test

class StarCatcherSessionTest {

    private fun session(d: Int = 1, seed: Int = 1, clock: FakeClock = FakeClock(0)) =
        StarCatcherSession(d, Random(seed), clock)

    /** Plays the run, catching every star [delayMs] after it appears and (optionally) one bomb. */
    private fun play(s: StarCatcherSession, clock: FakeClock, delayMs: Long, tapBombs: Int = 0, skipEvery: Int = 0) {
        var bombs = tapBombs
        var n = 0
        for (sp in s.spawns) {
            val at = sp.startMs + delayMs
            if (at >= sp.endMs) continue
            if (clock.mono < at) clock.mono = at
            if (sp.isBomb) {
                if (bombs > 0 && s.itemAt(sp.cell)?.id == sp.id) { s.tap(sp.cell); bombs-- }
                continue
            }
            n++
            if (skipEvery > 0 && n % skipEvery == 0) continue
            if (s.itemAt(sp.cell)?.id == sp.id) assertEquals(TapResult.CAUGHT, s.tap(sp.cell))
        }
        clock.mono = CHALLENGE_DURATION_MS
    }

    @Test
    fun `schedule is valid at every level`() {
        for (d in 1..10) for (seed in 0 until 40) {
            val s = session(d = d, seed = seed)
            val sp = s.spawns
            assertTrue(sp.size >= 10, "d=$d seed=$seed size=${sp.size}")
            assertTrue(!sp.first().isBomb)
            assertTrue(sp.any { it.isBomb })
            assertTrue(sp.all { it.cell in 0 until StarCatcherSession.CELLS && it.endMs > it.startMs })
            assertTrue(sp.all { it.endMs <= CHALLENGE_DURATION_MS })
            assertEquals(sp.sortedBy { it.startMs }, sp)
            // No two items share a portal at the same time.
            for (cell in 0 until StarCatcherSession.CELLS) {
                val inCell = sp.filter { it.cell == cell }
                inCell.zipWithNext().forEach { (a, b) -> assertTrue(b.startMs >= a.endMs) }
            }
        }
    }

    @Test
    fun `difficulty speeds things up`() {
        val easy = session(d = 1)
        val hard = session(d = 10)
        assertTrue(hard.lifetimeMs < easy.lifetimeMs)
        assertTrue(hard.intervalMs < easy.intervalMs)
        assertTrue(hard.bombChance > easy.bombChance)
        assertTrue(hard.spawns.size > easy.spawns.size)
    }

    @Test
    fun `tapping a star catches it once, empty portal does nothing`() {
        val clock = FakeClock(0)
        val s = session(d = 3, seed = 2, clock = clock)
        val first = s.spawns.first()
        clock.mono = first.startMs + 100
        val empty = (0 until StarCatcherSession.CELLS).first { s.itemAt(it) == null }
        assertEquals(TapResult.EMPTY, s.tap(empty))
        assertEquals(TapResult.CAUGHT, s.tap(first.cell))
        assertNull(s.itemAt(first.cell))
        assertEquals(TapResult.EMPTY, s.tap(first.cell))
        assertEquals(1, s.caught)
        assertEquals(first.id, s.lastHitId)
        assertEquals(TapResult.IGNORED, s.tap(-1))
    }

    @Test
    fun `bombs and missed stars cost points`() {
        fun result(bombs: Int, skip: Int) = FakeClock(0).let { c ->
            val s = session(d = 5, seed = 8, clock = c)
            play(s, c, 250, bombs, skip)
            s.finish()
        }
        val perfect = result(0, 0)
        assertEquals(ChallengeType.STAR_CATCHER, perfect.type)
        assertTrue(perfect.score > result(1, 0).score)
        assertTrue(result(1, 0).score > result(3, 0).score)
        assertTrue(perfect.score > result(0, 3).score)
        assertTrue(result(1, 0).detail.contains("1 bomb"))
    }

    @Test
    fun `catching every star is a top run, most stars is a good run`() {
        for (d in listOf(1, 5, 10)) {
            val c = FakeClock(0)
            val s = session(d = d, seed = 4, clock = c)
            play(s, c, 200)
            val r = s.finish()
            assertEquals(0.85, r.performance, 1e-9)
            assertTrue(r.score >= 850, "d=$d score=${r.score}")
            assertTrue(r.detail.contains("avg 200 ms"), r.detail)
            val c2 = FakeClock(0)
            val g = session(d = d, seed = 4, clock = c2)
            play(g, c2, 200, skipEvery = 5)
            assertTrue(g.finish().performance in 0.6..0.75, "d=$d")
        }
    }

    @Test
    fun `no input gives zero and schedules are deterministic`() {
        for (d in 1..10) {
            val clock = FakeClock(0)
            val s = session(d = d, clock = clock)
            clock.advance(CHALLENGE_DURATION_MS)
            assertEquals(TapResult.IGNORED, s.tap(0))
            val r = s.finish()
            assertEquals(0, r.score)
            assertEquals(0.0, r.performance)
            assertEquals("No catches", r.detail)
            assertTrue(r.mistakes > 0)
        }
        assertEquals(session(d = 6, seed = 3).spawns, session(d = 6, seed = 3).spawns)
        assertTrue(session(d = 6, seed = 3).spawns != session(d = 6, seed = 4).spawns)
    }
}
