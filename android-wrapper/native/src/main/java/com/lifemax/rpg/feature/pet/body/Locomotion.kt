package com.lifemax.rpg.feature.pet.body

import kotlin.math.sqrt

/** Where the cat is and how fast it is going: the state locomotion and jumps both write. */
internal class Kinematics {
    var x = 0f
    var z = World.ART_Z
    var y = World.FLOOR_Y_ART
    var vx = 0f
    var vz = 0f
    var surface = SurfaceId.FLOOR

    /** Acceleration along x over the last tick, px/s^2: leans the body and swings the tail. */
    var ax = 0f

    /** Feet row of the ground under the cat. */
    val groundY: Float
        get() = World.groundY(surface, z)
}

/** Which stride the legs are playing. */
internal enum class Gait {
    STAND,
    WALK,
    RUN,
    FRONT,
    BACK,
}

/**
 * Walking in the plane: eased acceleration and deceleration on each axis, a gait picked from the
 * actual speed (so walk, trot and run blend by speed, with hysteresis), and a skid when a gallop is
 * stopped hard. Stride phase is not kept here: the clip player advances it by `speed / stridePx`,
 * so the feet never slip.
 */
internal class Locomotion(private val k: Kinematics, private val turn: Turn) {
    var run = false
    var weak = false

    var gait = Gait.STAND
        private set

    /** Ground speed in px/s, with depth counted as the pixels it covers on screen. */
    var speed = 0f
        private set

    /** True when the cat is moving fast enough to need legs. */
    val moving: Boolean
        get() = speed > Tuning.Walk.STOP_SPEED

    /** True on the tick a hard stop begins; [CatBody] turns it into a lean and a squash. */
    var skidStarted = false
        private set

    private var goalX = 0f
    private var goalZ = 0f
    private var skidArmed = true

    /** Head for ([x], [z]) on the current surface. */
    fun setGoal(x: Float, z: Float, running: Boolean) {
        goalX = x
        goalZ = z
        run = running
    }

    /**
     * Advance one tick. With [braking] the cat comes to rest where it is; otherwise it heads for
     * the goal and the result says whether it has arrived.
     */
    fun update(dt: Float, braking: Boolean): Boolean {
        skidStarted = false
        val onFloor = k.surface == SurfaceId.FLOOR
        var dx = if (braking) 0f else goalX - k.x
        val dz = if (braking || !onFloor) 0f else goalZ - k.z
        val pause = steerTurn(dx, dz)
        if (pause) dx = 0f
        val before = k.vx
        val brakingAll = braking || pause
        k.vx = approach(k.vx, if (pause) 0f else dx, vMaxX(), accelX(), decelX(), dt, brakingAll)
        k.vz =
            approach(
                k.vz,
                if (pause) 0f else dz,
                vMaxZ(),
                Tuning.Walk.Z_ACCEL,
                Tuning.Walk.Z_DECEL,
                dt,
                brakingAll,
            )
        k.ax = (k.vx - before) / dt
        detectSkid(before, dt)
        val arrivedX = advance(brakingAll, goalX, dx, true, dt)
        val arrivedZ = !onFloor || advance(brakingAll, goalZ, dz, false, dt)
        speed = sqrt(k.vx * k.vx + sq(k.vz * Tuning.Walk.Z_PX_PER_UNIT))
        updateGait()
        return !braking && !pause && arrivedX && arrivedZ
    }

    /** Stop dead (teleport, or a plan that never moves again). */
    fun reset() {
        k.vx = 0f
        k.vz = 0f
        k.ax = 0f
        speed = 0f
        gait = Gait.STAND
    }

    private fun sq(v: Float) = v * v

    /** Ask the turn to face the way we are going; true while the cat must hold still for it. */
    private fun steerTurn(dx: Float, dz: Float): Boolean {
        val horizontal =
            Mathx.abs(dx) >= Tuning.Turn.MIN_DISTANCE_PX &&
                Mathx.abs(dx) > Mathx.abs(dz) * Tuning.Walk.Z_PX_PER_UNIT
        if (horizontal) turn.request(if (dx > 0f) 1 else -1)
        return turn.active
    }

    private fun vMaxX(): Float {
        val base =
            if (run) Tuning.Walk.RUN_SPEED
            else if (weak) Tuning.Walk.WEAK_SPEED else Tuning.Walk.WALK_SPEED
        return base * World.depthScale(k.z)
    }

    private fun vMaxZ(): Float {
        val gaitMul = if (run) Tuning.Walk.Z_RUN_MUL else if (weak) Tuning.Walk.Z_WEAK_MUL else 1f
        return Tuning.Walk.Z_SPEED * World.walkSpeedPct(k.z) / 100f * gaitMul
    }

    private fun accelX() = if (run) Tuning.Walk.RUN_ACCEL else Tuning.Walk.ACCEL

    private fun decelX() = if (run) Tuning.Walk.RUN_DECEL else Tuning.Walk.DECEL

    /**
     * Ease a velocity toward the speed that still lets the cat stop at the target: at most `vMax`,
     * at most `sqrt(2 * decel * distance)` measured from where the next tick will leave the cat,
     * with a margin so the braking never needs more than the cat has.
     */
    private fun approach(
        v: Float,
        d: Float,
        vMax: Float,
        acc: Float,
        dec: Float,
        dt: Float,
        brake: Boolean,
    ): Float {
        val want =
            if (brake) {
                0f
            } else {
                val ad = Mathx.abs(d)
                val power = 2f * dec * Tuning.Walk.BRAKE_MARGIN
                val zone = Tuning.Walk.LINEAR_ZONE_PX
                val ahead = maxOf(0f, ad - Mathx.abs(v) * dt)
                // Inside the last half pixel the square-root law is replaced by a proportional one
                // that meets it at the edge, so the cat glides in instead of snapping to a halt.
                val law = if (ad > zone) sqrt(power * ahead) else ad * sqrt(power / zone)
                val mag = minOf(vMax, law, ad / dt)
                Mathx.sign(d) * mag
            }
        val slowing = Mathx.abs(want) < Mathx.abs(v) || want * v < 0f
        val rate = (if (slowing) dec else acc) * dt
        return Mathx.moveToward(v, want, rate)
    }

    /** Integrate one axis; snap onto the goal when it is reached or crossed. */
    private fun advance(hold: Boolean, goal: Float, d: Float, isX: Boolean, dt: Float): Boolean {
        val pos = if (isX) k.x else k.z
        val vel = if (isX) k.vx else k.vz
        val next = pos + vel * dt
        var arrived = false
        var out = next
        var outV = vel
        if (!hold) {
            val crossed = d * (goal - next) <= 0f
            val close =
                Mathx.abs(goal - next) < Tuning.Walk.ARRIVE_DISTANCE &&
                    Mathx.abs(vel) < Tuning.Walk.ARRIVE_SPEED
            if (crossed || close) {
                out = goal
                outV = 0f
                arrived = true
            }
        }
        if (isX) {
            k.x = out
            k.vx = outV
        } else {
            k.z = out
            k.vz = outV
        }
        return arrived || (!hold && Mathx.abs(d) < Tuning.Walk.ARRIVE_DISTANCE)
    }

    private fun detectSkid(before: Float, dt: Float) {
        val decel = (Mathx.abs(before) - Mathx.abs(k.vx)) / dt
        val hard =
            decel >= Tuning.Walk.SKID_DECEL && Mathx.abs(before) >= Tuning.Walk.SKID_MIN_SPEED
        if (hard && skidArmed) {
            skidStarted = true
            skidArmed = false
        }
        if (Mathx.abs(k.vx) < Tuning.Walk.STOP_SPEED) skidArmed = true
    }

    /** Choose the stride from the actual speed, with a dead band so it never flickers. */
    private fun updateGait() {
        val vertical = Mathx.abs(k.vz * Tuning.Walk.Z_PX_PER_UNIT)
        val horizontal = Mathx.abs(k.vx)
        val threshold = if (gait == Gait.STAND) Tuning.Walk.START_SPEED else Tuning.Walk.STOP_SPEED
        gait =
            when {
                speed < threshold -> Gait.STAND
                vertical > horizontal * verticalRatio() -> if (k.vz > 0f) Gait.FRONT else Gait.BACK
                weak -> Gait.WALK
                speed > Tuning.Walk.RUN_ENTER_SPEED ||
                    (gait == Gait.RUN && speed > Tuning.Walk.RUN_EXIT_SPEED) -> Gait.RUN
                else -> Gait.WALK
            }
    }

    private fun verticalRatio(): Float =
        if (gait == Gait.FRONT || gait == Gait.BACK) Tuning.Walk.VERTICAL_EXIT_RATIO
        else Tuning.Walk.VERTICAL_ENTER_RATIO
}

/**
 * A turn is a short dip with the mirror at its middle, not a pop: the body narrows to a sliver, the
 * sprite flips at the narrowest point, and it widens again.
 */
internal class Turn {
    /** The way the sprite currently faces: +1 right, -1 left. It changes mid-turn. */
    var dir = 1
        private set

    /** Horizontal squash from the turn (1 when not turning). */
    var scaleX = 1f
        private set

    /** Vertical dip from the turn (1 when not turning). */
    var scaleY = 1f
        private set

    /** True on the tick the sprite flipped. */
    var flippedNow = false
        private set

    private var next = 1
    private var progress = NOT_TURNING
    private var flipped = false

    val active: Boolean
        get() = progress >= 0f

    /** Begin turning toward [wanted] unless already facing that way or already turning. */
    fun request(wanted: Int) {
        if (active || wanted == dir) return
        next = wanted
        progress = 0f
        flipped = false
    }

    /** Face [wanted] at once (placing the cat). */
    fun snapTo(wanted: Int) {
        dir = wanted
        progress = NOT_TURNING
        scaleX = 1f
        scaleY = 1f
    }

    fun update(dt: Float) {
        flippedNow = false
        if (!active) return
        progress += dt / Tuning.Turn.DURATION_S
        if (!flipped && progress >= HALF) {
            dir = next
            flipped = true
            flippedNow = true
        }
        if (progress >= 1f) {
            snapTo(dir)
            return
        }
        val dip = Mathx.sin(Mathx.PI * progress)
        scaleX = 1f - (1f - Tuning.Turn.MIN_SCALE_X) * dip
        scaleY = 1f - Tuning.Turn.DIP_Y * dip
    }

    private companion object {
        const val NOT_TURNING = -1f
        const val HALF = 0.5f
    }
}
