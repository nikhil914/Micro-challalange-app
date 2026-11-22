package com.microchallenge.core.games

import com.microchallenge.core.challenges.games.PipeFlowSession
import com.microchallenge.core.challenges.games.PipeFlowSession.Companion.E
import com.microchallenge.core.challenges.games.PipeFlowSession.Companion.N
import com.microchallenge.core.challenges.games.PipeFlowSession.Companion.S
import com.microchallenge.core.challenges.games.PipeFlowSession.Companion.W
import com.microchallenge.core.challenges.games.PipeFlowSession.Phase
import com.microchallenge.core.challenges.games.PipeFlowSession.TapResult
import com.microchallenge.core.engine.FakeClock
import com.microchallenge.core.model.CHALLENGE_DURATION_MS
import com.microchallenge.core.model.ChallengeType
import kotlin.random.Random
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.Test

class PipeFlowSessionTest {

    private fun session(d: Int = 1, seed: Int = 1, clock: FakeClock = FakeClock(0)) =
        PipeFlowSession(d, Random(seed), clock)

    /** Turns path tiles back; returns the last tap result. */
    private fun solve(s: PipeFlowSession): TapResult {
        var last = TapResult.IGNORED
        for (cell in s.pathCells) {
            repeat(s.tapsToFix(cell)) {
                if (last != TapResult.SOLVED) last = s.tap(cell)
            }
        }
        return last
    }

    private fun next(s: PipeFlowSession, clock: FakeClock) {
        clock.advance(PipeFlowSession.FLOW_MS)
        assertEquals(Phase.PLAY, s.phase)
    }

    @Test
    fun `rotation helpers`() {
        assertEquals(E, PipeFlowSession.rotate(N))
        assertEquals(N, PipeFlowSession.rotate(W))
        assertEquals(E or S, PipeFlowSession.rotate(N or E))
        assertEquals(S, PipeFlowSession.opposite(N))
        assertEquals(W, PipeFlowSession.opposite(E))
        assertTrue(PipeFlowSession.isStraight(E or W))
        assertFalse(PipeFlowSession.isStraight(N or E))
    }

    @Test
    fun `grid size scales with difficulty`() {
        assertEquals(3, session(d = 1).size)
        assertEquals(3, session(d = 4).size)
        assertEquals(4, session(d = 5).size)
        assertEquals(16, session(d = 10).cellCount)
    }

    @Test
    fun `every board starts dry at the drain and is solvable within par`() {
        for (d in 1..10) for (seed in 0 until 40) {
            val clock = FakeClock(0)
            val s = session(d = d, seed = seed, clock = clock)
            repeat(3) { round ->
                assertFalse(s.isFlowing, "d=$d seed=$seed round=$round")
                val path = s.pathCells
                assertEquals(s.sourceRow * s.size, path.first())
                assertEquals(s.drainRow * s.size + s.size - 1, path.last())
                assertEquals(path.size, path.toSet().size)
                assertTrue(path.size >= if (s.size == 3) 5 else 7)
                assertEquals(s.par, path.sumOf { s.tapsToFix(it) })
                assertEquals(TapResult.SOLVED, solve(s), "d=$d seed=$seed round=$round")
                assertTrue(s.moves <= s.par)
                assertTrue(s.isFlowing)
                assertEquals(round + 1, s.solvedCount)
                next(s, clock)
            }
        }
    }

    @Test
    fun `tap turns one tile clockwise and is ignored while flowing`() {
        val clock = FakeClock(0)
        val s = session(d = 6, seed = 4, clock = clock)
        val cell = 5
        val before = s.mask(cell)
        val others = (0 until s.cellCount).filter { it != cell }.map { s.mask(it) }
        s.tap(cell)
        assertEquals(PipeFlowSession.rotate(before), s.mask(cell))
        assertEquals(1, s.turns(cell))
        if (!s.isFlowing) assertEquals(others, (0 until s.cellCount).filter { it != cell }.map { s.mask(it) })

        val t = session(d = 3, seed = 9, clock = clock)
        assertEquals(TapResult.SOLVED, solve(t))
        assertEquals(Phase.FLOW, t.phase)
        val board = t.boardId
        assertEquals(TapResult.IGNORED, t.tap(0))
        assertTrue(t.flowProgress() in 0f..0.01f)
        next(t, clock)
        assertEquals(board + 1, t.boardId)
        assertEquals(0, t.moves)
        assertEquals(0, t.turns(0))
        assertEquals(TapResult.IGNORED, t.tap(-1))
        assertEquals(TapResult.IGNORED, t.tap(t.cellCount))
    }

    @Test
    fun `water distances follow open pipes from the source`() {
        val s = session(d = 7, seed = 3)
        solve(s)
        val start = s.sourceRow * s.size
        assertEquals(0, s.waterDistance(start))
        assertEquals(W, s.waterFrom(start))
        for (cell in s.pathCells) assertTrue(s.waterDistance(cell) >= 0)
        assertTrue(s.maxWaterDistance >= s.size - 1)
        assertEquals(-1, s.waterDistance(-1))
    }

    @Test
    fun `boards are deterministic for a seed`() {
        fun trace(seed: Int): List<List<Int>> {
            val clock = FakeClock(0)
            val s = session(d = 8, seed = seed, clock = clock)
            return List(4) { List(s.cellCount) { c -> s.mask(c) }.also { solve(s); next(s, clock) } }
        }
        assertEquals(trace(42), trace(42))
        assertTrue(trace(42) != trace(43))
    }

    @Test
    fun `good runs score well at low and high levels`() {
        val clock = FakeClock(0)
        val easy = session(d = 1, seed = 2, clock = clock)
        repeat(3) { solve(easy); next(easy, clock) }
        val r = easy.finish()
        assertEquals(3, r.correct)
        assertTrue(r.score in 600..800, "score ${r.score}")
        assertEquals(ChallengeType.PIPE_FLOW, r.type)

        val hard = session(d = 10, seed = 2)
        solve(hard)
        val h = hard.finish()
        assertTrue(h.score in 750..1000, "score ${h.score}")
        assertEquals(0, h.mistakes)
    }

    @Test
    fun `wasted turns cost points`() {
        fun score(wasted: Int): Int {
            val s = session(d = 4, seed = 12)
            val spare = (0 until s.cellCount).first { it !in s.pathCells }
            repeat(wasted * 4) { s.tap(spare) }
            assertEquals(TapResult.SOLVED, solve(s))
            return s.finish().score
        }
        assertTrue(score(0) > score(1))
        assertTrue(score(1) > score(3))
        assertTrue(score(3) > 0)
    }

    @Test
    fun `no input finish gives zero at every level`() {
        for (d in 1..10) {
            val clock = FakeClock(3_000)
            val s = session(d = d, clock = clock)
            clock.advance(CHALLENGE_DURATION_MS)
            assertTrue(s.isTimeUp)
            assertEquals(Phase.DONE, s.phase)
            val r = s.finish()
            assertEquals(0, r.score)
            assertEquals(0.0, r.performance)
            assertEquals("No taps", r.detail)
        }
    }

    @Test
    fun `scores clamp to bounds`() {
        for (d in 1..10) {
            val clock = FakeClock(0)
            val s = session(d = d, seed = d, clock = clock)
            repeat(8) { solve(s); next(s, clock) }
            val r = s.finish()
            assertEquals(1000, r.score)
            assertEquals(1.0, r.performance)
            val spam = session(d = d, seed = d)
            val rnd = Random(9)
            repeat(200) { spam.tap(rnd.nextInt(spam.cellCount)) }
            val q = spam.finish()
            assertTrue(q.score in 0..1000 && q.performance in 0.0..1.0)
        }
    }
}
