package com.microchallenge.core.games

import com.microchallenge.core.challenges.ChallengeRegistry
import com.microchallenge.core.challenges.games.StackTowerSession
import com.microchallenge.core.engine.FakeClock
import com.microchallenge.core.model.CHALLENGE_DURATION_MS
import com.microchallenge.core.model.ChallengeType
import kotlin.math.abs
import kotlin.random.Random
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.Test

class StackTowerSessionTest {

    private fun session(d: Int, seed: Long = 1L, clock: FakeClock = FakeClock()) =
        StackTowerSession(d, Random(seed), clock)

    /** Advances in 1 ms steps until the moving centre is within [eps] of [target]. */
    private fun waitFor(s: StackTowerSession, clock: FakeClock, target: Double, eps: Double = 0.002) {
        repeat(5_000) {
            if (abs(s.movingCenter() - target) <= eps) return
            clock.advance(1)
        }
        error("centre never reached $target")
    }

    @Test
    fun startsWithBaseOnly() {
        val s = session(1)
        assertEquals(0, s.height)
        assertEquals(1, s.tower.size)
        assertEquals(0.5, s.tower[0].center, 1e-9)
        assertEquals(s.baseWidth, s.movingWidth, 1e-9)
        assertFalse(s.isComplete)
        assertTrue(s.canDrop())
    }

    @Test
    fun movingBlockSweepsEdgeToEdge() {
        val clock = FakeClock()
        val s = session(3, clock = clock)
        val start = s.movingCenter()
        assertTrue(start == 0.0 || start == 1.0)
        clock.advance(s.sweepMs())
        assertEquals(1.0 - start, s.movingCenter(), 1e-6)
        clock.advance(s.sweepMs())
        assertEquals(start, s.movingCenter(), 1e-6)
        clock.advance(s.sweepMs() / 2)
        assertEquals(0.5, s.movingCenter(), 0.01)
    }

    @Test
    fun deterministicForSeed() {
        for (seed in 1L..20L) assertEquals(session(4, seed).movingCenter(), session(4, seed).movingCenter())
        assertTrue((1L..20L).map { session(4, it).movingCenter() }.distinct().size == 2)
    }

    @Test
    fun perfectDropSnapsAndKeepsWidth() {
        val clock = FakeClock()
        val s = session(5, clock = clock)
        waitFor(s, clock, 0.5 + s.perfectTolerance / 2)
        val d = assertNotNull(s.drop())
        assertTrue(d.perfect); assertFalse(d.missed); assertNull(d.cut)
        assertEquals(1, s.height); assertEquals(1, s.perfects); assertEquals(1, s.combo)
        assertEquals(s.baseWidth, s.tower.last().width, 1e-9)
        assertEquals(0.5, s.tower.last().center, 1e-9)
    }

    @Test
    fun overhangIsSlicedOff() {
        val clock = FakeClock()
        val s = session(1, clock = clock)
        val base = s.tower[0]
        waitFor(s, clock, 0.6)
        val c = s.movingCenter()
        val d = assertNotNull(s.drop())
        assertFalse(d.perfect); assertFalse(d.missed)
        val placed = s.tower.last()
        assertEquals(c - s.baseWidth / 2, placed.left, 1e-9)
        assertEquals(base.right, placed.right, 1e-9)
        val cut = assertNotNull(d.cut)
        assertEquals(base.right, cut.left, 1e-9)
        assertEquals(c + s.baseWidth / 2, cut.right, 1e-9)
        assertEquals(s.baseWidth, placed.width + cut.width, 1e-9)
        assertEquals(placed.width, s.movingWidth, 1e-9)
        assertEquals(0, s.combo)
    }

    @Test
    fun missEndsRun() {
        val clock = FakeClock()
        val s = session(10, clock = clock)
        // Narrow the tower with off-centre drops until a far drop misses.
        while (!s.isComplete) {
            waitFor(s, clock, 0.97, eps = 0.03)
            s.drop()
            assertTrue(s.height < 40)
        }
        assertTrue(s.missed)
        assertTrue(assertNotNull(s.lastDrop).missed)
        assertNull(s.drop())
        assertFalse(s.canDrop())
        val r = s.finish()
        assertEquals(1, r.mistakes)
        assertTrue(r.score in 0..1000)
    }

    @Test
    fun comboGrowsBlockBackCappedAtBase() {
        val clock = FakeClock()
        val s = session(2, clock = clock)
        waitFor(s, clock, 0.56)
        s.drop()
        val narrowed = s.movingWidth
        assertTrue(narrowed < s.baseWidth)
        repeat(4) {
            waitFor(s, clock, s.tower.last().center, eps = 0.001)
            assertTrue(assertNotNull(s.drop()).perfect)
        }
        assertEquals(4, s.combo); assertEquals(4, s.bestCombo)
        // Combo 3 and 4 each grow by GROW_STEP.
        assertEquals(narrowed + 2 * StackTowerSession.GROW_STEP, s.movingWidth, 1e-9)
        repeat(4) {
            waitFor(s, clock, s.tower.last().center, eps = 0.001)
            s.drop()
        }
        assertTrue(s.movingWidth <= s.baseWidth + 1e-9)
    }

    @Test
    fun speedsUpWithHeightAndDifficulty() {
        val clock = FakeClock()
        val s = session(1, clock = clock)
        val first = s.sweepMs()
        waitFor(s, clock, 0.5, eps = 0.001); s.drop()
        assertTrue(s.sweepMs() < first)
        assertTrue(session(10).baseSweepMs < session(1).baseSweepMs)
        assertTrue(session(10).baseWidth < session(1).baseWidth)
        assertTrue(session(10).perfectTolerance < session(1).perfectTolerance)
        assertTrue(session(10).perfectTolerance > 0.0)
    }

    @Test
    fun goodRunScoresHighSloppyRunLower() {
        fun play(offset: Double): Int {
            val clock = FakeClock()
            val s = session(5, clock = clock)
            while (s.canDrop() && clock.mono < CHALLENGE_DURATION_MS - 1500) {
                waitFor(s, clock, s.tower.last().center + offset, eps = 0.002)
                s.drop()
            }
            clock.mono = CHALLENGE_DURATION_MS
            return s.finish().score
        }
        val perfect = play(0.0)
        val sloppy = play(0.04)
        assertEquals(1000, perfect)
        assertTrue(sloppy < perfect, "sloppy $sloppy")
    }

    @Test
    fun noInputGivesZero() {
        val clock = FakeClock()
        val s = session(5, clock = clock)
        clock.advance(CHALLENGE_DURATION_MS)
        assertNull(s.drop())
        val r = s.finish()
        assertEquals(0, r.score); assertEquals(0.0, r.performance)
        assertEquals(CHALLENGE_DURATION_MS, r.durationMs)
        assertEquals("No floors", r.detail)
        assertFalse(s.isComplete)
    }

    @Test
    fun registryBuildsIt() {
        val s = ChallengeRegistry.create(ChallengeType.STACK_TOWER, 3, Random(1), FakeClock())
        assertTrue(s is StackTowerSession)
    }
}
