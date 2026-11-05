package com.microchallenge.core.games

import com.microchallenge.core.challenges.games.OddDifference
import com.microchallenge.core.challenges.games.OddOneOutSession
import com.microchallenge.core.engine.FakeClock
import com.microchallenge.core.model.CHALLENGE_DURATION_MS
import kotlin.random.Random
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import org.junit.Test

class OddOneOutSessionTest {

    private fun session(d: Int, seed: Long = 1L, clock: FakeClock = FakeClock()) =
        OddOneOutSession(d, Random(seed), clock)

    @Test
    fun deterministicForSeed() {
        val a = session(7, 99); val b = session(7, 99)
        repeat(15) {
            assertEquals(a.tiles, b.tiles); assertEquals(a.oddIndex, b.oddIndex)
            a.tap(a.oddIndex); b.tap(b.oddIndex)
        }
    }

    @Test
    fun exactlyOneTileDiffers() {
        for (d in 1..10) {
            val s = session(d, d.toLong())
            repeat(100) {
                assertEquals(s.gridSize * s.gridSize, s.tiles.size)
                val odd = s.tiles[s.oddIndex]
                val others = s.tiles.filterIndexed { i, _ -> i != s.oddIndex }
                assertEquals(1, others.distinct().size, "others must be identical")
                assertNotEquals(others.first(), odd)
                assertTrue(odd.colorIndex in 0 until OddOneOutSession.PALETTE_SIZE)
                assertTrue(odd.shape in 0 until OddOneOutSession.SHAPE_COUNT)
                if (s.difference == OddDifference.COLOR) {
                    assertNotEquals(others.first().scale, odd.scale, "color rounds need a non-color cue")
                }
                s.tap(s.oddIndex)
            }
        }
    }

    @Test
    fun difficultyScalesGridAndDifference() {
        assertEquals(3, session(1).gridSize)
        assertEquals(6, session(10).gridSize)
        assertEquals(OddDifference.SHAPE, session(1).difference)
        assertEquals(OddDifference.ROTATION, session(5).difference)
        val hard = session(10, 3)
        val kinds = mutableSetOf<OddDifference>()
        repeat(50) { kinds += hard.difference; hard.tap(hard.oddIndex) }
        assertFalse(OddDifference.SHAPE in kinds)
    }

    @Test
    fun wrongTapIsMistakeAndKeepsRound() {
        val s = session(4)
        val tiles = s.tiles
        val wrong = (s.oddIndex + 1) % tiles.size
        assertFalse(s.tap(wrong))
        assertEquals(1, s.mistakes); assertEquals(0, s.solved)
        assertEquals(tiles, s.tiles)
        assertTrue(s.tap(s.oddIndex))
        assertEquals(1, s.solved)
    }

    @Test
    fun noInputFinishGivesZero() {
        val clock = FakeClock()
        val s = session(6, clock = clock)
        clock.advance(CHALLENGE_DURATION_MS)
        val r = s.finish()
        assertEquals(0, r.score); assertEquals(0.0, r.performance)
    }

    @Test
    fun scoreBounds() {
        val s = session(10)
        repeat(40) { s.tap(s.oddIndex) }
        assertEquals(1000, s.finish().score)
        val bad = session(2)
        repeat(20) { bad.tap((bad.oddIndex + 1) % bad.tiles.size) }
        assertEquals(0, bad.finish().score)
        val mid = session(5)
        repeat(6) { mid.tap(mid.oddIndex) }
        val r = mid.finish()
        assertTrue(r.score in 700..850, "score ${r.score}")
        assertTrue(r.performance in 0.6..0.8)
    }

    @Test
    fun tapsIgnoredAfterTimeUp() {
        val clock = FakeClock()
        val s = session(1, clock = clock)
        clock.advance(CHALLENGE_DURATION_MS)
        assertFalse(s.tap(s.oddIndex))
        assertEquals(0, s.solved)
    }
}
