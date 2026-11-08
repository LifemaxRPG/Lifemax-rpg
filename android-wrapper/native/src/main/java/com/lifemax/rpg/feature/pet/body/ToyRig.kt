package com.lifemax.rpg.feature.pet.body

import kotlin.math.sqrt

/** What the toy directors need from the cat body beyond the leap host's clip playing. */
internal interface ToyHost : LeapHost {
    /** Hop on the floor to [x] (the pounce); the body reports the end through [ToyDirector]. */
    fun hop(x: Float)

    /** Report an event of the step being worked on. */
    fun emit(type: CatEventType)

    /** Body rotation from the jump and the leap, radians (it turns the mouth with the body). */
    val bodyRotation: Float

    /** True on the tick the paws touched down from a hop. */
    val hopLanded: Boolean
}

/**
 * What every toy act shares: the cat's body parts, the toy it holds, and the mechanics of holding
 * (the mouth anchor of the current frame, the lift into the mouth, the carry, the release). The
 * acts themselves are in [ToyDirector] (pick up, toss, set down) and [PlayActs] (swat, kick,
 * pounce, chase).
 */
internal class ToyRig(
    val k: Kinematics,
    val turn: Turn,
    val loco: Locomotion,
    val player: ClipPlayer,
    private val springs: BodySprings,
    val host: ToyHost,
    seed: Int,
) {
    /** The toys of the house, or null (every toy step then ends at once). */
    var field: ToyField? = null

    /** Where the mouth is in each frame. */
    var mouths: MouthAnchors = estimates

    /** The toy in the mouth, being lifted into it or carried. */
    var held: ToyBody? = null
        private set

    /** Ear and tail states the acts ask for (null: the brain's). */
    var ear: EarState? = null
    var tail: TailState? = null

    val rng = Rng.derive(seed, SALT)
    private val where = FloatArray(2)
    private val filterX = Spring(ANCHOR_FILTER)
    private val filterY = Spring(ANCHOR_FILTER)

    /** Depth scale of the cat. */
    val ds: Float
        get() = World.depthScale(k.z)

    /** The toy with [id] on the cat's own surface, or null. */
    fun toy(id: Int): ToyBody? = field?.get(id)

    // ---------------------------------------------------------------- moving about

    /** Walk toward ([x], [z]); true on arrival. Plays the stride. */
    fun walkTo(x: Float, z: Float, run: Boolean, dt: Float): Boolean {
        loco.setGoal(x, if (k.surface == SurfaceId.FLOOR) z else k.z, run && held == null)
        val arrived = loco.update(dt, false)
        if (!host.stride(dt)) player.advanceAtBaseRate(dt)
        return arrived
    }

    /** Stand still and turn to face [dir]; true when done turning. */
    fun faceTick(dir: Int, dt: Float): Boolean {
        turn.request(dir)
        loco.update(dt, true)
        return !turn.active && turn.dir == dir
    }

    /** +1 or -1: the way from the cat to [x]. */
    fun dirTo(x: Float): Int =
        if (Mathx.abs(x - k.x) < 1f) turn.dir else Mathx.sign(x - k.x).toInt()

    // ---------------------------------------------------------------- holding

    /** The toy begins to rise into the mouth (see [liftTick]). */
    fun beginLift(toy: ToyBody) {
        toy.state = ToyState.LIFT
        toy.overCat = true
        toy.liftT = 0f
        toy.liftX = toy.x
        toy.liftY = toy.y
        toy.airborne = false
        toy.vx = 0f
        toy.vy = 0f
        held = toy
        snapAnchor(toy)
    }

    /** Let the toy go as a free toy moving as it was (the tosses add their flick after). */
    fun release(): ToyBody? {
        val toy = held ?: return null
        toy.surface = k.surface
        Carry.release(toy)
        held = null
        host.emit(CatEventType.ToyReleased)
        return toy
    }

    /** Drop whatever is held without ceremony (a plan cut mid-lift). */
    fun dropIfLifting() {
        if (held?.state == ToyState.LIFT) release()
    }

    /** Forget the filter's lag (the cat was put down somewhere else). */
    fun snapAnchor(toy: ToyBody?) {
        val t = toy ?: held ?: return
        measure(t)
        filterX.snap(where[0])
        filterY.snap(where[1])
    }

    private fun measure(t: ToyBody) {
        val clip = turn.shown(player.clip)
        val frame = turn.shownFrame(player.frame)
        MouthAnchors.locate(
            mouths.u(clip.id, frame),
            mouths.v(clip.id, frame),
            clip.bottom,
            turn.dir < 0,
            springs.scaleX,
            springs.scaleY,
            springs.rotation.pos + host.bodyRotation,
            ds,
            k.x,
            k.y + springs.bob.pos,
            where,
        )
        t.z = k.z
        t.surface = k.surface
        t.holderGround = k.groundY
        t.holderDir = turn.dir
    }

    /** One footfall of the walking cat reaches the toy in the mouth. */
    fun footfall(share: Float) {
        val toy = held ?: return
        if (toy.state == ToyState.HELD) Carry.footfall(toy, share, ds)
    }

    /** Called at the end of every body tick: the toy in the mouth follows the head. */
    fun afterTick(dt: Float) {
        val toy = held ?: return
        measure(toy)
        val before0 = filterX.pos
        val before1 = filterY.pos
        filterX.step(where[0], dt)
        filterY.step(where[1], dt)
        toy.anchorX = filterX.pos
        toy.anchorY = filterY.pos
        toy.anchorVx = (filterX.pos - before0) / dt
        toy.anchorVy = (filterY.pos - before1) / dt
        if (toy.state == ToyState.LIFT) liftTick(toy, dt) else Carry.step(toy, dt, ds)
    }

    private fun liftTick(t: ToyBody, dt: Float) {
        t.liftT += dt / CarryTuning.LIFT_S
        val u = minOf(1f, t.liftT)
        val e = u * u * (3f - 2f * u)
        val tx = t.anchorX + t.kind.holdX * turn.dir * ds
        val ty = t.anchorY + t.kind.holdY * ds
        val arc = sin01(u) * CarryTuning.LIFT_ARC_PX * ds
        t.x = Mathx.lerp(t.liftX, tx, e)
        t.y = Mathx.lerp(t.liftY, ty, e) - arc
        t.pivotX = tx
        t.pivotY = ty
        t.squashY.snap(CarryTuning.LIFT_STRETCH * sin01(u))
        t.squashX.snap(-CarryTuning.LIFT_STRETCH * sin01(u) * Tuning.Squash.VOLUME_SHARE)
        t.compose()
        if (u >= 1f) {
            Carry.grab(t, turn.dir, ds)
            t.vx = t.anchorVx
            t.vy = t.anchorVy
            t.squashY.snap(0f)
            t.squashX.snap(0f)
            t.squashY.displace(-CarryTuning.GRAB_SQUASH)
            t.squashX.displace(CarryTuning.GRAB_SQUASH * Tuning.Squash.VOLUME_SHARE)
            springs.squash(GRAB_BODY_SQUASH)
            host.emit(CatEventType.ToyGrabbed)
        }
    }

    private fun sin01(u: Float): Float = Mathx.sin(Mathx.PI * u)

    /**
     * Distance from the mouth anchor of [clip]'s [frame] to the cat's feet along its facing, px.
     */
    fun mouthReach(clip: Clip, frame: Int): Float =
        (mouths.u(clip.id, frame) - MouthAnchors.FRAME_HALF_WIDTH) * ds

    /** Straight-line distance, px. */
    fun dist(ax: Float, ay: Float, bx: Float, by: Float): Float {
        val dx = ax - bx
        val dy = ay - by
        return sqrt(dx * dx + dy * dy)
    }

    companion object {
        private const val SALT = 0x70A5
        private const val GRAB_BODY_SQUASH = 0.25f

        /** The mouth anchor follows the head through a quick critically damped filter. */
        private val ANCHOR_FILTER = SpringParams(12f, 1f)

        /** Estimated anchors for hosts that never attach the lab table (built on first use). */
        private val estimates: MouthAnchors by lazy { MouthAnchors.estimated() }
    }
}
