package com.microchallenge.core.games

import com.microchallenge.core.challenges.games.LightsOutSession
import com.microchallenge.core.challenges.games.LightsOutSession.TapResult
import com.microchallenge.core.engine.FakeClock
import com.microchallenge.core.model.CHALLENGE_DURATION_MS
import com.microchallenge.core.model.ChallengeType
import kotlin.random.Random
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.Test

class LightsOutSessionTest {

    private fun session(d: Int = 1, seed: Int = 1, clock: FakeClock = FakeClock(0)) =
        LightsOutSession(d, Random(seed), clock)

    private fun board(s: LightsOutSession): List<Boolean> = List(s.cellCount) { s.isOn(it) }

    /** Taps out the known solution; returns the result of the last tap. */
    private fun solve(s: LightsOutSession): TapResult {
        var last = TapResult.IGNORED
        for (cell in s.solutionCells) {
            last = s.tap(cell)
            if (last == TapResult.SOLVED) break
        }
        return last
    }

    @Test
    fun `grid size and scramble depth scale with difficulty`() {
        assertEquals(3, session(d = 1).size)
        assertEquals(3, session(d = 6).size)
        assertEquals(4, session(d = 7).size)
        assertEquals(16, session(d = 10).cellCount)
        assertEquals(listOf(2, 3, 3, 4, 4, 5, 5, 6, 6, 7), (1..10).map { session(d = it).par })
    }

    @Test
    fun `every board has lights on and is solvable within par`() {
        for (d in 1..10) for (seed in 0 until 60) {
            val s = session(d = d, seed = seed)
            repeat(4) { round ->
                assertTrue(s.onCount > 0, "d=$d seed=$seed round=$round")
                assertEquals(s.onCount, s.initialOnCount)
                assertEquals(0, s.moves)
                val solution = s.solutionCells
                assertTrue(solution.size <= s.par && solution.isNotEmpty())
                assertTrue(solution.all { it in 0 until s.cellCount })
                assertEquals(TapResult.SOLVED, solve(s), "d=$d seed=$seed round=$round")
                assertEquals(round + 1, s.solvedCount)
            }
        }
    }

    @Test
    fun `tap flips the tile and its orthogonal neighbours only`() {
        val s = session(d = 3, seed = 4)
        val before = board(s)
        // Centre of the 3x3: a plus shape.
        s.tap(4)
        val changed = (0 until 9).filter { before[it] != s.isOn(it) }
        if (s.solvedCount == 0) assertEquals(listOf(1, 3, 4, 5, 7), changed)
        val t = session(d = 3, seed = 6)
        val b2 = board(t)
        // Corner: no wrap-around.
        t.tap(0)
        if (t.solvedCount == 0) assertEquals(listOf(0, 1, 3), (0 until 9).filter { b2[it] != t.isOn(it) })
        val big = session(d = 9, seed = 6)
        val b3 = board(big)
        big.tap(7)
        if (big.solvedCount == 0) assertEquals(listOf(3, 6, 7, 11), (0 until 16).filter { b3[it] != big.isOn(it) })
    }

    @Test
    fun `tapping twice undoes the move and counts both`() {
        val s = session(d = 5, seed = 8)
        val before = board(s)
        val cell = (0 until s.cellCount).first { it !in s.solutionCells }
        assertEquals(TapResult.TOGGLED, s.tap(cell))
        assertEquals(TapResult.TOGGLED, s.tap(cell))
        assertEquals(before, board(s))
        assertEquals(2, s.moves)
        assertEquals(2, s.totalMoves)
        assertFalse(s.isOn(-1))
        assertFalse(s.isOn(s.cellCount))
        assertEquals(TapResult.IGNORED, s.tap(-1))
        assertEquals(TapResult.IGNORED, s.tap(s.cellCount))
    }

    @Test
    fun `boards are deterministic for a seed`() {
        fun trace(seed: Int): List<List<Boolean>> {
            val s = session(d = 8, seed = seed)
            return List(5) { board(s).also { solve(s) } }
        }
        assertEquals(trace(42), trace(42))
        assertTrue(trace(42) != trace(43))
    }

    @Test
    fun `solving deals a fresh board and resets moves`() {
        val s = session(d = 2, seed = 3)
        assertEquals(TapResult.SOLVED, solve(s))
        assertEquals(1, s.solvedCount)
        assertEquals(0, s.moves)
        assertTrue(s.totalMoves in 1..s.par)
        assertTrue(s.onCount > 0)
        assertFalse(s.isComplete)
    }

    @Test
    fun `good runs land near the target at low and high levels`() {
        val easy = session(d = 1, seed = 2)
        repeat(4) { solve(easy) }
        val r = easy.finish()
        assertEquals(4, r.correct)
        assertTrue(r.score in 700..900, "score ${r.score}")
        assertEquals(0.8, r.performance, 1e-9)
        assertEquals(ChallengeType.LIGHTS_OUT, r.type)

        val hard = session(d = 10, seed = 2)
        solve(hard)
        val h = hard.finish()
        assertTrue(h.score in 800..1000, "score ${h.score}")
        assertEquals(0.7, h.performance, 1e-9)
        assertEquals(0, h.mistakes)
    }

    @Test
    fun `wasted moves cost points`() {
        fun score(wasted: Int): Int {
            val s = session(d = 4, seed = 12)
            val spare = (0 until s.cellCount).first { it !in s.solutionCells }
            repeat(wasted) { s.tap(spare); s.tap(spare) }
            assertEquals(TapResult.SOLVED, solve(s))
            return s.finish().score
        }
        assertTrue(score(0) > score(1))
        assertTrue(score(1) > score(4))
        assertTrue(score(4) > 0)
    }

    @Test
    fun `partial credit for lights switched off on an unfinished board`() {
        for (seed in 0 until 30) {
            val clock = FakeClock(0)
            val s = session(d = 6, seed = seed, clock = clock)
            val solution = s.solutionCells.toList()
            // Stop one tap short, wherever that leaves fewer lights than at the start.
            for (cell in solution.dropLast(1)) s.tap(cell)
            val better = s.onCount < s.initialOnCount
            clock.advance(CHALLENGE_DURATION_MS)
            assertEquals(TapResult.IGNORED, s.tap(solution.last()))
            val r = s.finish()
            assertEquals(0, r.correct)
            if (better) {
                assertTrue(r.score in 1..250, "seed $seed score ${r.score}")
                assertTrue(r.performance in 0.0..0.25)
                assertTrue(r.detail.endsWith("left on"), r.detail)
            } else {
                assertEquals(0, r.score)
            }
        }
    }

    @Test
    fun `difficulty rewards harder boards`() {
        fun score(d: Int): Int = session(d = d, seed = 5).also { solve(it) }.finish().score
        assertTrue(score(10) > score(5))
        assertTrue(score(5) > score(1))
    }

    @Test
    fun `no input finish gives zero at every level`() {
        for (d in 1..10) {
            val clock = FakeClock(3_000)
            val s = session(d = d, clock = clock)
            clock.advance(CHALLENGE_DURATION_MS)
            assertTrue(s.isTimeUp)
            val r = s.finish()
            assertEquals(0, r.score)
            assertEquals(0.0, r.performance)
            assertEquals("No taps", r.detail)
            assertEquals(CHALLENGE_DURATION_MS, r.durationMs)
        }
    }

    @Test
    fun `scores clamp to bounds`() {
        for (d in 1..10) {
            val s = session(d = d, seed = d)
            repeat(30) { solve(s) }
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
