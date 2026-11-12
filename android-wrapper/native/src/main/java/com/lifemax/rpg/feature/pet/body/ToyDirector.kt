package com.lifemax.rpg.feature.pet.body

/** The stages of the pick up, toss and set down acts. */
internal enum class ToyPhase {
    NONE,
    WALK,
    NOTICE,
    REACH,
    HEAD_UP,
    FACE,
    TOSS,
    DOWN,
    UP,
}

/**
 * Acts out the toy steps of a plan for one cat, as [LeapDirector] does the leaps. The steps:
 * - [PlanStep.PickUp]: **approach and notice**: the cat walks to its reach point (eased stop, ears
 *   forward, tail low), turns to the toy and pauses; **reach and bite**: the `pickup` row lowers
 *   the head; one frame before the bite frame the toy lifts from the floor along a short arc into
 *   the mouth anchor of the current frame, squashing as it is taken; the head comes up.
 * - [PlanStep.Carry] is an ordinary walk (the body plays the `carry` row while a toy is held; the
 *   toy hangs from the mouth anchor, see [Carry]).
 * - [PlanStep.Toss]: face the way, play `toss`; on its release frame the toy leaves the mouth with
 *   the velocity it had plus the head's flick (or the velocity that lands it on a given surface).
 * - [PlanStep.SetDown]: the head dips (the `pickup` row run forward to the floor and back), the toy
 *   is let go on the surface, in front.
 * - The play steps (swat, kick, pounce, chase) are in [PlayActs].
 *
 * Any toy step whose toy is missing, not on the cat's surface or not available ends at once.
 * Nothing here allocates.
 */
internal class ToyDirector(
    private val rig: ToyRig,
    private val player: ClipPlayer,
    private val springs: BodySprings,
    private val host: ToyHost,
) {
    private val play = PlayActs(rig, player, springs, host)
    private val aim = FloatArray(AIM_VALUES)

    var phase = ToyPhase.NONE
        private set

    private var toy: ToyBody? = null
    private var t = 0f
    private var dir = 1
    private var step: PlanStep? = null

    /** True while a toy step is being acted out. */
    val active: Boolean
        get() = phase != ToyPhase.NONE || play.active

    /** True when a plan cut now would be clean (no bite or flick in progress). */
    val canCut: Boolean
        get() =
            !play.active &&
                (phase == ToyPhase.NONE || phase == ToyPhase.WALK || phase == ToyPhase.NOTICE)

    /** True for the steps this director plays. */
    fun handles(step: PlanStep?): Boolean =
        step is PlanStep.PickUp ||
            step is PlanStep.Toss ||
            step is PlanStep.SetDown ||
            play.handles(step)

    /** Begin [step]. */
    fun begin(step: PlanStep) {
        this.step = step
        phase = ToyPhase.NONE
        t = 0f
        when (step) {
            is PlanStep.PickUp -> beginPickUp(step)
            is PlanStep.Toss -> beginToss(step)
            PlanStep.SetDown -> beginSetDown()
            else -> play.begin(step)
        }
    }

    /** Abandon the step in progress (a plan replaced it, or the cat was placed elsewhere). */
    fun cancel() {
        rig.dropIfLifting()
        phase = ToyPhase.NONE
        play.cancel()
        rig.ear = null
        rig.tail = null
    }

    private fun finish(): Boolean {
        phase = ToyPhase.NONE
        rig.ear = null
        rig.tail = null
        return true
    }

    /** Advance one tick of the step; true when it is over. */
    fun update(dt: Float): Boolean {
        if (play.active) return play.update(dt).also { if (it) finish() }
        return when (phase) {
            ToyPhase.WALK -> walkTick(dt)
            ToyPhase.NOTICE -> noticeTick(dt)
            ToyPhase.REACH -> reachTick(dt)
            ToyPhase.HEAD_UP -> headUpTick(dt)
            ToyPhase.FACE -> faceTick(dt)
            ToyPhase.TOSS -> tossTick(dt)
            ToyPhase.DOWN -> downTick(dt)
            ToyPhase.UP -> upTick(dt)
            ToyPhase.NONE -> true
        }
    }

    // ---------------------------------------------------------------- pick up

    private fun beginPickUp(s: PlanStep.PickUp) {
        val target = rig.toy(s.toyId)
        if (
            rig.held != null ||
                target == null ||
                !target.canBePicked ||
                target.surface != rig.k.surface
        ) {
            finish()
            return
        }
        toy = target
        dir = rig.dirTo(target.x)
        rig.ear = EarState.FORWARD
        rig.tail = TailState.LOW_SWISH
        phase = ToyPhase.WALK
    }

    private fun reachX(target: ToyBody): Float =
        target.x - dir * rig.mouthReach(Clips.PICKUP, BITE_FRAME)

    private fun walkTick(dt: Float): Boolean {
        val target = toy ?: return finish()
        if (!target.canBePicked) return finish()
        // a cat within a pixel of the toy keeps the side it chose: dirTo answers the way it faces
        // then, and the reach point behind it would flip with each turn it makes to walk there
        // (the cat turned back and forth on the spot for ever)
        if (Mathx.abs(target.x - rig.k.x) >= 1f) dir = rig.dirTo(target.x)
        val arrived = rig.walkTo(reachX(target), target.z, false, dt)
        if (arrived) {
            phase = ToyPhase.NOTICE
            t = 0f
            host.play(Clips.PICKUP, 0f)
            player.seek(0f)
        }
        return false
    }

    private fun noticeTick(dt: Float): Boolean {
        if (toy == null) return finish()
        val facing = rig.faceTick(dir, dt)
        player.seek(0f)
        t += dt
        if (facing && t >= CarryTuning.NOTICE_S) {
            phase = ToyPhase.REACH
            t = 0f
        }
        return false
    }

    private fun reachTick(dt: Float): Boolean {
        val target = toy ?: return finish()
        rig.loco.update(dt, true)
        player.advanceAtBaseRate(dt, CarryTuning.REACH_RATE)
        if (rig.held == null && player.frame >= BITE_FRAME - 1) {
            if (target.canBePicked) rig.beginLift(target) else return finish()
        }
        if (player.finished && target.state == ToyState.HELD) {
            phase = ToyPhase.HEAD_UP
            t = 0f
        }
        return false
    }

    private fun headUpTick(dt: Float): Boolean {
        rig.loco.update(dt, true)
        t += dt
        return t >= CarryTuning.HEAD_UP_S && finish()
    }

    // ---------------------------------------------------------------- toss

    private fun beginToss(s: PlanStep.Toss) {
        if (rig.held?.state != ToyState.HELD) {
            finish()
            return
        }
        dir = if (s.dir < 0) -1 else 1
        phase = ToyPhase.FACE
    }

    private fun faceTick(dt: Float): Boolean {
        if (rig.faceTick(dir, dt) && !rig.loco.moving) {
            host.play(Clips.TOSS, 0f)
            phase = ToyPhase.TOSS
            t = 0f
        }
        return false
    }

    private fun tossTick(dt: Float): Boolean {
        rig.loco.update(dt, true)
        player.advanceAtBaseRate(dt)
        if (rig.held != null && player.frame >= RELEASE_FRAME) throwIt(step as PlanStep.Toss)
        return player.finished && rig.held == null && finish()
    }

    private fun throwIt(s: PlanStep.Toss) {
        val held = rig.held ?: return
        val power = Mathx.clamp(s.power, 0f, 1f)
        var vx: Float
        var vy: Float
        val surface = s.surface
        if (surface != null) {
            val dest = World.surface(surface)
            val zGoal = if (surface == SurfaceId.FLOOR) held.z else dest.z
            val k = World.depthScale(zGoal)
            val x = Mathx.clamp(s.x, ToyPhysics.spanStart(surface), ToyPhysics.spanEnd(surface))
            val y = World.groundY(surface, zGoal) - held.kind.halfHeight * k
            val arc = Mathx.lerp(CarryTuning.AIM_ARC_MIN, CarryTuning.AIM_ARC_MAX, power)
            ToyPhysics.aimClear(held.kind, held.x, held.y, x, y, arc, surface, aim)
            vx = aim[0]
            vy = aim[1]
            held.zGoal = if (surface == SurfaceId.FLOOR) Float.NaN else dest.z
        } else {
            val speed = Mathx.lerp(CarryTuning.FLICK_MIN, CarryTuning.FLICK_MAX, power)
            vx = held.vx + dir * speed * Mathx.cos(CarryTuning.FLICK_ANGLE)
            vy = held.vy - speed * Mathx.sin(CarryTuning.FLICK_ANGLE)
        }
        val spin = dir * CarryTuning.TOSS_SPIN * (TOSS_SPIN_BASE + power)
        val zGoal = held.zGoal
        val toy = rig.release() ?: return
        toy.launch(vx, vy, spin)
        toy.zGoal = zGoal
        springs.rotation.kick(-dir * TOSS_NOD)
    }

    // ---------------------------------------------------------------- set down

    private fun beginSetDown() {
        if (rig.held?.state != ToyState.HELD) {
            finish()
            return
        }
        dir = rig.turn.dir
        host.play(Clips.PICKUP, 0f)
        phase = ToyPhase.DOWN
        t = 0f
    }

    private fun downTick(dt: Float): Boolean {
        rig.loco.update(dt, true)
        t += dt
        val u = Mathx.clamp(t / CarryTuning.SETDOWN_DOWN_S, 0f, 1f)
        player.seek(CarryTuning.SETDOWN_DIP_PHASE * (u * u * (3f - 2f * u)))
        if (u >= 1f) {
            val held = rig.held
            val surface = rig.k.surface
            if (held != null) {
                val lo = ToyPhysics.spanStart(surface) + SETDOWN_INSET
                val hi = ToyPhysics.spanEnd(surface) - SETDOWN_INSET
                held.x = Mathx.clamp(held.x, lo, maxOf(lo, hi))
            }
            val toy = rig.release()
            toy?.launch(0f, 0f)
            phase = ToyPhase.UP
            t = 0f
        }
        return false
    }

    private fun upTick(dt: Float): Boolean {
        rig.loco.update(dt, true)
        t += dt
        val u = Mathx.clamp(t / CarryTuning.SETDOWN_UP_S, 0f, 1f)
        player.seek(CarryTuning.SETDOWN_DIP_PHASE * (1f - u * u * (3f - 2f * u)))
        return u >= 1f && finish()
    }

    // ---------------------------------------------------------------- hooks from the body

    /** The pounce's hop ended (the body hands control back). */
    fun hopEnded() = play.hopEnded()

    /** A tick ends: the toy in the mouth follows the head, a pounce lands. */
    fun afterTick(dt: Float) {
        if (host.hopLanded) play.pounceLanded()
        rig.afterTick(dt)
    }

    companion object {
        /** The `pickup` row's bite frame and the `toss` row's release frame (`lab.json`). */
        const val BITE_FRAME = 4
        const val RELEASE_FRAME = 1
        private const val AIM_VALUES = 3
        private const val TOSS_SPIN_BASE = 0.6f
        private const val TOSS_NOD = 0.9f
        private const val SETDOWN_INSET = 2f
    }
}
