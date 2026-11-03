package com.lifemax.rpg.feature.pet.brain

import com.lifemax.rpg.feature.pet.body.CatBody
import com.lifemax.rpg.feature.pet.body.CatEventType
import com.lifemax.rpg.feature.pet.body.Clips
import com.lifemax.rpg.feature.pet.body.EarState
import com.lifemax.rpg.feature.pet.body.JumpCapability
import com.lifemax.rpg.feature.pet.body.ParkourPlanner
import com.lifemax.rpg.feature.pet.body.PlanStep
import com.lifemax.rpg.feature.pet.body.ReactionKind
import com.lifemax.rpg.feature.pet.body.Route
import com.lifemax.rpg.feature.pet.body.SurfaceId
import com.lifemax.rpg.feature.pet.body.TailState
import com.lifemax.rpg.feature.pet.body.Tuning
import com.lifemax.rpg.feature.pet.body.World

/**
 * One activity as the body will play it: the [steps] (the route first, [routeSteps] of them, then
 * the beats at the place), and a brain-side [cue] at the start of chosen steps (a begging bob). The
 * runner builds it at pick time, which is where the allocation belongs.
 */
class Script {
    val steps = ArrayList<PlanStep>()
    private val cues = ArrayList<Cue>()

    /** How many of [steps] are the way there. */
    var routeSteps = 0
        private set

    /** Seconds of beats at the place (the activity's own time). */
    var beatSeconds = 0.0
        private set

    /** Starts the script with a [route] of steps. */
    fun route(route: List<PlanStep>) {
        steps.addAll(route)
        repeat(route.size) { cues.add(Cue.NONE) }
        routeSteps = steps.size
    }

    /** A walk on the floor to ([x], [z]) as part of the way there. */
    fun approach(x: Float, z: Float) {
        steps.add(PlanStep.Walk(x, z))
        cues.add(Cue.NONE)
        routeSteps = steps.size
    }

    /** Plays row [anim] for [seconds], with [cue] at its start. */
    fun act(anim: String, seconds: Double, cue: Cue = Cue.NONE) {
        steps.add(PlanStep.Act(anim, seconds.toFloat()))
        cues.add(cue)
        beatSeconds += seconds
    }

    /** Turns to face [dir]. */
    fun face(dir: Int) {
        if (dir == 0) return
        steps.add(PlanStep.Face(dir))
        cues.add(Cue.NONE)
    }

    /** A walk on the current surface as a beat (a wander's second leg). */
    fun stroll(x: Float, z: Float?) {
        steps.add(PlanStep.Walk(x, z))
        cues.add(Cue.NONE)
    }

    /** The cue at the start of step [i]. */
    fun cueAt(i: Int): Cue = if (i in cues.indices) cues[i] else Cue.NONE
}

/** Brain-side beats played at the start of a step. */
enum class Cue {
    NONE,

    /** The begging bobs and the look up (the body's [ReactionKind.BEG]). */
    BEG,
}

/**
 * What plays a [Script]: the live [BodyExecutor] (the real body, sub-step by sub-step) or the
 * [FastExecutor] (the same scripts as timed steps, for long simulations and, later, catch-up).
 */
interface Executor {
    val surface: SurfaceId
    val x: Float
    val z: Float

    /** The script's way there is behind it. */
    val atPlace: Boolean

    /** Nothing of the script is left. */
    val done: Boolean

    /** The hungry look; it changes what the cat can jump. */
    var weak: Boolean

    /** What the cat can jump now. */
    val capability: JumpCapability

    /** The route to [surface] at [x] from where the cat stands, or null. */
    fun route(surface: SurfaceId, x: Float): Route?

    /** Plays [script] instead of whatever was playing (an interrupt when something was). */
    fun start(script: Script)

    /** One 1/120 s sub-step. */
    fun subStep()

    /** One think quantum (the fast executor's clock). */
    fun think()

    /** Puts the cat down at once, facing [dir] (the world's start: a declared cut). */
    fun place(surface: SurfaceId, x: Float, z: Float, dir: Int = 1)

    /** Which way the cat faces, -1 or 1. */
    val facing: Int

    /** The mood made visible: ear and tail states and the continuous tone. */
    fun affect(ear: EarState, tail: TailState, valence: Double, arousal: Double)
}

/**
 * Plays scripts on a [CatBody], which the host draws: the brain's own body, or one of the house's
 * handed over with [adopt].
 */
class BodyExecutor(body: CatBody) : Executor {
    /** The body being played; [adopt] changes it. */
    var body: CatBody = body
        private set

    private var planId = 0
    private var script: Script? = null
    private var doneIndex = -1

    override val surface: SurfaceId
        get() = body.pose.surface

    override val x: Float
        get() = body.pose.x

    override val z: Float
        get() = body.pose.z

    override val atPlace: Boolean
        get() = script.let { it == null || doneIndex >= it.routeSteps - 1 }

    override val done: Boolean
        get() = body.planId == planId && body.isIdle

    override var weak: Boolean
        get() = body.weak
        set(v) {
            body.weak = v
        }

    override val capability: JumpCapability
        get() = body.capability

    override val facing: Int
        get() = if (body.pose.facing < 0) -1 else 1

    override fun route(surface: SurfaceId, x: Float): Route? = body.parkourRoute(surface, x)

    /**
     * Plays on [next] from now on (the house's body, which the screens draw): it takes this body's
     * proportions and hunger and is put where this one stands, a declared cut. The script playing
     * is dropped; the mind resumes after.
     */
    fun adopt(next: CatBody) {
        if (next === body) return
        val was = body
        next.growthScale = was.growthScale
        next.bodyLength = was.bodyLength
        next.legLength = was.legLength
        next.weak = was.weak
        val p = was.pose
        next.placeAt(p.x, p.z, p.surface, facing)
        script = null
        body = next
    }

    override fun start(script: Script) {
        this.script = script
        doneIndex = -1
        // ids rise past any plan someone else gave this body (a touch's reflex on a shared house)
        planId = maxOf(planId, body.planId) + 1
        body.replacePlan(planId, script.steps)
        play(script.cueAt(0))
    }

    override fun subStep() {
        body.step(SUBSTEP)
        val events = body.pose.events
        for (i in 0 until events.count) {
            if (events.type(i) != CatEventType.StepDone || events.planId(i) != planId) continue
            doneIndex = events.stepIndex(i)
            script?.let { play(it.cueAt(doneIndex + 1)) }
        }
    }

    override fun think() = Unit

    override fun place(surface: SurfaceId, x: Float, z: Float, dir: Int) {
        script = null
        body.placeAt(x, z, surface, dir)
    }

    override fun affect(ear: EarState, tail: TailState, valence: Double, arousal: Double) {
        body.setAffect(ear, tail)
        body.tone(valence.toFloat(), arousal.toFloat())
    }

    private fun play(cue: Cue) {
        if (cue == Cue.BEG) body.react(ReactionKind.BEG)
    }

    private companion object {
        const val SUBSTEP = Tuning.Time.SUBSTEP
    }
}

/**
 * Plays scripts without a body (plan 2.6 "abstract mode"): each step takes the time a body would
 * roughly need (walks at walking speed, a leap [LEAP_S], acts their seconds) and the cat is moved
 * to where the step ends when it ends, a declared cut. The selector, drives and schedule are the
 * same as live, so a month of two cats runs in seconds (the soak test; CB2's catch-up).
 */
class FastExecutor(
    private val growthScale: Float,
    private val bodyLength: Float,
    private val legLength: Float,
    private val seed: Int,
) : Executor {
    override var surface = SurfaceId.FLOOR
        private set

    override var x = 0f
        private set

    override var z = World.ART_Z
        private set

    override var weak = false
    private var steps: List<PlanStep> = emptyList()
    private var routeSteps = 0
    private var index = 0
    private var left = 0.0

    override val atPlace: Boolean
        get() = index >= routeSteps

    override val done: Boolean
        get() = index >= steps.size

    override val capability: JumpCapability
        get() = JumpCapability.of(growthScale, weak, bodyLength, legLength)

    override fun route(surface: SurfaceId, x: Float): Route? =
        ParkourPlanner.plan(capability, this.surface, this.x, surface, x, seed)

    override fun start(script: Script) {
        steps = script.steps
        routeSteps = script.routeSteps
        index = 0
        left = if (steps.isEmpty()) 0.0 else seconds(steps[0])
    }

    override fun subStep() = Unit

    override fun think() {
        var q = BrainTuning.Time.QUANTUM_S
        while (index < steps.size && q > 0.0) {
            if (left > q) {
                left -= q
                return
            }
            q -= left
            finish(steps[index])
            index++
            if (index < steps.size) left = seconds(steps[index])
        }
    }

    override var facing = 1
        private set

    override fun place(surface: SurfaceId, x: Float, z: Float, dir: Int) {
        this.surface = surface
        this.x = x
        this.z = z
        facing = if (dir < 0) -1 else 1
        steps = emptyList()
        index = 0
    }

    override fun affect(ear: EarState, tail: TailState, valence: Double, arousal: Double) = Unit

    private fun seconds(s: PlanStep): Double =
        when (s) {
            is PlanStep.Walk -> {
                val speed = if (weak) Tuning.Walk.WEAK_SPEED else Tuning.Walk.WALK_SPEED
                val dz = s.z?.let { Math.abs(it - z) / Tuning.Walk.Z_SPEED } ?: 0f
                (Math.abs(s.x - x) / speed + dz).toDouble()
            }
            is PlanStep.Leap -> LEAP_S
            is PlanStep.Hop -> LEAP_S
            is PlanStep.Act -> s.seconds.toDouble()
            is PlanStep.Face -> FACE_S
            is PlanStep.Carry -> {
                val dz = s.z?.let { Math.abs(it - z) / Tuning.Walk.Z_SPEED } ?: 0f
                (Math.abs(s.x - x) / Tuning.Walk.WALK_SPEED + dz).toDouble()
            }
            is PlanStep.Kick -> s.juggleSeconds.toDouble()
            is PlanStep.Chase -> s.seconds.toDouble()
            // the toy beats without a time of their own: a mouthful, a swat, a spring
            is PlanStep.PickUp,
            is PlanStep.Toss,
            is PlanStep.Bat,
            is PlanStep.Pounce,
            PlanStep.SetDown -> TOY_BEAT_S
        }

    private fun finish(s: PlanStep) {
        when (s) {
            is PlanStep.Walk -> {
                x =
                    if (surface == SurfaceId.FLOOR) World.clampHouse(s.x)
                    else World.clampTo(surface, s.x)
                if (surface == SurfaceId.FLOOR && s.z != null) z = s.z
            }
            is PlanStep.Leap -> land(s.surface, s.x)
            is PlanStep.Hop -> land(s.surface, s.x)
            is PlanStep.Carry -> {
                x =
                    if (surface == SurfaceId.FLOOR) World.clampHouse(s.x)
                    else World.clampTo(surface, s.x)
                if (surface == SurfaceId.FLOOR && s.z != null) z = s.z
            }
            else -> Unit
        }
    }

    private fun land(to: SurfaceId, at: Float) {
        surface = to
        x = World.clampTo(to, at)
        z = if (to == SurfaceId.FLOOR) z else World.surface(to).z
    }

    private companion object {
        const val LEAP_S = 1.1
        const val FACE_S = 0.3
        const val TOY_BEAT_S = 0.8
    }
}

/** The row names the scripts play, checked against the clip table once. */
internal object Rows {
    const val SLEEP = "sleep"
    const val LOAF = "loaf"
    const val SIT = "idle_sit"
    const val GROOM = "groom"
    const val EAT = "eat"
    const val STRETCH = "stretch"
    const val LOOKUP = "lookup"
    const val SLOWBLINK = "slowblink"
    const val KNEAD = "knead"
    const val MEOW = "meow"
    const val SCRATCH = "scratch"
    const val SCRATCH_IN = "scratch_in"
    const val SCRATCH_OUT = "scratch_out"
    const val SNIFF = "sniff"
    const val YAWN = "yawn"
    const val SHAKE = "shake"
    const val TURN = "turn"

    /** Every row named here exists in the clip table (BrainCoreTest asserts it). */
    val ALL =
        listOf(
            SLEEP,
            LOAF,
            SIT,
            GROOM,
            EAT,
            STRETCH,
            LOOKUP,
            SLOWBLINK,
            KNEAD,
            MEOW,
            SCRATCH,
            SCRATCH_IN,
            SCRATCH_OUT,
            SNIFF,
            YAWN,
            SHAKE,
            TURN,
        )

    /** Seconds of one pass of row [name] at its base rate. */
    fun once(name: String): Double = (Clips.find(name)?.seconds ?: 1f).toDouble()
}
