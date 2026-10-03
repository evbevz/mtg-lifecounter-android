package com.mtg.lifecounter.dice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max

class DiceEngineTest {

    private val width = 1080f
    private val height = 1440f
    private val home = 860f to 1280f

    private val unitsPerPixel = DiceEngine.PV_WIDTH * DiceEngine.SHIFT_Z / DiceEngine.NEAR / width
    private val diceRadius = 0.7f * DiceEngine.SCALE_FACTOR

    private fun engine(marbles: List<DiceMarble> = emptyList()) = DiceEngine(pixelsPerPoint = 2.75f).apply {
        setViewSize(width, height)
        setDefaultPlace(home.first, home.second)
        setMarbles(marbles)
    }

    /** Runs the simulation at 60 fps until the dice stops; returns the number of frames. */
    private fun DiceEngine.settle(out: DiceTransform, maxFrames: Int = 60 * 30, onFrame: () -> Unit = {}): Int {
        var frames = 0
        while (advance(1f / 60f, out)) {
            onFrame()
            check(++frames < maxFrames) { "dice never stopped" }
        }
        return frames
    }

    private fun DiceEngine.flick(fromX: Float, fromY: Float, dx: Float, dy: Float) {
        assertTrue(touchDown(fromX, fromY, 0.0))
        val steps = 6
        for (i in 1..steps) {
            touchMove(fromX + dx * i / steps, fromY + dy * i / steps, fromX + dx * (i - 1) / steps, fromY + dy * (i - 1) / steps, 0.01 * i)
        }
        touchUp(fromX + dx, fromY + dy, 0.07)
    }

    private fun assertLiesOnFace(rotation: FloatArray) {
        val axisZ = floatArrayOf(rotation[2], rotation[6], rotation[10])
        val flat = axisZ.count { abs(abs(it) - 1f) < 1e-3f }
        assertEquals("one cube axis must point at the camera, got ${axisZ.toList()}", 1, flat)
    }

    @Test
    fun touchOnlyGrabsTheDice() {
        val engine = engine()
        assertTrue(engine.isDiceTouched(home.first, home.second))
        assertFalse(engine.isDiceTouched(100f, 100f))
        assertFalse(engine.touchDown(100f, 100f, 0.0))
    }

    @Test
    fun thrownDiceRollsStaysInsideWallsAndLandsOnAFace() {
        val throws = listOf(-600f to -900f, -900f to -200f, 300f to -1100f, -50f to -40f)
        for ((dx, dy) in throws) {
            val engine = engine()
            val out = DiceTransform()
            engine.advance(0f, out)
            engine.flick(home.first, home.second, dx, dy)

            val halfW = width / 2f * unitsPerPixel - diceRadius
            val halfH = height / 2f * unitsPerPixel - diceRadius
            var travelled = 0f
            var lastX = out.x
            var lastY = out.y
            val frames = engine.settle(out) {
                assertTrue("x=${out.x} outside walls", abs(out.x) <= halfW + 1e-3f)
                assertTrue("y=${out.y} outside walls", abs(out.y) <= halfH + 1e-3f)
                travelled += hypot(out.x - lastX, out.y - lastY)
                lastX = out.x
                lastY = out.y
            }

            assertTrue("throw ($dx, $dy) did not move the dice", travelled > 0.5f)
            assertTrue("throw ($dx, $dy) rolled for $frames frames", frames < 60 * 10)
            assertLiesOnFace(out.rotation)
        }
    }

    @Test
    fun diceBouncesOffMarbles() {
        val marble = DiceMarble(540f, 700f, 90f)
        val engine = engine(listOf(marble))
        val out = DiceTransform()
        engine.advance(0f, out)
        engine.flick(home.first, home.second, -320f, -580f)

        val mx = (marble.x - width / 2f) * unitsPerPixel
        val my = -(marble.y - height / 2f) * unitsPerPixel
        val minDistance = marble.radius * unitsPerPixel + diceRadius
        var closest = Float.MAX_VALUE
        engine.settle(out) {
            closest = minOf(closest, hypot(out.x - mx, out.y - my))
        }
        assertTrue("dice went into the marble: $closest < $minDistance", closest >= minDistance * 0.98f)
        assertLiesOnFace(out.rotation)
    }

    @Test
    fun marbleMovingOntoTheDicePushesItAway() {
        val engine = engine()
        val out = DiceTransform()
        engine.advance(0f, out)
        engine.setMarbles(listOf(DiceMarble(home.first + 10f, home.second, 90f)))
        engine.settle(out)

        val mx = (home.first + 10f - width / 2f) * unitsPerPixel
        val my = -(home.second - height / 2f) * unitsPerPixel
        val distance = hypot(out.x - mx, out.y - my)
        assertTrue(distance >= max(0f, 90f * unitsPerPixel + diceRadius) * 0.98f)
    }

    @Test
    fun returnToDefaultPlaceBringsDiceHome() {
        val engine = engine()
        val out = DiceTransform()
        engine.advance(0f, out)
        engine.flick(home.first, home.second, -600f, -900f)
        engine.settle(out)

        engine.returnToDefaultPlace()
        engine.settle(out)
        assertEquals((home.first - width / 2f) * unitsPerPixel, out.x, 1e-3f)
        assertEquals(-(home.second - height / 2f) * unitsPerPixel, out.y, 1e-3f)
        assertLiesOnFace(out.rotation)
    }
}
