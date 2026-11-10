package com.microchallenge.core.games

import com.microchallenge.core.challenges.games.ChangeKind
import com.microchallenge.core.challenges.games.SpotChangeSession
import com.microchallenge.core.challenges.games.SpotChangeSession.Phase
import com.microchallenge.core.engine.FakeClock
import com.microchallenge.core.model.CHALLENGE_DURATION_MS
import kotlin.random.Random
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import org.junit.Test

class SpotChangeSessionTest {

    private val toFind = SpotChangeSession.STUDY_MS + SpotChangeSession.MASK_MS

    private fun session(d: Int, seed: Long = 1L, clock: FakeClock = FakeClock()) =
        SpotChangeSession(d, Random(seed), clock)

    /** Waits for the changed grid, then taps. */
    private fun SpotChangeSession.play(clock: FakeClock, index: Int = changedIndex): Boolean {
        clock.advance(toFind)
        return tap(index)
    }

    @Test
    fun deterministicForSeed() {
        val ca = FakeClock(); val cb = FakeClock()
        val a = session(8, 99, ca); val b = session(8, 99, cb)
        repeat(6) {
            assertEquals(a.originalTiles, b.originalTiles); assertEquals(a.changedTiles, b.changedTiles)
            assertEquals(a.changedIndex, b.changedIndex)
            a.play(ca); b.play(cb)
        }
    }

    @Test
    fun exactlyOneTileChanges() {
        for (d in 1..10) for (seed in 1L..40L) {
            val clock = FakeClock()
            val s = session(d, seed, clock)
            repeat(6) {
                val before = s.originalTiles; val after = s.changedTiles
                assertEquals(s.gridSize * s.gridSize, before.size)
                assertEquals(before.size, after.size)
                val changed = before.indices.filter { before[it] != after[it] }
                assertEquals(listOf(s.changedIndex), changed, "d=$d")
                for (t in before + after) {
                    assertTrue(t.colorIndex in 0 until SpotChangeSession.PALETTE_SIZE)
                    assertTrue(t.shape in 0 until SpotChangeSession.SHAPE_COUNT)
                }
                val old = before[s.changedIndex]; val new = after[s.changedIndex]
                when (s.changeKind) {
                    ChangeKind.COLOR_AND_SHAPE -> {
                        assertNotEquals(old.colorIndex, new.colorIndex); assertNotEquals(old.shape, new.shape)
                    }
                    ChangeKind.COLOR -> assertEquals(old.shape, new.shape)
                    ChangeKind.SHAPE, ChangeKind.SUBTLE_SHAPE -> assertEquals(old.colorIndex, new.colorIndex)
                }
                assertTrue(s.play(clock))
            }
        }
    }

    @Test
    fun phasesAndTilesFollowTheClock() {
        val clock = FakeClock()
        val s = session(4, clock = clock)
        assertEquals(Phase.STUDY, s.phase())
        assertEquals(s.originalTiles, s.tiles())
        assertFalse(s.tap(s.changedIndex), "no taps while studying")
        clock.advance(999); assertEquals(Phase.STUDY, s.phase())
        clock.advance(1); assertEquals(Phase.MASK, s.phase())
        assertTrue(s.tiles().isEmpty())
        assertFalse(s.tap(s.changedIndex), "no taps during the mask")
        clock.advance(299); assertEquals(Phase.MASK, s.phase())
        clock.advance(1); assertEquals(Phase.FIND, s.phase())
        assertEquals(s.changedTiles, s.tiles())
        assertEquals(0, s.roundsPlayed)
        assertTrue(s.tap(s.changedIndex))
        assertEquals(Phase.STUDY, s.phase(), "next round starts right away")
        assertEquals(1, s.solved)
    }

    @Test
    fun wrongTapIsMistakeAndMovesOn() {
        val clock = FakeClock()
        val s = session(4, clock = clock)
        val grid = s.originalTiles
        assertFalse(s.play(clock, (s.changedIndex + 1) % grid.size))
        assertEquals(1, s.mistakes); assertEquals(0, s.solved)
        assertEquals(false, s.lastTapCorrect)
        assertEquals(1, s.roundsPlayed)
        assertNotEquals(grid, s.originalTiles)
        assertEquals(Phase.STUDY, s.phase())
    }

    @Test
    fun difficultyScalesGridAndChange() {
        assertEquals(3, session(1).gridSize); assertEquals(4, session(5).gridSize)
        assertEquals(5, session(10).gridSize)
        fun kinds(d: Int): Set<ChangeKind> {
            return (1L..30L).flatMap { seed ->
                val clock = FakeClock(); val s = session(d, seed, clock)
                List(6) { s.changeKind.also { s.play(clock) } }
            }.toSet()
        }
        assertEquals(setOf(ChangeKind.COLOR_AND_SHAPE), kinds(2))
        assertEquals(setOf(ChangeKind.COLOR, ChangeKind.SHAPE), kinds(6))
        assertTrue(ChangeKind.SUBTLE_SHAPE in kinds(9))
        assertFalse(ChangeKind.COLOR_AND_SHAPE in kinds(9))
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
            repeat(5) { good.play(cg) }
            val r = good.finish()
            assertEquals(1000, r.score); assertEquals(1.0, r.performance)
            val cb = FakeClock(); val bad = session(d, clock = cb)
            repeat(5) { bad.play(cb, (bad.changedIndex + 1) % bad.changedTiles.size) }
            assertEquals(0, bad.finish().score)
        }
        val clock = FakeClock(); val mid = session(5, clock = clock)
        repeat(3) { assertTrue(mid.play(clock)); clock.advance(1000) }
        val r = mid.finish()
        assertTrue(r.score in 650..800, "score ${r.score}")
        assertTrue(r.performance in 0.6..0.8, "performance ${r.performance}")
    }

    @Test
    fun tapsIgnoredAfterTimeUpOrBadIndex() {
        val clock = FakeClock()
        val s = session(1, clock = clock)
        clock.advance(toFind)
        assertFalse(s.tap(-1)); assertFalse(s.tap(9))
        assertEquals(0, s.roundsPlayed)
        clock.advance(CHALLENGE_DURATION_MS)
        assertFalse(s.tap(s.changedIndex))
        assertEquals(0, s.solved)
    }
}
