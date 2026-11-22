package com.microchallenge.core.games

import com.microchallenge.core.challenges.games.WeighInSession
import com.microchallenge.core.challenges.games.WeighInSession.Ask
import com.microchallenge.core.engine.FakeClock
import com.microchallenge.core.model.CHALLENGE_DURATION_MS
import com.microchallenge.core.model.ChallengeType
import kotlin.random.Random
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.Test

class WeighInSessionTest {

    private fun session(d: Int = 1, seed: Int = 1, clock: FakeClock = FakeClock(0)) =
        WeighInSession(d, Random(seed), clock)

    /** Works out the answer from the scales alone, the way a player must. */
    private fun deduce(s: WeighInSession): Int {
        val beatsSomeone = s.scales.map { it.heavier }.toSet()
        val losesToSomeone = s.scales.map { it.lighter }.toSet()
        val candidates = if (s.ask == Ask.HEAVIEST) s.weights.filter { it !in losesToSomeone }
        else s.weights.filter { it !in beatsSomeone }
        assertEquals(1, candidates.size, "answer must follow from the scales")
        return candidates.single()
    }

    @Test
    fun `weights per round scale with difficulty`() {
        assertEquals(listOf(3, 3, 3, 4, 4, 4, 4, 5, 5, 5), (1..10).map { session(d = it).weightCount })
    }

    @Test
    fun `the scales always decide the answer`() {
        for (d in 1..10) for (seed in 0 until 40) {
            val s = session(d = d, seed = seed)
            repeat(5) {
                assertEquals(s.weightCount, s.weights.distinct().size)
                assertEquals(s.weightCount - 1, s.scales.size)
                assertTrue(s.weights.all { it in 0 until WeighInSession.WEIGHT_KINDS })
                assertTrue(s.scales.all { it.left != it.right && it.left in s.weights && it.right in s.weights })
                if (d < WeighInSession.LIGHTEST_FROM) assertEquals(Ask.HEAVIEST, s.ask)
                assertEquals(s.answer, deduce(s))
                assertTrue(s.choose(deduce(s)))
            }
        }
    }

    @Test
    fun `lightest questions appear from level 5`() {
        val asks = (0 until 40).map { session(d = 7, seed = it).ask }.toSet()
        assertEquals(setOf(Ask.HEAVIEST, Ask.LIGHTEST), asks)
    }

    @Test
    fun `wrong answers count and move on`() {
        val s = session(d = 4, seed = 5)
        val wrong = s.weights.first { it != s.answer }
        assertFalse(s.choose(wrong))
        assertEquals(1, s.mistakes)
        assertEquals(false, s.lastCorrect)
        assertEquals(1, s.round)
        assertFalse(s.choose(99))
        assertEquals(1, s.round)
    }

    @Test
    fun `good runs score near the target`() {
        val s = session(d = 1, seed = 2)
        repeat(5) { s.choose(s.answer) }
        val r = s.finish()
        assertEquals(ChallengeType.WEIGH_IN, r.type)
        assertEquals(0.7, r.performance, 1e-9)
        assertTrue(r.score in 600..750, "score ${r.score}")
        assertEquals("5 right · no misses", r.detail)
        val m = session(d = 1, seed = 2)
        repeat(5) { m.choose(m.answer) }
        m.choose(m.weights.first { it != m.answer })
        assertTrue(m.finish().score < r.score)
    }

    @Test
    fun `no input gives zero, flood clamps, rounds deterministic`() {
        for (d in 1..10) {
            val clock = FakeClock(0)
            val s = session(d = d, clock = clock)
            clock.advance(CHALLENGE_DURATION_MS)
            assertFalse(s.choose(s.answer))
            val r = s.finish()
            assertEquals(0, r.score)
            assertEquals(0.0, r.performance)
            assertEquals("No answers", r.detail)
            val big = session(d = d, seed = d)
            repeat(30) { big.choose(big.answer) }
            assertEquals(1000, big.finish().score)
        }
        assertEquals(session(d = 8, seed = 3).scales, session(d = 8, seed = 3).scales)
    }
}
