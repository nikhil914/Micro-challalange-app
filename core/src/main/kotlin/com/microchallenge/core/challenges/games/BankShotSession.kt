package com.microchallenge.core.challenges.games

import com.microchallenge.core.challenges.ChallengeSession
import com.microchallenge.core.challenges.MAX_DIFFICULTY
import com.microchallenge.core.challenges.MIN_DIFFICULTY
import com.microchallenge.core.challenges.Scoring
import com.microchallenge.core.engine.Clock
import com.microchallenge.core.model.CHALLENGE_DURATION_MS
import com.microchallenge.core.model.ChallengeResult
import com.microchallenge.core.model.ChallengeType
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

/**
 * Bank Shot: a mini-golf physics puzzle. Drag back from the ball to aim, release to shoot.
 * The ball rolls with friction and bounces off walls and blocks. Sink it in the hole to get
 * the next course. Fewer shots per hole score more.
 *
 * The world is [WIDTH] x [HEIGHT] units, origin top-left. Physics runs in fixed steps of
 * [STEP_S] seconds, advanced lazily from [clock] whenever state is read, so the session stays
 * a pure state machine and replays the same for the same seed and clock.
 *
 * Difficulty adds blocks (1 at level 1 up to 5 at level 10) and some of them drift side to side
 * from level 6.
 */
class BankShotSession(
    difficulty: Int,
    private val random: Random,
    private val clock: Clock,
) : ChallengeSession {

    /** Axis-aligned block. [dx] is its sideways drift amplitude (0 = static). */
    data class Block(val baseX: Float, val y: Float, val w: Float, val h: Float, val dx: Float, val phase: Float) {
        fun x(timeS: Float): Float =
            if (dx == 0f) baseX else baseX + dx * kotlin.math.sin(timeS * DRIFT_SPEED + phase)
    }

    /** Outcome of a [shoot]. */
    enum class ShotResult { SHOT, IGNORED }

    override val type: ChallengeType = ChallengeType.BANK_SHOT
    override val difficulty: Int = difficulty.coerceIn(MIN_DIFFICULTY, MAX_DIFFICULTY)

    private val startMs: Long = clock.monotonicMs()

    val blockCount: Int = min(5, 1 + (this.difficulty - 1) / 2)
    private val moving: Boolean = this.difficulty >= 6

    var ballX: Float = 0f; private set
    var ballY: Float = 0f; private set
    private var vx = 0f
    private var vy = 0f
    var holeX: Float = 0f; private set
    var holeY: Float = 0f; private set
    var blocks: List<Block> = emptyList(); private set

    /** Shots on the current hole. */
    var shots: Int = 0; private set
    var totalShots: Int = 0; private set
    var holesSunk: Int = 0; private set
    /** Wall/block bounces since the last shot; the screen uses it for a tick. */
    var bounces: Int = 0; private set
    /** Elapsed ms when the last hole was sunk, or -1. */
    var lastSunkMs: Long = -1; private set

    private var simTimeS = 0f
    private var value = 0.0

    init {
        deal()
    }

    private fun elapsed(): Long = clock.monotonicMs() - startMs

    override val isComplete: Boolean get() = false
    val isTimeUp: Boolean get() = elapsed() >= CHALLENGE_DURATION_MS

    /** World time in seconds the physics has reached. */
    val timeS: Float get() { advance(); return simTimeS }

    val isRolling: Boolean get() { advance(); return vx != 0f || vy != 0f }

    private fun deal() {
        ballX = WIDTH / 2f
        ballY = HEIGHT - 0.15f
        vx = 0f; vy = 0f
        holeX = 0.15f + random.nextFloat() * (WIDTH - 0.3f)
        holeY = 0.12f + random.nextFloat() * 0.25f
        val list = ArrayList<Block>()
        var tries = 0
        while (list.size < blockCount && tries < 200) {
            tries++
            val w = 0.18f + random.nextFloat() * 0.3f
            val h = 0.05f + random.nextFloat() * 0.05f
            val x = random.nextFloat() * (WIDTH - w)
            val y = 0.35f + random.nextFloat() * (HEIGHT - 0.75f)
            val drift = if (moving && random.nextBoolean()) 0.08f + random.nextFloat() * 0.08f else 0f
            val b = Block(x.coerceIn(drift, WIDTH - w - drift), y, w, h, drift, random.nextFloat() * 6.28f)
            val clearOfBall = !overlaps(b, ballX, ballY, BALL_R + 0.08f + drift)
            val clearOfHole = !overlaps(b, holeX, holeY, HOLE_R + 0.08f + drift)
            val clearOfOthers = list.none { o -> b.y < o.y + o.h + 0.06f && o.y < b.y + b.h + 0.06f }
            if (clearOfBall && clearOfHole && clearOfOthers) list.add(b)
        }
        blocks = list
        shots = 0
    }

    private fun overlaps(b: Block, cx: Float, cy: Float, r: Float): Boolean {
        val nx = cx.coerceIn(b.baseX, b.baseX + b.w)
        val ny = cy.coerceIn(b.y, b.y + b.h)
        return hypot(cx - nx, cy - ny) < r
    }

    /**
     * Shoot with a pull vector (dragX, dragY) in world units: the ball goes the opposite way,
     * like a slingshot. Length is capped at [MAX_PULL].
     */
    fun shoot(dragX: Float, dragY: Float): ShotResult {
        advance()
        if (isTimeUp || vx != 0f || vy != 0f) return ShotResult.IGNORED
        val len = hypot(dragX, dragY)
        if (len < MIN_PULL) return ShotResult.IGNORED
        val scale = min(len, MAX_PULL) / len * POWER
        vx = -dragX * scale
        vy = -dragY * scale
        shots++
        totalShots++
        bounces = 0
        return ShotResult.SHOT
    }

    /** Runs physics up to the clock. Called from every state read. */
    fun advance() {
        val target = min(elapsed(), CHALLENGE_DURATION_MS) / 1000f
        while (simTimeS + STEP_S <= target) {
            simTimeS += STEP_S
            step()
        }
    }

    private fun step() {
        if (vx == 0f && vy == 0f) return
        ballX += vx * STEP_S
        ballY += vy * STEP_S
        // Walls.
        if (ballX < BALL_R) { ballX = BALL_R; vx = abs(vx) * RESTITUTION; bounces++ }
        if (ballX > WIDTH - BALL_R) { ballX = WIDTH - BALL_R; vx = -abs(vx) * RESTITUTION; bounces++ }
        if (ballY < BALL_R) { ballY = BALL_R; vy = abs(vy) * RESTITUTION; bounces++ }
        if (ballY > HEIGHT - BALL_R) { ballY = HEIGHT - BALL_R; vy = -abs(vy) * RESTITUTION; bounces++ }
        // Blocks: push the ball out along the contact normal and reflect.
        for (b in blocks) {
            val bx = b.x(simTimeS)
            val nx = ballX.coerceIn(bx, bx + b.w)
            val ny = ballY.coerceIn(b.y, b.y + b.h)
            var dx = ballX - nx
            var dy = ballY - ny
            var d = hypot(dx, dy)
            if (d >= BALL_R) continue
            if (d < 1e-5f) { // centre inside: push out the shortest way
                val left = ballX - bx; val right = bx + b.w - ballX
                val top = ballY - b.y; val bottom = b.y + b.h - ballY
                val m = min(min(left, right), min(top, bottom))
                dx = 0f; dy = 0f
                when (m) { left -> dx = -1f; right -> dx = 1f; top -> dy = -1f; else -> dy = 1f }
                d = 0f
            } else { dx /= d; dy /= d }
            ballX += dx * (BALL_R - d)
            ballY += dy * (BALL_R - d)
            val dot = vx * dx + vy * dy
            if (dot < 0) {
                vx = (vx - 2 * dot * dx) * RESTITUTION
                vy = (vy - 2 * dot * dy) * RESTITUTION
                bounces++
            }
        }
        // Rolling friction.
        val decay = exp(-FRICTION * STEP_S)
        vx *= decay; vy *= decay
        // Hole: captured when over it and not too fast; a light pull helps near misses.
        val hx = holeX - ballX
        val hy = holeY - ballY
        val hd = hypot(hx, hy)
        val speed = hypot(vx, vy)
        if (hd < HOLE_R && speed < CAPTURE_SPEED) {
            sink()
            return
        }
        if (hd < HOLE_R * 1.6f && hd > 1e-4f) {
            vx += hx / hd * PULL * STEP_S
            vy += hy / hd * PULL * STEP_S
        }
        if (hypot(vx, vy) < STOP_SPEED) { vx = 0f; vy = 0f }
    }

    private fun sink() {
        holesSunk++
        value += BASE_SHARE + (1.0 - BASE_SHARE) / shots
        lastSunkMs = (simTimeS * 1000).toLong()
        deal()
    }

    /** Where the ball would roll with no blocks or bounces, for the aim guide. */
    fun aimVelocity(dragX: Float, dragY: Float): Pair<Float, Float> {
        val len = hypot(dragX, dragY)
        if (len < 1e-5f) return 0f to 0f
        val scale = min(len, MAX_PULL) / len * POWER
        return -dragX * scale to -dragY * scale
    }

    override fun finish(): ChallengeResult {
        advance()
        val duration = elapsed().coerceIn(0L, CHALLENGE_DURATION_MS)
        // Partial credit for getting close to the hole on the unfinished course.
        val dist = hypot(holeX - ballX, holeY - ballY)
        val partial = if (shots > 0) PARTIAL_SHARE * max(0f, 1f - dist / 0.9f) else 0.0f
        val units = value + partial
        val raw = units * POINTS_PER_HOLE * Scoring.difficultyBonus(difficulty)
        val detail = when {
            totalShots == 0 -> "No shots"
            holesSunk == 0 -> "No holes sunk · $totalShots shots"
            else -> "$holesSunk ${if (holesSunk == 1) "hole" else "holes"} · $totalShots shots"
        }
        return ChallengeResult(
            type = type,
            score = Scoring.clampScore(raw),
            performance = (units / GOOD_HOLES).coerceIn(0.0, 1.0),
            difficulty = difficulty,
            correct = holesSunk,
            mistakes = 0,
            durationMs = duration,
            detail = detail,
        )
    }

    companion object {
        const val WIDTH = 1f
        const val HEIGHT = 1.5f
        const val BALL_R = 0.035f
        const val HOLE_R = 0.055f
        const val STEP_S = 1f / 240f
        const val RESTITUTION = 0.82f
        const val FRICTION = 1.1f
        const val POWER = 9f
        const val MIN_PULL = 0.03f
        const val MAX_PULL = 0.4f
        const val CAPTURE_SPEED = 1.6f
        const val STOP_SPEED = 0.03f
        const val PULL = 2.5f
        const val DRIFT_SPEED = 1.6f
        const val BASE_SHARE = 0.6
        const val PARTIAL_SHARE = 0.4f
        const val POINTS_PER_HOLE = 260.0
        const val GOOD_HOLES = 3.0
    }
}
