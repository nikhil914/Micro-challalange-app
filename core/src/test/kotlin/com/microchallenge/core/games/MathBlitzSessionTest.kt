package com.microchallenge.core.games

import com.microchallenge.core.challenges.games.MathBlitzSession
import com.microchallenge.core.challenges.games.MathBlitzSession.TapResult
import com.microchallenge.core.engine.FakeClock
import com.microchallenge.core.model.CHALLENGE_DURATION_MS
import kotlin.random.Random
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.Test

class MathBlitzSessionTest {

    /** Evaluates the session's equation strings (× and ÷ before + and −). */
    private fun eval(expr: String): Int {
        val t = expr.split(" ")
        val terms = mutableListOf(t[0].toInt())
        val ops = mutableListOf<String>()
        var i = 1
        while (i < t.size) {
            val op = t[i]; val n = t[i + 1].toInt()
            when (op) {
                "×" -> terms[terms.lastIndex] = terms.last() * n
                "÷" -> { assertEquals(0, terms.last() % n, expr); terms[terms.lastIndex] = terms.last() / n }
                else -> { ops += op; terms += n }
            }
            i += 2
        }
        var acc = terms[0]
        ops.forEachIndexed { k, op -> acc = if (op == "+") acc + terms[k + 1] else acc - terms[k + 1] }
        return acc
    }

    @Test
    fun expressionsEvaluateToTheirValue() {
        for (d in 1..10) {
            val clock = FakeClock()
            val s = MathBlitzSession(d, Random(d), clock)
            repeat(40) { clock.advance(200); s.bubbles().forEach { assertEquals(it.value, eval(it.expression), it.expression) } }
        }
    }

    @Test
    fun deterministicForSeed() {
        val ca = FakeClock(); val cb = FakeClock()
        val a = MathBlitzSession(6, Random(11), ca)
        val b = MathBlitzSession(6, Random(11), cb)
        ca.advance(3_000); cb.advance(3_000)
        assertEquals(a.target, b.target)
        assertEquals(a.bubbles(), b.bubbles())
    }

    @Test
    fun matchesAppearRegularly() {
        val clock = FakeClock()
        val s = MathBlitzSession(5, Random(2), clock)
        clock.advance(2_500)
        assertTrue(s.bubbles().any { it.value == s.target })
    }

    @Test
    fun popsBuildComboAndClearWave() {
        val clock = FakeClock()
        val s = MathBlitzSession(3, Random(5), clock)
        val firstTarget = s.target
        var hits = 0
        var result = TapResult.IGNORED
        while (hits < MathBlitzSession.WAVE_SIZE) {
            clock.advance(50)
            val match = s.bubbles().firstOrNull { it.value == s.target } ?: continue
            result = s.tap(match.id)
            hits++
        }
        assertEquals(TapResult.WAVE_CLEARED, result)
        assertEquals(3, s.combo)
        assertEquals(2, s.wave)
        assertTrue(s.target != firstTarget)
        assertTrue(s.bubbles().isEmpty())
    }

    @Test
    fun wrongTapResetsCombo() {
        val clock = FakeClock()
        val s = MathBlitzSession(4, Random(8), clock)
        var tapped = false
        while (!tapped) {
            clock.advance(50)
            val decoy = s.bubbles().firstOrNull { it.value != s.target } ?: continue
            assertEquals(TapResult.WRONG, s.tap(decoy.id))
            tapped = true
        }
        assertEquals(0, s.combo)
        assertEquals(1, s.mistakes)
        assertEquals(TapResult.IGNORED, s.tap(-1))
    }

    @Test
    fun landedMatchCountsAsMiss() {
        val clock = FakeClock()
        val s = MathBlitzSession(1, Random(3), clock)
        clock.advance(CHALLENGE_DURATION_MS - 1)
        s.bubbles()
        assertTrue(s.misses > 0)
        val r = s.finish()
        assertTrue(r.score in 0..1000)
        assertEquals(TapResult.IGNORED, s.tap(0))
    }
}
