package com.microchallenge.core.games

import com.microchallenge.core.challenges.games.Shift
import com.microchallenge.core.challenges.games.SlideShiftSession
import com.microchallenge.core.challenges.games.SlideShiftSession.Axis
import com.microchallenge.core.challenges.games.SlideShiftSession.MoveResult
import com.microchallenge.core.engine.FakeClock
import com.microchallenge.core.model.CHALLENGE_DURATION_MS
import com.microchallenge.core.model.ChallengeType
import kotlin.random.Random
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.Test

class SlideShiftSessionTest {

    private fun session(d: Int = 1, seed: Int = 1, clock: FakeClock = FakeClock(0)) =
        SlideShiftSession(d, Random(seed), clock)

    private fun SlideShiftSession.move(s: Shift) = shift(s.axis, s.line, s.forward)

    private fun solve(s: SlideShiftSession): MoveResult {
        var last = MoveResult.IGNORED
        for (m in s.solution) {
            last = s.move(m)
            if (last == MoveResult.SOLVED) break
        }
        return last
    }

    @Test
    fun `size and par scale with difficulty`() {
        assertEquals(listOf(1, 1, 2, 2, 2, 3, 3, 3, 3, 3), (1..10).map { session(d = it).par })
        assertEquals(3, session(d = 8).size)
        assertEquals(4, session(d = 9).size)
    }

    @Test
    fun `boards are scrambled, mixed and solvable within par`() {
        for (d in 1..10) for (seed in 0 until 40) {
            val s = session(d = d, seed = seed)
            repeat(3) {
                assertTrue(s.tilesNow != s.target, "d=$d seed=$seed")
                assertTrue(s.target.all { it in 0 until SlideShiftSession.PALETTE_SIZE })
                assertEquals(s.target.sorted(), s.tilesNow.sorted())
                assertEquals(s.par, s.solution.size)
                assertEquals(MoveResult.SOLVED, solve(s), "d=$d seed=$seed")
            }
            assertEquals(3, s.solvedCount)
        }
    }

    @Test
    fun `row and column shifts wrap around`() {
        val s = session(d = 1, seed = 6)
        val before = s.tilesNow
        s.shift(Axis.ROW, 0, forward = true)
        if (s.solvedCount == 0) {
            assertEquals(listOf(before[2], before[0], before[1]), s.tilesNow.subList(0, 3))
            assertEquals(before.subList(3, 9), s.tilesNow.subList(3, 9))
        }
        val t = session(d = 1, seed = 7)
        val b = t.tilesNow
        t.shift(Axis.COLUMN, 1, forward = false)
        if (t.solvedCount == 0) {
            assertEquals(listOf(b[4], b[7], b[1]), listOf(t.tileAt(1), t.tileAt(4), t.tileAt(7)))
            assertEquals(b[0], t.tileAt(0))
        }
        assertEquals(MoveResult.IGNORED, s.shift(Axis.ROW, 3, true))
    }

    @Test
    fun `solution stays valid after wrong moves`() {
        val s = session(d = 6, seed = 12)
        s.shift(Axis.ROW, 2, true)
        s.shift(Axis.COLUMN, 0, false)
        assertEquals(MoveResult.SOLVED, solve(s))
        assertEquals(1, s.solvedCount)
        assertEquals(0, s.moves)
        assertTrue(s.totalMoves >= 2)
    }

    @Test
    fun `good runs score near the target and wasted moves cost`() {
        val easy = session(d = 1, seed = 2)
        repeat(5) { solve(easy) }
        val r = easy.finish()
        assertEquals(ChallengeType.SLIDE_SHIFT, r.type)
        assertEquals(0.7, r.performance, 1e-9)
        assertTrue(r.score in 600..750, "score ${r.score}")
        fun score(wasted: Int): Int {
            val s = session(d = 4, seed = 9)
            repeat(wasted) { s.shift(Axis.ROW, 0, true); s.shift(Axis.ROW, 0, false) }
            solve(s)
            return s.finish().score
        }
        assertTrue(score(0) > score(1))
        assertTrue(score(1) > score(3))
    }

    @Test
    fun `no input gives zero, flood clamps, boards deterministic`() {
        for (d in 1..10) {
            val clock = FakeClock(0)
            val s = session(d = d, clock = clock)
            clock.advance(CHALLENGE_DURATION_MS)
            assertEquals(MoveResult.IGNORED, s.shift(Axis.ROW, 0, true))
            val r = s.finish()
            assertEquals(0, r.score)
            assertEquals(0.0, r.performance)
            assertEquals("No moves", r.detail)
            val big = session(d = d, seed = d)
            repeat(30) { solve(big) }
            assertEquals(1000, big.finish().score)
        }
        assertEquals(session(d = 7, seed = 3).target, session(d = 7, seed = 3).target)
    }
}
