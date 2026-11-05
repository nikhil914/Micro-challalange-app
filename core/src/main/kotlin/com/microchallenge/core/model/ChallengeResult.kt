package com.microchallenge.core.model

/** Default length of every micro-challenge, in milliseconds. */
const val CHALLENGE_DURATION_MS: Long = 10_000L

/**
 * Outcome of one play. [score] is normalised to 0..1000 so different
 * challenges can share streak, rating and daily-total logic.
 *
 * @param performance 0.0..1.0 quality of the run, fed to the difficulty rating.
 */
data class ChallengeResult(
    val type: ChallengeType,
    val score: Int,
    val performance: Double,
    val difficulty: Int,
    val correct: Int,
    val mistakes: Int,
    val durationMs: Long,
    val detail: String = "",
    val timestampMs: Long = 0L,
) {
    init {
        require(score in 0..1000) { "score must be 0..1000, was $score" }
        require(performance in 0.0..1.0) { "performance must be 0..1, was $performance" }
    }
}
