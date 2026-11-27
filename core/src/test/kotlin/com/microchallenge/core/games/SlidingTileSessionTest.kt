package com.microchallenge.core.games

import com.microchallenge.core.challenges.ChallengeRegistry
import com.microchallenge.core.challenges.games.SlidingTileSession
import com.microchallenge.core.challenges.games.SlidingTileSession.TapResult
import com.microchallenge.core.engine.FakeClock
import com.microchallenge.core.model.CHALLENGE_DURATION_MS
import com.microchallenge.core.model.ChallengeType
import kotlin.random.Random
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.Test

class SlidingTileSessionTest {

    private fun session(d: Int = 1, seed: Int = 1, clock: FakeClock = FakeClock(0)) =
        SlidingTileSession(d, Random(seed), clock)

    private fun board(s: SlidingTileSession): List<Int> = List(s.cellCount) { s.tileAt(it) }

    /** Optimal solution as a list of cells to tap (single-tile moves), found by BFS. */
    private fun solution(s: SlidingTileSession): List<Int> {
        val n = s.size
        val goal = List(s.cellCount) { if (it == s.cellCount - 1) 0 else it + 1 }
        val start = board(s)
        val prev = HashMap<List<Int>, Pair<List<Int>, Int>?>()
        prev[start] = null
        val queue = ArrayDeque(listOf(start))
        while (queue.isNotEmpty()) {
            val cur = queue.removeFirst()
            if (cur == goal) break
            val b = cur.indexOf(0)
            val r = b / n
            val c = b % n
            val next = buildList {
                if (r > 0) add(b - n)
                if (r < n - 1) add(b + n)
                if (c > 0) add(b - 1)
                if (c < n - 1) add(b + 1)
            }
            for (cell in next) {
                val m = cur.toMutableList()
                m[b] = m[cell]
                m[cell] = 0
                if (m !in prev) {
                    prev[m] = cur to cell
                    queue.addLast(m)
                }
            }
        }
        val path = ArrayList<Int>()
        var at = goal
        while (true) {
            val p = prev.getValue(at) ?: break
            path.add(p.second)
            at = p.first
        }
        return path.reversed()
    }

    private fun solve(s: SlidingTileSession): TapResult {
        var last = TapResult.IGNORED
        for (cell in solution(s)) last = s.tap(cell)
        return last
    }

    @Test
    fun `registry builds the session`() {
        val s = ChallengeRegistry.create(ChallengeType.SLIDING_TILE, 4, Random(1), FakeClock(0))
        assertTrue(s is SlidingTileSession)
        assertEquals(ChallengeType.SLIDING_TILE, s.type)
    }

    @Test
    fun `board size and par scale with difficulty`() {
        assertEquals(3, session(d = 1).size)
        assertEquals(3, session(d = 7).size)
        assertEquals(4, session(d = 8).size)
        assertEquals(16, session(d = 10).cellCount)
        assertEquals(listOf(3, 3, 4, 4, 5, 5, 6, 6, 7, 7), (1..10).map { session(d = it).par })
    }

    @Test
    fun `every board is a scrambled permutation solvable within par`() {
        for (d in 1..10) for (seed in 0 until 25) {
            val s = session(d = d, seed = seed)
            repeat(3) { round ->
                val tiles = board(s)
                assertEquals((0 until s.cellCount).toList(), tiles.sorted())
                assertFalse(s.isSolved, "d=$d seed=$seed round=$round")
                assertTrue(s.distance > 0)
                assertEquals(s.distance, s.initialDistance)
                assertEquals(0, s.moves)
                val sol = solution(s)
                assertTrue(sol.size in 1..s.par, "d=$d seed=$seed len=${sol.size}")
                assertEquals(TapResult.SOLVED, solve(s), "d=$d seed=$seed round=$round")
                assertEquals(round + 1, s.solvedCount)
            }
        }
    }

    @Test
    fun `tapping a tile beside the gap swaps them`() {
        val s = session(d = 3, seed = 4)
        val b = s.blankCell
        val n = s.size
        val neighbour = if (b % n > 0) b - 1 else b + 1
        val tile = s.tileAt(neighbour)
        val result = s.tap(neighbour)
        assertTrue(result != TapResult.IGNORED)
        if (result == TapResult.MOVED) {
            assertEquals(tile, s.tileAt(b))
            assertEquals(neighbour, s.blankCell)
            assertEquals(1, s.moves)
        }
    }

    @Test
    fun `tapping far along a line slides every tile between`() {
        for (seed in 0 until 40) {
            val s = session(d = 9, seed = seed)
            val n = s.size
            val b = s.blankCell
            val r = b / n
            val target = if (b % n >= 2) r * n else if (b % n <= n - 3) r * n + n - 1 else continue
            val before = board(s)
            val dist = kotlin.math.abs(target - b)
            val result = s.tap(target)
            assertTrue(result != TapResult.IGNORED)
            assertEquals(dist, s.totalMoves)
            if (result == TapResult.MOVED) {
                assertEquals(target, s.blankCell)
                val step = if (target > b) 1 else -1
                var cell = b
                while (cell != target) {
                    assertEquals(before[cell + step], s.tileAt(cell))
                    cell += step
                }
            }
        }
    }

    @Test
    fun `taps off the gap's lines are ignored`() {
        val s = session(d = 5, seed = 8)
        val n = s.size
        val b = s.blankCell
        val before = board(s)
        for (cell in 0 until s.cellCount) {
            if (cell == b || cell / n == b / n || cell % n == b % n) {
                if (cell == b) assertEquals(TapResult.IGNORED, s.tap(cell))
                continue
            }
            assertFalse(s.canSlide(cell))
            assertEquals(TapResult.IGNORED, s.tap(cell))
        }
        assertEquals(TapResult.IGNORED, s.tap(-1))
        assertEquals(TapResult.IGNORED, s.tap(s.cellCount))
        assertEquals(before, board(s))
        assertEquals(0, s.moves)
        assertEquals(0, s.tileAt(-1))
    }

    @Test
    fun `boards are deterministic for a seed`() {
        fun trace(seed: Int): List<List<Int>> {
            val s = session(d = 6, seed = seed)
            return List(4) { board(s).also { solve(s) } }
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
        assertFalse(s.isSolved)
        assertFalse(s.isComplete)
    }

    @Test
    fun `good runs land near the target at low and high levels`() {
        val easy = session(d = 1, seed = 2)
        repeat(3) { solve(easy) }
        val r = easy.finish()
        assertEquals(3, r.correct)
        assertTrue(r.score in 800..1000, "score ${r.score}")
        assertEquals(ChallengeType.SLIDING_TILE, r.type)
        assertTrue(r.detail.startsWith("3 puzzles solved"), r.detail)

        val hard = session(d = 10, seed = 2)
        solve(hard)
        val h = hard.finish()
        assertTrue(h.score in 600..1000, "score ${h.score}")
        assertEquals(0.7, h.performance, 1e-9)
        assertEquals(0, h.mistakes)
    }

    @Test
    fun `wasted moves cost points`() {
        fun score(wasted: Int): Int {
            val s = session(d = 4, seed = 12)
            repeat(wasted) {
                val b = s.blankCell
                val n = s.size
                val other = if (b % n > 0) b - 1 else b + 1
                s.tap(other)
                s.tap(b)
            }
            assertEquals(TapResult.SOLVED, solve(s))
            return s.finish().score
        }
        assertTrue(score(0) > score(1))
        assertTrue(score(1) > score(4))
        assertTrue(score(4) > 0)
    }

    @Test
    fun `partial credit for progress on an unfinished board`() {
        for (seed in 0 until 30) {
            val clock = FakeClock(0)
            val s = session(d = 6, seed = seed, clock = clock)
            val sol = solution(s)
            for (cell in sol.dropLast(1)) s.tap(cell)
            val better = s.moves > 0 && s.distance < s.initialDistance
            clock.advance(CHALLENGE_DURATION_MS)
            assertEquals(TapResult.IGNORED, s.tap(sol.last()))
            val r = s.finish()
            assertEquals(0, r.correct)
            if (better) {
                assertTrue(r.score in 1..300, "seed $seed score ${r.score}")
                assertTrue(r.detail.endsWith("out of place"), r.detail)
            } else {
                assertEquals(0, r.score)
            }
        }
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
            assertEquals("No moves", r.detail)
            assertEquals(CHALLENGE_DURATION_MS, r.durationMs)
        }
    }

    @Test
    fun `scores clamp to bounds`() {
        for (d in 1..10) {
            val s = session(d = d, seed = d)
            repeat(20) { solve(s) }
            val r = s.finish()
            assertEquals(1000, r.score)
            assertEquals(1.0, r.performance)
            val spam = session(d = d, seed = d)
            val rnd = Random(9)
            repeat(300) { spam.tap(rnd.nextInt(spam.cellCount)) }
            val q = spam.finish()
            assertTrue(q.score in 0..1000 && q.performance in 0.0..1.0)
        }
    }
}
