package com.microchallenge.core.challenges

import com.microchallenge.core.challenges.games.ColorWordSession
import com.microchallenge.core.challenges.games.GoNoGoSession
import com.microchallenge.core.challenges.games.MathSprintSession
import com.microchallenge.core.challenges.games.NumberMemorySession
import com.microchallenge.core.challenges.games.OddOneOutSession
import com.microchallenge.core.challenges.games.QuickCompareSession
import com.microchallenge.core.challenges.games.ReactionTapSession
import com.microchallenge.core.challenges.games.SequenceMemorySession
import com.microchallenge.core.challenges.games.FlankerSession
import com.microchallenge.core.challenges.games.ChimpOrderSession
import com.microchallenge.core.challenges.games.RuleSwitchSession
import com.microchallenge.core.challenges.games.LightsOutSession
import com.microchallenge.core.challenges.games.NumberSeriesSession
import com.microchallenge.core.challenges.games.DotCountSession
import com.microchallenge.core.challenges.games.SpotChangeSession
import com.microchallenge.core.challenges.games.StopClockSession
import com.microchallenge.core.challenges.games.MirrorMatchSession
import com.microchallenge.core.challenges.games.ColorRushSession
import com.microchallenge.core.challenges.games.PairUpSession
import com.microchallenge.core.challenges.games.BubbleSumSession
import com.microchallenge.core.challenges.games.StarCatcherSession
import com.microchallenge.core.challenges.games.SlideShiftSession
import com.microchallenge.core.challenges.games.WeighInSession
import com.microchallenge.core.challenges.games.BankShotSession
import com.microchallenge.core.challenges.games.GlowGridSession
import com.microchallenge.core.challenges.games.PipeFlowSession
import com.microchallenge.core.challenges.games.NeonDashSession
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
        ChallengeType.FLANKER to ChallengeFactory { d, r, c -> FlankerSession(d, r, c) },
        ChallengeType.CHIMP_ORDER to ChallengeFactory { d, r, c -> ChimpOrderSession(d, r, c) },
        ChallengeType.RULE_SWITCH to ChallengeFactory { d, r, c -> RuleSwitchSession(d, r, c) },
        ChallengeType.LIGHTS_OUT to ChallengeFactory { d, r, c -> LightsOutSession(d, r, c) },
        ChallengeType.NUMBER_SERIES to ChallengeFactory { d, r, c -> NumberSeriesSession(d, r, c) },
        ChallengeType.DOT_COUNT to ChallengeFactory { d, r, c -> DotCountSession(d, r, c) },
        ChallengeType.SPOT_CHANGE to ChallengeFactory { d, r, c -> SpotChangeSession(d, r, c) },
        ChallengeType.STOP_CLOCK to ChallengeFactory { d, r, c -> StopClockSession(d, r, c) },
        ChallengeType.MIRROR_MATCH to ChallengeFactory { d, r, c -> MirrorMatchSession(d, r, c) },
        ChallengeType.COLOR_RUSH to ChallengeFactory { d, r, c -> ColorRushSession(d, r, c) },
        ChallengeType.PAIR_UP to ChallengeFactory { d, r, c -> PairUpSession(d, r, c) },
        ChallengeType.BUBBLE_SUM to ChallengeFactory { d, r, c -> BubbleSumSession(d, r, c) },
        ChallengeType.STAR_CATCHER to ChallengeFactory { d, r, c -> StarCatcherSession(d, r, c) },
        ChallengeType.SLIDE_SHIFT to ChallengeFactory { d, r, c -> SlideShiftSession(d, r, c) },
        ChallengeType.WEIGH_IN to ChallengeFactory { d, r, c -> WeighInSession(d, r, c) },
        ChallengeType.BANK_SHOT to ChallengeFactory { d, r, c -> BankShotSession(d, r, c) },
        ChallengeType.GLOW_GRID to ChallengeFactory { d, r, c -> GlowGridSession(d, r, c) },
        ChallengeType.PIPE_FLOW to ChallengeFactory { d, r, c -> PipeFlowSession(d, r, c) },
        ChallengeType.NEON_DASH to ChallengeFactory { d, r, c -> NeonDashSession(d, r, c) },
    )

    fun create(type: ChallengeType, difficulty: Int, random: Random, clock: Clock): ChallengeSession =
        factories.getValue(type).create(difficulty.coerceIn(MIN_DIFFICULTY, MAX_DIFFICULTY), random, clock)
}
