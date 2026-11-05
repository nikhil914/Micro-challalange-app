package com.microchallenge.core.challenges

import com.microchallenge.core.engine.Clock
import com.microchallenge.core.model.ChallengeResult
import com.microchallenge.core.model.ChallengeType
import kotlin.random.Random

/** Difficulty levels run 1 (easiest) .. 10 (hardest). */
const val MIN_DIFFICULTY = 1
const val MAX_DIFFICULTY = 10

/**
 * One running micro-challenge. Each concrete session exposes its own input
 * methods (tap, submit, ...) that the matching Compose screen calls.
 * Sessions are pure state machines: no Android, no threads.
 */
interface ChallengeSession {
    val type: ChallengeType
    val difficulty: Int

    /** True when the session ended early (e.g. all items answered, or a fatal mistake). */
    val isComplete: Boolean

    /** Build the final result. Called once, when time runs out or [isComplete] turns true. */
    fun finish(): ChallengeResult
}

/** Builds a fresh session. Implementations must be deterministic for a given [random] seed. */
fun interface ChallengeFactory {
    fun create(difficulty: Int, random: Random, clock: Clock): ChallengeSession
}

/** Shared scoring helpers so every challenge maps onto 0..1000. */
object Scoring {
    /** Clamp to the 0..1000 score range. */
    fun clampScore(raw: Double): Int = raw.coerceIn(0.0, 1000.0).toInt()

    /** Difficulty multiplier: 1.0 at level 1 up to 1.45 at level 10. */
    fun difficultyBonus(difficulty: Int): Double =
        1.0 + (difficulty.coerceIn(MIN_DIFFICULTY, MAX_DIFFICULTY) - 1) * 0.05

    /** Accuracy in 0..1, 0 when nothing attempted. */
    fun accuracy(correct: Int, mistakes: Int): Double =
        if (correct + mistakes == 0) 0.0 else correct.toDouble() / (correct + mistakes)
}
