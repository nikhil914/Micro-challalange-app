package com.microchallenge.core.model

/** Cognitive skill a challenge mainly exercises. Used for stats grouping only (no medical claims). */
enum class Skill { MEMORY, SPEED, ATTENTION, LOGIC, MATH }

/**
 * Every micro-challenge in the app. Add a new type here, then register its
 * generator in [com.microchallenge.core.challenges.ChallengeRegistry].
 */
enum class ChallengeType(val title: String, val skill: Skill, val instructions: String) {
    REACTION_TAP("Reaction Tap", Skill.SPEED, "Wait for green, then tap as fast as you can."),
    NUMBER_MEMORY("Number Memory", Skill.MEMORY, "Memorize the digits, then type them back."),
    SEQUENCE_MEMORY("Pattern Recall", Skill.MEMORY, "Watch the tiles light up, then repeat the order."),
    COLOR_WORD("Color Clash", Skill.ATTENTION, "Tap the INK color, not the word."),
    MATH_SPRINT("Math Sprint", Skill.MATH, "Solve as many sums as you can."),
    ODD_ONE_OUT("Odd One Out", Skill.ATTENTION, "Find the one tile that is different."),
    GO_NO_GO("Go / No-Go", Skill.SPEED, "Tap on green circles. Never tap red."),
    QUICK_COMPARE("Bigger Number", Skill.LOGIC, "Tap the side with the bigger value."),
}
