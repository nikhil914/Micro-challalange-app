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
 * Twin Scenes (spot the difference): two scenes of scattered stickers are shown one above
 * the other. They are identical except for one sticker. The player taps that sticker in
 * either scene. Each find starts a fresh pair of scenes.
 *
 * Difficulty adds stickers and unlocks subtler kinds of difference
 * (missing / swapped icon first, then recolor, then size and rotation).
 */
class TwinScenesSession(
    difficulty: Int,
    private val random: Random,
    private val clock: Clock,
) : ChallengeSession {

    enum class Phase { PLAY, FOUND, DONE }

    enum class TapResult { FOUND, WRONG, IGNORED }

    enum class DiffKind { MISSING, ICON, COLOR, SIZE, ROTATION }

    /**
     * One sticker. [x], [y] are the centre in 0..1 of the scene; [size] is the radius as a
     * fraction of the scene height. [icon] and [tint] index the app's 8-icon art set.
     */
    data class Sticker(
        val icon: Int,
        val tint: Int,
        val x: Float,
        val y: Float,
        val size: Float,
        val rotation: Float,
    )

    /**
     * One pair of scenes. [top] and [bottom] have the same length and order; the sticker
     * at [diffIndex] differs. For [DiffKind.MISSING], `bottom[diffIndex]` is null.
     */
    data class Round(
        val top: List<Sticker?>,
        val bottom: List<Sticker?>,
        val diffIndex: Int,
        val kind: DiffKind,
        /** Seed for the decorative background, so it stays stable for the round. */
        val backdropSeed: Int,
        /** Which scene holds the "changed" version; the UI swaps scenes when true. */
        val swapped: Boolean,
    )

    override val type: ChallengeType = ChallengeType.TWIN_SCENES
    override val difficulty: Int = difficulty.coerceIn(MIN_DIFFICULTY, MAX_DIFFICULTY)

    private val startMs: Long = clock.monotonicMs()

    var round: Round = newRound()
        private set

    /** Session-relative time the current round started. */
    var roundStartMs: Long = 0L
        private set

    /** Session-relative time the difference was found, or -1 while searching. */
    private var foundAtMs: Long = -1L

    var found: Int = 0
        private set
    var mistakes: Int = 0
        private set

    /** Wrong taps in the current round, so the UI can mark them. */
    val wrongTaps: MutableList<Int> = mutableListOf()

    private var totalFindMs: Long = 0L
    private var ended = false

    private fun elapsed(): Long = clock.monotonicMs() - startMs

    /** Session-relative ms, for UI animation timing. */
    fun nowMs(): Long = elapsed()

    private fun stickerCount(): Int = 5 + (difficulty + 1) / 2 // 6..10

    private fun allowedKinds(): List<DiffKind> = buildList {
        add(DiffKind.MISSING)
        add(DiffKind.ICON)
        if (difficulty >= 3) add(DiffKind.COLOR)
        if (difficulty >= 5) add(DiffKind.SIZE)
        if (difficulty >= 7) add(DiffKind.ROTATION)
    }

    private fun newRound(): Round {
        val count = stickerCount()
        // Jittered 5x3 grid of cells keeps stickers apart and large enough to tap.
        val cells = (0 until COLS * ROWS).shuffled(random).take(count)
        val stickers = cells.map { cell ->
            val col = cell % COLS
            val row = cell / COLS
            val x = (col + 0.5f + (random.nextFloat() - 0.5f) * 0.35f) / COLS
            val y = (row + 0.5f + (random.nextFloat() - 0.5f) * 0.3f) / ROWS
            val icon = random.nextInt(ICONS)
            Sticker(
                icon = icon,
                tint = icon,
                x = x,
                y = y,
                size = 0.11f + random.nextFloat() * 0.04f,
                rotation = (random.nextFloat() - 0.5f) * 40f,
            )
        }
        val diffIndex = random.nextInt(count)
        val kind = allowedKinds().random(random)
        val original = stickers[diffIndex]
        val changed: Sticker? = when (kind) {
            DiffKind.MISSING -> null
            DiffKind.ICON -> {
                val icon = (original.icon + 1 + random.nextInt(ICONS - 1)) % ICONS
                original.copy(icon = icon, tint = icon)
            }
            DiffKind.COLOR -> original.copy(tint = (original.tint + 1 + random.nextInt(ICONS - 1)) % ICONS)
            DiffKind.SIZE -> {
                // Bigger change when easier.
                val factor = if (random.nextBoolean()) 1.55f - difficulty * 0.02f else 0.6f + difficulty * 0.015f
                original.copy(size = original.size * factor)
            }
            DiffKind.ROTATION -> original.copy(rotation = original.rotation + if (random.nextBoolean()) 90f else -90f)
        }
        val bottom = stickers.toMutableList<Sticker?>().also { it[diffIndex] = changed }
        return Round(
            top = stickers,
            bottom = bottom,
            diffIndex = diffIndex,
            kind = kind,
            backdropSeed = random.nextInt(),
            swapped = random.nextBoolean(),
        )
    }

    private fun advanceIfNeeded() {
        if (foundAtMs >= 0 && elapsed() - foundAtMs >= FOUND_MS) {
            roundStartMs = foundAtMs + FOUND_MS
            foundAtMs = -1L
            wrongTaps.clear()
            round = newRound()
        }
    }

    val phase: Phase
        get() {
            if (ended || elapsed() >= CHALLENGE_DURATION_MS) return Phase.DONE
            advanceIfNeeded()
            return if (foundAtMs >= 0) Phase.FOUND else Phase.PLAY
        }

    /** 0..1 progress of the found celebration, for the hit effect. */
    fun foundProgress(): Float =
        if (foundAtMs < 0) 0f else ((elapsed() - foundAtMs).toFloat() / FOUND_MS).coerceIn(0f, 1f)

    override val isComplete: Boolean get() = ended

    /** Player tapped the sticker slot [index] (same index in both scenes). */
    fun tap(index: Int): TapResult {
        if (phase != Phase.PLAY || index !in round.top.indices) return TapResult.IGNORED
        if (index in wrongTaps) return TapResult.IGNORED
        return if (index == round.diffIndex) {
            foundAtMs = elapsed()
            totalFindMs += foundAtMs - roundStartMs
            found++
            TapResult.FOUND
        } else {
            wrongTaps += index
            mistakes++
            TapResult.WRONG
        }
    }

    override fun finish(): ChallengeResult {
        ended = true
        val duration = elapsed().coerceIn(0L, CHALLENGE_DURATION_MS)
        val avg = if (found == 0) 0L else totalFindMs / found
        // Faster finds earn a bonus of up to 60 points each.
        val speedBonus = if (found == 0) 0.0 else found * (60.0 * (1.0 - avg / 4000.0).coerceIn(0.0, 1.0))
        val raw = (found * 150.0 + speedBonus - mistakes * 35.0) * Scoring.difficultyBonus(difficulty)
        val acc = Scoring.accuracy(found, mistakes)
        val performance = (0.5 * acc + 0.5 * (found / 4.0).coerceAtMost(1.0)).coerceIn(0.0, 1.0)
        val detail = if (found == 0) "None found" else "$found found · avg ${avg} ms"
        return ChallengeResult(
            type = type,
            score = Scoring.clampScore(raw),
            performance = if (found == 0) 0.0 else performance,
            difficulty = difficulty,
            correct = found,
            mistakes = mistakes,
            durationMs = duration,
            detail = detail,
        )
    }

    companion object {
        const val COLS = 5
        const val ROWS = 3

        /** Size of the app's icon set (GameArt). */
        const val ICONS = 8

        /** How long the found celebration plays before the next scenes, ms. */
        const val FOUND_MS = 700L
    }
}
