package com.microchallenge.core.games

import com.microchallenge.core.challenges.games.SequenceMemorySession
import com.microchallenge.core.challenges.games.SequenceMemorySession.Phase
import com.microchallenge.core.challenges.games.SequenceMemorySession.TapResult
import com.microchallenge.core.engine.FakeClock
import com.microchallenge.core.model.CHALLENGE_DURATION_MS
import com.microchallenge.core.model.ChallengeType
import kotlin.random.Random
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.Test

class SequenceMemorySessionTest {

    private fun session(d: Int = 1, seed: Int = 1, clock: FakeClock = FakeClock(2_000)) =
        SequenceMemorySession(d, Random(seed), clock)

    private fun toInput(s: SequenceMemorySession, clock: FakeClock) {
        clock.advance(s.playbackEndMs)
        assertEquals(Phase.INPUT, s.phase)
    }

    @Test
    fun `grid size and sequence length scale with difficulty`() {
        assertEquals(3, session(d = 1).gridSize)
        assertEquals(3, session(d = 6).gridSize)
        assertEquals(4, session(d = 7).gridSize)
        assertEquals(3, session(d = 1).sequence.size)
        assertEquals(8, session(d = 10).sequence.size)
        assertTrue(session(d = 10).litMs < session(d = 1).litMs)
        assertEquals(570L, session(d = 1).litMs)
        assertEquals(300L, session(d = 10).litMs)
    }

    @Test
    fun `playback leaves enough time to answer`() {
        for (d in 1..10) assertTrue(session(d = d).playbackEndMs <= 4_500, "d=$d")
    }

    @Test
    fun `sequence is deterministic in range and without immediate repeats`() {
        assertEquals(session(d = 9, seed = 5).sequence, session(d = 9, seed = 5).sequence)
        for (seed in 0 until 100) {
            val s = session(d = 10, seed = seed)
            assertTrue(s.sequence.all { it in 0 until s.tileCount })
            assertTrue(s.sequence.zipWithNext().none { (a, b) -> a == b })
        }
    }

    @Test
    fun `lit tile follows the playback schedule`() {
        val clock = FakeClock(2_000)
        val s = session(d = 4, seed = 3, clock = clock)
        assertEquals(-1, s.litTile(), "lead in")
        clock.advance(SequenceMemorySession.LEAD_IN_MS)
        assertEquals(s.sequence[0], s.litTile())
        assertEquals(0, s.litStep())
        clock.advance(s.litMs)
        assertEquals(-1, s.litTile(), "gap")
        clock.advance(s.gapMs)
        assertEquals(s.sequence[1], s.litTile())
        assertEquals(1, s.litStep())
        clock.mono = 2_000 + s.playbackEndMs
        assertEquals(-1, s.litTile())
        assertEquals(Phase.INPUT, s.phase)
    }

    @Test
    fun `taps during playback are ignored`() {
        val s = session()
        assertEquals(TapResult.IGNORED, s.tap(s.sequence[0]))
        assertEquals(0, s.progress)
    }

    @Test
    fun `perfect recall completes with high score`() {
        val clock = FakeClock(2_000)
        val s = session(d = 6, clock = clock)
        toInput(s, clock)
        s.sequence.dropLast(1).forEach { assertEquals(TapResult.CORRECT, s.tap(it)) }
        assertEquals(TapResult.COMPLETED, s.tap(s.sequence.last()))
        assertTrue(s.isComplete)
        val r = s.finish()
        assertEquals(1000, r.score)
        assertEquals(1.0, r.performance)
        assertEquals(ChallengeType.SEQUENCE_MEMORY, r.type)
        assertTrue(r.detail.startsWith("Perfect"))
    }

    @Test
    fun `wrong tap ends the run with partial credit`() {
        val clock = FakeClock(2_000)
        val s = session(d = 10, seed = 2, clock = clock)
        toInput(s, clock)
        repeat(4) { s.tap(s.sequence[it]) }
        val wrong = (s.sequence[4] + 1) % s.tileCount
        assertEquals(TapResult.WRONG, s.tap(wrong))
        assertTrue(s.isComplete)
        assertEquals(wrong, s.wrongTile)
        assertEquals(TapResult.IGNORED, s.tap(s.sequence[4]))
        val r = s.finish()
        assertEquals(4, r.correct)
        assertEquals(1, r.mistakes)
        assertEquals("4/8 tiles", r.detail)
        assertTrue(r.score in 300..600, "score ${r.score}")
        assertTrue(r.performance < 0.7)
    }

    @Test
    fun `out of range tap is ignored`() {
        val clock = FakeClock(2_000)
        val s = session(clock = clock)
        toInput(s, clock)
        assertEquals(TapResult.IGNORED, s.tap(-1))
        assertEquals(TapResult.IGNORED, s.tap(9))
        assertFalse(s.isComplete)
    }

    @Test
    fun `no input finish gives zero`() {
        val clock = FakeClock(2_000)
        val s = session(d = 8, clock = clock)
        clock.advance(CHALLENGE_DURATION_MS)
        assertEquals(Phase.DONE, s.phase)
        val r = s.finish()
        assertEquals(0, r.score)
        assertEquals(0.0, r.performance)
        assertEquals("No taps", r.detail)
        assertEquals(CHALLENGE_DURATION_MS, r.durationMs)
    }
}
