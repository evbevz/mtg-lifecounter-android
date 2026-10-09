package com.mtg.lifecounter.dice

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sqrt

/** Round obstacle the dice bounces off, in dice view pixels. */
data class DiceMarble(val x: Float, val y: Float, val radius: Float)

/**
 * Dice motion model ported from the iOS DiceView.
 *
 * The dice slides on the z = 0 plane of a perspective scene; its rotation is derived from
 * the travelled distance, so it appears to roll. Input coordinates are view pixels with
 * the origin in the top-left corner; scene coordinates are centered with y pointing up.
 *
 * All public methods are synchronized: touches arrive on the UI thread while the renderer
 * advances the simulation on the GL thread.
 */
class DiceEngine(private val pixelsPerPoint: Float) {

    private var viewWidth = 0f
    private var viewHeight = 0f

    private var x = 0f
    private var y = 0f
    private var vx = 0f
    private var vy = 0f
    private val rotation = identity()

    private var simTime = 0.0
    private var throwStartTime = 0.0
    private var accumulator = 0f
    private var dirty = true

    private var marbles: List<DiceMarble> = emptyList()
    private var sceneMarbles = FloatArray(0)
    private var defaultPlaceX = Float.NaN
    private var defaultPlaceY = Float.NaN

    private var hitX = 0f
    private var hitY = 0f
    private var reflectionX = 0f
    private var reflectionY = 0f
    private var planeX = 0f
    private var planeY = 0f

    private var locked = false
    private var touchCounter = 0
    private var throwStartPx = 0f
    private var throwStartPy = 0f
    private var throwEndPx = 0f
    private var throwEndPy = 0f
    private var previousMoveTime = 0.0

    private val stepRotation = FloatArray(16)
    private val product = FloatArray(16)

    private val unitsPerPixel: Float
        get() = if (viewWidth > 0f) PV_WIDTH * SHIFT_Z / NEAR / viewWidth else 0f

    @Synchronized
    fun setViewSize(width: Float, height: Float) {
        if (width == viewWidth && height == viewHeight) return
        viewWidth = width
        viewHeight = height
        if (!defaultPlaceX.isNaN()) moveDiceTo(defaultPlaceX, defaultPlaceY)
        applyMarbles()
        dirty = true
    }

    /** Remembers the dice home position and moves the dice there when it changes. */
    @Synchronized
    fun setDefaultPlace(px: Float, py: Float) {
        if (px == defaultPlaceX && py == defaultPlaceY) return
        defaultPlaceX = px
        defaultPlaceY = py
        moveDiceTo(px, py)
        dirty = true
    }

    /** Puts the dice back to its home position and lets it settle. */
    @Synchronized
    fun returnToDefaultPlace() {
        if (!defaultPlaceX.isNaN()) moveDiceTo(defaultPlaceX, defaultPlaceY)
        throwDice(1f, 0f, 0f)
    }

    @Synchronized
    fun setMarbles(newMarbles: List<DiceMarble>) {
        if (newMarbles == marbles) return
        marbles = newMarbles.take(MAX_MARBLES)
        applyMarbles()
        dirty = true
    }

    @Synchronized
    fun isDiceTouched(px: Float, py: Float): Boolean {
        if (viewWidth <= 0f) return false
        return hypot(toSceneX(px) - x, toSceneY(py) - y) < DICE_RADIUS
    }

    /** Returns true when the touch grabbed the dice and further events should be delivered. */
    @Synchronized
    fun touchDown(px: Float, py: Float, timeSec: Double): Boolean {
        if (!isDiceTouched(px, py)) return false
        throwStartPx = px
        throwStartPy = py
        throwEndPx = px
        throwEndPy = py
        previousMoveTime = timeSec
        locked = true
        touchCounter = 0
        return true
    }

    @Synchronized
    fun touchMove(px: Float, py: Float, previousPx: Float, previousPy: Float, timeSec: Double) {
        if (!locked) return
        moveDice(px - previousPx, py - previousPy)

        throwEndPx = px
        throwEndPy = py

        if (++touchCounter < 5) return
        touchCounter = 0

        previousMoveTime = timeSec
        throwStartPx = previousPx
        throwStartPy = previousPy
    }

    @Synchronized
    fun touchUp(px: Float, py: Float, timeSec: Double) {
        if (!locked) return
        locked = false
        throwEndPx = px
        throwEndPy = py

        val elapsed = timeSec - previousMoveTime
        if (elapsed < 0.3) {
            // Gestures are measured in iOS points so the throw strength matches the original.
            val dx = (throwEndPx - throwStartPx) / pixelsPerPoint
            val dy = (throwStartPy - throwEndPy) / pixelsPerPoint
            if (dx != 0f || dy != 0f) throwDice(max(elapsed, 0.01).toFloat(), dx, dy)
        }
    }

    @Synchronized
    fun touchCancel() {
        locked = false
    }

    /**
     * Advances the simulation by [dt] seconds in fixed steps and copies the dice transform
     * into [out]. Returns true while the dice still moves and more frames are needed.
     */
    @Synchronized
    fun advance(dt: Float, out: DiceTransform): Boolean {
        if (viewWidth > 0f) {
            accumulator += min(dt, MAX_FRAME_TIME)
            var steps = 0
            while (accumulator >= STEP && steps < MAX_STEPS_PER_FRAME) {
                step(STEP)
                accumulator -= STEP
                steps++
            }
            if (steps == MAX_STEPS_PER_FRAME) accumulator = 0f
        }
        out.x = x
        out.y = y
        rotation.copyInto(out.rotation)
        return dirty || vx != 0f || vy != 0f
    }

    private fun step(t: Float) {
        val startX = x
        val startY = y
        val startVx = vx
        val startVy = vy
        simTime += t

        var wx: Float
        var wy: Float

        findLowestPlane()
        if (vx != 0f || vy != 0f) {
            // make acceleration dependent on the lower plane orientation
            val acceleration = ACCELERATION / max(0.05f, hypot(planeX, planeY))

            // linear deceleration
            val v = hypot(vx, vy)
            vx = if (vx > 0) max(0f, vx + acceleration * vx / v * t) else min(0f, vx + acceleration * vx / v * t)
            vy = if (vy > 0) max(0f, vy + acceleration * vy / v * t) else min(0f, vy + acceleration * vy / v * t)

            // max roll duration limit
            val rollTime = min(simTime - throwStartTime, MAX_ROLL_DURATION)
            val correction = ((MAX_ROLL_DURATION - rollTime) / MAX_ROLL_DURATION)
                .pow(MAX_DURATION_CORRECTION_WEIGHT).toFloat()
            vx *= correction
            vy *= correction

            // dice edge angle correction
            vx += planeX * DICE_EDGE_ANGLE_CORRECTION_WEIGHT
            vy += planeY * DICE_EDGE_ANGLE_CORRECTION_WEIGHT

            wy = vx * t * RADIANS_TO_DEGREES
            wx = -(vy * t) * RADIANS_TO_DEGREES

            if (testMarblesHit(t, vx, vy)) {
                x = hitX
                y = hitY
                vx = reflectionX
                vy = reflectionY
            } else {
                x += vx * t
                y += vy * t
            }

            if (abs(vx) < DICE_VELOCITY_STOP_THRESHOLD) vx = 0f
            if (abs(vy) < DICE_VELOCITY_STOP_THRESHOLD) vy = 0f

            // dice stopped on an edge: push it towards the nearest face
            if (vx == 0f && vy == 0f) {
                if (abs(planeX) > 0.1f || abs(planeY) > 0.1f) {
                    vx += planeX * (0.7f - abs(planeX)) * 40f
                    vy += planeY * (0.7f - abs(planeY)) * 40f
                }
                if (abs(planeX) < 0.1f) wy = planeX * RADIANS_TO_DEGREES
                if (abs(planeY) < 0.1f) wx = -planeY * RADIANS_TO_DEGREES
            }
        } else if (planeX != 0f || planeY != 0f) {
            // at rest: lay the lowest face flat
            wy = asin(planeX.coerceIn(-1f, 1f)) * RADIANS_TO_DEGREES
            wx = -asin(planeY.coerceIn(-1f, 1f)) * RADIANS_TO_DEGREES
        } else {
            wx = 0f
            wy = 0f
        }

        // field walls invert velocity
        val right = PV_WIDTH / 2f * SHIFT_Z / NEAR
        val top = pvHeight() / 2f * SHIFT_Z / NEAR
        if (x > right - DICE_RADIUS) {
            x = right - DICE_RADIUS
            vx = -vx
        }
        if (x < -right + DICE_RADIUS) {
            x = -right + DICE_RADIUS
            vx = -vx
        }
        if (y > top - DICE_RADIUS) {
            y = top - DICE_RADIUS
            vy = -vy
        }
        if (y < -top + DICE_RADIUS) {
            y = -top + DICE_RADIUS
            vy = -vy
        }

        if (wx != 0f || wy != 0f) {
            rotationYXZ(stepRotation, wx, wy)
            multiply(product, stepRotation, rotation)
            orthonormalize(product)
            product.copyInto(rotation)
        }

        dirty = x != startX || y != startY || vx != startVx || vy != startVy
    }

    /**
     * Checks whether the dice moving by (vx, vy) * time hits a marble. On a hit stores the
     * contact point in hitX/hitY and the reflected velocity in reflectionX/reflectionY.
     */
    private fun testMarblesHit(time: Float, vx: Float, vy: Float): Boolean {
        var wasHit = false
        var lx = 0f
        var ly = 0f
        var marble = -1

        for (m in 0 until sceneMarbles.size / 3) {
            val mx = sceneMarbles[m * 3]
            val my = sceneMarbles[m * 3 + 1]
            val r = sceneMarbles[m * 3 + 2] + DICE_RADIUS

            // move coordinates to the marble center
            val x1 = x - mx
            val y1 = y - my
            val x2 = x1 + vx * time
            val y2 = y1 + vy * time
            val a = y1 - y2
            val b = x2 - x1
            val c = x1 * y2 - x2 * y1
            val ab = a * a + b * b
            if (ab == 0f || c * c > r * r * ab + FLT_EPSILON) continue

            // moving away from the marble
            if (((vx > 0 && x1 > 0) || (vx < 0 && x1 < 0)) && ((vy > 0 && y1 > 0) || (vy < 0 && y1 < 0))) continue

            val x0 = -a * c / ab
            val y0 = -b * c / ab
            val mult = sqrt((r * r - c * c / ab) / ab)
            val ax = x0 + b * mult
            val bx = x0 - b * mult
            val ay = y0 - a * mult
            val by = y0 + a * mult

            // nearest intersection, moved back to scene coordinates
            val nearA = hypot(x1 - ax, y1 - ay) < hypot(x1 - bx, y1 - by)
            val px = (if (nearA) ax else bx) + mx
            val py = (if (nearA) ay else by) + my

            if (wasHit && hypot(x - lx, y - ly) < hypot(x - px, y - py)) continue
            lx = px
            ly = py
            marble = m
            wasHit = true
        }
        if (!wasHit) return false

        val mx = sceneMarbles[marble * 3]
        val my = sceneMarbles[marble * 3 + 1]
        val r = sceneMarbles[marble * 3 + 2] + DICE_RADIUS
        val x2 = x + vx * time
        val y2 = y + vy * time
        val between = ((x < lx && lx < x2) || (x > lx && lx > x2)) && ((y < ly && ly < y2) || (y > ly && ly > y2))
        val overlaps = hypot(x2 - mx, y2 - my) < r
        if (!between && !overlaps) return false

        hitX = lx
        hitY = ly
        val nLength = hypot(mx - lx, my - ly)
        val nx = (mx - lx) / nLength
        val ny = (my - ly) / nLength
        val dot = nx * vx + ny * vy
        reflectionX = vx - nx * 2f * dot
        reflectionY = vy - ny * 2f * dot
        return true
    }

    /** Stores into planeX/planeY the in-plane part of the cube axis pointing down the most. */
    private fun findLowestPlane() {
        val m = rotation
        var lx = m[4]
        var ly = m[5]
        var lz = m[6]
        val candidates = floatArrayOf(
            -m[8], -m[9], -m[10],   // forward
            m[0], m[1], m[2],       // right
            -m[4], -m[5], -m[6],    // -up
            m[8], m[9], m[10],      // -forward
            -m[0], -m[1], -m[2],    // -right
        )
        for (i in candidates.indices step 3) {
            if (candidates[i + 2] < lz) {
                lx = candidates[i]
                ly = candidates[i + 1]
                lz = candidates[i + 2]
            }
        }
        planeX = lx
        planeY = ly
    }

    private fun moveDice(dxPx: Float, dyPx: Float) {
        val k = unitsPerPixel
        val dx = dxPx * k
        val dy = -dyPx * k
        if (testMarblesHit(1f, dx, dy)) {
            x = hitX
            y = hitY
            if (locked) {
                throwDice(0.1f, DICE_TO_MARBLE_MANUAL_HIT_MULTIPLIER * dx, DICE_TO_MARBLE_MANUAL_HIT_MULTIPLIER * dy)
                locked = false
            }
        } else {
            x += dx
            y += dy
        }
        dirty = true
    }

    private fun throwDice(time: Float, x0: Float, y0: Float) {
        val velocity = hypot(x0, y0) / time
        val damping = max(1f, sqrt(abs(30f - velocity)))
        vx = x0 / time / damping
        vy = y0 / time / damping
        throwStartTime = simTime
        dirty = true
    }

    private fun moveDiceTo(px: Float, py: Float) {
        if (viewWidth <= 0f) return
        x = toSceneX(px)
        y = toSceneY(py)
    }

    /** Converts marbles to scene coordinates and pushes the dice out of any it overlaps. */
    private fun applyMarbles() {
        if (viewWidth <= 0f) return
        val k = unitsPerPixel
        sceneMarbles = FloatArray(marbles.size * 3)
        marbles.forEachIndexed { i, marble ->
            val mx = toSceneX(marble.x)
            val my = toSceneY(marble.y)
            val radius = marble.radius * k
            sceneMarbles[i * 3] = mx
            sceneMarbles[i * 3 + 1] = my
            sceneMarbles[i * 3 + 2] = radius

            val r = radius + DICE_RADIUS
            val distance = hypot(mx - x, my - y)
            if (distance < r) {
                val dx = if (distance == 0f) -r else r * (x - mx) / distance + mx - x
                val dy = if (distance == 0f) 0f else r * (y - my) / distance + my - y
                x += dx
                y += dy
                throwDice(0.1f, DICE_TO_MARBLE_MANUAL_HIT_MULTIPLIER * dx, DICE_TO_MARBLE_MANUAL_HIT_MULTIPLIER * dy)
            }
        }
    }

    private fun pvHeight() = if (viewWidth > 0f) PV_WIDTH * viewHeight / viewWidth else 0f

    private fun toSceneX(px: Float) = (px - viewWidth / 2f) * unitsPerPixel

    private fun toSceneY(py: Float) = -(py - viewHeight / 2f) * unitsPerPixel

    companion object {
        const val NEAR = 40f
        const val FAR = 60f
        const val PV_WIDTH = 10f
        const val SHIFT_Z = 57f
        const val SCALE_FACTOR = 2.2f

        private const val ACCELERATION = -10f
        private const val MAX_ROLL_DURATION = 3.0
        private const val MAX_DURATION_CORRECTION_WEIGHT = 0.1

        private const val DICE_VELOCITY_STOP_THRESHOLD = 1.5f
        private const val DICE_EDGE_ANGLE_CORRECTION_WEIGHT = 1.7f
        private const val DICE_RADIUS = 0.7f * SCALE_FACTOR
        private const val DICE_TO_MARBLE_MANUAL_HIT_MULTIPLIER = 100f

        private const val MAX_MARBLES = 5

        /** The iOS version advanced the simulation once per 60 Hz display frame. */
        private const val STEP = 1f / 60f
        private const val MAX_STEPS_PER_FRAME = 6
        private const val MAX_FRAME_TIME = 0.1f

        private const val FLT_EPSILON = 1.1920929e-7f
        private const val RADIANS_TO_DEGREES = (180.0 / PI).toFloat()
        private const val DEGREES_TO_RADIANS = (PI / 180.0).toFloat()

        private fun identity() = FloatArray(16).also { m -> for (i in 0 until 4) m[i * 5] = 1f }

        /** Column-major YXZ Euler rotation (z = 0), matching kmMat4RotationYXZ. */
        private fun rotationYXZ(m: FloatArray, xDegrees: Float, yDegrees: Float) {
            val ax = xDegrees * DEGREES_TO_RADIANS
            val ay = yDegrees * DEGREES_TO_RADIANS
            val cx = kotlin.math.cos(ax)
            val sx = kotlin.math.sin(ax)
            val cy = kotlin.math.cos(ay)
            val sy = kotlin.math.sin(ay)
            m.fill(0f)
            m[0] = cy
            m[2] = -sy
            m[4] = sx * sy
            m[5] = cx
            m[6] = cy * sx
            m[8] = cx * sy
            m[9] = -sx
            m[10] = cx * cy
            m[15] = 1f
        }

        /** out = a * b for column-major 4x4 matrices. */
        private fun multiply(out: FloatArray, a: FloatArray, b: FloatArray) {
            for (col in 0 until 4) {
                for (row in 0 until 4) {
                    var sum = 0f
                    for (k in 0 until 4) sum += a[k * 4 + row] * b[col * 4 + k]
                    out[col * 4 + row] = sum
                }
            }
        }

        /** Keeps the accumulated rotation from drifting into a skewed or scaled matrix. */
        private fun orthonormalize(m: FloatArray) {
            var len = sqrt(m[0] * m[0] + m[1] * m[1] + m[2] * m[2])
            m[0] /= len; m[1] /= len; m[2] /= len
            val d = m[0] * m[4] + m[1] * m[5] + m[2] * m[6]
            m[4] -= d * m[0]; m[5] -= d * m[1]; m[6] -= d * m[2]
            len = sqrt(m[4] * m[4] + m[5] * m[5] + m[6] * m[6])
            m[4] /= len; m[5] /= len; m[6] /= len
            m[8] = m[1] * m[6] - m[2] * m[5]
            m[9] = m[2] * m[4] - m[0] * m[6]
            m[10] = m[0] * m[5] - m[1] * m[4]
        }
    }
}

/** Dice position in scene units and its rotation matrix, as read by the renderer. */
class DiceTransform {
    var x = 0f
    var y = 0f
    val rotation = FloatArray(16)
}
