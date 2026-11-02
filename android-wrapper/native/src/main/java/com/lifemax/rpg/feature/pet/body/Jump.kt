package com.lifemax.rpg.feature.pet.body

/** The stages of a hop, in order. */
internal enum class JumpPhase {
    NONE,

    /** Turning to face the landing spot. */
    FACE,

    /** The crouch: weight goes down and back before the push. */
    ANTICIPATE,

    /** In the air, from launch to touchdown. */
    AIR,

    /** The landing squash, before the next step may start. */
    LAND,
}

/**
 * One hop, the same code for a hop to the sill, onto the tree or off the couch: face the spot,
 * crouch (anticipation, the `crouch` row), launch with a stretch, hang at the apex, land with a
 * squash and settle. Distance and height set the duration and the arc, as in the web room (`T =
 * 0.42 + 0.0035|dx| + 0.004|dy|`), but the body also gets the motion the web lacked.
 *
 * The hop writes the cat's position while it flies; [CatBody] reads the one-tick flags
 * [launchedNow] and [landedNow] to nudge the body springs and swap clips.
 */
internal class Jump(private val k: Kinematics) {
    var phase = JumpPhase.NONE
        private set

    /** The surface being jumped to. */
    var target = SurfaceId.FLOOR
        private set

    /** The direction the cat must face to jump (+1 or -1). */
    var wantedDir = 1
        private set

    /** True on the tick the legs push off. */
    var launchedNow = false
        private set

    /** True on the tick the paws touch down. */
    var landedNow = false
        private set

    /** Strength of the landing squash, 1 for an ordinary hop. */
    var impact = 1f
        private set

    /** Progress through the flight, 0..1. */
    var flightProgress = 0f
        private set

    /** Body tilt from the arc: nose up on the way up, nose down on the way down. */
    var arcRotation = 0f
        private set

    /** Height of the cat above the ground line under it. */
    var height = 0f
        private set

    /** Row of the ground the shadow lies on, which moves from take-off ground to landing ground. */
    var shadowY = 0f
        private set

    /** Length of the anticipation crouch, so the clip can be fitted to it. */
    var anticipationSeconds = 0f
        private set

    /** Length of the flight in seconds. */
    var flightSeconds = 0f
        private set

    val active: Boolean
        get() = phase != JumpPhase.NONE

    val airborne: Boolean
        get() = phase == JumpPhase.AIR

    private var t = 0f
    private var x0 = 0f
    private var y0 = 0f
    private var z0 = 0f
    private var x1 = 0f
    private var y1 = 0f
    private var z1 = 0f
    private var apexLift = 0f

    /** Plan a hop from where the cat stands to [surface] at [x]. */
    fun begin(surface: SurfaceId, x: Float) {
        target = surface
        val dest = World.surface(surface)
        x1 =
            if (surface == SurfaceId.FLOOR) World.clampHouse(x)
            else Mathx.clamp(x, dest.x0, dest.x1)
        z1 = if (surface == SurfaceId.FLOOR) k.z else dest.z
        y1 = World.groundY(surface, z1)
        x0 = k.x
        y0 = k.y
        z0 = k.z
        val dx = x1 - x0
        val dy = y1 - y0
        val dist = Mathx.abs(dx)
        wantedDir = if (dist > Tuning.Jump.FACE_MIN_DISTANCE) Mathx.sign(dx).toInt() else 0
        flightSeconds =
            Tuning.Jump.BASE_S + Tuning.Jump.PER_DX_S * dist + Tuning.Jump.PER_DY_S * Mathx.abs(dy)
        apexLift = Tuning.Jump.BASE_HEIGHT + maxOf(0f, -dy) * Tuning.Jump.RISE_SHARE
        anticipationSeconds =
            Mathx.clamp(
                Tuning.Jump.ANTICIPATE_BASE_S +
                    Tuning.Jump.ANTICIPATE_PER_PX * (dist + Mathx.abs(dy)),
                Tuning.Jump.ANTICIPATE_BASE_S,
                Tuning.Jump.ANTICIPATE_MAX_S,
            )
        impact =
            Mathx.clamp(
                Tuning.Jump.IMPACT_BASE + (apexLift + dy * HALF) * Tuning.Jump.IMPACT_PER_PX,
                Tuning.Jump.IMPACT_BASE,
                Tuning.Jump.IMPACT_MAX,
            )
        phase = JumpPhase.FACE
        t = 0f
        height = 0f
        arcRotation = 0f
        shadowY = y0
    }

    /** Abandon a hop that has not left the ground. */
    fun cancel() {
        phase = JumpPhase.NONE
        arcRotation = 0f
        height = 0f
    }

    /** True when the legs have not yet left the ground and the hop may still be dropped. */
    val canCancel: Boolean
        get() = phase == JumpPhase.FACE || phase == JumpPhase.ANTICIPATE

    /** Advance one tick. [facingOk] says the cat now faces the way it must. True when finished. */
    fun update(dt: Float, facingOk: Boolean): Boolean {
        launchedNow = false
        landedNow = false
        when (phase) {
            JumpPhase.FACE -> if (facingOk) startAnticipation()
            JumpPhase.ANTICIPATE -> anticipate(dt)
            JumpPhase.AIR -> fly(dt)
            JumpPhase.LAND -> return land(dt)
            JumpPhase.NONE -> Unit
        }
        return false
    }

    private fun startAnticipation() {
        phase = JumpPhase.ANTICIPATE
        t = 0f
    }

    private fun anticipate(dt: Float) {
        t += dt
        val wiggle =
            Mathx.sin(Mathx.TWO_PI * Tuning.Jump.WIGGLE_HZ * t) * Tuning.Jump.WIGGLE_AMPLITUDE
        arcRotation = wiggle * Mathx.clamp(t / anticipationSeconds, 0f, 1f)
        if (t >= anticipationSeconds) {
            phase = JumpPhase.AIR
            t = 0f
            launchedNow = true
            arcRotation = 0f
        }
    }

    private fun fly(dt: Float) {
        t += dt
        val u = Mathx.clamp(t / flightSeconds, 0f, 1f)
        // Time is warped so the cat slows near the top of the arc and hangs there a moment.
        val s = u + Tuning.Jump.HANG * Mathx.sin(Mathx.TWO_PI * u) / Mathx.TWO_PI
        val lift = 4f * apexLift * s * (1f - s)
        flightProgress = u
        k.x = Mathx.lerp(x0, x1, s)
        k.z = Mathx.lerp(z0, z1, s)
        shadowY = Mathx.lerp(y0, y1, s)
        k.y = shadowY - lift
        height = lift
        val facing = if (wantedDir == 0) 1f else wantedDir.toFloat()
        arcRotation = -facing * Tuning.Jump.TILT * Mathx.cos(Mathx.PI * u)
        if (u >= 1f) touchDown()
    }

    private fun touchDown() {
        k.x = x1
        k.z = z1
        k.y = y1
        k.surface = target
        k.vx = 0f
        k.vz = 0f
        shadowY = y1
        height = 0f
        arcRotation = 0f
        phase = JumpPhase.LAND
        landedNow = true
        t = 0f
    }

    private fun land(dt: Float): Boolean {
        t += dt
        if (t < Tuning.Jump.LAND_S) return false
        phase = JumpPhase.NONE
        return true
    }

    private companion object {
        const val HALF = 0.5f
    }
}
