package com.microchallenge.core.games

import com.microchallenge.core.challenges.games.FlankerSession
import com.microchallenge.core.challenges.games.FlankerSession.AnswerResult
import com.microchallenge.core.challenges.games.FlankerSession.Glyph
import com.microchallenge.core.challenges.games.FlankerSession.TrialKind
import com.microchallenge.core.engine.FakeClock
import com.microchallenge.core.model.CHALLENGE_DURATION_MS
import com.microchallenge.core.model.ChallengeType
import kotlin.random.Random
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.Test

class FlankerSessionTest {

    private fun session(d: Int = 1, seed: Int = 1, clock: FakeClock = FakeClock(0)) =
        FlankerSession(d, Random(seed), clock)

    @Test
    fun `row size congruence and extras scale with difficulty`() {
        assertEquals(5, session(d = 1).arrowCount)
        assertEquals(5, session(d = 6).arrowCount)
        assertEquals(7, session(d = 7).arrowCount)
        assertEquals(0.70, session(d = 1).congruentProbability, 1e-9)
        assertEquals(0.25, session(d = 10).congruentProbability, 1e-9)
        assertFalse(session(d = 4).hasTrickyFlankers)
        assertTrue(session(d = 5).hasTrickyFlankers)
        assertFalse(session(d = 7).hasJitter)
        assertTrue(session(d = 8).hasJitter)
    }

    @Test
    fun `rows are well formed for every kind`() {
        for (d in 1..10) {
            val s = session(d = d, seed = d)
            repeat(600) {
                val row = s.row
                assertEquals(s.arrowCount, row.size)
                val target = if (s.targetLeft) Glyph.LEFT else Glyph.RIGHT
                val wrong = if (s.targetLeft) Glyph.RIGHT else Glyph.LEFT
                assertEquals(target, row[s.middleIndex])
                val flankers = row.filterIndexed { i, _ -> i != s.middleIndex }
                when (s.kind) {
                    TrialKind.CONGRUENT -> assertTrue(flankers.all { it == target })
                    TrialKind.INCONGRUENT -> assertTrue(flankers.all { it == wrong })
                    TrialKind.NEUTRAL -> assertTrue(flankers.all { it == Glyph.DASH })
                    TrialKind.MIXED -> {
                        assertTrue(target in flankers && wrong in flankers && Glyph.DASH !in flankers)
                        assertEquals(wrong, row[s.middleIndex - 1])
                        assertEquals(wrong, row[s.middleIndex + 1])
                    }
                }
                if (d < 5) assertTrue(s.kind == TrialKind.CONGRUENT || s.kind == TrialKind.INCONGRUENT)
                s.answer(s.targetLeft)
            }
        }
    }

    private fun kindRates(d: Int): Map<TrialKind, Double> {
        val s = session(d = d, seed = 11)
        val n = 4000
        val counts = HashMap<TrialKind, Int>()
        repeat(n) {
            counts[s.kind] = (counts[s.kind] ?: 0) + 1
            s.answer(true)
        }
        return counts.mapValues { it.value.toDouble() / n }
    }

    @Test
    fun `observed congruent rate follows probability`() {
        assertEquals(0.70, kindRates(1).getValue(TrialKind.CONGRUENT), 0.04)
        val hard = kindRates(10)
        assertEquals(0.25, hard.getValue(TrialKind.CONGRUENT), 0.04)
        assertTrue(hard.getValue(TrialKind.NEUTRAL) > 0.05)
        assertTrue(hard.getValue(TrialKind.MIXED) > 0.1)
    }

    @Test
    fun `both directions come up`() {
        val s = session(d = 3, seed = 4)
        var left = 0
        repeat(1000) { if (s.targetLeft) left++; s.answer(true) }
        assertTrue(left in 420..580, "left $left")
    }

    @Test
    fun `row only jitters from level 8 and always moves`() {
        val calm = session(d = 7, seed = 2)
        repeat(200) { assertEquals(0, calm.offsetSlot); calm.answer(true) }
        val s = session(d = 8, seed = 2)
        val seen = HashSet<Int>()
        var prev = s.offsetSlot
        repeat(300) {
            s.answer(true)
            assertTrue(s.offsetSlot in -FlankerSession.MAX_OFFSET_SLOT..FlankerSession.MAX_OFFSET_SLOT)
            assertTrue(s.offsetSlot != prev)
            prev = s.offsetSlot
            seen += prev
        }
        assertEquals(5, seen.size)
    }

    @Test
    fun `deterministic trials for same seed`() {
        fun trace(): List<Any> {
            val s = session(d = 9, seed = 99)
            return List(40) { Triple(s.row, s.targetLeft, s.offsetSlot).also { s.answer(s.targetLeft) } }
        }
        assertEquals(trace(), trace())
    }

    @Test
    fun `answers count and advance trials`() {
        val s = session()
        assertEquals(AnswerResult.IGNORED, s.lastResult)
        assertEquals(AnswerResult.WRONG, s.answer(!s.targetLeft))
        assertEquals(AnswerResult.CORRECT, s.answer(s.targetLeft))
        assertEquals(AnswerResult.CORRECT, s.lastResult)
        assertEquals(1, s.correct)
        assertEquals(1, s.mistakes)
        assertEquals(2, s.trialIndex)
        assertFalse(s.isComplete)
    }

    @Test
    fun `good player around 14 correct scores well and near target`() {
        val clock = FakeClock(0)
        val s = session(d = 2, clock = clock)
        repeat(14) { clock.advance(650); s.answer(s.targetLeft) }
        s.answer(!s.targetLeft)
        val r = s.finish()
        assertEquals(14, r.correct)
        assertTrue(r.score in 750..1000, "score ${r.score}")
        assertTrue(r.performance in 0.55..0.85, "perf ${r.performance}")
        assertEquals("14 correct · 1 miss", r.detail)
        assertEquals(ChallengeType.FLANKER, r.type)
    }

    @Test
    fun `mashing one side earns next to nothing`() {
        for (seed in 0 until 20) {
            val s = session(d = 1, seed = seed)
            repeat(40) { s.answer(true) }
            val r = s.finish()
            assertTrue(r.score <= 500, "seed $seed score ${r.score}")
            assertTrue(r.performance < 0.5, "seed $seed perf ${r.performance}")
        }
    }

    @Test
    fun `difficulty bonus rewards higher levels`() {
        fun score(d: Int): Int {
            val s = session(d = d)
            repeat(8) { s.answer(s.targetLeft) }
            return s.finish().score
        }
        assertTrue(score(10) > score(1))
    }

    @Test
    fun `answers after time up are ignored and no input gives zero`() {
        val clock = FakeClock(500)
        val s = session(clock = clock)
        clock.advance(CHALLENGE_DURATION_MS)
        assertTrue(s.isTimeUp)
        assertEquals(AnswerResult.IGNORED, s.answer(s.targetLeft))
        val r = s.finish()
        assertEquals(0, r.score)
        assertEquals(0.0, r.performance)
        assertEquals("No answers", r.detail)
        assertEquals(CHALLENGE_DURATION_MS, r.durationMs)
    }

    @Test
    fun `scores clamp to bounds`() {
        for (d in 1..10) {
            val perfect = session(d = d)
            repeat(60) { perfect.answer(perfect.targetLeft) }
            assertEquals(1000, perfect.finish().score)
            assertEquals(1.0, perfect.finish().performance)
            val awful = session(d = d)
            repeat(60) { awful.answer(!awful.targetLeft) }
            assertEquals(0, awful.finish().score)
            assertEquals(0.0, awful.finish().performance)
        }
    }
}
