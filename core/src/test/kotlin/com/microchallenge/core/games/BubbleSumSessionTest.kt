package com.microchallenge.core.games

import com.microchallenge.core.challenges.games.BubbleSumSession
import com.microchallenge.core.challenges.games.BubbleSumSession.PopResult
import com.microchallenge.core.engine.FakeClock
import com.microchallenge.core.model.CHALLENGE_DURATION_MS
import com.microchallenge.core.model.ChallengeType
import kotlin.random.Random
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.Test

class BubbleSumSessionTest {

    private fun session(d: Int = 1, seed: Int = 1, clock: FakeClock = FakeClock(0)) =
        BubbleSumSession(d, Random(seed), clock)

    private fun solve(s: BubbleSumSession): PopResult {
        var last = PopResult.IGNORED
        for (id in s.solutionIds.toList()) last = s.pop(id)
        return last
    }

    @Test
    fun `board shape scales with difficulty`() {
        assertEquals(listOf(5, 5, 5, 6, 6, 6, 7, 7, 7, 7), (1..10).map { session(d = it).bubbleCount })
        assertEquals(2, session(d = 4).parts)
        assertEquals(3, session(d = 5).parts)
        assertTrue(session(d = 10).maxValue > session(d = 1).maxValue)
    }

    @Test
    fun `every round has a planted exact solution`() {
        for (d in 1..10) for (seed in 0 until 40) {
            val s = session(d = d, seed = seed)
            repeat(3) {
                assertEquals(s.bubbleCount, s.bubbles.size)
                assertEquals(s.bubbleCount, s.bubbles.map { it.id }.distinct().size)
                assertTrue(s.bubbles.all { it.value in 1..s.maxValue && it.lane in 0f..1f && it.phase in 0f..1f })
                val sum = s.bubbles.filter { it.id in s.solutionIds }.sumOf { it.value }
                assertEquals(s.target, sum)
                assertEquals(s.parts, s.solutionIds.size)
                assertEquals(PopResult.SOLVED, solve(s))
            }
            assertEquals(3, s.solved)
        }
    }

    @Test
    fun `tapping a selected bubble deselects it`() {
        val s = session(d = 6, seed = 3)
        val id = s.solutionIds.first()
        assertEquals(PopResult.SELECTED, s.pop(id))
        assertTrue(s.isSelected(id))
        assertEquals(PopResult.DESELECTED, s.pop(id))
        assertEquals(0, s.selectedSum)
        assertEquals(PopResult.IGNORED, s.pop(-99))
    }

    @Test
    fun `going over the target is a bust and clears the selection`() {
        for (seed in 0 until 40) {
            val s = session(d = 3, seed = seed)
            // Pop from the biggest down until the sum passes the target or hits it.
            var last = PopResult.SELECTED
            for (b in s.bubbles.sortedByDescending { it.value }) {
                last = s.pop(b.id)
                if (last != PopResult.SELECTED) break
            }
            if (last == PopResult.BUST) {
                assertEquals(1, s.busts)
                assertEquals(0, s.selectedSum)
                assertTrue(s.selectedIds.isEmpty())
            }
        }
    }

    @Test
    fun `good runs score near the target`() {
        val easy = session(d = 1, seed = 2)
        repeat(4) { solve(easy) }
        val r = easy.finish()
        assertEquals(ChallengeType.BUBBLE_SUM, r.type)
        assertEquals(0.7, r.performance, 1e-9)
        assertTrue(r.score in 600..750, "score ${r.score}")
        assertEquals("4 sums · no busts", r.detail)
        val hard = session(d = 10, seed = 2)
        repeat(3) { solve(hard) }
        val h = hard.finish()
        assertTrue(h.performance > 0.7)
        assertTrue(h.score > r.score)
    }

    @Test
    fun `no input gives zero, flood clamps, rounds deterministic`() {
        for (d in 1..10) {
            val clock = FakeClock(0)
            val s = session(d = d, clock = clock)
            clock.advance(CHALLENGE_DURATION_MS)
            assertEquals(PopResult.IGNORED, s.pop(s.bubbles.first().id))
            val r = s.finish()
            assertEquals(0, r.score)
            assertEquals(0.0, r.performance)
            assertEquals("No sums", r.detail)
            val big = session(d = d, seed = d)
            repeat(30) { solve(big) }
            assertEquals(1000, big.finish().score)
            val spam = session(d = d, seed = d)
            val rnd = Random(3)
            repeat(300) { spam.pop(spam.bubbles[rnd.nextInt(spam.bubbles.size)].id) }
            val q = spam.finish()
            assertTrue(q.score in 0..1000 && q.performance in 0.0..1.0)
        }
        fun trace(seed: Int) = session(d = 7, seed = seed).let { s -> List(4) { s.target.also { solve(s) } } }
        assertEquals(trace(5), trace(5))
    }
}
