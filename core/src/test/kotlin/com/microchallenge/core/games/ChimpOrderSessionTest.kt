package com.microchallenge.core.games

import com.microchallenge.core.challenges.games.ChimpOrderSession
import com.microchallenge.core.challenges.games.ChimpOrderSession.Phase
import com.microchallenge.core.challenges.games.ChimpOrderSession.TapResult
import com.microchallenge.core.engine.FakeClock
import com.microchallenge.core.model.CHALLENGE_DURATION_MS
import com.microchallenge.core.model.ChallengeType
import kotlin.random.Random
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.Test

class ChimpOrderSessionTest {

    private fun session(d: Int = 1, seed: Int = 1, clock: FakeClock = FakeClock(2_000)) =
        ChimpOrderSession(d, Random(seed), clock)

    private fun cellOf(s: ChimpOrderSession, n: Int): Int =
        (0 until s.cellCount).single { s.numberAt(it) == n }

    private fun toInput(s: ChimpOrderSession, clock: FakeClock) {
        clock.advance(s.showMs)
        assertEquals(Phase.INPUT, s.phase)
    }

    @Test
    fun `grid count and show time scale with difficulty`() {
        assertEquals(4, session(d = 1).cols)
        assertEquals(4, session(d = 5).rows)
        assertEquals(5, session(d = 6).rows)
        assertEquals(4, session(d = 6).cols)
        assertEquals(listOf(4, 5, 5, 6, 6, 7, 7, 8, 8, 9), (1..10).map { session(d = it).firstCount })
        assertEquals(2_200L, session(d = 1).showMs)
        // Less time per number as the level climbs.
        val perNumber = (1..10).map { session(d = it).let { s -> s.showMs.toDouble() / s.count } }
        assertTrue(perNumber.zipWithNext().all { (a, b) -> b < a }, "$perNumber")
        for (d in 1..10) assertTrue(session(d = d).showMs <= 3_000, "d=$d")
    }

    @Test
    fun `board holds each number once and is deterministic`() {
        for (d in 1..10) for (seed in 0 until 40) {
            val s = session(d = d, seed = seed)
            val placed = (0 until s.cellCount).map { s.numberAt(it) }.filter { it != 0 }
            assertEquals((1..s.count).toList(), placed.sorted())
            assertEquals(0, s.numberAt(-1))
            assertEquals(0, s.numberAt(s.cellCount))
        }
        fun layout(seed: Int) = session(d = 8, seed = seed).let { s -> List(s.cellCount) { s.numberAt(it) } }
        assertEquals(layout(7), layout(7))
        assertTrue(layout(7) != layout(8))
    }

    @Test
    fun `numbers show then hide`() {
        val clock = FakeClock(2_000)
        val s = session(d = 3, clock = clock)
        val one = cellOf(s, 1)
        assertEquals(Phase.SHOW, s.phase)
        assertTrue(s.isNumberVisible(one))
        clock.advance(s.showMs - 1)
        assertEquals(Phase.SHOW, s.phase)
        clock.advance(1)
        assertEquals(Phase.INPUT, s.phase)
        assertFalse(s.isNumberVisible(one))
        assertEquals(TapResult.CORRECT, s.tap(one))
        assertTrue(s.isNumberVisible(one))
        assertEquals(setOf(one), s.revealed)
        assertEquals(2, s.nextExpected)
    }

    @Test
    fun `tapping one early hides the numbers and other early taps are ignored`() {
        val s = session(d = 4)
        assertEquals(TapResult.IGNORED, s.tap(cellOf(s, 2)))
        assertEquals(Phase.SHOW, s.phase)
        assertEquals(TapResult.CORRECT, s.tap(cellOf(s, 1)))
        assertEquals(Phase.INPUT, s.phase)
        assertFalse(s.isNumberVisible(cellOf(s, 2)))
    }

    @Test
    fun `empty cells repeats and out of range taps are ignored`() {
        val clock = FakeClock(2_000)
        val s = session(clock = clock)
        toInput(s, clock)
        val empty = (0 until s.cellCount).first { s.numberAt(it) == 0 }
        assertEquals(TapResult.IGNORED, s.tap(empty))
        assertEquals(TapResult.IGNORED, s.tap(-1))
        assertEquals(TapResult.IGNORED, s.tap(s.cellCount))
        s.tap(cellOf(s, 1))
        assertEquals(TapResult.IGNORED, s.tap(cellOf(s, 1)))
        assertFalse(s.isComplete)
        assertEquals(1, s.firstProgress)
    }

    @Test
    fun `wrong tile ends the run with partial credit`() {
        val clock = FakeClock(2_000)
        val s = session(d = 10, seed = 2, clock = clock)
        toInput(s, clock)
        for (n in 1..4) assertEquals(TapResult.CORRECT, s.tap(cellOf(s, n)))
        val wrong = cellOf(s, 7)
        assertEquals(TapResult.WRONG, s.tap(wrong))
        assertTrue(s.isComplete)
        assertEquals(Phase.DONE, s.phase)
        assertEquals(wrong, s.wrongCell)
        assertTrue(s.isNumberVisible(cellOf(s, 9)), "numbers come back at the end")
        assertEquals(TapResult.IGNORED, s.tap(cellOf(s, 5)))
        val r = s.finish()
        assertEquals(4, r.correct)
        assertEquals(1, r.mistakes)
        assertEquals("4/9 in order", r.detail)
        assertTrue(r.score in 250..500, "score ${r.score}")
        assertTrue(r.performance < 0.5)
    }

    @Test
    fun `clearing the board deals a bigger one and timing out there keeps the credit`() {
        val clock = FakeClock(2_000)
        val s = session(d = 1, seed = 5, clock = clock)
        toInput(s, clock)
        for (n in 1..3) { clock.advance(400); assertEquals(TapResult.CORRECT, s.tap(cellOf(s, n))) }
        clock.advance(400)
        assertEquals(TapResult.BOARD_CLEARED, s.tap(cellOf(s, 4)))
        assertFalse(s.isComplete)
        assertEquals(1, s.boardIndex)
        assertEquals(5, s.count)
        assertEquals(1, s.nextExpected)
        assertTrue(s.revealed.isEmpty())
        assertEquals(Phase.SHOW, s.phase)
        assertEquals((1..5).toList(), (0 until s.cellCount).map { s.numberAt(it) }.filter { it != 0 }.sorted())
        toInput(s, clock)
        s.tap(cellOf(s, 1))
        s.tap(cellOf(s, 2))
        clock.mono = 2_000 + CHALLENGE_DURATION_MS
        assertEquals(Phase.DONE, s.phase)
        assertEquals(TapResult.IGNORED, s.tap(cellOf(s, 3)))
        val r = s.finish()
        assertEquals(6, r.correct)
        assertEquals(0, r.mistakes)
        assertEquals("Board cleared · 2/5 on the next", r.detail)
        assertTrue(r.score in 800..900, "score ${r.score}")
        assertTrue(r.performance in 0.75..0.9, "perf ${r.performance}")
        assertEquals(CHALLENGE_DURATION_MS, r.durationMs)
    }

    @Test
    fun `one cleared board is a good run at every level`() {
        for (d in 1..10) {
            val clock = FakeClock(2_000)
            val s = session(d = d, clock = clock)
            toInput(s, clock)
            for (n in 1..s.firstCount) s.tap(cellOf(s, n))
            val r = s.finish()
            assertEquals(0.75, r.performance, 1e-9)
            assertTrue(r.score in 750..1000, "d=$d score ${r.score}")
        }
    }

    @Test
    fun `clearing both boards completes with full marks`() {
        val clock = FakeClock(2_000)
        val s = session(d = 6, seed = 9, clock = clock)
        for (n in 1..s.firstCount) s.tap(cellOf(s, n))
        val second = s.firstCount + 1
        for (n in 1 until second) assertEquals(TapResult.CORRECT, s.tap(cellOf(s, n)))
        assertEquals(TapResult.COMPLETED, s.tap(cellOf(s, second)))
        assertTrue(s.isComplete)
        val r = s.finish()
        assertEquals(1000, r.score)
        assertEquals(1.0, r.performance)
        assertEquals("Both boards cleared", r.detail)
        assertEquals(ChallengeType.CHIMP_ORDER, r.type)
    }

    @Test
    fun `whole run is deterministic for a seed`() {
        fun trace(): List<Int> {
            val s = session(d = 7, seed = 31)
            val first = List(s.cellCount) { s.numberAt(it) }
            for (n in 1..s.firstCount) s.tap(cellOf(s, n))
            return first + List(s.cellCount) { s.numberAt(it) }
        }
        assertEquals(trace(), trace())
    }

    @Test
    fun `no input finish gives zero`() {
        for (d in 1..10) {
            val clock = FakeClock(2_000)
            val s = session(d = d, clock = clock)
            clock.advance(CHALLENGE_DURATION_MS)
            assertEquals(Phase.DONE, s.phase)
            val r = s.finish()
            assertEquals(0, r.score)
            assertEquals(0.0, r.performance)
            assertEquals("No taps", r.detail)
            assertEquals(CHALLENGE_DURATION_MS, r.durationMs)
        }
    }
}
