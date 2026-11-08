package com.lifemax.rpg.core.lifedirector

import com.lifemax.rpg.core.lifedirector.KernelGolden.array
import com.lifemax.rpg.core.lifedirector.KernelGolden.bool
import com.lifemax.rpg.core.lifedirector.KernelGolden.number
import com.lifemax.rpg.core.lifedirector.KernelGolden.obj
import com.lifemax.rpg.core.lifedirector.KernelGolden.string
import com.lifemax.rpg.core.lifedirector.KernelGolden.threw
import com.lifemax.rpg.core.save.JsonNumber
import com.lifemax.rpg.core.save.JsonObject
import com.lifemax.rpg.core.save.JsonString
import com.lifemax.rpg.core.save.JsonValue

/**
 * The adapter table: each page function name (`fn` of a case) as a call on the kernel, its result
 * as the JSON the page wrote. Page names appear only here. A [Mutation] replaces entries, for the
 * mutation spot checks.
 */
object KernelReplay {
    /** The page threw a `RangeError`: `Intl` refused the zone, or a date was out of range. */
    class RangeError : RuntimeException()

    /** What a case's arguments are, read the way the page reads them. */
    class Args(private val values: List<JsonValue>) {
        fun number(i: Int): Double = (KernelGolden.decode(values[i]) as JsonNumber).value

        fun int(i: Int): Int = number(i).toInt()

        fun text(i: Int): String = (values[i] as JsonString).value

        fun textOrNull(i: Int): String? = (values[i] as? JsonString)?.value

        fun longOrNull(i: Int): Long? = (values[i] as? JsonNumber)?.value?.toLong()

        fun zone(i: Int): PageZone = ZoneNames.resolve(text(i)) ?: throw RangeError()

        /** `{y, mo, d}`, or `{$parse: key}` for what `parseYmd(key)` gives. */
        fun date(i: Int): DateFields {
            val o = values[i] as JsonObject
            (o["\$parse"] as? JsonString)?.let {
                return CalendarMath.parse(it.value)?.fields ?: NAN_DATE
            }
            fun field(key: String) = (KernelGolden.decode(o[key]!!) as JsonNumber).value
            return DateFields(field("y"), field("mo"), field("d"))
        }
    }

    /** Entries to use instead of the real ones. */
    class Mutation(val replace: Map<String, (Args) -> JsonValue> = emptyMap())

    private val NAN_DATE = DateFields(Double.NaN, Double.NaN, Double.NaN)

    fun parts(p: LocalParts): JsonValue =
        obj(
            "y" to number(p.year),
            "mo" to number(p.month),
            "d" to number(p.day),
            "h" to number(p.hour),
            "mi" to number(p.minute),
            "s" to number(p.second),
            "dow" to number(p.weekday),
        )

    fun date(d: CalendarDate?): JsonValue =
        if (d == null)
            obj(
                "y" to number(Double.NaN),
                "mo" to number(Double.NaN),
                "d" to number(Double.NaN),
                "dow" to number(Double.NaN),
            )
        else
            obj(
                "y" to number(d.year),
                "mo" to number(d.month),
                "d" to number(d.day),
                "dow" to number(d.weekday),
            )

    fun day(d: PlanDay): JsonValue =
        obj(
            "index" to number(d.index),
            "ymd" to date(d.date),
            "key" to string(d.key),
            "dow" to number(d.date.weekday),
            "startEpoch" to number(d.startEpoch),
            "endEpoch" to number(d.endEpoch),
            "slots" to number(d.slots),
        )

    private fun ints(values: IntArray): JsonValue = array(values.map { number(it) })

    /** The real adapters, by page name. */
    val ADAPTERS: Map<String, (Args) -> JsonValue> =
        mapOf(
            "cyrb53" to
                { a ->
                    number(EngineHash.hash53(a.text(0), EngineHash.seedOf(a.number(1))).toDouble())
                },
            "stableId" to { a -> string(EngineHash.stableId(a.text(0), a.text(1))) },
            "completionEventId" to
                { a ->
                    string(
                        EngineHash.completionEventId(
                            a.text(0),
                            a.textOrNull(1),
                            a.textOrNull(2),
                            a.longOrNull(3),
                        )
                    )
                },
            "mulberry32" to
                { a ->
                    val rng = EngineHash.generator(a.number(0))
                    array(List(a.int(1)) { number(rng.next()) })
                },
            "ceilTo15" to { a -> number(QuarterHours.roundUp(a.number(0))) },
            "isValidTimeZone" to { a -> bool(ZoneNames.isValid(a.text(0))) },
            "localParts" to { a -> parts(WallClock.localParts(a.number(0), a.zone(1))) },
            "tzOffsetSec" to { a -> number(WallClock.offsetSeconds(a.number(0), a.zone(1))) },
            "zonedToEpoch" to
                { a ->
                    number(
                        WallClock.instantOf(
                            a.number(0),
                            a.number(1),
                            a.number(2),
                            a.number(3),
                            a.zone(4),
                        )
                    )
                },
            "ymdAdd" to { a -> date(CalendarMath.plusDays(a.date(0), a.number(1))) },
            "ymdKey" to { a -> string(CalendarMath.key(a.date(0))) },
            "parseYmd" to { a -> date(CalendarMath.parse(a.text(0))) },
            "daysBetween" to { a -> number(CalendarMath.daysBetween(a.date(0), a.date(1))) },
            "buildHorizon" to
                { a ->
                    array(PlanHorizon.days(a.number(0), a.int(1), a.zone(2)).map(::day))
                },
            "grid" to
                { a ->
                    val zone = a.zone(2)
                    val g = SlotGrid.of(PlanHorizon.days(a.number(0), a.int(1), zone), zone)
                    obj(
                        "H0" to number(g.startEpoch),
                        "HN" to number(g.endEpoch),
                        "n" to number(g.count),
                        "localMin" to ints(g.localMinute),
                        "dayOf" to ints(g.dayIndex),
                    )
                },
            "slotOf" to { a -> number(SlotGrid.startingAt(a.number(0)).slotAt(a.number(1))) },
            "slotCeil" to
                { a ->
                    number(SlotGrid.startingAt(a.number(0)).slotAtOrAfter(a.number(1)))
                },
            "epochOf" to { a -> number(SlotGrid.startingAt(a.number(0)).epochAt(a.number(1))) },
            "SLOT_SEC" to { _ -> number(SlotGrid.SLOT_SECONDS) },
            "SLOTS_PER_HOUR" to { _ -> number(SlotGrid.SLOTS_PER_HOUR) },
            "dayEndAfter" to { a -> number(PlanHorizon.nextDayStart(a.number(0), a.zone(1))) },
            "weekKey" to { a -> string(PlanHorizon.weekKey(a.number(0), a.zone(1))) },
            "weekBounds" to
                { a ->
                    val w = PlanHorizon.weekBounds(a.number(0), a.zone(1))
                    obj(
                        "start" to number(w.start),
                        "end" to number(w.end),
                        "nextEnd" to number(w.nextEnd),
                        "monKey" to string(w.mondayKey),
                    )
                },
        )

    /** What [case] gives through the adapters, as the page's JSON. */
    fun run(case: KernelGolden.Case, mutation: Mutation = Mutation()): JsonValue {
        val adapter =
            mutation.replace[case.fn] ?: ADAPTERS[case.fn] ?: error("no adapter for ${case.fn}")
        return try {
            adapter(Args(case.args))
        } catch (refused: RangeError) {
            threw("RangeError")
        } catch (refused: IllegalArgumentException) {
            threw("Error")
        }
    }

    /** A difference between [case]'s recorded output and the kernel's, or null when they agree. */
    fun difference(case: KernelGolden.Case, mutation: Mutation = Mutation()): String? {
        val got = run(case, mutation)
        return if (got == case.out) null
        else "${case.id}: expected ${KernelGolden.show(case.out)}, got ${KernelGolden.show(got)}"
    }
}
