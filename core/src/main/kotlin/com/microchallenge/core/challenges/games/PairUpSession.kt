package com.microchallenge.core.challenges.games

import com.microchallenge.core.challenges.ChallengeSession
import com.microchallenge.core.challenges.MAX_DIFFICULTY
import com.microchallenge.core.challenges.MIN_DIFFICULTY
import com.microchallenge.core.challenges.Scoring
import com.microchallenge.core.engine.Clock
import com.microchallenge.core.model.CHALLENGE_DURATION_MS
import com.microchallenge.core.model.ChallengeResult
import com.microchallenge.core.model.ChallengeType
import kotlin.random.Random

/**
 * Pair Up: a classic memory "concentration" board squeezed into 10 seconds.
 *
 * Every board starts face up for a short preview, then flips face down. Flip two cards:
 * a match stays up, a miss shows both cards briefly and flips them back. Clearing the
 * board deals a fresh one (with a fresh preview).
 *
 * Difficulty grows the board (3, 4, then 6 pairs) and shortens the preview.
 */
class PairUpSession(
    difficulty: Int,
    private val random: Random,
    private val clock: Clock,
) : ChallengeSession {

    /** PREVIEW: every card face up, taps ignored. PLAY: cards face down, taps live. */
    enum class Phase { PREVIEW, PLAY }

    /** Outcome of a [tap]. */
    enum class FlipResult { FLIPPED, MATCH, MISMATCH, BOARD_CLEARED, IGNORED }

    override val type: ChallengeType = ChallengeType.PAIR_UP
    override val difficulty: Int = difficulty.coerceIn(MIN_DIFFICULTY, MAX_DIFFICULTY)

    private val startMs: Long = clock.monotonicMs()
    private var boardStartMs: Long = startMs

    /** Pairs on each board. */
    val pairs: Int = when (this.difficulty) {
        1, 2, 3 -> 3
        4, 5, 6 -> 4
        else -> 6
    }

    val cardCount: Int get() = pairs * 2

    /** Cards per row: 3x2, 4x2 or 4x3. */
    val columns: Int = if (pairs == 3) 3 else 4

    val rows: Int get() = cardCount / columns

    /** How long each new board stays face up. */
    val previewMs: Long = PREVIEW_MAX_MS - (this.difficulty - 1) * PREVIEW_STEP_MS

    private var icons = IntArray(cardCount)
    private var matched = BooleanArray(cardCount)

    /** Index of the single face-up unmatched card, or -1. */
    var firstPick: Int = -1
        private set

    private var missA = -1
    private var missB = -1
    private var missAtMs = Long.MIN_VALUE

    /** Pairs found over the whole run. */
    var matches: Int = 0
        private set

    /** Wrong second flips over the whole run. */
    var mistakes: Int = 0
        private set

    /** Boards fully cleared. */
    var boardsCleared: Int = 0
        private set

    init {
        deal()
    }

    private fun now(): Long = clock.monotonicMs()
    private fun elapsed(): Long = now() - startMs

    override val isComplete: Boolean get() = false

    val isTimeUp: Boolean get() = elapsed() >= CHALLENGE_DURATION_MS

    fun phase(): Phase = if (now() - boardStartMs < previewMs) Phase.PREVIEW else Phase.PLAY

    /** Icon id (0 until [ICON_COUNT]) printed on card [index]. */
    fun iconAt(index: Int): Int = icons.getOrElse(index) { 0 }

    fun isMatched(index: Int): Boolean = matched.getOrElse(index) { false }

    private fun missShowing(): Boolean = missA >= 0 && now() - missAtMs < MISS_SHOW_MS

    /** True while the last wrong pair is still shown face up. */
    val isShowingMiss: Boolean get() = missShowing()

    /** Whether card [index] should be drawn face up right now. */
    fun isFaceUp(index: Int): Boolean {
        if (index !in 0 until cardCount) return false
        if (phase() == Phase.PREVIEW) return true
        if (matched[index] || index == firstPick) return true
        return missShowing() && (index == missA || index == missB)
    }

    /** Flip card [index]. */
    fun tap(index: Int): FlipResult {
        if (isTimeUp || index !in 0 until cardCount || phase() == Phase.PREVIEW) return FlipResult.IGNORED
        // Any tap hides a wrong pair still on show, so fast players are never held up.
        missA = -1
        missB = -1
        if (matched[index] || index == firstPick) return FlipResult.IGNORED
        val first = firstPick
        if (first < 0) {
            firstPick = index
            return FlipResult.FLIPPED
        }
        firstPick = -1
        if (icons[first] != icons[index]) {
            mistakes++
            missA = first
            missB = index
            missAtMs = now()
            return FlipResult.MISMATCH
        }
        matched[first] = true
        matched[index] = true
        matches++
        if (matched.all { it }) {
            boardsCleared++
            boardStartMs = now()
            deal()
            return FlipResult.BOARD_CLEARED
        }
        return FlipResult.MATCH
    }

    private fun deal() {
        val chosen = (0 until ICON_COUNT).shuffled(random).take(pairs)
        icons = (chosen + chosen).shuffled(random).toIntArray()
        matched = BooleanArray(cardCount)
        firstPick = -1
        missA = -1
        missB = -1
    }

    override fun finish(): ChallengeResult {
        val duration = elapsed().coerceIn(0L, CHALLENGE_DURATION_MS)
        // Bigger boards and shorter previews make each pair worth more.
        val good = GOOD_MATCHES_EASY - (difficulty - 1) * GOOD_MATCHES_STEP
        val units = matches - MISTAKE_WEIGHT * mistakes
        val ratio = (units / good).coerceAtLeast(0.0)
        val performance = (ratio * GOOD_PERFORMANCE).coerceIn(0.0, 1.0)
        val raw = ratio * POINTS_AT_GOOD * Scoring.difficultyBonus(difficulty)
        val detail = when {
            matches + mistakes == 0 -> "No pairs"
            mistakes == 0 -> "$matches ${if (matches == 1) "pair" else "pairs"} · no misses"
            else -> "$matches ${if (matches == 1) "pair" else "pairs"} · $mistakes ${if (mistakes == 1) "miss" else "misses"}"
        }
        return ChallengeResult(
            type = type,
            score = Scoring.clampScore(raw),
            performance = performance,
            difficulty = difficulty,
            correct = matches,
            mistakes = mistakes,
            durationMs = duration,
            detail = detail,
            timestampMs = clock.wallMs(),
        )
    }

    companion object {
        /** Distinct card faces the UI can draw. */
        const val ICON_COUNT = 8

        const val PREVIEW_MAX_MS = 1_700L
        const val PREVIEW_STEP_MS = 90L
        const val MISS_SHOW_MS = 650L

        /** Pairs in 10 s that count as a "good" (performance 0.7) run at level 1. */
        const val GOOD_MATCHES_EASY = 6.0
        const val GOOD_MATCHES_STEP = 0.2
        const val MISTAKE_WEIGHT = 0.25
        const val GOOD_PERFORMANCE = 0.7
        const val POINTS_AT_GOOD = 650.0
    }
}
