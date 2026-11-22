package com.microchallenge.core.games

import com.microchallenge.core.challenges.games.WordScrambleSession
import com.microchallenge.core.challenges.games.WordScrambleSession.Phase
import com.microchallenge.core.challenges.games.WordScrambleSession.TapResult
import com.microchallenge.core.challenges.games.WordList
import com.microchallenge.core.engine.FakeClock
import com.microchallenge.core.model.CHALLENGE_DURATION_MS
import kotlin.random.Random
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import org.junit.Test

class WordScrambleSessionTest {

    /** Taps tiles so they spell the target word. */
    private fun WordScrambleSession.solve(): TapResult {
        phase // Settle any pending feedback so [round] is current.
        val used = mutableSetOf<Int>()
        var last = TapResult.IGNORED
        for (ch in round.target) {
            val i = round.letters.indices.first { it !in used && round.letters[it] == ch }
            used += i
            last = tapLetter(i)
        }
        return last
    }

    @Test
    fun deterministicForSeed() {
        val a = WordScrambleSession(5, Random(7), FakeClock())
        val b = WordScrambleSession(5, Random(7), FakeClock())
        assertEquals(a.round, b.round)
    }

    @Test
    fun scrambleIsNeverAlreadySolved() {
        for (seed in 0 until 200) {
            val s = WordScrambleSession(seed % 10 + 1, Random(seed), FakeClock())
            val shown = String(s.round.letters.toCharArray())
            assertFalse(WordList.isAnagramOf(shown, s.round.target), "seed $seed shows $shown")
            assertEquals(s.round.target.toList().sorted(), s.round.letters.sorted())
        }
    }

    @Test
    fun lengthScalesWithDifficulty() {
        assertEquals(4, WordScrambleSession(1, Random(1), FakeClock()).round.target.length)
        assertEquals(5, WordScrambleSession(5, Random(1), FakeClock()).round.target.length)
        assertEquals(6, WordScrambleSession(10, Random(1), FakeClock()).round.target.length)
    }

    @Test
    fun solvingDealsNextWord() {
        val clock = FakeClock()
        val s = WordScrambleSession(3, Random(2), clock)
        val first = s.round
        assertEquals(TapResult.SOLVED, s.solve())
        assertEquals(Phase.SOLVED, s.phase)
        assertEquals(TapResult.IGNORED, s.tapLetter(0))
        clock.advance(WordScrambleSession.SOLVED_MS)
        assertEquals(Phase.PLAY, s.phase)
        assertNotEquals(first.target, s.round.target)
        assertTrue(s.picked.isEmpty())
        assertEquals(1, s.wordsSolved)
    }

    @Test
    fun wrongSpellingClearsAndCounts() {
        val clock = FakeClock()
        val s = WordScrambleSession(1, Random(5), clock)
        // The shown order is never a valid answer, so tapping in order is wrong.
        val results = s.round.letters.indices.map { s.tapLetter(it) }
        assertEquals(TapResult.WRONG, results.last())
        assertEquals(Phase.WRONG, s.phase)
        clock.advance(WordScrambleSession.WRONG_MS)
        assertEquals(Phase.PLAY, s.phase)
        assertTrue(s.picked.isEmpty())
        assertEquals(1, s.finish().mistakes)
    }

    @Test
    fun slotTapReturnsLetters() {
        val s = WordScrambleSession(5, Random(3), FakeClock())
        s.tapLetter(0); s.tapLetter(1); s.tapLetter(2)
        assertEquals(TapResult.IGNORED, s.tapLetter(1))
        assertTrue(s.tapSlot(1))
        assertEquals(listOf(0), s.picked)
    }

    @Test
    fun skipCountsMistake() {
        val s = WordScrambleSession(5, Random(3), FakeClock())
        val first = s.round.target
        assertTrue(s.skip())
        assertNotEquals(first, s.round.target)
        assertEquals(1, s.finish().mistakes)
    }

    @Test
    fun noInputScoresZeroAndScoreInRange() {
        val clock = FakeClock()
        val idle = WordScrambleSession(4, Random(1), clock)
        clock.advance(CHALLENGE_DURATION_MS)
        assertEquals(Phase.DONE, idle.phase)
        assertEquals(0, idle.finish().score)

        val c2 = FakeClock()
        val s = WordScrambleSession(10, Random(9), c2)
        repeat(3) { s.solve(); c2.advance(WordScrambleSession.SOLVED_MS) }
        val r = s.finish()
        assertTrue(r.score in 1..1000)
        assertEquals(3, r.correct)
    }
}
