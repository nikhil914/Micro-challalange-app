package com.microchallenge.core.games

import com.microchallenge.core.challenges.games.QuickCompareSession
import com.microchallenge.core.engine.FakeClock
import com.microchallenge.core.model.CHALLENGE_DURATION_MS
import kotlin.random.Random
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.Test

class QuickCompareSessionTest {

    private fun session(d: Int, seed: Long = 1L, clock: FakeClock = FakeClock()) =
        QuickCompareSession(d, Random(seed), clock)

    /** Parses a label the way a player reads it. */
    private fun valueOf(label: String): Double = when {
        "×" in label -> label.split("×").let { it[0].toDouble() * it[1].toDouble() }
        "÷" in label -> label.split("÷").let { it[0].toDouble() / it[1].toDouble() }
        "+" in label -> label.split("+").let { it[0].toDouble() + it[1].toDouble() }
        "−" in label -> label.split("−").let { it[0].toDouble() - it[1].toDouble() }
        "/" in label -> label.split("/").let { it[0].toDouble() / it[1].toDouble() }
        else -> label.toDouble()
    }

    @Test
    fun deterministicForSeed() {
        val a = session(8, 5); val b = session(8, 5)
        repeat(20) { assertEquals(a.current, b.current); a.choose(true); b.choose(true) }
    }

    @Test
    fun labelsMatchValuesAndNoTies() {
        for (d in 1..10) {
            val s = session(d, d * 17L)
            repeat(200) {
                val p = s.current
                assertEquals(valueOf(p.leftLabel), p.leftValue, 1e-9, "d=$d ${p.leftLabel}")
                assertEquals(valueOf(p.rightLabel), p.rightValue, 1e-9, "d=$d ${p.rightLabel}")
                assertNotEquals(p.leftValue, p.rightValue)
                assertEquals(p.leftValue > p.rightValue, p.leftIsLarger)
                if (p.fontTrick) assertEquals(!p.leftIsLarger, p.leftEmphasized) else assertNull(p.leftEmphasized)
                assertTrue(s.choose(p.leftIsLarger))
            }
        }
    }

    @Test
    fun difficultyScaling() {
        fun pairs(d: Int) = session(d, 3).let { s -> List(200) { s.current.also { s.choose(true) } } }
        val easy = pairs(1)
        assertTrue(easy.all { it.leftDen == 1L && it.rightDen == 1L && !it.fontTrick })
        assertTrue(easy.all { maxOf(it.leftValue, it.rightValue) / minOf(it.leftValue, it.rightValue) >= 1.4 })
        assertTrue(pairs(4).all { kotlin.math.abs(it.leftValue - it.rightValue) <= 2.0 })
        assertTrue(pairs(5).any { it.leftDen > 1 || it.rightDen > 1 })
        val hard = pairs(10)
        assertTrue(hard.any { it.fontTrick })
        assertTrue(hard.all { it.leftLabel.any { c -> c in "×÷+−" } || it.rightLabel.any { c -> c in "×÷+−" } })
    }

    @Test
    fun noInputFinishGivesZero() {
        val clock = FakeClock()
        val s = session(5, clock = clock)
        clock.advance(CHALLENGE_DURATION_MS)
        val r = s.finish()
        assertEquals(0, r.score); assertEquals(0.0, r.performance)
    }

    @Test
    fun guessingScoresNothingAndBoundsHold() {
        val wrong = session(6)
        repeat(30) { wrong.choose(!wrong.current.leftIsLarger) }
        assertEquals(30, wrong.mistakes)
        assertEquals(0, wrong.finish().score)
        val perfect = session(10)
        repeat(50) { perfect.choose(perfect.current.leftIsLarger) }
        assertEquals(1000, perfect.finish().score)
        val mid = session(5)
        repeat(10) { mid.choose(mid.current.leftIsLarger) }
        val r = mid.finish()
        assertEquals(720, r.score)
        assertTrue(r.performance in 0.7..0.9)
    }

    @Test
    fun ignoredAfterTimeUp() {
        val clock = FakeClock()
        val s = session(2, clock = clock)
        clock.advance(CHALLENGE_DURATION_MS)
        assertFalse(s.choose(s.current.leftIsLarger))
        assertEquals(0, s.correct)
    }
}
