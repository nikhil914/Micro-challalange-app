package com.microchallenge.core.games

import com.microchallenge.core.challenges.games.ReactionTapSession
import com.microchallenge.core.challenges.games.ReactionTapSession.Phase
import com.microchallenge.core.challenges.games.ReactionTapSession.TapResult
import com.microchallenge.core.engine.FakeClock
import com.microchallenge.core.model.CHALLENGE_DURATION_MS
import com.microchallenge.core.model.ChallengeType
import kotlin.random.Random
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.Test

class ReactionTapSessionTest {

    private fun session(d: Int = 1, seed: Int = 1, clock: FakeClock = FakeClock(1_000)) =
        ReactionTapSession(d, Random(seed), clock)

    /** Session start on the fake clock; tests build sessions with FakeClock(origin). */
    private var origin = 1_000L

    /** Jump to [reactMs] after the current round's green onset and tap. */
    private fun playRound(s: ReactionTapSession, clock: FakeClock, reactMs: Long): TapResult {
        clock.mono = origin + s.goAtMs + reactMs
        return s.tap()
    }

    @Test
    fun `no input finish gives zero`() {
        val clock = FakeClock(1_000)
        val s = session(clock = clock)
        clock.advance(CHALLENGE_DURATION_MS)
        assertEquals(Phase.DONE, s.phase)
        val r = s.finish()
        assertEquals(0, r.score)
        assertEquals(0.0, r.performance)
        assertEquals(ChallengeType.REACTION_TAP, r.type)
        assertEquals(CHALLENGE_DURATION_MS, r.durationMs)
    }

    @Test
    fun `phases progress waiting then go`() {
        val clock = FakeClock(1_000)
        origin = 1_000
        val s = session(clock = clock)
        assertEquals(Phase.WAITING, s.phase)
        assertTrue(s.goAtMs in s.minDelayMs..s.maxDelayMs)
        clock.mono = origin + s.goAtMs
        assertEquals(Phase.GO, s.phase)
    }

    @Test
    fun `three fast rounds complete with high score and detail`() {
        val clock = FakeClock(1_000)
        origin = 1_000
        val s = session(d = 3, clock = clock)
        repeat(3) { assertEquals(TapResult.REACTION, playRound(s, clock, 200)) }
        assertTrue(s.isComplete)
        assertEquals(3, s.roundsDone)
        assertEquals(200L, s.lastReactionMs)
        val r = s.finish()
        assertTrue(r.score >= 950, "score ${r.score}")
        assertEquals("Avg 200 ms", r.detail)
        assertEquals(3, r.correct)
        assertTrue(r.performance in 0.9..1.0)
    }

    @Test
    fun `slow reactions score low and around 300 ms is near target performance`() {
        fun run(react: Long): com.microchallenge.core.model.ChallengeResult {
            val clock = FakeClock(1_000); origin = 1_000
            val s = session(d = 1, seed = 7, clock = clock)
            repeat(3) { playRound(s, clock, react) }
            return s.finish()
        }
        val slow = run(500)
        assertTrue(slow.score in 1..300, "slow ${slow.score}")
        val mid = run(300)
        assertTrue(mid.performance in 0.6..0.8, "perf ${mid.performance}")
        assertTrue(run(250).score > mid.score)
        assertEquals(0, run(650).score)
    }

    @Test
    fun `tap while waiting is a false start and restarts the wait`() {
        val clock = FakeClock(1_000); origin = 1_000
        val s = session(clock = clock)
        clock.advance(500)
        assertEquals(TapResult.FALSE_START, s.tap())
        assertEquals(1, s.falseStarts)
        assertFalse(s.isComplete)
        assertTrue(s.goAtMs >= 500 + s.minDelayMs.coerceAtMost(600))
        assertEquals(Phase.WAITING, s.phase)
        playRound(s, clock, 250)
        val r = s.finish()
        assertEquals(1, r.mistakes)
        assertTrue(r.detail.startsWith("Avg 250 ms"))
        assertTrue(r.detail.contains("1 false start"))
    }

    @Test
    fun `anticipation faster than human limit counts as false start`() {
        val clock = FakeClock(1_000); origin = 1_000
        val s = session(clock = clock)
        assertEquals(TapResult.FALSE_START, playRound(s, clock, 50))
        assertEquals(0, s.roundsDone)
    }

    @Test
    fun `deterministic for same seed and inputs`() {
        fun trace(seed: Int): List<Long> {
            val clock = FakeClock(1_000); origin = 1_000
            val s = session(d = 8, seed = seed, clock = clock)
            val out = mutableListOf(s.goAtMs)
            repeat(3) { playRound(s, clock, 240); out += s.goAtMs }
            return out
        }
        assertEquals(trace(42), trace(42))
    }

    @Test
    fun `decoys only from level 5 and tapping one is a false start`() {
        for (seed in 0 until 50) {
            val s = session(d = 4, seed = seed)
            assertTrue(s.decoys.isEmpty())
        }
        var found = false
        for (seed in 0 until 200) {
            val clock = FakeClock(1_000); origin = 1_000
            val s = session(d = 10, seed = seed, clock = clock)
            val decoy = s.decoys.firstOrNull() ?: continue
            assertTrue(decoy.endMs < s.goAtMs)
            clock.mono = origin + decoy.startMs + 10
            assertEquals(Phase.DECOY, s.phase)
            assertEquals(TapResult.FALSE_START, s.tap())
            found = true
            break
        }
        assertTrue(found, "level 10 should produce decoys")
    }

    @Test
    fun `higher difficulty shortens the wait range`() {
        val easy = session(d = 1)
        val hard = session(d = 10)
        assertTrue(hard.maxDelayMs < easy.maxDelayMs)
        assertTrue(hard.minDelayMs < easy.minDelayMs)
    }

    @Test
    fun `green always comes early enough to react before time is up`() {
        for (seed in 0 until 30) {
            val clock = FakeClock(1_000); origin = 1_000
            val s = session(d = 1, seed = seed, clock = clock)
            repeat(3) {
                if (!s.isComplete && s.phase != Phase.DONE) {
                    assertTrue(s.goAtMs <= CHALLENGE_DURATION_MS - 600, "goAt ${s.goAtMs}")
                    playRound(s, clock, 280)
                }
            }
        }
    }

    @Test
    fun `difficulty bonus and clamped scores stay in range`() {
        for (d in 1..10) for (react in listOf(100L, 150L, 400L, 900L)) {
            val clock = FakeClock(1_000); origin = 1_000
            val s = session(d = d, seed = d, clock = clock)
            repeat(3) { playRound(s, clock, react) }
            val r = s.finish()
            assertTrue(r.score in 0..1000)
            assertTrue(r.performance in 0.0..1.0)
        }
    }

    @Test
    fun `taps after time up are ignored`() {
        val clock = FakeClock(1_000)
        val s = session(clock = clock)
        clock.advance(CHALLENGE_DURATION_MS + 5)
        assertEquals(TapResult.IGNORED, s.tap())
        assertEquals(0, s.falseStarts)
    }
}
