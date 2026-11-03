package com.lifemax.rpg.feature.pet.body

import kotlin.math.sqrt

/**
 * How a toy moves while a cat holds it: the physics of the mouth. The cat's [ToyDirector] writes
 * the mouth anchor and the way the cat faces into the toy ([ToyBody.anchorX], [ToyBody.anchorY],
 * [ToyBody.holderDir]) every tick and calls [step].
 * - [HoldStyle.MOUTH] (balls, yarn, spring): the toy is pulled to a place at the mouth by a spring
 *   of its own frequency and damping. A ball is stiff and bobs a little; the spring toy is soft and
 *   bounces on every footfall.
 * - [HoldStyle.HANG] (mouse by its tail, feather by its quill): a point mass on a stiff elastic rod
 *   from the mouth, under gravity and air drag. It swings with every footfall (see [footfall]),
 *   lags when the cat turns (the anchor crosses the body while the sprite narrows), trails behind a
 *   walking cat (a feather far more than a mouse) and settles when the cat stops.
 *
 * Everything scales with the cat's depth scale so a cat at the glass carries a bigger toy that
 * swings at the same pace. Yarn also drags a thread ([stepThread]). Nothing allocates.
 */
internal object Carry {
    private const val GROUND_BOUNCE = 0.3f
    private const val FLUTTER_HZ = 2.4f
    private const val FLUTTER_BASE_SHARE = 0.25f
    private const val FLUTTER_SPEED_FULL = 40f
    private const val ROLL_DAMP = 5f
    private const val THREAD_DAMP = 0.97f
    private const val THREAD_FRICTION = 0.7f
    private const val THREAD_LIFT = 0.5f

    /** Take hold of [t] with the cat facing [dir]: the state, the draw layer and a fresh thread. */
    fun grab(t: ToyBody, dir: Int, ds: Float) {
        t.state = ToyState.HELD
        t.airborne = false
        t.overCat = true
        t.holderDir = dir
        t.mirrored = dir < 0
        t.zGoal = Float.NaN
        t.surface = SurfaceId.FLOOR
        t.spin = 0f
        t.vz = 0f
        placePivot(t, ds)
        if (t.kind.thread) t.resetThread()
    }

    /** Let go: [t] becomes a free toy moving as it was. */
    fun release(t: ToyBody) {
        t.state = ToyState.FREE
        t.airborne = true
        t.overCat = false
        t.stillFor = 0f
        t.zGoal = Float.NaN
    }

    private fun placePivot(t: ToyBody, ds: Float) {
        t.pivotX = t.anchorX + t.kind.holdX * t.holderDir * ds
        t.pivotY = t.anchorY + t.kind.holdY * ds
    }

    /** One footfall of the cat: a bounce down into the toy and a sway, alternating sides. */
    fun footfall(t: ToyBody, strength: Float, ds: Float) {
        val k = t.kind
        t.footSide = -t.footSide
        t.vy += k.footfallKick * strength * ds
        t.vx += t.footSide * k.footfallKick * k.footfallSway * strength * ds
    }

    /** Move a held toy one tick. */
    fun step(t: ToyBody, dt: Float, ds: Float) {
        t.age += dt
        t.bumpedNow = false
        t.squashX.step(0f, dt)
        t.squashY.step(0f, dt)
        placePivot(t, ds)
        if (t.kind.hold == HoldStyle.HANG) hang(t, dt, ds) else mouth(t, dt, ds)
        clampToGround(t, ds)
        t.mirrored = t.holderDir < 0
        if (t.kind.thread) stepThread(t, dt, ds)
        t.compose()
    }

    private fun mouth(t: ToyBody, dt: Float, ds: Float) {
        val k = t.kind
        val w = Mathx.TWO_PI * k.holdHz
        val sag = ToyTuning.GRAVITY * ds / (w * w)
        val ax = -w * w * (t.x - t.pivotX) - 2f * k.holdZeta * w * t.vx
        val ay = -w * w * (t.y - (t.pivotY + sag)) - 2f * k.holdZeta * w * t.vy
        t.vx += ax * dt
        t.vy += ay * dt
        t.x += t.vx * dt
        t.y += t.vy * dt
        // A ball in the mouth turns a little with the swing instead of staying locked.
        t.spin += (-t.angle * ROLL_DAMP - t.spin * 2f) * dt
        t.angle += t.spin * dt
    }

    private fun hang(t: ToyBody, dt: Float, ds: Float) {
        val k = t.kind
        val rod = k.rodLength * ds
        val dx = t.x - t.pivotX
        val dy = t.y - t.pivotY
        val d = sqrt(dx * dx + dy * dy)
        if (d < MIN_ROD || d > rod * CarryTuning.MAX_STRETCH) {
            t.x = t.pivotX
            t.y = t.pivotY + rod
            t.vx = t.anchorVx
            t.vy = t.anchorVy
            return
        }
        val nx = dx / d
        val ny = dy / d
        val w = Mathx.TWO_PI * k.holdHz
        val rel = (t.vx - t.anchorVx) * nx + (t.vy - t.anchorVy) * ny
        val pull = -w * w * (d - rod) - 2f * CarryTuning.ROD_DAMP_ZETA * w * rel
        var ax = nx * pull
        var ay = ny * pull + ToyTuning.GRAVITY * k.gravityScale * ds
        if (k.flutter > 0f) {
            val speed = Mathx.abs(t.anchorVx) + Mathx.abs(t.anchorVy)
            val share = FLUTTER_BASE_SHARE + Mathx.clamp(speed / FLUTTER_SPEED_FULL, 0f, 1f)
            val a = k.flutter * share * Mathx.sin(t.age * FLUTTER_HZ * Mathx.TWO_PI) * ds
            ax += -ny * a
            ay += nx * a
        }
        t.vx += ax * dt
        t.vy += ay * dt
        val drag = 1f / (1f + k.swingDrag * dt)
        t.vx = t.anchorVx + (t.vx - t.anchorVx) * drag
        t.vy = t.anchorVy + (t.vy - t.anchorVy) * drag
        t.x += t.vx * dt
        t.y += t.vy * dt
    }

    private const val MIN_ROD = 0.01f

    /** A toy never goes through the surface the cat stands on. */
    private fun clampToGround(t: ToyBody, ds: Float) {
        val ground = t.holderGround
        val limit = ground - t.kind.halfHeight * ds
        if (t.y > limit) {
            t.y = limit
            if (t.vy > 0f) t.vy = -t.vy * GROUND_BOUNCE
        }
    }

    /**
     * The thread of a yarn ball: a chain that follows the ball, hangs under gravity and drags on
     * the ground (verlet, one pass of distance constraints from the ball to the end).
     */
    fun stepThread(t: ToyBody, dt: Float, ds: Float) {
        val n = ToyBody.THREAD_POINTS
        val seg = ToyBody.THREAD_SEGMENT * ds
        val g = ToyTuning.GRAVITY * ds * dt * dt
        val ground = if (t.state == ToyState.HELD) t.holderGround else World.groundY(t.surface, t.z)
        var px = t.x
        var py = t.y + t.kind.halfHeight * ds * THREAD_LIFT
        for (i in 0 until n) {
            val ix = i * 2
            val iy = ix + 1
            val vx = (t.thread[ix] - t.threadPrev[ix]) * THREAD_DAMP
            val vy = (t.thread[iy] - t.threadPrev[iy]) * THREAD_DAMP
            t.threadPrev[ix] = t.thread[ix]
            t.threadPrev[iy] = t.thread[iy]
            var x = t.thread[ix] + vx
            var y = t.thread[iy] + vy + g
            if (y > ground) {
                y = ground
                t.threadPrev[ix] = x - vx * THREAD_FRICTION
            }
            val dx = x - px
            val dy = y - py
            val d = sqrt(dx * dx + dy * dy)
            if (d > seg) {
                x = px + dx / d * seg
                y = py + dy / d * seg
                if (y > ground) y = ground
            }
            t.thread[ix] = x
            t.thread[iy] = y
            px = x
            py = y
        }
    }
}
