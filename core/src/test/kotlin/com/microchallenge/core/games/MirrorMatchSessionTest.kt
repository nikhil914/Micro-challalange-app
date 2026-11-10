package com.microchallenge.core.games

import com.microchallenge.core.challenges.games.MirrorMatchSession
import com.microchallenge.core.challenges.games.ShapeCell
import com.microchallenge.core.engine.FakeClock
import com.microchallenge.core.model.CHALLENGE_DURATION_MS
import kotlin.math.abs
import kotlin.random.Random
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import org.junit.Test

class MirrorMatchSessionTest {

    private fun session(d: Int, seed: Long = 1L, clock: FakeClock = FakeClock()) =
        MirrorMatchSession(d, Random(seed), clock)

    // Independent geometry, written without the session's helpers.
    private fun norm(cells: Collection<Pair<Int, Int>>): Set<Pair<Int, Int>> {
        val mx = cells.minOf { it.first }; val my = cells.minOf { it.second }
        return cells.map { (it.first - mx) to (it.second - my) }.toSet()
    }
    private fun turn(cells: Set<Pair<Int, Int>>) = norm(cells.map { (x, y) -> -y to x })
    private fun flip(cells: Set<Pair<Int, Int>>) = norm(cells.map { (x, y) -> -x to y })
    private fun allTurns(cells: Set<Pair<Int, Int>>): List<Set<Pair<Int, Int>>> {
        val out = mutableListOf(cells)
        repeat(3) { out += turn(out.last()) }
        return out
    }
    private fun List<ShapeCell>.pairs() = map { it.x to it.y }.toSet()

    private fun connected(cells: Set<Pair<Int, Int>>): Boolean {
        val seen = mutableSetOf(cells.first())
        val queue = ArrayDeque(listOf(cells.first()))
        while (queue.isNotEmpty()) {
            val (x, y) = queue.removeFirst()
            for (n in listOf(x + 1 to y, x - 1 to y, x to y + 1, x to y - 1)) {
                if (n in cells && seen.add(n)) queue += n
            }
        }
        return seen.size == cells.size
    }

    @Test
    fun deterministicForSeed() {
        val a = session(8, 42); val b = session(8, 42)
        repeat(30) {
            assertEquals(a.current, b.current)
            a.answer(true); b.answer(true)
        }
    }

    @Test
    fun sameTrialsAreRotationsAndMirrorTrialsAreNot() {
        for (d in 1..10) {
            val s = session(d, d * 17L)
            var same = 0
            repeat(400) {
                val t = s.current
                val left = t.left.pairs(); val right = t.right.pairs()
                assertEquals(s.cellCount, left.size, "duplicate or missing cells ${t.left}")
                assertEquals(left.size, right.size)
                assertEquals(norm(left), left, "left must start at 0,0")
                assertEquals(norm(right), right, "right must start at 0,0")
                assertTrue(connected(left), "left not connected $left")
                val rotations = allTurns(left)
                assertFalse(flip(left) in rotations, "shape is not chiral: $left")
                if (t.isSame) {
                    same++
                    assertTrue(right in rotations, "d=$d same trial is not a rotation: $left / $right")
                    assertEquals(rotations[t.rotationDeg / 90], right)
                } else {
                    assertFalse(right in rotations, "d=$d mirror trial reachable by rotation: $left / $right")
                    assertTrue(right in allTurns(flip(left)), "mirror trial is not the mirrored shape")
                }
                assertTrue(t.rotationDeg in setOf(90, 180, 270))
                assertTrue(t.leftColorIndex in 0 until MirrorMatchSession.PALETTE_SIZE)
                assertTrue(t.rightColorIndex in 0 until MirrorMatchSession.PALETTE_SIZE)
                assertTrue(t.rightScale > 0.5f && t.rightScale <= 1f)
                assertTrue(s.answer(t.isSame))
            }
            assertTrue(abs(same - 200) < 50, "d=$d expected about half same trials, got $same of 400")
        }
    }

    @Test
    fun helperGeometryIsConsistent() {
        val s = listOf(ShapeCell(1, 0), ShapeCell(2, 0), ShapeCell(0, 1), ShapeCell(1, 1)) // S tetromino
        val square = listOf(ShapeCell(0, 0), ShapeCell(1, 0), ShapeCell(0, 1), ShapeCell(1, 1))
        val t = listOf(ShapeCell(0, 0), ShapeCell(1, 0), ShapeCell(2, 0), ShapeCell(1, 1))
        assertTrue(MirrorMatchSession.isChiral(s))
        assertFalse(MirrorMatchSession.isChiral(square))
        assertFalse(MirrorMatchSession.isChiral(t))
        var r = MirrorMatchSession.normalize(t)
        repeat(4) { r = MirrorMatchSession.rotateCw(r) }
        assertEquals(MirrorMatchSession.normalize(t), r)
        // Clockwise with y pointing down: a bar pointing right from the stem ends up pointing down.
        val l = listOf(ShapeCell(0, 0), ShapeCell(0, 1), ShapeCell(0, 2), ShapeCell(1, 2))
        assertEquals(
            setOf(0 to 0, 1 to 0, 2 to 0, 0 to 1),
            MirrorMatchSession.rotateCw(l).pairs(),
        )
    }

    @Test
    fun difficultyScalesCellsRotationsAndLook() {
        assertEquals(4, session(1).cellCount); assertEquals(5, session(5).cellCount)
        assertEquals(6, session(10).cellCount)
        fun trials(d: Int) = session(d, 3).let { s -> List(200) { s.current.also { s.answer(true) } } }
        assertTrue(trials(2).none { it.rotationDeg == 180 })
        assertEquals(setOf(90, 180, 270), trials(5).map { it.rotationDeg }.toSet())
        for (t in trials(6)) { assertEquals(t.leftColorIndex, t.rightColorIndex); assertEquals(1f, t.rightScale) }
        for (t in trials(7)) { assertNotEquals(t.leftColorIndex, t.rightColorIndex); assertTrue(t.rightScale < 1f) }
        assertFalse(session(6).variedLook); assertTrue(session(7).variedLook)
    }

    @Test
    fun noInputFinishGivesZero() {
        val clock = FakeClock()
        val s = session(5, clock = clock)
        clock.advance(CHALLENGE_DURATION_MS)
        val r = s.finish()
        assertEquals(0, r.score); assertEquals(0.0, r.performance)
        assertEquals(CHALLENGE_DURATION_MS, r.durationMs)
        assertFalse(s.isComplete)
    }

    @Test
    fun scoreBoundsAndGuessingDoesNotPay() {
        for (d in 1..10) {
            val perfect = session(d)
            repeat(60) { perfect.answer(perfect.current.isSame) }
            val r = perfect.finish()
            assertEquals(1000, r.score); assertEquals(1.0, r.performance)
            val bad = session(d)
            repeat(60) { bad.answer(!bad.current.isSame) }
            assertEquals(60, bad.mistakes); assertEquals(0, bad.finish().score)
        }
        val coin = session(5, 11)
        repeat(3) { coin.answer(coin.current.isSame); coin.answer(!coin.current.isSame) }
        assertEquals(0, coin.finish().score)
        val mid = session(5)
        repeat(6) { assertTrue(mid.answer(mid.current.isSame)) }
        val r = mid.finish()
        assertTrue(r.score in 700..850, "score ${r.score}")
        assertTrue(r.performance in 0.6..0.8, "performance ${r.performance}")
    }

    @Test
    fun inputIgnoredAfterTimeUp() {
        val clock = FakeClock()
        val s = session(3, clock = clock)
        clock.advance(CHALLENGE_DURATION_MS + 5)
        assertFalse(s.answer(s.current.isSame))
        assertEquals(0, s.correct); assertEquals(0, s.answeredCount)
    }
}
