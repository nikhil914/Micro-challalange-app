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

/** One pop-up in a portal. Times are ms since the start of the run; [endMs] is exclusive. */
data class Spawn(
    val id: Int,
    val cell: Int,
    val startMs: Long,
    val endMs: Long,
    val isBomb: Boolean,
)

/**
 * Star Catcher: stars and bombs pop out of a 3x3 field of portals. Tap stars before they
 * vanish, never tap a bomb. A whack-the-target reflex game with a no-go twist.
 *
 * The whole 10 s schedule is generated up front from the seed. Difficulty shortens how long
 * each item stays up, spawns them faster, and adds more bombs.
 */
class StarCatcherSession(
    difficulty: Int,
    private val random: Random,
    private val clock: Clock,
) : ChallengeSession {

    /** Outcome of a [tap]. */
    enum class TapResult { CAUGHT, BOMB, EMPTY, IGNORED }

    override val type: ChallengeType = ChallengeType.STAR_CATCHER
    override val difficulty: Int = difficulty.coerceIn(MIN_DIFFICULTY, MAX_DIFFICULTY)

    private val startMs: Long = clock.monotonicMs()

    /** How long one item stays up. */
    val lifetimeMs: Long = LIFE_MAX_MS - (this.difficulty - 1) * LIFE_STEP_MS

    /** Gap between spawns. */
    val intervalMs: Long = GAP_MAX_MS - (this.difficulty - 1) * GAP_STEP_MS

    /** Chance that a spawn is a bomb. */
    val bombChance: Double = BOMB_MIN + (this.difficulty - 1) * BOMB_STEP

    /** Full schedule for the run, ordered by start time. */
    val spawns: List<Spawn> = buildSchedule()

    private val resolved = HashSet<Int>()
    private val reactionMs = ArrayList<Long>()

    var caught: Int = 0
        private set
    var bombsHit: Int = 0
        private set

    /** Id of the last item the player tapped, and when (ms since start), for the UI burst. */
    var lastHitId: Int = -1
        private set
    var lastHitAtMs: Long = -1L
        private set

    private fun elapsed(): Long = clock.monotonicMs() - startMs

    override val isComplete: Boolean get() = false

    val isTimeUp: Boolean get() = elapsed() >= CHALLENGE_DURATION_MS

    /** Ms since the start of the run, for the UI's grow/shrink animation. */
    fun elapsedMs(): Long = elapsed().coerceIn(0L, CHALLENGE_DURATION_MS)

    /** The item up in [cell] right now, or null. Tapped items disappear at once. */
    fun itemAt(cell: Int): Spawn? {
        val t = elapsed()
        return spawns.firstOrNull { it.cell == cell && t >= it.startMs && t < it.endMs && it.id !in resolved }
    }

    /** Stars whose window has closed without a catch. */
    val starsMissed: Int get() {
        val t = elapsed()
        return spawns.count { !it.isBomb && it.endMs <= t && it.id !in resolved }
    }

    /** Stars that have appeared so far (caught, missed, or still up). */
    val starsShown: Int get() {
        val t = elapsed().coerceAtMost(CHALLENGE_DURATION_MS)
        return spawns.count { !it.isBomb && it.startMs <= t }
    }

    fun tap(cell: Int): TapResult {
        if (isTimeUp || cell !in 0 until CELLS) return TapResult.IGNORED
        val item = itemAt(cell) ?: return TapResult.EMPTY
        resolved.add(item.id)
        lastHitId = item.id
        lastHitAtMs = elapsed()
        return if (item.isBomb) {
            bombsHit++
            TapResult.BOMB
        } else {
            caught++
            reactionMs.add(elapsed() - item.startMs)
            TapResult.CAUGHT
        }
    }

    private fun buildSchedule(): List<Spawn> {
        val out = ArrayList<Spawn>()
        val busyUntil = LongArray(CELLS)
        var t = FIRST_SPAWN_MS
        var id = 0
        var lastCell = -1
        while (t < CHALLENGE_DURATION_MS - MIN_VISIBLE_MS) {
            val free = (0 until CELLS).filter { busyUntil[it] <= t && it != lastCell }
            if (free.isNotEmpty()) {
                val cell = free[random.nextInt(free.size)]
                val end = (t + lifetimeMs).coerceAtMost(CHALLENGE_DURATION_MS)
                // The first item is always a star, so the rule is clear before the first bomb.
                val bomb = id > 0 && random.nextDouble() < bombChance
                out.add(Spawn(id++, cell, t, end, bomb))
                busyUntil[cell] = end + CELL_REST_MS
                lastCell = cell
            }
            // Jitter keeps the rhythm from becoming predictable.
            val jitter = random.nextLong(-intervalMs / 4, intervalMs / 4 + 1)
            t += intervalMs + jitter
        }
        // Every run has at least one bomb, so holding back always matters.
        if (out.size > 2 && out.none { it.isBomb }) {
            val i = 1 + random.nextInt(out.size - 1)
            out[i] = out[i].copy(isBomb = true)
        }
        return out
    }

    override fun finish(): ChallengeResult {
        val duration = elapsed().coerceIn(0L, CHALLENGE_DURATION_MS)
        val shown = starsShown
        val hitRate = if (shown == 0) 0.0 else caught.toDouble() / shown
        val quality = (hitRate - BOMB_PENALTY * bombsHit).coerceAtLeast(0.0)
        val performance = (quality * PERFORMANCE_SCALE).coerceIn(0.0, 1.0)
        val raw = quality * POINTS_FULL * Scoring.difficultyBonus(difficulty)
        val avg = if (reactionMs.isEmpty()) 0L else reactionMs.sum() / reactionMs.size
        val detail = when {
            caught + bombsHit == 0 -> "No catches"
            bombsHit == 0 -> "$caught/$shown stars · avg $avg ms"
            else -> "$caught/$shown stars · $bombsHit ${if (bombsHit == 1) "bomb" else "bombs"}"
        }
        return ChallengeResult(
            type = type,
            score = Scoring.clampScore(raw),
            performance = performance,
            difficulty = difficulty,
            correct = caught,
            mistakes = bombsHit + starsMissed,
            durationMs = duration,
            detail = detail,
            timestampMs = clock.wallMs(),
        )
    }

    companion object {
        const val CELLS = 9
        const val FIRST_SPAWN_MS = 350L
        const val MIN_VISIBLE_MS = 300L
        const val CELL_REST_MS = 120L

        const val LIFE_MAX_MS = 1_250L
        const val LIFE_STEP_MS = 70L
        const val GAP_MAX_MS = 620L
        const val GAP_STEP_MS = 30L
        const val BOMB_MIN = 0.18
        const val BOMB_STEP = 0.02

        /** Share of hit rate lost per bomb tapped. */
        const val BOMB_PENALTY = 0.2
        /** Catching ~82% of stars without a bomb is a "good" (0.7) run. */
        const val PERFORMANCE_SCALE = 0.85
        const val POINTS_FULL = 850.0
    }
}
