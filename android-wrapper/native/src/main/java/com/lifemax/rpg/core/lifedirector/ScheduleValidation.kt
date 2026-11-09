package com.lifemax.rpg.core.lifedirector

import com.lifemax.rpg.core.lifedirector.PageValues.member
import com.lifemax.rpg.core.lifedirector.PageValues.number
import com.lifemax.rpg.core.lifedirector.PageValues.numberText
import com.lifemax.rpg.core.lifedirector.PageValues.order
import com.lifemax.rpg.core.lifedirector.PageValues.same
import com.lifemax.rpg.core.lifedirector.PageValues.text
import com.lifemax.rpg.core.lifedirector.PageValues.truthy
import com.lifemax.rpg.core.lifedirector.ScheduleJson.bool
import com.lifemax.rpg.core.lifedirector.ScheduleJson.num
import com.lifemax.rpg.core.lifedirector.ScheduleJson.obj
import com.lifemax.rpg.core.lifedirector.ScheduleJson.str
import com.lifemax.rpg.core.save.JsonArray
import com.lifemax.rpg.core.save.JsonNumber
import com.lifemax.rpg.core.save.JsonObject
import com.lifemax.rpg.core.save.JsonString
import com.lifemax.rpg.core.save.JsonValue
import kotlin.math.abs
import kotlin.math.ceil

/**
 * LDV-1 to LDV-6 `validateSchedule` (`engine.js:1537-1614`): the plan checked against its input
 * again, independently of the planner's working state: availability is derived afresh from [input],
 * and every session ([planned], the page's objects) and rejection ([rejected]) is read as stored,
 * with JavaScript's coercions (LDG-6). [displacedIds] is the caller's list of excused pinned
 * sessions; [fair] the planner's fairness ledger (none: fairness reads 1). Throws as the page does
 * on malformed values.
 */
object ScheduleValidation {
    private const val SLOT_MINUTES = 15.0
    private const val MINUTE = 60.0
    private const val HOUR = 3600.0
    private val KEPT_WARNINGS = setOf("SLEEP_BELOW_FLOOR", "SLEEP_COMMITMENT_OVERLAP")
    private val ACTIVE_STATES = listOf("PLANNED", "IN_PROGRESS")
    private val THREE = JsonNumber(3.0)
    private const val FREE_CODE = 0
    private const val SQI_REQUIRED = 40
    private const val SQI_ALL = 15
    private const val SQI_RECOVERY = 20
    private const val SQI_FAIRNESS = 15
    private const val SQI_UNCOMPRESSED = 10
    private const val TENTHS = 10.0
    private const val THOUSANDTHS = 1000.0
    private const val PINNED = -2
    private const val NONE = -1

    fun validate(
        input: JsonValue?,
        planned: JsonValue?,
        rejected: JsonValue?,
        displacedIds: JsonValue? = null,
        fair: Fairness? = null,
    ): JsonValue {
        if (!PageValues.present(input)) throw PageTypeError("Cannot read properties of undefined")
        val d = Availability.derive(input)
        val g = d.grid
        val prefs = d.prefs
        val n = g.count.toInt()
        val now = member(input, "currentTimeEpoch")
        val nowSlot =
            PageValues.clamp(
                g.slots.slotAtOrAfter(plusMinutes(now, number(prefs["leadMinutes"]) * MINUTE)),
                0.0,
                g.count,
            )
        val violations = ArrayList<JsonValue>()
        val warnings = ArrayList<JsonValue>()
        for (w in d.warnings) if (w.code in KEPT_WARNINGS) warnings += ScheduleJson.warning(w)
        val sessions = items(or(planned), "forEach")
        val rejections = or(rejected)
        val occ = IntArray(n) { NONE }
        val byDay = HashMap<Int, MutableList<JsonValue>>()
        sessions.forEachIndexed { idx, inst ->
            val start = prop(inst, "startEpoch")
            val a = g.slots.slotAt(number(start))
            val b = g.slots.slotAtOrAfter(number(prop(inst, "endEpoch")))
            val title = text(member(inst, "title"))
            if (b <= a)
                violations +=
                    violation(
                        "I1",
                        "instanceId",
                        member(inst, "instanceId"),
                        "Zero-length instance",
                    )
            var day = PINNED
            var i = maxOf(0.0, a)
            val until = minOf(g.count, b)
            while (i < until) {
                val k = i.toInt()
                i += 1
                if (i - 1 < nowSlot && PageValues.lessOrEqual(start, now)) {
                    occ[k] = idx
                    continue
                }
                if (g.type[k] != FREE_CODE) {
                    violations +=
                        violation(
                            "I1",
                            "instanceId",
                            member(inst, "instanceId"),
                            "\"$title\" overlaps " + nameOf(g.type[k]),
                        )
                    break
                }
                if (occ[k] >= 0 || occ[k] == PINNED) {
                    val other =
                        if (occ[k] >= 0) sessions[occ[k]]
                        else throw PageTypeError("Cannot read properties of undefined")
                    violations +=
                        violation(
                            "I1_OVERLAP",
                            "instanceId",
                            member(inst, "instanceId"),
                            "\"$title\" overlaps \"" + text(prop(other, "title")) + "\"",
                        )
                    break
                }
                occ[k] = idx
                if (day == PINNED) day = g.wakeCycle[k]
                else if (g.wakeCycle[k] != day) {
                    violations +=
                        violation(
                            "I1_SLEEP_CROSS",
                            "instanceId",
                            member(inst, "instanceId"),
                            "Crosses a sleep boundary",
                        )
                    break
                }
            }
            if (day >= 0) byDay.getOrPut(day) { ArrayList() } += inst
            if (truthy(member(inst, "isUserLocked"))) {
                var j = maxOf(0.0, a)
                while (j < until) {
                    if (occ[j.toInt()] == idx) occ[j.toInt()] = PINNED
                    j += 1
                }
            }
            val explanation = member(inst, "explanation")
            if (!truthy(explanation) || !truthy(member(explanation, "summary")))
                violations +=
                    violation(
                        "I10",
                        "instanceId",
                        member(inst, "instanceId"),
                        "Missing explanation",
                    )
            hardChecks(inst, sessions, now, violations)
        }
        spacing(sessions, now, violations)
        val need = ceil(number(prefs["minDailyRecoveryMinutes"]) / SLOT_MINUTES)
        var dayOk = 0
        var dayCount = 0
        d.wakeCycles.forEachIndexed { bi, bw ->
            if (bw.endSlot <= nowSlot || bw.truncatedStart) return@forEachIndexed
            var free = 0
            var used = 0
            var i = maxOf(bw.startSlot, nowSlot)
            while (i < bw.endSlot) {
                val k = i.toInt()
                i += 1
                if (g.type[k] != FREE_CODE || occ[k] == PINNED) continue
                free++
                if (occ[k] >= 0) used++
            }
            dayCount++
            val keep = minOf(need, free.toDouble())
            if (free - used < keep)
                violations +=
                    violation(
                        "I3",
                        "bwcId",
                        str(bw.id),
                        "Wake cycle " +
                            bw.dateKey +
                            " keeps only " +
                            numberText((free - used) * SLOT_MINUTES) +
                            " min of downtime (needs " +
                            numberText(keep * SLOT_MINUTES) +
                            ")",
                    )
            else dayOk++
            if (free < need)
                warnings +=
                    violation(
                        "RECOVERY_SHORTFALL",
                        "bwcId",
                        str(bw.id),
                        bw.dateKey +
                            ": your commitments leave only " +
                            numberText(free * SLOT_MINUTES) +
                            " free minutes, so all of it is kept as downtime.",
                    )
            val mine = byDay[bi].orEmpty()
            val auto =
                mine.count { !truthy(member(it, "isUserLocked")) && !truthy(member(it, "kept")) }
            val minutes =
                mine
                    .filter { !truthy(member(it, "isUserLocked")) }
                    .fold(0.0) { m, x -> m + number(member(x, "durationSlots")) * SLOT_MINUTES }
            if (auto > 0 && order(JsonNumber(minutes), prefs["maxDailyGoalMinutes"]) == 1)
                violations +=
                    violation(
                        "H_DAILY_CAP",
                        "bwcId",
                        str(bw.id),
                        bw.dateKey + " exceeds the daily effort limit",
                    )
        }
        pinned(input, sessions, rejections, displacedIds, g.slots, violations)
        for (r in iterate(rejections)) if (!truthy(prop(r, "humanExplanation")))
            violations +=
                violation("I10", "taskId", member(r, "taskId"), "Rejected task without explanation")
        val missed = items(rejections, "filter")
        val metrics = metrics(sessions, missed, dayOk, dayCount, fair)
        return obj {
            this["isValid"] = bool(violations.isEmpty())
            this["violatedInvariants"] = JsonArray(violations)
            this["warnings"] = JsonArray(warnings)
            this["qualityMetrics"] = metrics
        }
    }

    /**
     * LDV-2: an auto-placed session still to start keeps its deadline, its week window and its
     * prerequisites.
     */
    private fun hardChecks(
        inst: JsonValue,
        sessions: List<JsonValue>,
        now: JsonValue?,
        out: MutableList<JsonValue>,
    ) {
        val source = member(inst, "source").takeIf(::truthy)
        val id = member(inst, "instanceId")
        val title = text(member(inst, "title"))
        if (
            truthy(member(inst, "isUserLocked")) ||
                truthy(member(inst, "kept")) ||
                order(prop(inst, "startEpoch"), now) != 1
        )
            return
        val end = member(inst, "endEpoch")
        val deadline = member(source, "deadlineEpoch")
        if (truthy(deadline) && order(end, deadline) == 1)
            out += violation("H_DEADLINE", "instanceId", id, "\"$title\" ends after its deadline")
        val windowEnd = member(source, "windowEnd")
        if (truthy(windowEnd) && order(end, windowEnd) == 1)
            out += violation("H_WINDOW", "instanceId", id, "\"$title\" is outside its week window")
        val deps = member(source, "dependsOn")
        for (dep in if (truthy(deps)) iterate(deps) else emptyList()) {
            val di =
                sessions.firstOrNull { p ->
                    same(prop(p, "taskId"), dep) ||
                        same(
                            member(p, "instanceId"),
                            JsonString(
                                "i" +
                                    (dep as? JsonString
                                            ?: throw PageTypeError("dep.slice is not a function"))
                                        .value
                                        .drop(1)
                            ),
                        )
                }
            if (di == null) out += violation("I5", "instanceId", id, "Prerequisite not scheduled")
            else {
                val lag =
                    number(member(source, "minLagMin").takeIf(::truthy) ?: JsonNumber(0.0)) * MINUTE
                if (order(plus(member(di, "endEpoch"), lag), member(inst, "startEpoch")) == 1)
                    out +=
                        violation(
                            "I5",
                            "instanceId",
                            id,
                            "\"$title\" starts before its prerequisite ends",
                        )
            }
        }
    }

    /**
     * LDV-3: auto-placed sessions of one spacing key (in the order keys first appear) closer than
     * its minimum gap.
     */
    private fun spacing(sessions: List<JsonValue>, now: JsonValue?, out: MutableList<JsonValue>) {
        val byKey = LinkedHashMap<SpacingKey, MutableList<JsonValue>>()
        for (p in sessions) {
            val source = member(p, "source")
            if (truthy(member(p, "isUserLocked")) || !truthy(source)) continue
            if (order(member(source, "spacingMinH"), JsonNumber(0.0)) != 1) continue
            byKey.getOrPut(SpacingKey(member(p, "spacingKey"))) { ArrayList() } += p
        }
        for (list in byKey.values) {
            val sorted =
                list.sortedWith { a, b ->
                    PageValues.byDifference(
                        number(member(a, "startEpoch")) - number(member(b, "startEpoch"))
                    )
                }
            for (i in 1 until sorted.size) {
                val x = sorted[i]
                val gap =
                    number(member(x, "startEpoch")) - number(member(sorted[i - 1], "startEpoch"))
                val min = member(member(x, "source"), "spacingMinH")
                if (
                    gap < number(min) * HOUR &&
                        order(member(x, "startEpoch"), now) == 1 &&
                        !truthy(member(x, "kept"))
                )
                    out +=
                        violation(
                            "H_SPACING",
                            "instanceId",
                            member(x, "instanceId"),
                            "\"" +
                                text(member(x, "title")) +
                                "\" is closer than " +
                                text(min) +
                                "h to a similar session",
                        )
            }
        }
    }

    /** LDV-5: a pinned stored session inside the plan must be on it unchanged, unless excused. */
    private fun pinned(
        input: JsonValue?,
        sessions: List<JsonValue>,
        rejections: JsonValue,
        displacedIds: JsonValue?,
        slots: SlotGrid,
        out: MutableList<JsonValue>,
    ) {
        val existing = member(input, "existingSchedule")
        for (e in if (truthy(existing)) iterate(existing) else emptyList()) {
            if (!truthy(prop(e, "isUserLocked"))) continue
            val state = member(e, "lifecycleState")
            if (!(state is JsonString && state.value in ACTIVE_STATES)) continue
            if (PageValues.lessOrEqual(member(e, "endEpoch"), JsonNumber(slots.startEpoch)))
                continue
            if (PageValues.atLeast(member(e, "startEpoch"), JsonNumber(slots.endEpoch))) continue
            val id = member(e, "instanceId")
            val p = sessions.firstOrNull { same(prop(it, "instanceId"), id) }
            val excused =
                includes(displacedIds, id) ||
                    items(rejections, "some").any { same(prop(it, "instanceId"), id) }
            if (
                !excused &&
                    (p == null ||
                        !same(member(p, "startEpoch"), member(e, "startEpoch")) ||
                        !same(member(p, "endEpoch"), member(e, "endEpoch")))
            )
                out += violation("I7", "instanceId", id, "A pinned activity was moved")
        }
    }

    /** LDV-6: the quality metrics and SQI (`engine.js:1604-1613`). */
    private fun metrics(
        sessions: List<JsonValue>,
        missed: List<JsonValue>,
        dayOk: Int,
        dayCount: Int,
        fair: Fairness?,
    ): JsonValue {
        val req = sessions.count { PageValues.atLeast(prop(it, "tier"), THREE) }.toDouble()
        val reqMiss = missed.count { PageValues.atLeast(prop(it, "tier"), THREE) }.toDouble()
        val all = sessions.size.toDouble()
        val allMiss = missed.count { truthy(prop(it, "tier")) }.toDouble()
        val coverageRequired = if (truthy(req + reqMiss)) req / (req + reqMiss) else 1.0
        val coverageAll = if (truthy(all + allMiss)) all / (all + allMiss) else 1.0
        val recovery = if (dayCount != 0) dayOk.toDouble() / dayCount else 1.0
        val compressed =
            if (truthy(all)) sessions.count { truthy(prop(it, "compressed")) } / all else 0.0
        var fairness = 1.0
        if (fair != null) {
            val ids = PageKeys.ordered(fair.share.keys)
            if (ids.size > 1 && truthy(fair.total))
                fairness =
                    1 -
                        ids.fold(0.0) { m, id ->
                            m +
                                abs(
                                    (fair.goalMinutes[id]?.takeIf(PageValues::truthy) ?: 0.0) /
                                        fair.total - fair.share.getValue(id)
                                )
                        } / 2
        }
        val sqi =
            PageValues.round(
                (SQI_REQUIRED * coverageRequired +
                    SQI_ALL * coverageAll +
                    SQI_RECOVERY * recovery +
                    SQI_FAIRNESS * fairness +
                    SQI_UNCOMPRESSED * (1 - compressed)) * TENTHS
            ) / TENTHS
        return obj {
            this["sqi"] = num(sqi)
            this["coverageRequired"] = num(coverageRequired)
            this["coverageAll"] = num(coverageAll)
            this["recoveryCompliance"] = num(recovery)
            this["fairness"] = num(PageValues.round(fairness * THOUSANDTHS) / THOUSANDTHS)
            this["compressedFraction"] = num(compressed)
        }
    }

    private fun nameOf(code: Int) = SlotCode.entries.first { it.code == code }.pageName

    private fun violation(code: String, idKey: String, id: JsonValue?, message: String) = obj {
        this["code"] = str(code)
        this[idKey] = id
        this["message"] = str(message)
    }

    /** `value || []`. */
    private fun or(value: JsonValue?): JsonValue = if (truthy(value)) value!! else JsonArray.EMPTY

    /** A list's items, for a list method ([method]); anything else throws a TypeError. */
    private fun items(value: JsonValue, method: String): List<JsonValue> =
        (value as? JsonArray)?.items ?: throw PageTypeError("$method is not a function")

    /** `for (x of value)`: a list's items, a text's units, else a TypeError. */
    private fun iterate(value: JsonValue?): List<JsonValue> = PlanState.iterate(value, "value")

    /**
     * `value.key`: undefined for a value that is not an object, a TypeError for null or undefined.
     */
    private fun prop(value: JsonValue?, key: String): JsonValue? {
        if (!PageValues.present(value))
            throw PageTypeError("Cannot read properties of null (reading '$key')")
        return member(value, key)
    }

    /** `list.includes(id)` on `displacedIds || []`. */
    private fun includes(list: JsonValue?, id: JsonValue?): Boolean =
        when {
            !truthy(list) -> false
            list is JsonArray -> list.items.any { same(it, id) || bothNaN(it, id) }
            list is JsonString -> list.value.contains(text(id))
            else -> throw PageTypeError("includes is not a function")
        }

    private fun bothNaN(a: JsonValue?, b: JsonValue?) =
        a is JsonNumber && b is JsonNumber && a.value.isNaN() && b.value.isNaN()

    /** `value + number` kept as the page keeps it: text joins, anything else adds. */
    private fun plus(value: JsonValue?, n: Double): JsonValue =
        if (value is JsonString || value is JsonArray || value is JsonObject)
            JsonString(text(value) + numberText(n))
        else JsonNumber(number(value) + n)

    /** `now + lead` for the clock's stored value, read back as a number (text joins first). */
    private fun plusMinutes(now: JsonValue?, lead: Double): Double = number(plus(now, lead))

    /** A Map key as the page's `Map` compares them (SameValueZero: NaN finds NaN, -0 is 0). */
    private class SpacingKey(val value: JsonValue?) {
        override fun equals(other: Any?): Boolean =
            other is SpacingKey &&
                (same(value, other.value) ||
                    bothNaN(value, other.value) ||
                    (value is JsonArray || value is JsonObject) && value === other.value)

        override fun hashCode(): Int =
            when (value) {
                is JsonNumber -> if (value.value == 0.0) 0 else value.value.hashCode()
                null -> -1
                is JsonArray,
                is JsonObject -> System.identityHashCode(value)
                else -> value.hashCode()
            }
    }
}

/** The order in which the page's `Object.keys` lists an object's keys. */
object PageKeys {
    private const val MAX_INDEX = 4294967294.0

    /**
     * Array-index keys (`0`, `1`, ... below 2^32 - 1, no leading zero) first in number order, then
     * the rest as inserted.
     */
    fun ordered(keys: Collection<String>): List<String> {
        val (index, rest) = keys.partition(::isIndex)
        return index.sortedBy { it.toDouble() } + rest
    }

    fun isIndex(k: String): Boolean =
        k.isNotEmpty() &&
            k.all { it in '0'..'9' } &&
            (k == "0" || k[0] != '0') &&
            k.length <= 10 &&
            k.toDouble() <= MAX_INDEX
}
