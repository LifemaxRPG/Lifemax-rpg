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

    /** Play the animation row named [anim] for [seconds]. */
    data class Act(val anim: String, val seconds: Float) : PlanStep()

    /** Turn to face [dir] (+1 right, -1 left). */
    data class Face(val dir: Int) : PlanStep()
}

/** What the body reports back to whoever sent the plan. */
enum class CatEventType {
    /** A step finished. */
    StepDone,

    /** A walk or hop reached its destination (sent just before its StepDone). */
    Arrived,

    /** The plan was replaced while it still had work left. */
    Interrupted,
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
