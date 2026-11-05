package com.microchallenge.core.challenges

import com.microchallenge.core.challenges.games.ColorWordSession
import com.microchallenge.core.challenges.games.GoNoGoSession
import com.microchallenge.core.challenges.games.MathSprintSession
import com.microchallenge.core.challenges.games.NumberMemorySession
import com.microchallenge.core.challenges.games.OddOneOutSession
import com.microchallenge.core.challenges.games.QuickCompareSession
import com.microchallenge.core.challenges.games.ReactionTapSession
import com.microchallenge.core.challenges.games.SequenceMemorySession
import com.microchallenge.core.engine.Clock
import com.microchallenge.core.model.ChallengeType
import kotlin.random.Random

/** Maps each [ChallengeType] to its factory. */
object ChallengeRegistry {
    private val factories: Map<ChallengeType, ChallengeFactory> = mapOf(
        ChallengeType.REACTION_TAP to ChallengeFactory { d, r, c -> ReactionTapSession(d, r, c) },
        ChallengeType.NUMBER_MEMORY to ChallengeFactory { d, r, c -> NumberMemorySession(d, r, c) },
        ChallengeType.SEQUENCE_MEMORY to ChallengeFactory { d, r, c -> SequenceMemorySession(d, r, c) },
        ChallengeType.COLOR_WORD to ChallengeFactory { d, r, c -> ColorWordSession(d, r, c) },
        ChallengeType.MATH_SPRINT to ChallengeFactory { d, r, c -> MathSprintSession(d, r, c) },
        ChallengeType.ODD_ONE_OUT to ChallengeFactory { d, r, c -> OddOneOutSession(d, r, c) },
        ChallengeType.GO_NO_GO to ChallengeFactory { d, r, c -> GoNoGoSession(d, r, c) },
        ChallengeType.QUICK_COMPARE to ChallengeFactory { d, r, c -> QuickCompareSession(d, r, c) },
    )

    fun create(type: ChallengeType, difficulty: Int, random: Random, clock: Clock): ChallengeSession =
        factories.getValue(type).create(difficulty.coerceIn(MIN_DIFFICULTY, MAX_DIFFICULTY), random, clock)
}
