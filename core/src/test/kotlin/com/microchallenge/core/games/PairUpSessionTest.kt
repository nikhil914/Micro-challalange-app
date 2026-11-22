package com.microchallenge.core.games

import com.microchallenge.core.challenges.games.PairUpSession
import com.microchallenge.core.challenges.games.PairUpSession.FlipResult
import com.microchallenge.core.challenges.games.PairUpSession.Phase
import com.microchallenge.core.engine.FakeClock
import com.microchallenge.core.model.CHALLENGE_DURATION_MS
import com.microchallenge.core.model.ChallengeType
import kotlin.random.Random
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.Test

class PairUpSessionTest {

    private fun session(d: Int = 1, seed: Int = 1, clock: FakeClock = FakeClock(0)) =
        PairUpSession(d, Random(seed), clock)

    /** Skip the preview of the current board. */
    private fun skipPreview(s: PairUpSession, clock: FakeClock) {
        while (s.phase() == Phase.PREVIEW) clock.advance(50)
    }

    /** Index pairs on the current board, grouped by icon. */
    private fun pairsOf(s: PairUpSession): List<List<Int>> =
        (0 until s.cardCount).groupBy { s.iconAt(it) }.values.toList()

    /** Clears the current board perfectly. */
    private fun clearBoard(s: PairUpSession, clock: FakeClock) {
        skipPreview(s, clock)
        for ((a, b) in pairsOf(s)) {
            s.tap(a)
            s.tap(b)
        }
    }

    @Test
    fun `board size and preview scale with difficulty`() {
        assertEquals(listOf(3, 3, 3, 4, 4, 4, 6, 6, 6, 6), (1..10).map { session(d = it).pairs })
        assertEquals(3, session(d = 1).columns)
        assertEquals(2, session(d = 1).rows)
        assertEquals(4, session(d = 5).columns)
        assertEquals(3, session(d = 10).rows)
        assertTrue(session(d = 1).previewMs > session(d = 10).previewMs)
        assertTrue(session(d = 10).previewMs >= 800)
    }

    @Test
    fun `every board holds exactly two of each icon`() {
        for (d in 1..10) for (seed in 0 until 30) {
            val s = session(d = d, seed = seed)
            val groups = pairsOf(s)
            assertEquals(s.pairs, groups.size)
            assertTrue(groups.all { it.size == 2 })
            assertTrue((0 until s.cardCount).all { s.iconAt(it) in 0 until PairUpSession.ICON_COUNT })
        }
    }

    @Test
    fun `cards are face up during preview and taps are ignored`() {
        val clock = FakeClock(0)
        val s = session(clock = clock)
        assertEquals(Phase.PREVIEW, s.phase())
        assertTrue((0 until s.cardCount).all { s.isFaceUp(it) })
        assertEquals(FlipResult.IGNORED, s.tap(0))
        skipPreview(s, clock)
        assertTrue((0 until s.cardCount).none { s.isFaceUp(it) })
    }

    @Test
    fun `match stays up and mismatch flips back`() {
        val clock = FakeClock(0)
        val s = session(d = 4, seed = 3, clock = clock)
        skipPreview(s, clock)
        val groups = pairsOf(s)
        val (a, b) = groups[0]
        val c = groups[1][0]
        assertEquals(FlipResult.FLIPPED, s.tap(a))
        assertEquals(FlipResult.IGNORED, s.tap(a))
        assertEquals(FlipResult.MISMATCH, s.tap(c))
        assertTrue(s.isFaceUp(a) && s.isFaceUp(c) && s.isShowingMiss)
        clock.advance(PairUpSession.MISS_SHOW_MS)
        assertFalse(s.isFaceUp(a) || s.isFaceUp(c))
        assertEquals(FlipResult.FLIPPED, s.tap(a))
        assertEquals(FlipResult.MATCH, s.tap(b))
        assertTrue(s.isMatched(a) && s.isMatched(b) && s.isFaceUp(a))
        assertEquals(FlipResult.IGNORED, s.tap(a))
        assertEquals(1, s.matches)
        assertEquals(1, s.mistakes)
    }

    @Test
    fun `a tap during the miss display hides the wrong pair at once`() {
        val clock = FakeClock(0)
        val s = session(d = 2, seed = 9, clock = clock)
        skipPreview(s, clock)
        val groups = pairsOf(s)
        s.tap(groups[0][0])
        s.tap(groups[1][0])
        assertEquals(FlipResult.FLIPPED, s.tap(groups[2][0]))
        assertFalse(s.isFaceUp(groups[0][0]))
        assertTrue(s.isFaceUp(groups[2][0]))
    }

    @Test
    fun `clearing a board deals a new one with a fresh preview`() {
        val clock = FakeClock(0)
        val s = session(d = 1, seed = 5, clock = clock)
        clearBoard(s, clock)
        assertEquals(1, s.boardsCleared)
        assertEquals(3, s.matches)
        assertEquals(Phase.PREVIEW, s.phase())
        assertTrue((0 until s.cardCount).none { s.isMatched(it) })
        assertFalse(s.isComplete)
    }

    @Test
    fun `good runs score near the target`() {
        val clock = FakeClock(0)
        val s = session(d = 1, seed = 2, clock = clock)
        clearBoard(s, clock)
        clearBoard(s, clock)
        val r = s.finish()
        assertEquals(ChallengeType.PAIR_UP, r.type)
        assertEquals(6, r.correct)
        assertEquals(0.7, r.performance, 1e-9)
        assertTrue(r.score in 600..750, "score ${r.score}")
        assertEquals("6 pairs · no misses", r.detail)
    }

    @Test
    fun `misses cost points and difficulty pays more`() {
        fun score(d: Int, misses: Int): Int {
            val clock = FakeClock(0)
            val s = session(d = d, seed = 7, clock = clock)
            skipPreview(s, clock)
            val g = pairsOf(s)
            repeat(misses) { s.tap(g[0][0]); s.tap(g[1][0]) }
            s.tap(g[0][0]); s.tap(g[0][1])
            s.tap(g[1][0]); s.tap(g[1][1])
            return s.finish().score
        }
        assertTrue(score(1, 0) > score(1, 2))
        assertTrue(score(10, 0) > score(1, 0))
    }

    @Test
    fun `no input finish gives zero at every level`() {
        for (d in 1..10) {
            val clock = FakeClock(500)
            val s = session(d = d, clock = clock)
            clock.advance(CHALLENGE_DURATION_MS)
            assertTrue(s.isTimeUp)
            assertEquals(FlipResult.IGNORED, s.tap(0))
            val r = s.finish()
            assertEquals(0, r.score)
            assertEquals(0.0, r.performance)
            assertEquals("No pairs", r.detail)
            assertEquals(CHALLENGE_DURATION_MS, r.durationMs)
        }
    }

    @Test
    fun `scores clamp and boards are deterministic`() {
        val clock = FakeClock(0)
        val s = session(d = 10, seed = 4, clock = clock)
        repeat(6) { clearBoard(s, clock) }
        val r = s.finish()
        assertEquals(1000, r.score)
        assertEquals(1.0, r.performance)
        fun icons(seed: Int) = session(d = 6, seed = seed).let { p -> List(p.cardCount) { p.iconAt(it) } }
        assertEquals(icons(11), icons(11))
        assertTrue((0 until 20).map { icons(it) }.distinct().size > 1)
    }
}
