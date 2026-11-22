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
 * Neon Dash (lane-switching dodge runner): the player rides one of three lanes while
 * barriers rush in from the horizon. Switch lanes to dodge barriers and grab energy orbs.
 * Three crashes end the run early.
 *
 * The whole course is generated up front from [random], so a seed always gives the same
 * run. Each item reaches the player line at `spawnMs + travelMs`; at that moment it is
 * resolved against the lane the player is in. Speed and barrier density scale with
 * difficulty and ramp up during the run.
 */
class NeonDashSession(
    difficulty: Int,
    private val random: Random,
    private val clock: Clock,
) : ChallengeSession {

    enum class Kind { BARRIER, ORB }

    enum class Outcome { PENDING, DODGED, CRASHED, COLLECTED, MISSED }

    /** One barrier or orb. [spawnMs] is session-relative; it sits at the horizon then. */
    data class Item(val id: Int, val lane: Int, val kind: Kind, val spawnMs: Long, val travelMs: Long) {
        val arriveMs: Long get() = spawnMs + travelMs
    }

    override val type: ChallengeType = ChallengeType.NEON_DASH
    override val difficulty: Int = difficulty.coerceIn(MIN_DIFFICULTY, MAX_DIFFICULTY)

    private val startMs: Long = clock.monotonicMs()

    val items: List<Item> = generateCourse()

    private val outcomes: Array<Outcome> = Array(items.size) { Outcome.PENDING }

    /** Items sorted by arrival, and the index of the next one to resolve. */
    private val byArrival: List<Item> = items.sortedBy { it.arriveMs }
    private var nextToResolve = 0

    var lane: Int = 1
        private set

    var lives: Int = MAX_LIVES
        private set
    var dodged: Int = 0
        private set
    var crashes: Int = 0
        private set
    var orbs: Int = 0
        private set
    var moves: Int = 0
        private set

    /** Lane and session time of the last crash / orb pickup, for effects. -1 when none yet. */
    var lastCrashLane: Int = -1
        private set
    var lastCrashMs: Long = -1L
        private set
    var lastOrbLane: Int = -1
        private set
    var lastOrbMs: Long = -1L
        private set

    private var ended = false

    /** Session-relative time, capped at the challenge length. */
    fun nowMs(): Long = (clock.monotonicMs() - startMs).coerceIn(0L, CHALLENGE_DURATION_MS)

    private fun generateCourse(): List<Item> {
        val list = mutableListOf<Item>()
        var t = FIRST_WAVE_MS
        var id = 0
        val baseTravel = (1700L - difficulty * 70L).coerceAtLeast(900L)
        val baseGap = (950L - difficulty * 40L).coerceAtLeast(480L)
        val doubleChance = 0.2 + difficulty * 0.04
        var lastBlocked = emptySet<Int>()
        while (t < CHALLENGE_DURATION_MS) {
            val ramp = t.toDouble() / CHALLENGE_DURATION_MS
            val travel = (baseTravel * (1.0 - 0.3 * ramp)).toLong()
            val blockCount = if (random.nextDouble() < doubleChance) 2 else 1
            var blocked = (0 until LANES).shuffled(random).take(blockCount).toSet()
            // Never repeat a double wall exactly: keeps the course readable.
            if (blockCount == 2 && blocked == lastBlocked) blocked = (0 until LANES).toSet() - blocked + blocked.first()
            for (l in blocked.sorted()) list += Item(id++, l, Kind.BARRIER, t, travel)
            val free = (0 until LANES).filter { it !in blocked }
            if (random.nextDouble() < 0.55) {
                list += Item(id++, free[random.nextInt(free.size)], Kind.ORB, t, travel)
            }
            lastBlocked = blocked
            t += (baseGap * (1.0 - 0.25 * ramp)).toLong() + random.nextLong(0L, 160L)
        }
        // Only items that can reach the player before time runs out.
        return list.filter { it.arriveMs <= CHALLENGE_DURATION_MS }
    }

    /** Resolves every item that has reached the player line by now. */
    fun step() {
        if (ended) return
        val now = nowMs()
        while (nextToResolve < byArrival.size && byArrival[nextToResolve].arriveMs <= now) {
            if (lives <= 0) break
            val item = byArrival[nextToResolve++]
            val hit = item.lane == lane
            outcomes[item.id] = when (item.kind) {
                Kind.BARRIER -> if (hit) {
                    lives--
                    crashes++
                    lastCrashLane = item.lane
                    lastCrashMs = item.arriveMs
                    Outcome.CRASHED
                } else {
                    dodged++
                    Outcome.DODGED
                }
                Kind.ORB -> if (hit) {
                    orbs++
                    lastOrbLane = item.lane
                    lastOrbMs = item.arriveMs
                    Outcome.COLLECTED
                } else {
                    Outcome.MISSED
                }
            }
        }
    }

    fun outcomeOf(item: Item): Outcome = outcomes[item.id]

    /** 0 at the horizon, 1 at the player line, >1 once past the player. */
    fun progressOf(item: Item, now: Long = nowMs()): Float =
        (now - item.spawnMs).toFloat() / item.travelMs

    /** Current scroll speed relative to the start of an easy run, for parallax. */
    fun speedFactor(now: Long = nowMs()): Float {
        val ramp = now.toFloat() / CHALLENGE_DURATION_MS
        return (1f + difficulty * 0.06f) * (1f + 0.4f * ramp)
    }

    /** Moves one lane left (-1) or right (+1). Returns true if the player moved. */
    fun move(direction: Int): Boolean {
        step()
        if (isComplete || nowMs() >= CHALLENGE_DURATION_MS) return false
        val target = (lane + direction.coerceIn(-1, 1)).coerceIn(0, LANES - 1)
        if (target == lane) return false
        lane = target
        moves++
        return true
    }

    override val isComplete: Boolean get() = ended || lives <= 0

    override fun finish(): ChallengeResult {
        step()
        ended = true
        val duration = nowMs()
        val raw = (dodged * 28.0 + orbs * 55.0 - crashes * 60.0 + duration / 100.0) *
            Scoring.difficultyBonus(difficulty)
        val barriersFaced = dodged + crashes
        val dodgeRate = if (barriersFaced == 0) 0.0 else dodged.toDouble() / barriersFaced
        val orbsSeen = items.count { it.kind == Kind.ORB && outcomes[it.id] != Outcome.PENDING }
        val orbRate = if (orbsSeen == 0) 0.0 else orbs.toDouble() / orbsSeen
        val survival = duration.toDouble() / CHALLENGE_DURATION_MS
        val performance = (0.55 * dodgeRate + 0.2 * orbRate + 0.25 * survival).coerceIn(0.0, 1.0)
        val detail = when {
            barriersFaced == 0 -> "No barriers reached"
            lives <= 0 -> "Crashed out · $dodged dodged · $orbs orbs"
            else -> "$dodged dodged · $orbs orbs · $crashes crashes"
        }
        return ChallengeResult(
            type = type,
            score = Scoring.clampScore(raw),
            performance = performance,
            difficulty = difficulty,
            correct = dodged + orbs,
            mistakes = crashes,
            durationMs = duration,
            detail = detail,
        )
    }

    companion object {
        const val LANES = 3
        const val MAX_LIVES = 3

        /** First wave leaves the horizon this long after start, ms. */
        const val FIRST_WAVE_MS = 350L
    }
}
