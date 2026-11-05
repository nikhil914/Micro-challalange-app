package com.microchallenge.core

import com.microchallenge.core.model.ChallengeResult
import com.microchallenge.core.model.ChallengeType
import com.microchallenge.core.store.InMemoryStore
import com.microchallenge.core.store.ProgressRepository
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ProgressRepositoryTest {
    private fun result(type: ChallengeType, score: Int, perf: Double = 0.7) =
        ChallengeResult(type, score, perf, 2, 5, 1, 10_000)

    @Test fun recordsBestPlaysAverageAndStreak() {
        val repo = ProgressRepository(InMemoryStore())
        assertTrue(repo.record(result(ChallengeType.MATH_SPRINT, 400), today = 10))
        assertFalse(repo.record(result(ChallengeType.MATH_SPRINT, 300), today = 10))
        assertTrue(repo.record(result(ChallengeType.MATH_SPRINT, 900), today = 11))
        val st = repo.stats(ChallengeType.MATH_SPRINT)
        assertEquals(3, st.plays); assertEquals(900, st.best); assertEquals(533, st.average)
        assertEquals(2, repo.streak().current)
        assertEquals(3, repo.totalPlays())
        assertEquals(listOf(ChallengeType.MATH_SPRINT, ChallengeType.MATH_SPRINT, ChallengeType.MATH_SPRINT), repo.recentTypes())
        assertEquals(3, repo.history().size)
    }

    @Test fun ratingPersistsAndAdapts() {
        val repo = ProgressRepository(InMemoryStore())
        val start = repo.rating(ChallengeType.GO_NO_GO)
        repeat(5) { repo.record(result(ChallengeType.GO_NO_GO, 1000, 1.0), 1) }
        assertTrue(repo.rating(ChallengeType.GO_NO_GO) > start)
        assertTrue(repo.nextDifficulty(ChallengeType.GO_NO_GO) > 2)
    }

    @Test fun historyIsCapped() {
        val repo = ProgressRepository(InMemoryStore())
        repeat(ProgressRepository.HISTORY_LIMIT + 15) { repo.record(result(ChallengeType.ODD_ONE_OUT, it % 1000), 1) }
        assertEquals(ProgressRepository.HISTORY_LIMIT, repo.history().size)
    }

    @Test fun dailyRecordedOncePerDay() {
        val repo = ProgressRepository(InMemoryStore())
        assertNull(repo.dailyScore(5))
        repo.record(result(ChallengeType.COLOR_WORD, 640), 5, isDaily = true)
        repo.record(result(ChallengeType.COLOR_WORD, 990), 5, isDaily = true)
        assertEquals(640, repo.dailyScore(5))
        assertFalse(repo.isDailyDone(6))
    }
}
