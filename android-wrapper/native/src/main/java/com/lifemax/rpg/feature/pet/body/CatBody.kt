package com.lifemax.rpg.feature.pet.body

/**
 * The cat's body: movement, animation choice, blending and secondary motion, with no drawing and no
 * Android types. A host steps it with real frame times and reads [pose]; the page's brain (or a
 * local autopilot) feeds it plans and affect.
 *
 * Time. [step] accumulates the host's `dt` and advances a fixed 1/120 s sub-step as many times as
 * fit, so two hosts running at 60 and 120 Hz see equal poses at equal times. The pose is the state
 * after the last whole sub-step (it trails the host clock by less than 8 ms).
 *
 * Plans. [replacePlan] interrupts: it reports [CatEventType.Interrupted] at once, lets the cat end
 * a cheap beat (a footfall, a blink) and then starts the new plan. [extendPlan] appends to the plan
 * being followed and is ignored when its id is stale. With no steps left the cat idles, alive.
 *
 * Allocation. Nothing in [step] allocates: state lives in preallocated fields, springs and arrays,
 * loops are index loops and the pose and its event buffer are refilled in place. Plans are built by
 * the caller (that is where the allocation belongs).
 *
 * @param seed makes this cat's idle life, blinks and tail twitches its own; the same seed replays.
 */
class CatBody(
    val seed: Int = 1,
    startX: Float = DEFAULT_START_X,
    startSurface: SurfaceId = SurfaceId.FLOOR,
    startZ: Float = World.ART_Z,
) {
    /** The latest pose; refilled in place by [step]. */
    val pose = CatPose()

    /** The hungry look: slower walk and the `_w` rows (PET_WEAK in the web room). */
    var weak: Boolean
        get() = loco.weak
        set(value) {
            loco.weak = value
        }

    /**
     * Body scale for jumping: 1 grown, a kitten's `Growth.scale` (0.6 to 0.93) while it grows. With
     * [bodyLength] and [legLength] (the rig's `bl` and `lg` morphs), [fatigue] and [mood] it sets
     * [capability]; set them before asking for a route.
     */
    var growthScale = 1f
    var bodyLength = 1f
    var legLength = 1f
    var fatigue = 0f
    var mood = 0f

    /** The toy in the mouth (being lifted into it, or carried), or null. */
    val heldToy: ToyBody?
        get() = toyRig.held

    private val carrying
        get() = toyRig.held?.state == ToyState.HELD

    /** What this cat can jump right now (see [JumpCapability]). */
    val capability: JumpCapability
        get() = JumpCapability.of(growthScale, weak, bodyLength, legLength, fatigue, mood)

    /** Id of the plan being followed, or [PlanQueue.NO_PLAN]. */
    val planId: Int
        get() = queue.planId

    /** True when there is nothing left to do and the cat is living its idle life. */
    val isIdle: Boolean
        get() = mode == Mode.IDLE && !hasPending

    /** True while the cat is walking, hopping or leaping somewhere: what a camera follows. */
    val isTravelling: Boolean
        get() = mode == Mode.WALK || mode == Mode.HOP || mode == Mode.LEAP

    /** True while the cat gallops (the camera then leads further and follows faster). */
    val isRunning: Boolean
        get() = loco.gait == Gait.RUN

    private enum class Mode {
        IDLE,
        SETTLE,
        WALK,
        HOP,
        LEAP,
        ACT,
        FACE,
        TOY,
    }

    private val kin = Kinematics()
    private val turn = Turn()
    private val loco = Locomotion(kin, turn)
    private val springs = BodySprings()
    private val jump = Jump(kin)
    private val player = ClipPlayer(Rng.derive(seed, SALT_BLINK))
    private val ears = Ears()
    private val whiskers = Whiskers()
    private val tail = Tail(Rng.derive(seed, SALT_TAIL))
    private val breathing = Breathing()
    private val attention = Attention()
    private val host: ToyHost =
        object : ToyHost {
            override fun play(clip: Clip, phase: Float) = playClip(clip, phase)

            override fun stride(dt: Float): Boolean = animateStride(dt)

            override fun hop(x: Float) {
                jump.begin(SurfaceId.FLOOR, x)
                mode = Mode.HOP
            }

            override fun emit(type: CatEventType): Unit =
                pose.events.add(type, queue.planId, queue.currentIndex, queue.isOnLast)

            override val bodyRotation: Float
                get() = jump.arcRotation + leap.arcRotation

            override val hopLanded: Boolean
                get() = jump.landedNow
        }
    private val leap: LeapDirector = LeapDirector(kin, turn, loco, player, springs, attention, host)
    private val toyRig = ToyRig(kin, turn, loco, player, springs, host, seed)
    private val toyDir = ToyDirector(toyRig, player, springs, host)
    private var toyTicks = 0L
    private val reactions = Reactions(springs, ears, tail, whiskers, attention)
    private val idleLife = IdleLife(Rng.derive(seed, SALT_IDLE), springs, ears, attention)
    private val queue = PlanQueue()
    private val tone = BodyTone()
    private val jumpAnim =
        JumpAnimation(jump, player, springs, { playClip(it) }) {
            pose.events.add(CatEventType.Arrived, queue.planId, queue.currentIndex, queue.isOnLast)
        }

    private var mode = Mode.IDLE
    private var clock = 0.0
    private var pending = ArrayList<PlanStep>()
    private var hasPending = false
    private var pendingId = PlanQueue.NO_PLAN
    private var lastPlanId = PlanQueue.NO_PLAN
    private var beatAge = 0f
    private var actElapsed = 0f
    private var actSeconds = 0f
    private var skidLeft = 0f
    private val way = FloorWay()
    private var walkRun = false
    private var earState = EarState.FORWARD
    private var tailState = TailState.NEUTRAL
    private val early = EarlyInterrupts()

    init {
        placeAt(startX, startZ, startSurface, 1)
        compose()
    }

    // ---------------------------------------------------------------- public API

    /** Share the house's [toys] (or none) and give the cat its mouth [anchors] for toy steps. */
    fun attachToys(toys: ToyField?, anchors: MouthAnchors) {
        toyRig.field = toys
        toyRig.mouths = anchors
        toyTicks = toys?.ticks ?: 0L
    }

    /** Put the cat down at ([x], [z]) on [surface] facing [facing], dropping any plan. */
    fun placeAt(x: Float, z: Float, surface: SurfaceId, facing: Int = 1) {
        kin.surface = surface
        kin.x = x
        kin.z = z
        kin.y = kin.groundY
        loco.reset()
        turn.snapTo(facing)
        jump.cancel()
        leap.cancel()
        springs.reset()
        queue.clear()
        hasPending = false
        mode = Mode.IDLE
        player.play(if (weak) Clips.IDLE_SIT_W else Clips.IDLE_SIT)
        toyRig.snapAnchor(null)
        compose()
    }

    /**
     * Interrupt: follow [steps] as plan [id] instead. Returns false (and changes nothing) when [id]
     * is not newer than the last plan accepted, so a late or duplicate delivery is ignored.
     */
    fun replacePlan(id: Int, steps: List<PlanStep>): Boolean {
        if (id <= lastPlanId) return false
        lastPlanId = id
        if (queue.hasWork && !hasPending) {
            noteInterrupt(queue.planId, queue.currentIndex)
        }
        pending.clear()
        pending.addAll(steps)
        pendingId = id
        hasPending = true
        beatAge = 0f
        if (canCutNow()) applyPending()
        return true
    }

    /**
     * Append [steps] to plan [id]. Returns false and ignores them when [id] is not the plan being
     * followed (it was interrupted or never started).
     */
    fun extendPlan(id: Int, steps: List<PlanStep>): Boolean {
        if (hasPending && id == pendingId) {
            pending.addAll(steps)
            return true
        }
        if (id != queue.planId) return false
        val wasIdle = !queue.hasWork
        queue.append(steps)
        if (wasIdle && mode == Mode.IDLE) beginCurrentStep()
        return true
    }

    /**
     * The steps that carry the cat from where it stands to [surface] at [x] with the jumps it can
     * make (see [ParkourPlanner] and [capability]). Empty when it is already there on the bed or
     * cannot get there at all; use [parkourRoute] to tell the two apart.
     */
    fun routeTo(surface: SurfaceId, x: Float): List<PlanStep> =
        parkourRoute(surface, x)?.steps ?: emptyList()

    /** The planned route to [surface] at [x] for this cat, or null when it cannot get there. */
    fun parkourRoute(surface: SurfaceId, x: Float): Route? =
        ParkourPlanner.plan(capability, kin.surface, kin.x, surface, x, seed)

    /** The brain's ear and tail states: targets for the springs, not poses. */
    fun setAffect(ear: EarState, tail: TailState) {
        earState = ear
        tailState = tail
    }

    /**
     * The brain's mood as a continuous tone on the springs (see [BodyTone]): [valence] and
     * [arousal] in -1..1. Neutral (0, 0) leaves the body exactly as it was.
     */
    fun tone(valence: Float, arousal: Float) {
        tone.set(valence, arousal)
        loco.pace = tone.pace
    }

    /** Look at room position ([x], [z]); the head, eyes and ears follow after a short delay. */
    fun lookAt(x: Float, z: Float) = attention.lookAt(x, z)

    /** Stop looking at anything. */
    fun stopLooking() = attention.clear()

    /** Layer a short reaction on top of whatever the cat is doing. */
    fun react(kind: ReactionKind, seconds: Float = 0f) = reactions.trigger(kind, seconds)

    /** End a reaction early (the purr stops when the hand lifts). */
    fun endReaction(kind: ReactionKind) = reactions.cancel(kind)

    /**
     * Advance by [dt] seconds of host time. Events from this call are in `pose.events` afterwards
     * and stay there until the next call.
     */
    fun step(dt: Float) {
        pose.events.clear()
        flushInterrupts()
        clock += maxOf(0f, minOf(dt, Tuning.Time.MAX_FRAME_DT)).toDouble()
        while (clock >= SUBSTEP - Tuning.Time.TOLERANCE) {
            clock -= SUBSTEP
            tick(Tuning.Time.SUBSTEP)
        }
        compose()
    }

    /**
     * Interrupts happen between steps; they are held here and reported at the start of the next.
     */
    private fun noteInterrupt(plan: Int, index: Int) = early.note(plan, index)

    private fun flushInterrupts() = early.flush(pose.events)

    // ---------------------------------------------------------------- one sub-step

    private fun tick(dt: Float) {
        resolveInterrupt(dt)
        when (mode) {
            Mode.IDLE -> idleTick(dt)
            Mode.SETTLE -> settleTick(dt)
            Mode.WALK -> walkTick(dt)
            Mode.HOP -> hopTick(dt)
            Mode.LEAP -> leapTick(dt)
            Mode.ACT -> actTick(dt)
            Mode.FACE -> faceTick(dt)
            Mode.TOY -> {
                settleOnGround()
                if (toyDir.update(dt)) finishStep()
            }
        }
        turn.update(dt, loco.speed > 0f, loco.gait)
        if (turn.flippedNow) tail.swing(turn.dir)
        // the turn row's frames carry the dip: nothing scales, tilts or bobs the drawing under it
        if (turn.plantedNow) springs.reset()
        updateBodyAndSecondary(dt)
        toyDir.afterTick(dt)
        toyRig.field?.stepTo(++toyTicks)
    }

    private fun idleTick(dt: Float) {
        loco.update(dt, true)
        settleOnGround()
        if (!animateStride(dt)) {
            if (!player.clip.isHoldable) playClip(restingClip())
            player.advanceAtBaseRate(dt)
        }
    }

    private fun settleTick(dt: Float) {
        loco.update(dt, true)
        settleOnGround()
        animateStride(dt)
        if (!loco.moving) startStaticStep(queue.current)
    }

    private fun walkTick(dt: Float) {
        val arrived = loco.update(dt, false)
        settleOnGround()
        if (!animateStride(dt)) player.advanceAtBaseRate(dt)
        if (arrived && way.atCorner) { // round the furniture: on to the next corner or the end
            way.from(kin.x, kin.z)
            loco.setGoal(way.goalX, way.goalZ, walkRun)
        } else if (arrived) {
            pose.events.add(CatEventType.Arrived, queue.planId, queue.currentIndex, queue.isOnLast)
            finishStep()
        }
    }

    private fun actTick(dt: Float) {
        loco.update(dt, true)
        settleOnGround()
        player.advanceAtBaseRate(dt)
        actElapsed += dt
        if (actElapsed >= actSeconds) finishStep()
    }

    private fun faceTick(dt: Float) {
        val step = queue.current as? PlanStep.Face
        if (step != null) turn.request(step.dir)
        loco.update(dt, true)
        settleOnGround()
        player.advanceAtBaseRate(dt)
        if (step == null || (!turn.active && turn.dir == step.dir)) finishStep()
    }

    private fun hopTick(dt: Float) {
        val facingOk = jump.wantedDir == 0 || (!turn.active && turn.dir == jump.wantedDir)
        if (jump.phase == JumpPhase.FACE) {
            if (!facingOk) turn.request(jump.wantedDir)
            loco.update(dt, true)
            player.advanceAtBaseRate(dt)
        }
        val done = jump.update(dt, facingOk)
        jumpAnim.update(dt)
        if (!jump.airborne) kin.y = kin.groundY
        if (done && toyDir.active) {
            toyDir.hopEnded()
            mode = Mode.TOY
        } else if (done) finishStep()
    }

    private fun leapTick(dt: Float) {
        val done = leap.update(dt)
        if (leap.landedNow) {
            pose.events.add(CatEventType.Arrived, queue.planId, queue.currentIndex, queue.isOnLast)
        }
        if (done) finishStep()
    }

    // ---------------------------------------------------------------- stepping through the plan

    private fun beginCurrentStep() {
        val step = queue.current
        when {
            step == null -> mode = Mode.IDLE
            step is PlanStep.Walk -> startWalk(step)
            step is PlanStep.Carry -> startWalkTo(step.x, step.z, false)
            loco.moving -> mode = Mode.SETTLE
            else -> startStaticStep(step)
        }
    }

    private fun startWalk(step: PlanStep.Walk) = startWalkTo(step.x, step.z, step.run)

    private fun startWalkTo(goalX: Float, goalZ: Float?, run: Boolean) {
        val onFloor = kin.surface == SurfaceId.FLOOR
        val x = if (onFloor) World.clampHouse(goalX) else World.clampTo(kin.surface, goalX)
        val z = if (onFloor && goalZ != null) Mathx.clamp(goalZ, 0f, World.MAX_Z) else kin.z
        if (onFloor) way.begin(kin.x, kin.z, x, z) else way.straight(x, z)
        walkRun = run && !carrying
        loco.setGoal(way.goalX, way.goalZ, walkRun)
        mode = Mode.WALK
    }

    private fun startStaticStep(step: PlanStep?) {
        when (step) {
            is PlanStep.Hop -> {
                jump.begin(step.surface, step.x)
                mode = Mode.HOP
            }
            is PlanStep.Leap -> {
                if (turn.active) turn.snapTo(turn.target)
                leap.begin(step, queue.nextIsLeap)
                mode = Mode.LEAP
            }
            is PlanStep.Act -> startAct(step)
            is PlanStep.Face -> mode = Mode.FACE
            is PlanStep.Walk -> startWalk(step)
            is PlanStep.Carry -> startWalkTo(step.x, step.z, false)
            null -> mode = Mode.IDLE
            else -> {
                toyDir.begin(step)
                mode = Mode.TOY
            }
        }
    }

    private fun startAct(step: PlanStep.Act) {
        val named = Clips.find(step.anim) ?: Clips.IDLE_SIT
        val clip =
            Headroom.fit(if (weak) Clips.weakOf(named) else named, kin.surface, growthScale, weak)
        if (player.clip !== clip) playClip(clip)
        actElapsed = 0f
        actSeconds = step.seconds
        mode = Mode.ACT
    }

    /** The current step is complete: report it and move on (or, if interrupted, swap plans). */
    private fun finishStep() {
        if (hasPending) {
            applyPending()
            return
        }
        pose.events.add(CatEventType.StepDone, queue.planId, queue.currentIndex, queue.isOnLast)
        queue.advance()
        beginCurrentStep()
    }

    // ---------------------------------------------------------------- interrupts

    private fun applyPending() {
        if (mode == Mode.HOP) jump.cancel()
        if (mode == Mode.LEAP) leap.cancel()
        if (toyDir.active) toyDir.cancel()
        queue.load(pendingId, pending)
        hasPending = false
        pending.clear()
        beginCurrentStep()
    }

    private fun resolveInterrupt(dt: Float) {
        if (!hasPending) return
        beatAge += dt
        val mustWait =
            (mode == Mode.HOP && !jump.canCancel) || (mode == Mode.LEAP && !leap.canCancel)
        if (canCutNow() || (beatAge >= Tuning.Interrupt.BEAT_MAX_S && !mustWait)) applyPending()
    }

    /** True at a cheap beat: a footfall, a finished blink, the ground before a jump. */
    private fun canCutNow(): Boolean =
        when (mode) {
            // a turn in progress carries on by itself (or runs back) under the next plan
            Mode.IDLE,
            Mode.SETTLE,
            Mode.FACE -> !player.blinking
            Mode.ACT -> !player.blinking && !(player.clip.cheapBeat && !player.finished)
            Mode.WALK -> player.footfall || loco.gait == Gait.STAND
            Mode.HOP -> jump.canCancel
            Mode.LEAP -> leap.canCancel
            Mode.TOY -> toyDir.canCut
        }

    // ---------------------------------------------------------------- animation

    private fun settleOnGround() {
        if (!jump.airborne) kin.y = kin.groundY
    }

    /** Play the stride clip for the current gait, phase matched. False if the cat is standing. */
    private fun animateStride(dt: Float): Boolean {
        val gait = loco.gait
        if (gait == Gait.STAND) {
            // a walk stopped for a turn keeps its stride frame: it does not sit down in between
            if (player.clip.isLocomotion && turn.holdsStride) return true
            if (player.clip.isLocomotion) playClip(restingClip())
            return false
        }
        val clip = strideClipFor(gait, weak, carrying)
        if (player.clip !== clip) playClip(clip)
        player.advance(dt, loco.speed / clip.stridePx)
        if (player.footfall) {
            val share = Mathx.clamp(loco.speed / Tuning.Walk.RUN_SPEED, FOOTFALL_MIN_SHARE, 1f)
            springs.footfall(share)
            toyRig.footfall(share)
        }
        return true
    }

    private fun restingClip(): Clip =
        Headroom.fit(if (weak) Clips.IDLE_SIT_W else Clips.IDLE_SIT, kin.surface, growthScale, weak)

    /** Start [clip]; stride clips keep the stride phase, a change of posture squashes the body. */
    private fun playClip(clip: Clip, phase: Float = 0f) {
        val from = player.clip
        val stride = from.isLocomotion && clip.isLocomotion
        if (!stride) springs.kickPosture(from.height, clip.height)
        player.play(clip, if (stride) player.phase else phase)
    }

    // ---------------------------------------------------------------- body, secondary motion

    private fun updateBodyAndSecondary(dt: Float) {
        if (loco.skidStarted) startSkid()
        skidLeft = maxOf(0f, skidLeft - dt / Tuning.Walk.SKID_SECONDS)
        val lean =
            Mathx.clamp(
                kin.ax * Tuning.Walk.LEAN_PER_ACCEL,
                -Tuning.Walk.LEAN_MAX,
                Tuning.Walk.LEAN_MAX,
            )
        springs.step(dt, turn.dir * lean)
        val speedShare = Mathx.clamp(loco.speed / Tuning.Walk.RUN_SPEED, 0f, 1f)
        attention.update(dt, kin.x, kin.z, turn.dir)
        reactions.update(dt)
        val ear = reactions.earOverride ?: toyRig.ear ?: earState
        val tailPose = reactions.tailOverride ?: toyRig.tail ?: tailState
        ears.update(dt, ear, tone.ear(attention.x), speedShare)
        whiskers.update(dt, ears.flat, speedShare, Mathx.clamp(attention.x, 0f, 1f))
        tail.update(dt, tailPose, tone.tail(kin.ax * turn.dir * Tuning.Walk.TAIL_INERTIA))
        breathing.update(dt, player.clip.posture == Posture.LIE)
        idleLife.update(dt, isCalm(), player.clip.posture == Posture.LIE)
    }

    private fun startSkid() {
        skidLeft = 1f
        springs.rotation.kick(-turn.dir * Tuning.Walk.SKID_ROTATION_KICK)
        springs.squash(Tuning.Walk.SKID_SQUASH)
    }

    /** Sitting quietly with nothing queued: when weight shifts and glances are natural. */
    private fun isCalm(): Boolean =
        (mode == Mode.IDLE || mode == Mode.ACT) && player.clip.posture == Posture.SIT && !hasPending

    // ---------------------------------------------------------------- output

    private fun compose() {
        val p = pose
        p.x = kin.x
        p.y = kin.y
        p.z = kin.z
        p.elevation = maxOf(0f, World.floorY(kin.z) - kin.y)
        p.depthScale = World.depthScale(kin.z)
        p.surface = kin.surface
        p.facing = turn.dir
        p.flip = turn.dir < 0
        p.clip = turn.shown(player.clip).id
        p.frame = turn.shownFrame(player.frame)
        p.framePhase = if (turn.showing) 0f else player.framePhase
        p.scaleX = springs.scaleX
        p.scaleY = springs.scaleY
        p.rotation = springs.rotation.pos + jump.arcRotation + leap.arcRotation
        p.bob = springs.bob.pos
        p.ear[0] = ears.leftAngle
        p.ear[1] = ears.rightAngle
        p.earFlat = ears.flat
        for (i in 0 until Tail.SEGMENTS) p.tail[i] = tail.bend(i)
        p.whisker = whiskers.offset
        p.breath = tone.breath(breathing.scale)
        p.lookX = attention.x
        p.lookY = attention.y
        p.lean = attention.leanRadians
        p.eyeClose = reactions.eyeClose
        p.vibration = reactions.vibration
        p.skid = skidLeft
        composeShadow(p)
    }

    private fun composeShadow(p: CatPose) {
        val row = if (jump.active) jump.shadowY else if (leap.active) leap.shadowY else kin.groundY
        val halfWidth = turn.shown(player.clip).shadowHalfWidth
        p.setShadow(kin.x, row, maxOf(jump.height, leap.height), halfWidth)
    }

    private companion object {
        const val DEFAULT_START_X = 92f
        const val SUBSTEP = Tuning.Time.SUBSTEP.toDouble()
        const val SALT_BLINK = 0x51ED
        const val SALT_TAIL = 0x7A11
        const val SALT_IDLE = 0x1D1E
        const val FOOTFALL_MIN_SHARE = 0.4f
    }
}
