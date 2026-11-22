package com.microchallenge.core.games

import com.microchallenge.core.challenges.games.BankShotSession
import com.microchallenge.core.challenges.games.BankShotSession.ShotResult
import com.microchallenge.core.engine.FakeClock
import com.microchallenge.core.model.CHALLENGE_DURATION_MS
import com.microchallenge.core.model.ChallengeType
import kotlin.random.Random
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.Test

class BankShotSessionTest {

    @Test
    fun `deals a course with ball, hole and blocks`() {
        val s = BankShotSession(1, Random(3), FakeClock(0))
        assertEquals(ChallengeType.BANK_SHOT, s.type)
        assertEquals(1, s.blocks.size)
        assertTrue(s.ballY > s.holeY)
        assertFalse(s.isRolling)
        assertEquals(5, BankShotSession(10, Random(3), FakeClock(0)).blockCount)
    }

    @Test
    fun `shot rolls, stays in bounds and stops`() {
        val clock = FakeClock(0)
        val s = BankShotSession(1, Random(5), clock)
        assertEquals(ShotResult.SHOT, s.shoot(0.1f, 0.3f))
        clock.advance(100)
        assertTrue(s.isRolling)
        assertEquals(ShotResult.IGNORED, s.shoot(0.1f, 0.3f))
        clock.advance(8_000)
        assertFalse(s.isRolling)
        assertTrue(s.ballX in 0f..BankShotSession.WIDTH)
        assertTrue(s.ballY in 0f..BankShotSession.HEIGHT)
    }

    @Test
    fun `tiny drag is ignored and no shots scores zero`() {
        val clock = FakeClock(0)
        val s = BankShotSession(1, Random(1), clock)
        assertEquals(ShotResult.IGNORED, s.shoot(0.001f, 0f))
        clock.advance(CHALLENGE_DURATION_MS)
        assertEquals(0, s.finish().score)
        assertEquals(ShotResult.IGNORED, s.shoot(0f, 0.3f))
    }
}
