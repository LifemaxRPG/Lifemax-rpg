package com.lifemax.rpg.feature.pet.body

import kotlin.math.sqrt

/** The stages of a planned leap, in order. */
internal enum class LeapPhase {
    NONE,

    /** Turning (the turn row) to face the way the leap goes. */
    TURN,

    /**
     * A pause before a big jump: a look up at the target and, near the limit, a measuring wiggle.
     */
    MEASURE,

    /** The run-up to the take-off point. */
    APPROACH,

    /** The crouch (the first frames of `jump_up`). */
    CROUCH,

    /** In the air, from the push to touchdown. */
    AIR,

    /** The landing squash and settle, before the next step may start. */
    LAND,
}

/** What the director needs from the body it works on: playing a clip and the stride clips. */
internal interface LeapHost {
    /** Start [clip] at [phase] (the body also squashes for a change of posture). */
    fun play(clip: Clip, phase: Float)

    /** Play the stride clip for the current gait; false if the cat is standing. */
    fun stride(dt: Float): Boolean
}

/**
 * Acts out one [PlanStep.Leap] with the lab's jump rows: face the target (the `turn` row), look up
 * and measure a hard jump, run up if the planner asked for it, crouch (`jump_up`), fly a true
 * ballistic arc (`jump_air`: rising frame, then falling frame) that ends exactly on the target, and
 * land (`jump_land` squash and settle). Time is physics: gravity is [Tuning.Parkour.GRAVITY], the
 * flight time and apex come from the planned arc, so a high jump hangs longer than a hop and a drop
 * falls faster than it rose.
 *
 * Back to back (`chained`): when the next step is a leap too, the landing is cut short and the next
 * leap begins from that landing, so a chain such as floor, counter, fridge flows. The next leap
 * skips its own long crouch; a hard one still pauses to look and measure.
 *
 * Nothing here allocates; [CatBody] reads the one-tick flags and the arc fields to compose the
 * pose.
 */
internal class LeapDirector(
    private val k: Kinematics,
    private val turn: Turn,
    private val loco: Locomotion,
    private val player: ClipPlayer,
    private val springs: BodySprings,
    private val attention: Attention,
    private val host: LeapHost,
) {
    var phase = LeapPhase.NONE
        private set

    /** True on the tick the feet leave the ground. */
    var launchedNow = false
        private set

    /** True on the tick the paws touch down. */
    var landedNow = false
        private set

    /** Body tilt along the arc plus the measuring wiggle, radians. */
    var arcRotation = 0f
        private set

    /** Height of the cat above the ground line under it, px. */
    var height = 0f
        private set

    /** Row of the ground under the cat: from the take-off ground to the landing ground. */
    var shadowY = 0f
        private set

    val active: Boolean
        get() = phase != LeapPhase.NONE

    val airborne: Boolean
        get() = phase == LeapPhase.AIR

    /** True until the feet leave the ground: the leap may still be dropped. */
    val canCancel: Boolean
        get() = phase != LeapPhase.AIR && phase != LeapPhase.LAND

    /** The surface being leapt to. */
    var target = SurfaceId.FLOOR
        private set

    // plan
    private var x0 = 0f
    private var y0 = 0f
    private var z0 = 0f
    private var x1 = 0f
    private var y1 = 0f
    private var z1 = 0f
    private var takeOffX = 0f
    private var apex = 0f
    private var effort = 0f
    private var dir = 1
    private var kind = LeapKind.LEAP
    private var running = false
    private var chained = false
    private var linkedIn = false
    private var linkedOut = false

    // flight
    private var v0 = 0f
    private var flight = 0f
    private var fall = 0f

    // clocks
    private var t = 0f
    private var measureSeconds = 0f
    private var crouchSeconds = 0f
    private var landSeconds = 0f
    private var pushLeft = 0f

    /**
     * Begin acting out [step]. [nextIsLeap] says the plan goes straight on to another leap (this
     * one lands into it).
     */
    fun begin(step: PlanStep.Leap, nextIsLeap: Boolean) {
        val p = Tuning.Parkour
        target = step.surface
        val dest = World.surface(step.surface)
        x1 =
            if (step.surface == SurfaceId.FLOOR) World.clampHouse(step.x)
            else Mathx.clamp(step.x, dest.x0, dest.x1)
        z1 = if (step.surface == SurfaceId.FLOOR) k.z else dest.z
        y1 = World.groundY(step.surface, z1)
        takeOffX = step.takeOffX
        y0 = k.groundY
        z0 = k.z
        val rise = y0 - y1
        val dx = Mathx.abs(x1 - takeOffX)
        dir = if (dx > FACE_MIN_PX) Mathx.sign(x1 - takeOffX).toInt() else turn.dir
        effort = step.effort
        apex = if (step.apex > 0f) step.apex else naturalApex(rise, dx)
        kind = LeapKind.of(rise, dx, apex)
        running = Mathx.abs(takeOffX - k.x) > RUN_UP_DETECT_PX
        chained = nextIsLeap
        linkedIn = linkedOut
        linkedOut = false
        measureSeconds = measureFor()
        crouchSeconds = if (linkedIn || running) p.CROUCH_CHAIN_S else p.CROUCH_S
        landSeconds =
            if (kind == LeapKind.STEP) p.STEP_LAND_S else if (chained) p.LAND_CHAIN_S else p.LAND_S
        launchedNow = false
        landedNow = false
        arcRotation = 0f
        height = 0f
        shadowY = y0
        t = 0f
        phase = LeapPhase.NONE
        nextPhase(LeapPhase.TURN)
    }

    /** Abandon a leap that has not left the ground. */
    fun cancel() {
        phase = LeapPhase.NONE
        arcRotation = 0f
        height = 0f
        linkedOut = false
    }

    /** Advance one tick. True when the leap, landing and settle included, is over. */
    fun update(dt: Float): Boolean {
        launchedNow = false
        landedNow = false
        when (phase) {
            LeapPhase.TURN -> turnTick(dt)
            LeapPhase.MEASURE -> measureTick(dt)
            LeapPhase.APPROACH -> approachTick(dt)
            LeapPhase.CROUCH -> crouchTick(dt)
            LeapPhase.AIR -> airTick(dt)
            LeapPhase.LAND -> return landTick(dt)
            LeapPhase.NONE -> Unit
        }
        if (phase != LeapPhase.AIR && phase != LeapPhase.NONE) k.y = k.groundY
        return false
    }

    // ---------------------------------------------------------------- phases

    /** Enter the first phase from [from] on that the leap needs. */
    private fun nextPhase(from: LeapPhase) {
        var next = from
        if (next == LeapPhase.TURN) {
            if (turn.dir != dir) {
                startTurn()
                return
            }
            next = LeapPhase.MEASURE
        }
        if (next == LeapPhase.MEASURE) {
            if (measureSeconds > 0f) {
                startMeasure()
                return
            }
            next = LeapPhase.APPROACH
        }
        if (next == LeapPhase.APPROACH) {
            if (running) {
                startApproach()
                return
            }
            next = LeapPhase.CROUCH
        }
        if (next == LeapPhase.CROUCH) {
            if (kind != LeapKind.STEP) {
                startCrouch()
                return
            }
        }
        launch()
    }

    private fun startTurn() {
        phase = LeapPhase.TURN
        loco.reset()
        host.play(Clips.TURN, 0f)
    }

    private fun turnTick(dt: Float) {
        loco.update(dt, true)
        player.advanceAtBaseRate(dt)
        if (player.finished) {
            turn.snapTo(dir)
            nextPhase(LeapPhase.MEASURE)
        }
    }

    private fun measureFor(): Float {
        val p = Tuning.Parkour
        if (kind == LeapKind.STEP || effort < p.HESITATE_FROM) return 0f
        val pause = p.HESITATE_BASE_S + p.HESITATE_PER_EFFORT_S * effort
        val base = if (linkedIn) pause * LINKED_PAUSE_SHARE else pause
        return base + if (effort >= p.MEASURE_FROM) p.MEASURE_S else 0f
    }

    private fun startMeasure() {
        phase = LeapPhase.MEASURE
        t = 0f
        loco.reset()
        host.play(Clips.JUMP_UP, 0f)
        player.seek(0f)
        attention.glance(Tuning.Parkour.LOOK_FORWARD, Tuning.Parkour.LOOK_UP, measureSeconds)
    }

    private fun measureTick(dt: Float) {
        val p = Tuning.Parkour
        loco.update(dt, true)
        t += dt
        player.seek(0f)
        val wiggleStart = measureSeconds - p.MEASURE_S
        arcRotation =
            if (effort >= p.MEASURE_FROM && t > wiggleStart) {
                val into = (t - wiggleStart) / p.MEASURE_S
                Mathx.sin(Mathx.TWO_PI * p.MEASURE_HZ * (t - wiggleStart)) *
                    p.MEASURE_ROTATION *
                    Mathx.sin(Mathx.PI * Mathx.clamp(into, 0f, 1f))
            } else 0f
        if (t >= measureSeconds) {
            arcRotation = 0f
            nextPhase(LeapPhase.APPROACH)
        }
    }

    private fun startApproach() {
        phase = LeapPhase.APPROACH
        // The goal lies well past the take-off so the cat never brakes for it: it is cut at the
        // mark.
        loco.setGoal(takeOffX + dir * OVERSHOOT_PX, k.z, true)
    }

    private fun approachTick(dt: Float) {
        loco.update(dt, false)
        host.stride(dt)
        if ((k.x - takeOffX) * dir >= 0f) nextPhase(LeapPhase.CROUCH)
    }

    private fun startCrouch() {
        phase = LeapPhase.CROUCH
        t = 0f
        if (running) {
            // Off a running start there is no pause: the push comes at once, at speed.
            launch()
            return
        }
        loco.reset()
        host.play(Clips.JUMP_UP, 0f)
    }

    private fun crouchTick(dt: Float) {
        loco.update(dt, true)
        t += dt
        player.advance(dt, CROUCH_FRAMES_SHARE / crouchSeconds)
        arcRotation =
            Mathx.sin(Mathx.TWO_PI * WIGGLE_HZ * t) *
                WIGGLE_AMPLITUDE *
                Mathx.clamp(t / crouchSeconds, 0f, 1f)
        if (t >= crouchSeconds) launch()
    }

    private fun launch() {
        val p = Tuning.Parkour
        phase = LeapPhase.AIR
        x0 = k.x
        y0 = k.y
        z0 = k.z
        val rise = y0 - y1
        val g = p.GRAVITY
        flight = Ballistics.flightTime(apex, rise, g)
        v0 = sqrt(2f * g * apex)
        fall = sqrt(2f * g * maxOf(0f, apex - rise))
        t = 0f
        pushLeft = PUSH_S
        loco.reset()
        launchedNow = true
        arcRotation = 0f
        host.play(Clips.JUMP_UP, PUSH_PHASE)
        springs.stretch(p.LAUNCH_STRETCH * (if (kind == LeapKind.STEP) STEP_STRETCH else 1f))
    }

    private fun airTick(dt: Float) {
        val g = Tuning.Parkour.GRAVITY
        t += dt
        val u = Mathx.clamp(t / flight, 0f, 1f)
        val lift = Ballistics.lift(v0, minOf(t, flight), g)
        k.x = Mathx.lerp(x0, x1, u)
        k.z = Mathx.lerp(z0, z1, u)
        shadowY = Mathx.lerp(y0, y1, u)
        k.y = y0 - lift
        height = maxOf(0f, shadowY - k.y)
        arcRotation = -dir * Tuning.Parkour.TILT * Mathx.cos(Mathx.PI * u)
        pushLeft -= dt
        if (pushLeft <= 0f) {
            if (player.clip !== Clips.JUMP_AIR) host.play(Clips.JUMP_AIR, 0f)
            player.seek(if (t < v0 / g) 0f else FALLING_PHASE)
        }
        if (u >= 1f) touchDown()
    }

    private fun touchDown() {
        val p = Tuning.Parkour
        k.x = x1
        k.z = z1
        k.y = y1
        k.surface = target
        k.vx = 0f
        k.vz = 0f
        shadowY = y1
        height = 0f
        arcRotation = 0f
        phase = LeapPhase.LAND
        landedNow = true
        t = 0f
        host.play(Clips.JUMP_LAND, 0f)
        springs.squash(
            Mathx.clamp(p.IMPACT_BASE + fall * p.IMPACT_PER_PX_S, p.IMPACT_BASE, p.IMPACT_MAX)
        )
    }

    private fun landTick(dt: Float): Boolean {
        val p = Tuning.Parkour
        loco.update(dt, true)
        k.y = k.groundY
        t += dt
        // Chained: only the squash frames play, then the next leap takes over from the bend.
        val share = if (chained) CHAIN_LAND_SHARE else 1f
        if (player.clip === Clips.JUMP_LAND) player.advance(dt, share / landSeconds)
        val total = landSeconds + if (chained || kind == LeapKind.STEP) 0f else p.SETTLE_S
        if (t < total) return false
        phase = LeapPhase.NONE
        linkedOut = chained
        return true
    }

    /** An easy apex for a leap the planner left unspecified. */
    private fun naturalApex(rise: Float, dx: Float): Float {
        val arc = JumpCapability.ADULT.arc(rise, dx, 0f)
        return arc?.apex ?: (JumpCapability.ADULT.lowestApex(rise) + dx * FALLBACK_APEX_PER_PX)
    }

    private companion object {
        const val FACE_MIN_PX = 1f
        const val RUN_UP_DETECT_PX = 6f
        const val OVERSHOOT_PX = 60f
        const val LINKED_PAUSE_SHARE = 0.5f
        const val CROUCH_FRAMES_SHARE = 0.75f
        const val PUSH_PHASE = 0.8f
        const val PUSH_S = 0.08f
        const val FALLING_PHASE = 0.75f
        const val CHAIN_LAND_SHARE = 0.67f
        const val STEP_STRETCH = 0.4f
        const val WIGGLE_HZ = 9f
        const val WIGGLE_AMPLITUDE = 0.035f
        const val FALLBACK_APEX_PER_PX = 0.4f
    }
}
