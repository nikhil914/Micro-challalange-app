package com.microchallenge.core.games

import com.microchallenge.core.challenges.games.RuleSwitchSession
import com.microchallenge.core.challenges.games.RuleSwitchSession.AnswerResult
import com.microchallenge.core.challenges.games.RuleSwitchSession.Rule
import com.microchallenge.core.challenges.games.RuleSwitchSession.Tint
import com.microchallenge.core.engine.FakeClock
import com.microchallenge.core.model.CHALLENGE_DURATION_MS
import com.microchallenge.core.model.ChallengeType
import kotlin.random.Random
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.Test

class RuleSwitchSessionTest {

    private fun session(d: Int = 1, seed: Int = 1, clock: FakeClock = FakeClock(0)) =
        RuleSwitchSession(d, Random(seed), clock)

    @Test
    fun `rules switch rate and cue time scale with difficulty`() {
        assertEquals(listOf(Rule.PARITY, Rule.MAGNITUDE), session(d = 5).rules)
        assertEquals(listOf(Rule.PARITY, Rule.MAGNITUDE, Rule.TINT), session(d = 6).rules)
        assertEquals(0.20, session(d = 1).switchProbability, 1e-9)
        assertEquals(0.60, session(d = 10).switchProbability, 1e-9)
        assertEquals(Long.MAX_VALUE, session(d = 7).cueMs)
        assertEquals(900L, session(d = 8).cueMs)
        assertEquals(600L, session(d = 10).cueMs)
    }

    @Test
    fun `rule prompts and button labels`() {
        assertEquals("ODD or EVEN?", Rule.PARITY.prompt)
        assertEquals("LOW or HIGH?", Rule.MAGNITUDE.prompt)
        assertEquals("CYAN or GOLD?", Rule.TINT.prompt)
        val s = session(d = 10, seed = 3)
        repeat(200) {
            assertEquals(s.rule.first, s.firstLabel)
            assertEquals(s.rule.second, s.secondLabel)
            s.answer(true)
        }
        assertTrue(Tint.CYAN.shape != Tint.GOLD.shape, "tints differ by shape, not just color")
    }

    @Test
    fun `stimuli are valid and never repeat back to back`() {
        for (d in 1..10) {
            val s = session(d = d, seed = d * 7)
            var prev = -1
            repeat(500) {
                assertTrue(s.stimulus in 1..9 && s.stimulus != 5, "stimulus ${s.stimulus}")
                assertTrue(s.stimulus != prev)
                assertTrue(s.rule in s.rules)
                if (d >= 6) assertTrue(s.tint != Tint.NONE) else assertEquals(Tint.NONE, s.tint)
                prev = s.stimulus
                s.answer(true)
            }
        }
    }

    @Test
    fun `correct button follows the rule`() {
        val s = session(d = 10, seed = 21)
        val seen = HashSet<Rule>()
        repeat(400) {
            seen += s.rule
            val expected = when (s.rule) {
                Rule.PARITY -> s.stimulus % 2 == 1
                Rule.MAGNITUDE -> s.stimulus < 5
                Rule.TINT -> s.tint == Tint.CYAN
            }
            assertEquals(expected, s.firstIsCorrect)
            assertEquals(AnswerResult.CORRECT, s.answer(expected))
        }
        assertEquals(Rule.entries.toSet(), seen)
        assertEquals(400, s.correct)
        assertEquals(0, s.mistakes)
    }

    private fun switchRate(d: Int): Double {
        val s = session(d = d, seed = 13)
        val n = 4000
        var switches = 0
        var prev = s.rule
        repeat(n) {
            s.answer(true)
            assertEquals(s.rule != prev, s.isSwitch)
            if (s.isSwitch) switches++
            prev = s.rule
        }
        return switches.toDouble() / n
    }

    @Test
    fun `observed switch rate follows probability`() {
        assertEquals(0.20, switchRate(1), 0.03)
        assertEquals(0.60, switchRate(10), 0.03)
    }

    @Test
    fun `cue hides after its window from level 8 and returns each trial`() {
        val clock = FakeClock(100)
        val s = session(d = 10, clock = clock)
        assertTrue(s.cueVisible)
        clock.advance(s.cueMs - 1)
        assertTrue(s.cueVisible)
        clock.advance(1)
        assertFalse(s.cueVisible)
        s.answer(true)
        assertTrue(s.cueVisible)
        val easy = session(d = 7, clock = clock)
        clock.advance(9_000)
        assertTrue(easy.cueVisible)
    }

    @Test
    fun `deterministic trials for same seed`() {
        fun trace(): List<Any> {
            val s = session(d = 9, seed = 99)
            return List(40) { Triple(s.rule, s.stimulus, s.tint).also { s.answer(s.firstIsCorrect) } }
        }
        assertEquals(trace(), trace())
    }

    @Test
    fun `answers count and advance trials`() {
        val s = session()
        assertEquals(AnswerResult.WRONG, s.answer(!s.firstIsCorrect))
        assertEquals(AnswerResult.CORRECT, s.answer(s.firstIsCorrect))
        assertEquals(AnswerResult.CORRECT, s.lastResult)
        assertEquals(1, s.correct)
        assertEquals(1, s.mistakes)
        assertEquals(2, s.trialIndex)
        assertFalse(s.isComplete)
    }

    @Test
    fun `good player around 11 correct scores well and near target`() {
        val clock = FakeClock(0)
        val s = session(d = 3, clock = clock)
        repeat(11) { clock.advance(800); s.answer(s.firstIsCorrect) }
        val r = s.finish()
        assertEquals(11, r.correct)
        assertTrue(r.score in 750..1000, "score ${r.score}")
        assertTrue(r.performance in 0.6..0.85, "perf ${r.performance}")
        assertEquals("11 correct", r.detail)
        assertEquals(ChallengeType.RULE_SWITCH, r.type)
        assertEquals(8_800L, r.durationMs)
    }

    @Test
    fun `mashing one button earns next to nothing`() {
        for (seed in 0 until 20) {
            val s = session(d = 4, seed = seed)
            repeat(40) { s.answer(true) }
            val r = s.finish()
            assertTrue(r.score <= 600, "seed $seed score ${r.score}")
            assertTrue(r.performance < 0.5, "seed $seed perf ${r.performance}")
        }
    }

    @Test
    fun `answers after time up are ignored and no input gives zero`() {
        val clock = FakeClock(0)
        val s = session(d = 8, clock = clock)
        clock.advance(CHALLENGE_DURATION_MS)
        assertTrue(s.isTimeUp)
        assertEquals(AnswerResult.IGNORED, s.answer(s.firstIsCorrect))
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
            repeat(60) { perfect.answer(perfect.firstIsCorrect) }
            assertEquals(1000, perfect.finish().score)
            assertEquals(1.0, perfect.finish().performance)
            val awful = session(d = d)
            repeat(60) { awful.answer(!awful.firstIsCorrect) }
            assertEquals(0, awful.finish().score)
            assertEquals(0.0, awful.finish().performance)
        }
    }
}
