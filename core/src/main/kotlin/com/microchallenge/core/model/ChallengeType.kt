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
    FLANKER("Arrow Focus", Skill.ATTENTION, "Which way does the MIDDLE arrow point? Ignore the rest."),
    CHIMP_ORDER("Chimp Order", Skill.MEMORY, "Memorize where the numbers are, then tap them 1, 2, 3..."),
    RULE_SWITCH("Rule Switch", Skill.LOGIC, "Follow the rule shown on top. It keeps changing."),
    LIGHTS_OUT("Lights Out", Skill.LOGIC, "Tapping a tile flips it and its neighbours. Turn every light off."),
    NUMBER_SERIES("Next in Line", Skill.LOGIC, "Find the number that comes next in the pattern."),
    DOT_COUNT("Dot Flash", Skill.ATTENTION, "Dots flash for a moment. How many were there?"),
    SPOT_CHANGE("Spot the Change", Skill.MEMORY, "The grid blinks and one tile changes. Tap it."),
    STOP_CLOCK("Stop the Clock", Skill.SPEED, "Stop the timer as close to the target as you can."),
    MIRROR_MATCH("Mirror Match", Skill.LOGIC, "Same shape rotated, or a mirror image?"),
}
