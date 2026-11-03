package com.lifemax.rpg.feature.pet.body

/**
 * The numbers of solo play. Impulses are the web's (`petKnock`, `petPlayTick`, the belly juggle).
 */
object PlayTuning {
    /** Where the toy sits ahead of the feet when the cat lies and swats (web: 30 * sx + 2). */
    const val SWAT_REACH = 32f

    /** The `swat` row's frame on which the paw lands (web `SWAT_HIT_FRAME`). */
    const val SWAT_HIT_FRAME = 3

    /** Soft pat (power 0) to hard knock (power 1): speed ranges, web 26..44 and 78..128 along. */
    const val PAT_VX_MIN = 26f
    const val PAT_VX_MAX = 44f
    const val KNOCK_VX_MIN = 78f
    const val KNOCK_VX_MAX = 128f

    /** Upward speed of a pat (web 28..58) and of a knock (web 75..115). */
    const val PAT_VY_MIN = 28f
    const val PAT_VY_MAX = 58f
    const val KNOCK_VY_MIN = 75f
    const val KNOCK_VY_MAX = 115f

    /** The spring toy bounces higher off a paw (web 1.4, 1.45 on a knock). */
    const val SPRING_LIFT = 1.4f
    const val SPRING_KICK_LIFT = 1.3f
    const val HIT_SQUASH_SHARE = 0.5f

    /** The pause before the paw swings, standing over the toy, seconds. */
    const val SWAT_NOTICE_S = 0.15f

    /**
     * Belly-up: how close the cat gets before it rolls (web: 4 px), crouch, kick frame, kick speed.
     */
    const val KICK_GAP = 4f
    const val KICK_CROUCH_S = 0.35f
    const val KICK_FRAME = 3
    const val KICK_VX_MIN = 40f
    const val KICK_VX_MAX = 80f
    const val KICK_VY_MIN = 80f
    const val KICK_VY_MAX = 120f
    const val JUGGLE_BEAT_S = 0.5f
    const val JUGGLE_BEAT_SHARE = 0.7f

    /** Pounce: stalk to this far from the toy (web: 36), crouch, wiggle, land this far short. */
    const val STALK_OFFSET = 36f
    const val CROUCH_MIN_S = 0.4f
    const val CROUCH_MAX_S = 0.7f
    const val WIGGLE_S = 0.6f
    const val WIGGLE_PERIOD_S = 0.12f
    const val WIGGLE_KICK = 1.1f
    const val PAW_GAP = 10f
    const val PIN_SQUASH = 0.45f
    const val PIN_VX_MIN = 20f
    const val PIN_VX_MAX = 45f
    const val PIN_VY_MIN = 25f
    const val PIN_VY_MAX = 55f

    /** Chase: stay this far behind the toy, run when it is this far or this fast. */
    const val CHASE_GAP = 14f
    const val CHASE_RUN_DISTANCE = 50f
    const val CHASE_RUN_SPEED = 40f
    const val CHASE_DONE_SPEED = 8f
    const val CHASE_DONE_NEAR = 18f
}

private enum class PlayPhase {
    NONE,
    WALK,
    FACE,
    SWAT,
    CROUCH,
    JUGGLE,
    KICK,
    WIGGLE,
    HOP,
    AFTER,
    CHASE,
}

/**
 * The solo play steps of [ToyDirector]: [PlanStep.Bat] (a paw swat knocks the toy with an impulse),
 * [PlanStep.Kick] (belly-up: hug the toy, then a hind kick), [PlanStep.Pounce] (stalk, crouch,
 * wiggle, leap onto it) and [PlanStep.Chase] (follow the rolling toy). They are the primitives the
 * brain strings together into play; each ends with the toy free and the cat ready for the next
 * step. Nothing allocates.
 */
internal class PlayActs(
    private val rig: ToyRig,
    private val player: ClipPlayer,
    private val springs: BodySprings,
    private val host: ToyHost,
) {
    private var phase = PlayPhase.NONE
    private var step: PlanStep? = null
    private var toy: ToyBody? = null
    private var dir = 1
    private var t = 0f
    private var limit = 0f
    private var beat = 0f
    private var wiggleSide = 1f

    val active: Boolean
        get() = phase != PlayPhase.NONE

    fun handles(step: PlanStep?): Boolean =
        step is PlanStep.Bat ||
            step is PlanStep.Kick ||
            step is PlanStep.Pounce ||
            step is PlanStep.Chase

    fun cancel() {
        rig.dropIfLifting()
        phase = PlayPhase.NONE
    }

    private fun end(): Boolean {
        phase = PlayPhase.NONE
        return true
    }

    fun begin(step: PlanStep) {
        this.step = step
        val id =
            when (step) {
                is PlanStep.Bat -> step.toyId
                is PlanStep.Kick -> step.toyId
                is PlanStep.Pounce -> step.toyId
                is PlanStep.Chase -> step.toyId
                else -> -1
            }
        val target = rig.toy(id)
        if (
            target == null ||
                rig.held != null ||
                target.surface != rig.k.surface ||
                (step !is PlanStep.Chase && !target.canBePicked)
        ) {
            phase = PlayPhase.AFTER
            return
        }
        toy = target
        dir = rig.dirTo(target.x)
        t = 0f
        phase = if (step is PlanStep.Chase) PlayPhase.CHASE else PlayPhase.WALK
        if (step is PlanStep.Chase) limit = step.seconds
    }

    fun update(dt: Float): Boolean {
        val target = toy
        if (phase == PlayPhase.AFTER || (target == null && phase != PlayPhase.NONE)) return end()
        return when (phase) {
            PlayPhase.WALK -> walkTick(target!!, dt)
            PlayPhase.FACE -> faceTick(target!!, dt)
            PlayPhase.SWAT -> swatTick(target!!, dt)
            PlayPhase.CROUCH -> crouchTick(dt)
            PlayPhase.JUGGLE -> juggleTick(dt)
            PlayPhase.KICK -> kickTick(dt)
            PlayPhase.WIGGLE -> wiggleTick(dt)
            PlayPhase.HOP -> false
            PlayPhase.CHASE -> chaseTick(target!!, dt)
            else -> true
        }
    }

    private fun standoff(): Float =
        when (step) {
            is PlanStep.Bat -> PlayTuning.SWAT_REACH
            is PlanStep.Kick -> PlayTuning.KICK_GAP
            else -> PlayTuning.STALK_OFFSET
        } * rig.ds

    private fun walkTick(target: ToyBody, dt: Float): Boolean {
        dir = rig.dirTo(target.x)
        val gap = standoff()
        val near = Mathx.abs(target.x - rig.k.x) <= gap + CarryTuning.REACH_TOLERANCE_PX
        val inside = step is PlanStep.Pounce && near
        val arrived =
            inside || rig.walkTo(target.x - dir * gap, target.z, step is PlanStep.Pounce, dt)
        if (arrived) {
            phase = PlayPhase.FACE
            t = 0f
        }
        return false
    }

    private fun faceTick(target: ToyBody, dt: Float): Boolean {
        if (!rig.faceTick(dirTo(target), dt) || rig.loco.moving) return false
        t += dt
        if (t < PlayTuning.SWAT_NOTICE_S) return false
        t = 0f
        when (step) {
            is PlanStep.Bat -> {
                host.play(Clips.SWAT, 0f)
                phase = PlayPhase.SWAT
            }
            is PlanStep.Kick -> {
                host.play(Clips.CROUCH, 0f)
                phase = PlayPhase.CROUCH
                limit = PlayTuning.KICK_CROUCH_S
            }
            else -> {
                host.play(Clips.CROUCH, 0f)
                phase = PlayPhase.CROUCH
                limit = rig.rng.range(PlayTuning.CROUCH_MIN_S, PlayTuning.CROUCH_MAX_S)
            }
        }
        return false
    }

    private fun dirTo(target: ToyBody): Int = rig.dirTo(target.x).also { dir = it }

    // ---------------------------------------------------------------- bat

    private fun swatTick(target: ToyBody, dt: Float): Boolean {
        rig.loco.update(dt, true)
        player.advanceAtBaseRate(dt)
        val s = step as PlanStep.Bat
        if (t == 0f && player.frame >= PlayTuning.SWAT_HIT_FRAME) {
            t = 1f
            hit(target, s.power)
        }
        return player.elapsed >= Clips.SWAT.seconds && end()
    }

    private fun hit(target: ToyBody, power: Float) {
        val p = Mathx.clamp(power, 0f, 1f)
        val lo = Mathx.lerp(PlayTuning.PAT_VX_MIN, PlayTuning.KNOCK_VX_MIN, p)
        val hi = Mathx.lerp(PlayTuning.PAT_VX_MAX, PlayTuning.KNOCK_VX_MAX, p)
        val vlo = Mathx.lerp(PlayTuning.PAT_VY_MIN, PlayTuning.KNOCK_VY_MIN, p)
        val vhi = Mathx.lerp(PlayTuning.PAT_VY_MAX, PlayTuning.KNOCK_VY_MAX, p)
        val lift = if (target.kind == ToyKind.SPRING) PlayTuning.SPRING_LIFT else 1f
        target.launch(dir * rig.rng.range(lo, hi), -rig.rng.range(vlo, vhi) * lift)
        target.squashX.displace(-PlayTuning.PIN_SQUASH * PlayTuning.HIT_SQUASH_SHARE)
        host.emit(CatEventType.ToyHit)
    }

    // ---------------------------------------------------------------- kick

    private fun crouchTick(dt: Float): Boolean {
        rig.loco.update(dt, true)
        player.advanceAtBaseRate(dt)
        t += dt
        if (t < limit) return false
        t = 0f
        if (step is PlanStep.Kick) {
            host.play(Clips.ROLL, 0f)
            toy?.let { rig.beginLift(it) }
            beat = 0f
            limit = (step as PlanStep.Kick).juggleSeconds
            phase = PlayPhase.JUGGLE
        } else {
            phase = PlayPhase.WIGGLE
            limit = PlayTuning.WIGGLE_S
            wiggleSide = 1f
            beat = 0f
        }
        return false
    }

    private fun juggleTick(dt: Float): Boolean {
        rig.loco.update(dt, true)
        player.advanceAtBaseRate(dt)
        t += dt
        beat += dt
        if (beat >= PlayTuning.JUGGLE_BEAT_S) {
            beat = 0f
            rig.footfall(PlayTuning.JUGGLE_BEAT_SHARE)
        }
        if (t >= limit && rig.held?.state == ToyState.HELD) {
            host.play(Clips.KICK, 0f)
            phase = PlayPhase.KICK
            t = 0f
        }
        return false
    }

    private fun kickTick(dt: Float): Boolean {
        rig.loco.update(dt, true)
        player.advanceAtBaseRate(dt)
        if (t == 0f && player.frame >= PlayTuning.KICK_FRAME) {
            t = 1f
            val s = step as PlanStep.Kick
            val p = Mathx.clamp(s.power, 0f, 1f)
            val vx = rig.rng.range(PlayTuning.KICK_VX_MIN, PlayTuning.KICK_VX_MAX) * (0.5f + p)
            val vy = rig.rng.range(PlayTuning.KICK_VY_MIN, PlayTuning.KICK_VY_MAX) * (0.5f + p)
            val lift = if (rig.held?.kind == ToyKind.SPRING) PlayTuning.SPRING_KICK_LIFT else 1f
            val thrown = rig.release()
            thrown?.launch(dir * vx, -vy * lift)
            host.emit(CatEventType.ToyHit)
        }
        return player.elapsed >= Clips.KICK.seconds && rig.held == null && end()
    }

    // ---------------------------------------------------------------- pounce

    private fun wiggleTick(dt: Float): Boolean {
        rig.loco.update(dt, true)
        player.seek(1f)
        t += dt
        beat += dt
        if (beat >= PlayTuning.WIGGLE_PERIOD_S) {
            beat = 0f
            wiggleSide = -wiggleSide
            springs.rotation.kick(wiggleSide * PlayTuning.WIGGLE_KICK)
        }
        if (t >= limit) {
            val target = toy ?: return end()
            phase = PlayPhase.HOP
            host.hop(target.x - dir * PlayTuning.PAW_GAP * rig.ds)
        }
        return false
    }

    /** The pounce's hop touched down: the toy is pinned, squashed, and squirts out a little. */
    fun pounceLanded() {
        val target = toy ?: return
        if (phase != PlayPhase.HOP) return
        val lift = if (target.kind == ToyKind.SPRING) PlayTuning.SPRING_LIFT else 1f
        target.launch(
            dir * rig.rng.range(PlayTuning.PIN_VX_MIN, PlayTuning.PIN_VX_MAX),
            -rig.rng.range(PlayTuning.PIN_VY_MIN, PlayTuning.PIN_VY_MAX) * lift,
        )
        target.squashY.displace(-PlayTuning.PIN_SQUASH)
        target.squashX.displace(PlayTuning.PIN_SQUASH * Tuning.Squash.VOLUME_SHARE)
        host.emit(CatEventType.ToyHit)
    }

    /** The hop is over; the step ends on the next tick. */
    fun hopEnded() {
        if (phase == PlayPhase.HOP) phase = PlayPhase.AFTER
    }

    // ---------------------------------------------------------------- chase

    private fun chaseTick(target: ToyBody, dt: Float): Boolean {
        t += dt
        dir = if (target.vx > 1f) 1 else if (target.vx < -1f) -1 else rig.dirTo(target.x)
        val behind = target.x - dir * PlayTuning.CHASE_GAP * rig.ds
        val far = Mathx.abs(target.x - rig.k.x) > PlayTuning.CHASE_RUN_DISTANCE
        val fast = target.speed() > PlayTuning.CHASE_RUN_SPEED
        val arrived = rig.walkTo(behind, target.z, far || fast, dt)
        val still = target.speed() < PlayTuning.CHASE_DONE_SPEED && !target.airborne
        val near = Mathx.abs(target.x - rig.k.x) < PlayTuning.CHASE_DONE_NEAR * rig.ds
        return (t >= limit || (still && (arrived || near))) && end()
    }
}
