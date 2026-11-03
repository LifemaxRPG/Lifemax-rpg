package com.lifemax.rpg.feature.pet.body

/** One intent in a plan. These are the four step kinds the page's brain sends. */
sealed class PlanStep {
    /**
     * Walk to [x] (and depth [z] when given) on the surface the cat is on; [run] gallops. On a
     * raised surface only x is used and it is kept on the surface.
     */
    data class Walk(val x: Float, val z: Float? = null, val run: Boolean = false) : PlanStep()

    /** Jump to [surface], landing at [x]. The cat turns to face the jump first. */
    data class Hop(val surface: SurfaceId, val x: Float) : PlanStep()

    /**
     * A planned leap (see [ParkourPlanner]): jump to [surface], landing at [x], taking off from
     * [takeOffX]. A cat standing at the take-off point jumps from rest; one that stands further
     * back (the planner's Walk put it there) runs up to it and jumps at speed. [apex] is the height
     * of the flight above the take-off feet (0 lets the body pick an easy one) and [effort] (0..1)
     * how much of the cat's best the jump takes: a hard one is met with a pause, a look and a
     * measuring wiggle. Only the planner makes these: the page's plans use [Hop].
     */
    data class Leap(
        val surface: SurfaceId,
        val x: Float,
        val takeOffX: Float = x,
        val apex: Float = 0f,
        val effort: Float = 0f,
    ) : PlanStep()

    /** Play the animation row named [anim] for [seconds]. */
    data class Act(val anim: String, val seconds: Float) : PlanStep()

    /** Turn to face [dir] (+1 right, -1 left). */
    data class Face(val dir: Int) : PlanStep()

    /**
     * Walk up to toy [toyId], notice it, reach down and take it in the mouth (see [ToyDirector]).
     */
    data class PickUp(val toyId: Int) : PlanStep()

    /** Walk to [x] (and depth [z]) with the toy in the mouth: the carry row, the head up. */
    data class Carry(val x: Float, val z: Float? = null) : PlanStep()

    /**
     * Toss the carried toy toward [dir] with a head flick of [power] (0..1). With a [surface] the
     * toss is aimed to land on it at [x].
     */
    data class Toss(
        val dir: Int,
        val power: Float = 0.6f,
        val surface: SurfaceId? = null,
        val x: Float = 0f,
    ) : PlanStep()

    /** Lower the head and set the carried toy down in front, on the surface the cat stands on. */
    data object SetDown : PlanStep()

    /**
     * Paw-swat toy [toyId] (lying on the side) with an impulse of [power] (0 soft pat, 1 hard
     * knock).
     */
    data class Bat(val toyId: Int, val power: Float = 0.3f) : PlanStep()

    /** Belly-up: hug toy [toyId] for [juggleSeconds], then kick it away with [power]. */
    data class Kick(val toyId: Int, val power: Float = 0.6f, val juggleSeconds: Float = 2f) :
        PlanStep()

    /** Stalk, crouch, wiggle and pounce on toy [toyId]. */
    data class Pounce(val toyId: Int) : PlanStep()

    /**
     * Follow toy [toyId] while it rolls, for at most [seconds]; ends when it is still and reached.
     */
    data class Chase(val toyId: Int, val seconds: Float = 6f) : PlanStep()
}

/** What the body reports back to whoever sent the plan. */
enum class CatEventType {
    /** A step finished. */
    StepDone,

    /** A walk or hop reached its destination (sent just before its StepDone). */
    Arrived,

    /** The plan was replaced while it still had work left. */
    Interrupted,

    /** The cat took a toy into its mouth. */
    ToyGrabbed,

    /** The cat let a toy go (tossed or set down). */
    ToyReleased,

    /** A paw or foot hit a toy (bat, kick, pounce). */
    ToyHit,
}

/**
 * A fixed-capacity event buffer that is refilled in place on every [CatBody.step], so reporting
 * costs no allocation. Read it right after stepping; it is cleared at the start of the next step.
 */
class CatEvents {
    private val types = Array(CAPACITY) { CatEventType.StepDone }
    private val planIds = IntArray(CAPACITY)
    private val stepIndexes = IntArray(CAPACITY)
    private val lasts = BooleanArray(CAPACITY)

    /** How many events were recorded by the latest step call. */
    var count = 0
        private set

    /** How many events did not fit and were dropped (a frame that stalled for seconds). */
    var dropped = 0
        private set

    fun type(i: Int): CatEventType = types[i]

    fun planId(i: Int): Int = planIds[i]

    fun stepIndex(i: Int): Int = stepIndexes[i]

    /** True when the event is the end of the plan's last queued step. */
    fun isLastStep(i: Int): Boolean = lasts[i]

    internal fun clear() {
        count = 0
        dropped = 0
    }

    internal fun add(type: CatEventType, planId: Int, stepIndex: Int, last: Boolean) {
        if (count == CAPACITY) {
            dropped++
            return
        }
        types[count] = type
        planIds[count] = planId
        stepIndexes[count] = stepIndex
        lasts[count] = last
        count++
    }

    internal fun copyFrom(o: CatEvents) {
        count = o.count
        dropped = o.dropped
        for (i in 0 until count) {
            types[i] = o.types[i]
            planIds[i] = o.planIds[i]
            stepIndexes[i] = o.stepIndexes[i]
            lasts[i] = o.lasts[i]
        }
    }

    companion object {
        const val CAPACITY = 32
    }
}

/**
 * The queue of steps for the plan being followed. Step indexes count from the start of the plan,
 * even as the page appends more steps behind the cat's back.
 */
internal class PlanQueue {
    private val steps = ArrayList<PlanStep>()
    private var index = 0

    /** Id of the plan being followed, or [NO_PLAN]. */
    var planId = NO_PLAN
        private set

    /** The step being worked on, or null when the queue is empty (idle life). */
    val current: PlanStep?
        get() = if (index < steps.size) steps[index] else null

    /** Index of [current] within the plan. */
    val currentIndex: Int
        get() = index

    /** True when [current] is the last step queued so far. */
    val isOnLast: Boolean
        get() = index == steps.size - 1

    /** True when the step after [current] is a leap: the current leap lands into it. */
    val nextIsLeap: Boolean
        get() = index + 1 < steps.size && steps[index + 1] is PlanStep.Leap

    val hasWork: Boolean
        get() = index < steps.size

    fun load(id: Int, list: List<PlanStep>) {
        steps.clear()
        steps.addAll(list)
        index = 0
        planId = id
    }

    fun append(list: List<PlanStep>) {
        steps.addAll(list)
    }

    fun advance() {
        if (index < steps.size) index++
    }

    fun clear() {
        steps.clear()
        index = 0
    }

    companion object {
        const val NO_PLAN = Int.MIN_VALUE
    }
}
