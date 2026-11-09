package com.lifemax.rpg.core.lifedirector

import com.lifemax.rpg.core.lifedirector.PageValues.member
import com.lifemax.rpg.core.lifedirector.PageValues.number
import com.lifemax.rpg.core.lifedirector.PageValues.or
import com.lifemax.rpg.core.lifedirector.PageValues.orZero
import com.lifemax.rpg.core.lifedirector.PageValues.present
import com.lifemax.rpg.core.lifedirector.PageValues.text
import com.lifemax.rpg.core.lifedirector.PageValues.truthy
import com.lifemax.rpg.core.save.JsonArray
import com.lifemax.rpg.core.save.JsonNull
import com.lifemax.rpg.core.save.JsonNumber
import com.lifemax.rpg.core.save.JsonString
import com.lifemax.rpg.core.save.JsonValue
import kotlin.math.ceil

/**
 * LDA-13: one waking day, from a main sleep's end ([wakeEpoch]) to the next main sleep's start
 * ([sleepEpoch]), slots [startSlot] up to [endSlot]. [skippedMeals] are the meals LDA-15 found no
 * room for; [heavyShiftEndSlot] is LDA-16's marker (-1 none).
 */
class WakeCycle(
    val index: Int,
    val wakeEpoch: Double,
    val sleepEpoch: Double,
    val startSlot: Double,
    val endSlot: Double,
    val sleepBeforeMin: Double,
    val dateKey: String,
    val weekday: Int,
    val truncatedStart: Boolean,
) {
    /** The page's id, `bwc-` and the wake-up epoch. */
    val id: String
        get() = "bwc-" + PageValues.numberText(wakeEpoch)

    var heavyShiftEndSlot: Double = -1.0
        internal set

    val skippedMeals = ArrayList<String>()
}

/**
 * The result of [Availability.derive]: the zone value as given ([zoneValue]), the checked
 * preferences, the rules sorted by id, the painted [grid], the waking days and the warnings in the
 * page's order.
 */
class AvailabilityPlan(
    val zoneValue: JsonValue,
    val prefs: PlanPreferences,
    val rules: List<AvailabilityRule>,
    val grid: AvailabilityGrid,
    val wakeCycles: List<WakeCycle>,
    val warnings: List<AvailabilityWarning>,
)

/**
 * LDA-10 to LDA-17 `deriveAvailability`: the plan's days painted with everything the player is not
 * free for, in the page's order: commitments, blackouts and rules (LDA-11), their commutes and
 * decompression (LDA-12), sleep (LDS), the waking days between main sleeps (LDA-13), the morning
 * routine, meals and wind-down of each (LDA-14, LDA-15), heavy-shift markers (LDA-16) and the
 * preferred windows (LDA-17). The input is the engine's input object as the page builds it.
 */
object Availability {
    private const val MINUTE = 60.0
    private const val HOUR = 3600.0
    private const val DAY = 86400.0
    private const val MS_PER_SECOND = 1000.0
    private const val MAIN_SLEEP = 3 * HOUR
    private const val SHIFT = 3 * HOUR
    private const val HEAVY_SHIFT = 6 * HOUR
    private const val WAKING_DAY = 16 * HOUR
    private const val SHIFT_DECOMPRESS = 30.0
    private const val SLEEP_NEED = 480.0
    private const val MEAL_SLOT_MINUTES = 15.0
    private const val MEAL_REACH = 8
    private const val MIN_WAKE_SLOTS = 2
    private val MEAL_NAMES = listOf("Breakfast", "Lunch", "Dinner")
    private val LOADS =
        mapOf(
            "NONE" to 0,
            "PHYSICAL_HEAVY" to 1,
            "COGNITIVE_HEAVY" to 2,
            "MIXED_MODERATE" to 3,
            "LIGHT" to 4,
        )
    private const val DEFAULT_LOAD = 3

    /** LDA-10: the engine's availability of [params]. Throws as the page does (LDG-5). */
    fun derive(params: JsonValue?): AvailabilityPlan {
        val av = member(params, "playerAvailability")
        val zoneValue = or(or(member(params, "timezone"), member(av, "timezone")), "UTC")
        val zone =
            ZoneNames.resolve(text(zoneValue))
                ?: throw EngineRefusal("Invalid IANA timezone: " + text(zoneValue))
        val prefs = PlanPreferences.of(member(params, "playerPreferences"))
        val start = number(member(params, "horizonStartEpoch"))
        if (EcmaTime.timeClip(start * MS_PER_SECOND).isNaN())
            throw PageRangeError("Invalid time value")
        val dayCount = number(or(member(params, "horizonDays"), prefs.planningHorizonDays))
        val days = PlanHorizon.days(start, if (dayCount > 0) ceil(dayCount).toInt() else 0, zone)
        if (days.isEmpty()) throw PageTypeError("no plan days")
        val grid = AvailabilityGrid(SlotGrid.of(days, zone))
        val rules =
            listItems(or(member(av, "rules"), member(av, "weeklyRecurringRules")), "map")
                .mapIndexed { i, r -> AvailabilityRule.of(r, i) }
                .sortedWith { a, b -> a.id.compareTo(b.id) }
        val hard = ArrayList<HardInterval>()
        for (r in rules) {
            if (r.kind == RuleKind.SLEEP || r.kind == RuleKind.PREFERRED) continue
            r.intervals(days, zone).mapTo(hard) {
                HardInterval(
                    r.id,
                    r.title,
                    JsonString(r.kind.name),
                    it.startEpoch,
                    it.endEpoch,
                    JsonNumber(r.commuteMin),
                    r.load,
                    r.decompressMin,
                    "rule",
                )
            }
        }
        hard += commitments(listItems(member(params, "fixedCommitments"), "sort"))
        hard += blackouts(or(member(av, "blackouts"), member(av, "oneTimeBlackouts")))
        val h0 = grid.slots.startEpoch
        val hn = grid.slots.endEpoch
        val timed = hard.filter { it.endEpoch > h0 - DAY && it.startEpoch < hn + DAY }
        paintHard(grid, timed)
        val sleepNeed = number(or(member(av, "sleepRequirementMinutes"), JsonNumber(SLEEP_NEED)))
        val sleep =
            SleepWindows.derive(
                rules,
                days,
                zone,
                prefs,
                sleepNeed,
                timed.filter { it.isCommitment },
            )
        val warnings = ArrayList(sleep.warnings)
        val asleep = BlockMeta(JsonString("SLEEP"), JsonString("Sleep"), protected = true)
        for (s in sleep.intervals) grid.paint(s.startEpoch, s.endEpoch, SlotCode.SLEEP, asleep)
        for (h in timed) {
            if (!h.isKind("WORK") && !h.isKind("CLASS")) continue
            for (s in sleep.intervals) if (
                h.startEpoch < s.endEpoch &&
                    h.endEpoch > s.startEpoch &&
                    h.endEpoch > h0 &&
                    h.startEpoch < hn
            )
                warnings +=
                    AvailabilityWarning(
                        "SLEEP_COMMITMENT_OVERLAP",
                        text(h.title) +
                            " overlaps your sleep window. Commitments win; please check your sleep times.",
                        startEpoch = h.startEpoch,
                    )
        }
        val cycles = wakeCycles(grid, sleep.intervals, zone)
        for (bw in cycles) if (!bw.truncatedStart) Maintenance.paintDay(grid, bw, prefs)
        markHeavyShifts(grid, timed, cycles)
        for (r in rules) if (r.kind == RuleKind.PREFERRED)
            for (iv in r.intervals(days, zone)) {
                val a = PageValues.clamp(grid.slots.slotAt(iv.startEpoch), 0.0, grid.count)
                val b = PageValues.clamp(grid.slots.slotAtOrAfter(iv.endEpoch), 0.0, grid.count)
                var i = a
                while (i < b) {
                    grid.preferred[i.toInt()] = true
                    i += 1
                }
            }
        return AvailabilityPlan(zoneValue, prefs, rules, grid, cycles, warnings)
    }

    /**
     * The items of a stored list; a truthy value that is not a list throws (the page calls [method]
     * on it).
     */
    private fun listItems(value: JsonValue?, method: String): List<JsonValue> =
        when {
            value is JsonArray -> value.items
            truthy(value) -> throw PageTypeError("$method is not a function")
            else -> emptyList()
        }

    /**
     * LDA-11: the one-off commitments, sorted by start then id; their kind, load and title as
     * LDA-11 says.
     */
    private fun commitments(items: List<JsonValue>): List<HardInterval> {
        if (items.any { !present(it) }) throw PageTypeError("Cannot read properties of null")
        fun idOf(c: JsonValue) = text(or(member(c, "id"), member(c, "commitmentId")))
        return items
            .sortedWith { a, b ->
                val d = number(member(a, "startEpoch")) - number(member(b, "startEpoch"))
                if (d != 0.0 && !d.isNaN()) PageValues.byDifference(d)
                else idOf(a).compareTo(idOf(b)).coerceIn(-1, 1)
            }
            .map { c ->
                val type = member(c, "commitmentType")
                val kind = member(c, "kind")
                val emergency = JsonString("EMERGENCY")
                val resolved =
                    if (type == emergency || kind == emergency) emergency
                    else
                        or(kind, JsonString(if (type == JsonString("CLASS")) "CLASS" else "WORK"))!!
                HardInterval(
                    idOf(c),
                    or(member(c, "title"), "Commitment"),
                    resolved,
                    number(member(c, "startEpoch")),
                    number(member(c, "endEpoch")),
                    orZero(member(c, "commuteMin")),
                    or(or(member(c, "shiftIntensity"), member(c, "load")), "MIXED_MODERATE"),
                    member(c, "decompressMin")?.takeIf(::present) ?: JsonNull,
                    "oneoff",
                )
            }
    }

    /**
     * LDA-11: the blackouts, in their stored order; SICK or BLACKOUT. The page walks them with `for
     * ... of`, so stored text gives one (empty) blackout per character.
     */
    private fun blackouts(value: JsonValue?): List<HardInterval> {
        val items =
            if (value is JsonString && value.value.isNotEmpty())
                value.value.codePoints().toArray().map { JsonString(String(Character.toChars(it))) }
            else listItems(value, "iterator")
        return items.map { b ->
            if (!present(b)) throw PageTypeError("Cannot read properties of null")
            val kind = if (member(b, "kind") == JsonString("SICK")) "SICK" else "BLACKOUT"
            HardInterval(
                text(or(member(b, "id"), member(b, "blackoutId"))),
                or(or(member(b, "reason"), member(b, "title")), "Unavailable"),
                JsonString(kind),
                number(member(b, "startEpoch")),
                number(member(b, "endEpoch")),
                JsonNumber(0.0),
                null,
                null,
                "blackout",
            )
        }
    }

    /**
     * LDA-11, LDA-12: the hard intervals, then the commute before and after and the decompression
     * after each commitment.
     */
    private fun paintHard(grid: AvailabilityGrid, timed: List<HardInterval>) {
        for (h in timed) {
            val title = h.title
            when {
                h.isKind("BLACKOUT") || h.isKind("SICK") || h.isKind("EMERGENCY") ->
                    grid.paint(
                        h.startEpoch,
                        h.endEpoch,
                        SlotCode.EMERGENCY,
                        BlockMeta(h.kind, title, h.ruleId),
                    )
                h.isKind("UNAVAILABLE") ->
                    grid.paint(
                        h.startEpoch,
                        h.endEpoch,
                        SlotCode.PERSONAL,
                        BlockMeta(JsonString("UNAVAILABLE"), title, h.ruleId),
                    )
                else ->
                    grid.paint(
                        h.startEpoch,
                        h.endEpoch,
                        SlotCode.HARD,
                        BlockMeta(
                            h.kind,
                            title,
                            h.ruleId,
                            load = LOADS[text(h.load)]?.takeIf { it != 0 } ?: DEFAULT_LOAD,
                        ),
                    )
            }
        }
        for (h in timed) {
            if (!h.isCommitment) continue
            val cm = h.commuteSeconds
            if (cm != 0.0 && !cm.isNaN()) {
                grid.paint(
                    h.startEpoch - cm,
                    h.startEpoch,
                    SlotCode.COMMUTE,
                    BlockMeta(JsonString("COMMUTE"), JsonString("Commute"), h.ruleId),
                )
                grid.paint(
                    h.endEpoch,
                    h.endEpoch + cm,
                    SlotCode.COMMUTE,
                    BlockMeta(JsonString("COMMUTE"), JsonString("Commute home"), h.ruleId),
                )
            }
            val shift = h.isKind("WORK") && h.endEpoch - h.startEpoch >= SHIFT
            val dec =
                h.decompressMin?.takeIf(::present)
                    ?: JsonNumber(
                        if (shift && h.load != JsonString("LIGHT")) SHIFT_DECOMPRESS else 0.0
                    )
            if (truthy(dec))
                grid.paint(
                    h.endEpoch + cm,
                    h.endEpoch + cm + number(dec) * MINUTE,
                    SlotCode.COMMUTE,
                    BlockMeta(
                        JsonString("DECOMPRESS"),
                        JsonString("Decompress"),
                        h.ruleId,
                        restorative = true,
                    ),
                )
        }
    }

    /**
     * LDA-13: a waking day after each main sleep (3 hours or more) that leaves at least two slots
     * inside the grid.
     */
    private fun wakeCycles(
        grid: AvailabilityGrid,
        sleep: List<SleepInterval>,
        zone: PageZone,
    ): List<WakeCycle> {
        val mains = sleep.filter { it.endEpoch - it.startEpoch >= MAIN_SLEEP }
        val out = ArrayList<WakeCycle>()
        for ((i, m) in mains.withIndex()) {
            val wake = m.endEpoch
            val next = if (i + 1 < mains.size) mains[i + 1].startEpoch else wake + WAKING_DAY
            if (next <= grid.slots.startEpoch || wake >= grid.slots.endEpoch) continue
            val a = PageValues.clamp(grid.slots.slotAtOrAfter(wake), 0.0, grid.count)
            val b = PageValues.clamp(grid.slots.slotAt(next), 0.0, grid.count)
            if (b - a < MIN_WAKE_SLOTS) continue
            val local = WallClock.localParts(wake, zone)
            val bw =
                WakeCycle(
                    out.size,
                    wake,
                    next,
                    a,
                    b,
                    (m.endEpoch - m.startEpoch) / MINUTE,
                    CalendarMath.key(local.date),
                    local.weekday,
                    wake < grid.slots.startEpoch,
                )
            out += bw
            var k = a
            while (k < b) {
                grid.wakeCycle[k.toInt()] = bw.index
                k += 1
            }
        }
        return out
    }

    /**
     * LDA-16: the last slot of a heavy physical shift of 6 hours or more ending inside a waking
     * day.
     */
    private fun markHeavyShifts(
        grid: AvailabilityGrid,
        timed: List<HardInterval>,
        cycles: List<WakeCycle>,
    ) {
        for (h in timed) {
            if (
                !h.isKind("WORK") ||
                    h.load != JsonString("PHYSICAL_HEAVY") ||
                    h.endEpoch - h.startEpoch < HEAVY_SHIFT
            )
                continue
            val endSlot = PageValues.clamp(grid.slots.slotAtOrAfter(h.endEpoch), 0.0, grid.count)
            for (bw in cycles) if (h.endEpoch > bw.wakeEpoch && h.endEpoch <= bw.sleepEpoch)
                bw.heavyShiftEndSlot = maxOf(bw.heavyShiftEndSlot, endSlot)
        }
    }

    /** LDA-14, LDA-15: the routine, meals and wind-down of a waking day. */
    internal object Maintenance {
        fun paintDay(grid: AvailabilityGrid, bw: WakeCycle, prefs: PlanPreferences) {
            paintUpkeep(
                grid,
                bw,
                bw.wakeEpoch,
                bw.wakeEpoch + number(prefs["morningRoutineMinutes"]) * MINUTE,
                "Morning routine",
                "ROUTINE",
            )
            val windDownStart = bw.sleepEpoch - number(prefs["windDownMinutes"]) * MINUTE
            if (prefs.mealsEnabled) {
                val offsets =
                    prefs["mealOffsetsMinutes"] as? JsonArray
                        ?: throw PageTypeError("forEach is not a function")
                for ((k, off) in offsets.items.withIndex()) {
                    val s = bw.wakeEpoch + number(off) * MINUTE
                    if (s >= bw.sleepEpoch - number(prefs["windDownMinutes"]) * MINUTE) continue
                    placeMeal(grid, bw, s, prefs["mealMinutes"], MEAL_NAMES.getOrNull(k) ?: "Meal")
                }
            }
            paintUpkeep(grid, bw, windDownStart, bw.sleepEpoch, "Wind-down", "WIND_DOWN")
        }

        /** LDA-14: painted only when at least one of its slots (inside the waking day) is free. */
        private fun paintUpkeep(
            grid: AvailabilityGrid,
            bw: WakeCycle,
            s: Double,
            e: Double,
            title: String,
            kind: String,
        ) {
            val a = PageValues.clamp(grid.slots.slotAt(s), bw.startSlot, bw.endSlot)
            val b = PageValues.clamp(grid.slots.slotAtOrAfter(e), bw.startSlot, bw.endSlot)
            if (grid.freeBetween(a, b) == 0) return
            grid.paint(
                grid.slots.epochAt(a),
                grid.slots.epochAt(b),
                SlotCode.MAINT,
                BlockMeta(JsonString(kind), JsonString(title), restorative = kind != "ROUTINE"),
            )
        }

        /**
         * LDA-15: a meal starting in a commitment slot is eaten on the break (nothing painted);
         * otherwise the first free run of its length at its slot, then 1, -1, 2, -2 ... 8, -8 slots
         * away, inside the waking day; none found, it is listed as skipped.
         */
        private fun placeMeal(
            grid: AvailabilityGrid,
            bw: WakeCycle,
            s: Double,
            minutes: JsonValue?,
            title: String,
        ) {
            val n = ceil(number(minutes) / MEAL_SLOT_MINUTES)
            val want = grid.slots.slotAt(s)
            if (want >= 0 && want < grid.count && grid.codeAt(want) == SlotCode.HARD.code) return
            for (delta in 0..MEAL_REACH) for (dir in
                if (delta == 0) intArrayOf(0) else intArrayOf(1, -1)) {
                val a = want + dir * delta
                if (a < bw.startSlot || a + n > bw.endSlot) continue
                if (grid.freeBetween(a, a + n) != freeLength(a, n)) continue
                grid.paint(
                    grid.slots.epochAt(a),
                    grid.slots.epochAt(a + n),
                    SlotCode.MAINT,
                    BlockMeta(JsonString("MEAL"), JsonString(title), restorative = true),
                )
                return
            }
            bw.skippedMeals += title
        }

        /**
         * How many slots `a` up to `a + n` holds (the count [AvailabilityGrid.freeBetween] needs
         * for "all free").
         */
        private fun freeLength(a: Double, n: Double): Int {
            var count = 0
            var i = a
            while (i < a + n) {
                count++
                i += 1
            }
            return count
        }
    }
}
