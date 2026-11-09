package com.lifemax.rpg.core.lifedirector

import com.lifemax.rpg.core.lifedirector.PageValues.number
import com.lifemax.rpg.core.lifedirector.PageValues.text
import com.lifemax.rpg.core.lifedirector.PageValues.truthy
import com.lifemax.rpg.core.save.JsonArray
import com.lifemax.rpg.core.save.JsonNull
import com.lifemax.rpg.core.save.JsonNumber
import com.lifemax.rpg.core.save.JsonObject
import com.lifemax.rpg.core.save.JsonString
import com.lifemax.rpg.core.save.JsonValue

/**
 * LDR-5: the player's energy check-in as placement reads it: from [epoch] on, every slot's energy
 * is drawn toward [value] (0.30 low, 0.65 normal, 0.95 high), halving its pull every six hours.
 * [stored] is the epoch as the input gave it; [at] its number.
 */
class CheckIn(val stored: JsonValue, val value: Double) {
    val at: Double = number(stored)
}

/**
 * A session on the plan (LDR-3, LDW-1): one placement committed for a task, or a stored session the
 * re-plan keeps where it is ([stored] holds it as the input gave it, [kept] true). Its numbers and
 * keys are the page's values: [physical] and [cognitive] as stored (text, missing or a number),
 * [spacingKey] as stored, [goalLinks] the linked goal ids as the list gives them, [questLinks] the
 * requirements it counts toward (coverage adds to a committed session's, LDW-6), and a committed
 * session's [commitment] what it took from its task.
 */
class PlanSession(
    val instanceId: JsonValue?,
    val taskId: JsonValue?,
    val templateId: JsonValue?,
    val tag: JsonValue?,
    val startEpoch: Double,
    val endEpoch: Double,
    val physical: JsonValue?,
    val cognitive: JsonValue?,
    val spacingKey: JsonValue?,
    val goalLinks: List<JsonValue>,
    val questLinks: MutableList<JsonValue>,
    val durationSlots: Double,
    val cycleId: String,
    val kept: Boolean,
    val stored: JsonObject? = null,
    val commitment: Commitment? = null,
) {
    /** LDK-2: a session of intensity 4 or more on either axis. */
    val high: Boolean
        get() = atLeastFour(physical) || atLeastFour(cognitive)

    /** Whether it counts toward requirement [reqId] of quest [questId] (LDW-6). */
    fun counts(questId: String, reqId: String): Boolean =
        questLinks.any {
            PageValues.same(PageValues.member(it, "questId"), JsonString(questId)) &&
                PageValues.same(PageValues.member(it, "reqId"), JsonString(reqId))
        }

    companion object {
        private val FOUR = JsonNumber(4.0)

        /** `v >= 4` on a stored value (LDG-6). */
        fun atLeastFour(v: JsonValue?): Boolean = PageValues.atLeast(v, FOUR)
    }
}

/**
 * A candidate task (LDC) during placement: what it is ([task]), its constants ([prepared], LDE-2),
 * and what placement learns: whether it is placed (by its own session or by a session that covers
 * its day, [coveredBy], LDW-6) and where a re-plan found it before ([origStartEpoch], LDR-3).
 */
class PlanTask(val task: CandidateTask, val prepared: PreparedTask) {
    var isPlaced = false
    var coveredBy: JsonValue? = null
    var origStartEpoch: JsonValue? = null

    val id: String
        get() = task.id

    /** LDK-2: whether it is high-intensity (LDE-2's `_high`). */
    val high: Boolean
        get() = prepared.high

    val physical: Double = number(task.physical)
    val cognitive: Double = number(task.cognitive)
    /** LDU-1: whether the physical axis leads (`IP > IC`, LDG-6's comparison). */
    val physicalLeads: Boolean = PageValues.order(task.physical, task.cognitive) == 1

    /** The ideal spacing in hours, as a number. */
    val idealGap: Double = number(task.spacingIdealH)

    val physicalHigh: Boolean = PlanSession.atLeastFour(task.physical)
    val cognitiveHigh: Boolean = PlanSession.atLeastFour(task.cognitive)

    /** The quest deadline's number, or infinity when it has none (`deadlineEpoch || Infinity`). */
    val deadline: Double =
        if (truthy(task.deadlineEpoch)) number(task.deadlineEpoch) else Double.POSITIVE_INFINITY

    /** Whether it depends on [taskId] (LDW-5; `dependsOn.includes`). */
    fun dependsOnTask(taskId: JsonValue?): Boolean =
        taskId is JsonString && task.dependsOn.contains(taskId.value)
}

/**
 * LDR-1, LDR-4: one waking day (LDA-13) during placement: its sessions in the order they were put
 * there, how many of each spacing key ([perKey], keyed by the key's text), its goal minutes and
 * high-intensity count, its sleep factor (LDR-2) and recovery reservation (LDR-4).
 */
class CycleState(val cycle: WakeCycle) {
    val sessions = ArrayList<PlanSession>()
    val perKey = LinkedHashMap<String, Double>()
    var goalMinutes = 0.0
    var highCount = 0
    var sleepFactor = 0.0
    var freeSlots = 0
    var recoverySlots = 0
    var recoveryShortfall = false

    val index: Int
        get() = cycle.index

    val start: Int
        get() = cycle.startSlot.toInt()

    val end: Int
        get() = cycle.endSlot.toInt()
}

/**
 * The fairness ledger (LDU-2): goal minutes placed per goal (keyed by the id's text), their total
 * over sessions linked to any goal, and each goal's target share (LDE-4).
 */
class Fairness {
    val goalMinutes = LinkedHashMap<String, Double>()
    var total = 0.0
    val share = LinkedHashMap<String, Double>()
}

/**
 * The state placement works on (`buildState`, LDR-1): the painted availability ([plan]), the slot
 * arrays of the energy model ([energy], with [baseP] and [baseC] in single precision as the page's
 * `Float32Array`s, and [owner], the session index in each slot), the clock and the first plannable
 * slot ([nowSlot]), the sleep need, the check-in, the waking days, the sessions in the order they
 * were added, the spacing ledger (each key's session starts, keys as the page's map holds them, in
 * the order first seen), the fairness ledger and the tasks.
 */
class PlanState(
    val input: JsonValue?,
    val plan: AvailabilityPlan,
    val now: Double,
    val nowSlot: Double,
    val sleepTarget: JsonValue,
    val checkIn: CheckIn?,
) {
    val slots: SlotGrid
        get() = plan.grid.slots

    val prefs: PlanPreferences
        get() = plan.prefs

    val size: Int = plan.grid.count.toInt()
    val energy = EnergyGrid(size)
    val baseP = FloatArray(size)
    val baseC = FloatArray(size)
    val owner = IntArray(size) { -1 }
    val cycles: List<CycleState> = plan.wakeCycles.map(::CycleState)
    val sessions = ArrayList<PlanSession>()
    val sessionById = HashMap<JsonValue?, PlanSession>()
    val spacing = LinkedHashMap<JsonValue?, MutableList<Double>>()
    val fair = Fairness()
    var tasks: List<PlanTask> = emptyList()

    /** The check-in's pull per slot once worked out (NaN not yet), see [EnergyModel.at]. */
    val checkInPull = DoubleArray(size) { Double.NaN }

    private val expKept = HashMap<Double, Double>()

    /**
     * `Math.exp` as the page computes it ([PageMath.exp]), kept per argument: scoring asks the same
     * spacing and movement exponentials again and again, and the kept value is the same double.
     */
    fun exp(x: Double): Double = expKept.getOrPut(x) { PageMath.exp(x) }

    /** Fairness off: the construction's first scores are raw (LDW-2). */
    var rawScores = false

    /**
     * LDU-1: the avoided windows as scoring iterates them (`for (aw of avoidWindows || [])`): a
     * list's items or a text's units, each window's `startMin` and `endMin`; a null window, or a
     * value that is neither a list nor text, throws when scoring first reads it.
     */
    val avoidWindows: List<Pair<JsonValue?, JsonValue?>> by lazy {
        val v = prefs["avoidWindows"]
        if (!truthy(v)) emptyList()
        else
            iterate(v, "avoidWindows").map {
                if (it == JsonNull) throw PageTypeError("Cannot read properties of null")
                PageValues.member(it, "startMin") to PageValues.member(it, "endMin")
            }
    }

    /**
     * LDU-1: whether each weekday (0 Sunday) is a preferred rest day, `(preferredRestDays ||
     * []).includes(weekday)`: a list holding that number, or a text holding its digit; any other
     * truthy value throws when scoring first reads it.
     */
    val restDays: BooleanArray by lazy {
        val v = prefs["preferredRestDays"]
        BooleanArray(DAYS_IN_WEEK) { day ->
            when {
                !truthy(v) -> false
                v is JsonArray -> v.items.any { it is JsonNumber && it.value == day.toDouble() }
                v is JsonString -> v.value.contains(day.toString())
                else -> throw PageTypeError("preferredRestDays.includes is not a function")
            }
        }
    }

    init {
        val g = plan.grid
        g.type.copyInto(energy.type)
        g.load.copyInto(energy.load)
        g.tag.copyInto(energy.tag)
        energy.restorative = g.blocks.map { it.meta?.restorative == true }
    }

    /** The start of slot [s]. */
    fun epochOf(s: Double): Double = slots.epochAt(s)

    /** The slot code at [s]. */
    fun code(s: Int): Int = energy.type[s]

    /** LDR-3 `addSpacing`. */
    fun addSpacing(key: JsonValue?, start: Double) {
        spacing.getOrPut(key) { ArrayList() } += start
    }

    /** The session starts of spacing key [key] (a task's key is always text). */
    fun spacingOf(key: String): List<Double>? = spacing[JsonString(key)]

    /** LDE-3 `recomputeBwc` for waking day [c]. */
    fun recompute(c: CycleState) = WakeCycleDrain.recompute(energy, nowSlot, c.start, c.end)

    /**
     * LDR-3 `occupy`: [session] fills its slots (rounded out to whole slots, cut to the grid) with
     * [code], its index and its intensities (as the page's byte arrays hold them), joins waking day
     * [cycle], counts toward its key, goal minutes and high count there, the spacing ledger and the
     * fairness ledger, and the day's drain is worked out again. A stored session whose goal links
     * are no list stops the page with a `TypeError` (text is read unit by unit).
     */
    fun occupy(session: PlanSession, cycle: Int, code: SlotCode) {
        val c = cycles[cycle]
        val a = PageValues.clamp(slots.slotAt(session.startEpoch), 0.0, size.toDouble())
        val b = PageValues.clamp(slots.slotAtOrAfter(session.endEpoch), 0.0, size.toDouble())
        val index = sessions.size
        var i = a
        while (i < b) {
            val k = i.toInt()
            energy.type[k] = code.code
            owner[k] = index
            energy.taskPhysical[k] = toUint8(session.physical)
            energy.taskCognitive[k] = toUint8(session.cognitive)
            i += 1
        }
        sessions += session
        sessionById[session.instanceId] = session
        c.sessions += session
        val key = text(session.spacingKey)
        c.perKey[key] = (c.perKey[key] ?: 0.0) + 1
        val minutes = (b - a) * SLOT_MINUTES
        c.goalMinutes += minutes
        if (session.high) c.highCount++
        addSpacing(session.spacingKey, session.startEpoch)
        for (gid in session.goalLinks) {
            val k = text(gid)
            fair.goalMinutes[k] = (fair.goalMinutes[k] ?: 0.0) + minutes
        }
        if (session.goalLinks.isNotEmpty()) fair.total += minutes
        recompute(c)
    }

    companion object {
        const val SLOT_MINUTES = 15.0

        /** JavaScript's ToUint8, what a `Uint8Array` stores of a value. */
        fun toUint8(v: JsonValue?): Int {
            val n = number(v)
            if (n.isNaN() || n.isInfinite()) return 0
            val whole = if (n < 0) -kotlin.math.floor(-n) else kotlin.math.floor(n)
            return Math.floorMod(whole.rem(BYTE).toLong(), BYTE.toLong()).toInt()
        }

        private const val BYTE = 256.0
        private const val DAYS_IN_WEEK = 7

        /**
         * The items `for (x of value)` visits: a list's items, a text's units, else a TypeError.
         */
        fun iterate(value: JsonValue?, what: String): List<JsonValue> =
            when (value) {
                is JsonArray -> value.items
                is JsonString -> value.value.map { JsonString(it.toString()) }
                else -> throw PageTypeError("$what is not iterable")
            }
    }
}
