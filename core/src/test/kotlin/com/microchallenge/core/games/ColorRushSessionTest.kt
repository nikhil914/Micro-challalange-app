package com.microchallenge.core.games

import com.microchallenge.core.challenges.games.ColorRushSession
import com.microchallenge.core.challenges.games.RushTapOutcome
import com.microchallenge.core.engine.FakeClock
import com.microchallenge.core.model.CHALLENGE_DURATION_MS
import kotlin.random.Random
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.Test

class ColorRushSessionTest {

    private fun session(d: Int, seed: Long = 7L, clock: FakeClock = FakeClock()) =
        ColorRushSession(d, Random(seed), clock)

    @Test
    fun scheduleIsDeterministicAndInBounds() {
        val a = session(5)
        val b = session(5)
        assertEquals(a.bubbles, b.bubbles)
        assertEquals(a.phases, b.phases)
        assertTrue(a.bubbles.isNotEmpty())
        for (bub in a.bubbles) {
            assertTrue(bub.colorIndex in 0 until a.colorCount)
            assertTrue(bub.x - bub.radius >= 0f && bub.x + bub.radius <= 1f)
            assertTrue(bub.despawnMs <= CHALLENGE_DURATION_MS)
        }
        assertTrue(a.totalTargets > 0)
    }

    @Test
    fun consecutivePhasesHaveDifferentTargets() {
        for (seed in 1L..20L) {
            val p = session(8, seed).phases
            for (i in 1 until p.size) assertTrue(p[i].colorIndex != p[i - 1].colorIndex)
        }
    }

    @Test
    fun perfectPlayScoresHighAndBuildsCombo() {
        val clock = FakeClock()
        val s = session(4, clock = clock)
        for (b in s.bubbles.filter { s.isTarget(it) }) {
            val phaseEnd = s.phaseAt(b.spawnMs).endMs
            clock.mono = minOf(b.spawnMs + 100, phaseEnd - 1)
            assertEquals(RushTapOutcome.HIT, s.tap(b.id))
        }
        clock.mono = CHALLENGE_DURATION_MS
        val r = s.finish()
        assertEquals(s.totalTargets, r.correct)
        assertEquals(0, r.mistakes)
        assertTrue(r.score > 700, "score ${r.score}")
        assertTrue(s.bestCombo >= 3)
    }

    @Test
    fun wrongColorBreaksComboAndCounts() {
        val clock = FakeClock()
        val s = session(3, clock = clock)
        val wrong = s.bubbles.first { !s.isTarget(it) }
        clock.mono = minOf(wrong.spawnMs + 50, s.phaseAt(wrong.spawnMs).endMs - 1)
        assertEquals(RushTapOutcome.WRONG_COLOR, s.tap(wrong.id))
        assertEquals(1, s.wrongTaps)
        assertEquals(0, s.combo)
        assertEquals(RushTapOutcome.IGNORED, s.tap(wrong.id))
    }

    @Test
    fun noTapsScoresZero() {
        val clock = FakeClock()
        val s = session(1, clock = clock)
        clock.mono = CHALLENGE_DURATION_MS
        val r = s.finish()
        assertEquals(0, r.score)
        assertEquals(s.totalTargets, r.mistakes)
        assertTrue(s.isComplete)
    }
}
