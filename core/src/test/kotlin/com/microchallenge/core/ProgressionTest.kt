package com.microchallenge.core

import com.microchallenge.core.engine.Progression
import com.microchallenge.core.engine.Rank
import com.microchallenge.core.model.ChallengeResult
import com.microchallenge.core.model.ChallengeType
import com.microchallenge.core.store.InMemoryStore
import com.microchallenge.core.store.ProgressRepository
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ProgressionTest {
    @Test fun ranksByXp() {
        assertEquals(Rank.ROOKIE, Progression.rankFor(0))
        assertEquals(Rank.CHALLENGER, Progression.rankFor(500))
        assertEquals(Rank.LEGEND, Progression.rankFor(1_000_000))
        assertNull(Progression.nextRank(20_000))
        assertEquals(0.5f, Progression.rankProgress(1_000), 1e-4f)
    }

    @Test fun xpFormula() {
        val g = Progression.xpFor(score = 800, streakDays = 10, combo = 2, isBest = true)
        assertEquals(80, g.base); assertEquals(40, g.streakBonus); assertEquals(20, g.comboBonus)
        assertEquals(25, g.bestBonus); assertEquals(165, g.total)
        assertEquals(0, Progression.xpFor(0, 0, 0, false).total)
        assertEquals(50, Progression.xpFor(0, 0, 99, false).comboBonus)
    }

    @Test fun comboBuildsAndBreaks() {
        val repo = ProgressRepository(InMemoryStore())
        fun play(score: Int) = repo.record(ChallengeResult(ChallengeType.MATH_SPRINT, score, 0.7, 2, 1, 0, 10_000), 1)
        play(750); play(900)
        assertEquals(2, repo.combo())
        play(100)
        assertEquals(0, repo.combo())
        assertTrue(repo.xp() > 0)
    }
}
