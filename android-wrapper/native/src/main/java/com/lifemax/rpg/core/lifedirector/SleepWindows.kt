package com.lifemax.rpg.core.lifedirector

import com.lifemax.rpg.core.lifedirector.PageValues.number
import com.lifemax.rpg.core.lifedirector.PageValues.orZero
import com.lifemax.rpg.core.lifedirector.PageValues.text
import com.lifemax.rpg.core.save.JsonString
import com.lifemax.rpg.core.save.JsonValue

/**
 * LDA-10: a time the player is not free, before it is painted: a rule's interval, a one-off
 * commitment or a blackout. [title], [kind], [commuteMin], [load] and [decompressMin] are kept as
 * stored for commitments (the page prints and compares them as they are); null is an absent member.
 */
data class HardInterval(
    val ruleId: String,
    val title: JsonValue,
    val kind: JsonValue,
    val startEpoch: Double,
    val endEpoch: Double,
    val commuteMin: JsonValue?,
    val load: JsonValue?,
    val decompressMin: JsonValue?,
    val source: String,
) {
    /** LDA-12, LDS-3: the commute in seconds, `(commuteMin || 0) * 60`. */
    val commuteSeconds: Double
        get() = number(orZero(commuteMin)) * SECONDS_PER_MINUTE

    /** Whether the stored kind is exactly [name]. */
    fun isKind(name: String): Boolean = kind is JsonString && kind.value == name

    /** LDA-12: work, class or an appointment, the kinds with commutes and decompression. */
    val isCommitment: Boolean
        get() = isKind("WORK") || isKind("CLASS") || isKind("APPOINTMENT")

    private companion object {
        const val SECONDS_PER_MINUTE = 60.0
    }
}

/** LDS-1: one sleep window, from the rule (or the default bedtime) of its local [date]. */
data class SleepInterval(
    val ruleId: String,
    val title: JsonValue,
    val date: CalendarDate,
    val startEpoch: Double,
    val endEpoch: Double,
)

/**
 * A warning the availability layer gives with its result (LDS-3, LDS-6, LDS-8): [date] or
 * [startEpoch] say where, [message] is the page's text.
 */
data class AvailabilityWarning(
    val code: String,
    val message: String,
    val date: String? = null,
    val startEpoch: Double? = null,
)

/** LDS-1 to LDS-7: the sleep windows `deriveSleep` derives. */
data class SleepPlan(val intervals: List<SleepInterval>, val warnings: List<AvailabilityWarning>)

/** LDS-1 to LDS-7 `deriveSleep`: where the player sleeps over the plan's days. */
object SleepWindows {
    private const val MINUTE = 60.0
    private const val HOUR = 3600.0
    private const val MAIN_SLEEP = 3 * HOUR
    private const val MERGE_GAP = 30 * MINUTE
    private const val ROUTINE_MARGIN = 15.0
    private const val DEFAULT_ID = "default-sleep"
    private val SLEEP_TITLE = JsonString("Sleep")

    /**
     * Sleep over [days] in [zone]: LDS-1 the intervals of every SLEEP rule; or, with none, LDS-2 a
     * default night from `defaultBedtimeMin` of each date (the day before the plan to the day
     * after) lasting [sleepNeedMinutes], moved earlier (LDS-3) when a commitment of [hard] starts
     * too soon after it; then LDS-4 sorted and LDS-5 merged, and LDS-6 a warning for each main
     * window shorter than the minimum.
     */
    fun derive(
        rules: List<AvailabilityRule>,
        days: List<PlanDay>,
        zone: PageZone,
        prefs: PlanPreferences,
        sleepNeedMinutes: Double,
        hard: List<HardInterval>,
    ): SleepPlan {
        val explicit = rules.filter { it.kind == RuleKind.SLEEP }
        val raw = ArrayList<SleepInterval>()
        val warnings = ArrayList<AvailabilityWarning>()
        for (r in explicit) r.intervals(days, zone).mapTo(raw) {
            SleepInterval(r.id, r.title, it.date, it.startEpoch, it.endEpoch)
        }
        if (explicit.isEmpty()) {
            val first = days.firstOrNull() ?: throw PageTypeError("no plan days")
            for (i in -1..days.size) {
                val date = requireNotNull(CalendarMath.plusDays(first.date, i))
                raw += defaultNight(date, zone, prefs, sleepNeedMinutes, hard, warnings)
            }
        }
        val merged = ArrayList<SleepInterval>()
        for (x in raw.sortedWith { a, b -> PageValues.byDifference(a.startEpoch - b.startEpoch) }) {
            val last = merged.lastOrNull()
            if (last != null && x.startEpoch <= last.endEpoch + MERGE_GAP)
                merged[merged.size - 1] = last.copy(endEpoch = maxOf(last.endEpoch, x.endEpoch))
            else merged += x
        }
        val floor = prefs.minSleepMinutes * MINUTE
        for (m in merged) {
            val length = m.endEpoch - m.startEpoch
            if (length < floor && length >= MAIN_SLEEP)
                warnings +=
                    AvailabilityWarning(
                        "SLEEP_BELOW_FLOOR",
                        "A sleep window is shorter than your " +
                            PageValues.numberText(prefs.minSleepMinutes / MINUTE) +
                            "h minimum. Life Director will never schedule into sleep, but cannot add sleep you have not entered.",
                        startEpoch = m.startEpoch,
                    )
        }
        return SleepPlan(merged, warnings)
    }

    /**
     * LDS-2, LDS-3: the default night of [date]. The wake-up needs a lead of the morning routine
     * plus 15 minutes (LDS-7: computed as `(morningRoutineMinutes + 15) * 60`, so a routine stored
     * as text is joined, not added). The first commitment starting inside the night or its lead
     * moves the wake-up to its start less its commute and the lead (but at least an hour after
     * bedtime), and the bedtime with it.
     */
    private fun defaultNight(
        date: CalendarDate,
        zone: PageZone,
        prefs: PlanPreferences,
        needMinutes: Double,
        hard: List<HardInterval>,
        warnings: MutableList<AvailabilityWarning>,
    ): SleepInterval {
        var start = AvailabilityTime.instant(date, number(prefs["defaultBedtimeMin"]), zone)
        var end = start + needMinutes * MINUTE
        val lead = PageValues.plusAsNumber(prefs["morningRoutineMinutes"], ROUTINE_MARGIN) * MINUTE
        val s = start
        val e = end
        val conflict =
            hard
                .filter { it.startEpoch < e + lead && it.startEpoch >= s }
                .sortedWith { a, b -> PageValues.byDifference(a.startEpoch - b.startEpoch) }
                .firstOrNull()
        if (conflict != null) {
            val wake = conflict.startEpoch - conflict.commuteSeconds - lead
            end = maxOf(start + HOUR, wake)
            start = end - needMinutes * MINUTE
            warnings +=
                AvailabilityWarning(
                    "SLEEP_SHIFTED",
                    "Bedtime moved earlier so you can wake in time for " +
                        text(conflict.title) +
                        ".",
                    date = CalendarMath.key(date),
                )
        }
        return SleepInterval(DEFAULT_ID, SLEEP_TITLE, date, start, end)
    }
}
