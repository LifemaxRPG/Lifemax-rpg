package com.lifemax.rpg.core.lifedirector

import com.lifemax.rpg.core.golden.GoldenFiles
import com.lifemax.rpg.core.lifedirector.KernelGolden.array
import com.lifemax.rpg.core.lifedirector.KernelGolden.bool
import com.lifemax.rpg.core.lifedirector.KernelGolden.number
import com.lifemax.rpg.core.lifedirector.KernelGolden.obj
import com.lifemax.rpg.core.lifedirector.KernelGolden.string
import com.lifemax.rpg.core.lifedirector.KernelGolden.threw
import com.lifemax.rpg.core.save.JsonArray
import com.lifemax.rpg.core.save.JsonNull
import com.lifemax.rpg.core.save.JsonNumber
import com.lifemax.rpg.core.save.JsonObject
import com.lifemax.rpg.core.save.JsonRead
import com.lifemax.rpg.core.save.JsonReader
import com.lifemax.rpg.core.save.JsonString
import com.lifemax.rpg.core.save.JsonValue
import kotlin.math.sign

/**
 * The availability golden cases, `ld-availability-golden.json` (recorded from the page in Chromium;
 * format in `tools/gen_ld_availability_golden.js`), and the adapter table that runs each page
 * function name on the native availability code and writes its result as the page's JSON. Page
 * names appear only here. A [Mutation] replaces entries, for the mutation spot checks.
 */
object AvailabilityReplay {
    const val FILE = "ld-availability-golden.json"

    /** Every case, in the file's order. */
    val cases: List<KernelGolden.Case> by lazy {
        val read = JsonReader.read(GoldenFiles.text(FILE))
        check(read is JsonRead.Parsed) { "$FILE does not parse: $read" }
        ((read.value as JsonObject)["cases"] as JsonArray).items.map {
            val c = it as JsonObject
            KernelGolden.Case(
                KernelGolden.text(c["id"]),
                KernelGolden.text(c["family"]),
                KernelGolden.text(c["rule"]),
                KernelGolden.text(c["fn"]),
                (c["args"] as JsonArray).items.map(KernelGolden::decode),
                KernelGolden.decode(c["out"]!!),
            )
        }
    }

    /** A case's arguments. */
    class Args(private val values: List<JsonValue>) {
        operator fun get(i: Int): JsonValue? = values.getOrNull(i)

        fun number(i: Int): Double = (values[i] as JsonNumber).value

        fun int(i: Int): Int = number(i).toInt()

        fun zone(i: Int): PageZone =
            ZoneNames.resolve((values[i] as JsonString).value) ?: throw PageRangeError("zone")

        fun date(i: Int): CalendarDate {
            val o = values[i] as JsonObject
            fun f(k: String) = (o[k] as JsonNumber).value.toInt()
            return requireNotNull(CalendarMath.plusDays(DateFields(f("y"), f("mo"), f("d")), 0))
        }

        fun days(at: Int): List<PlanDay> = PlanHorizon.days(number(at), int(at + 1), zone(at + 2))
    }

    /** Entries to use instead of the real ones. */
    class Mutation(val replace: Map<String, (Args) -> JsonValue> = emptyMap())

    fun date(d: CalendarDate): JsonValue =
        obj(
            "y" to number(d.year),
            "mo" to number(d.month),
            "d" to number(d.day),
            "dow" to number(d.weekday),
        )

    fun rule(r: AvailabilityRule): JsonValue =
        obj(
            "id" to string(r.id),
            "kind" to string(r.kind.name),
            "title" to r.title,
            "days" to array(r.days),
            "startMin" to number(r.startMin),
            "durationMin" to number(r.durationMin),
            "commuteMin" to number(r.commuteMin),
            "decompressMin" to r.decompressMin,
            "load" to r.load,
            "rotation" to
                (r.rotation?.let {
                    obj(
                        "anchorDate" to it.anchorDate,
                        "cycleLength" to number(it.cycleLength),
                        "activeDays" to array(it.activeDays.map(::number)),
                    )
                } ?: JsonNull),
            "validFrom" to r.validFrom,
            "validTo" to r.validTo,
        )

    private fun interval(
        ruleId: String,
        title: JsonValue,
        kind: String,
        start: Double,
        end: Double,
        d: CalendarDate,
    ) =
        obj(
            "ruleId" to string(ruleId),
            "title" to title,
            "kind" to string(kind),
            "startEpoch" to number(start),
            "endEpoch" to number(end),
            "ymd" to date(d),
        )

    fun warning(w: AvailabilityWarning): JsonValue {
        val m = linkedMapOf<String, JsonValue>("code" to string(w.code))
        w.date?.let { m["date"] = string(it) }
        w.startEpoch?.let { m["startEpoch"] = number(it) }
        m["message"] = string(w.message)
        return JsonObject(m)
    }

    fun prefs(p: PlanPreferences): JsonValue = JsonObject(p.members)

    /** `value*count` runs of a slot array, joined by `,`. */
    fun runs(values: IntArray): JsonValue {
        val out = StringBuilder()
        var i = 0
        while (i < values.size) {
            var j = i
            while (j < values.size && values[j] == values[i]) j++
            if (out.isNotEmpty()) out.append(',')
            out.append(values[i]).append('*').append(j - i)
            i = j
        }
        return string(out.toString())
    }

    private fun block(b: GridBlock): JsonValue {
        val m =
            linkedMapOf(
                "code" to number(b.code),
                "startEpoch" to number(b.startEpoch),
                "endEpoch" to number(b.endEpoch),
            )
        b.meta?.let { meta ->
            meta.kind?.let { m["kind"] = it }
            meta.title?.let { m["title"] = it }
            meta.refId?.let { m["refId"] = string(it) }
            meta.load?.let { m["load"] = number(it) }
            meta.restorative?.let { m["restorative"] = bool(it) }
            meta.protected?.let { m["protected"] = bool(it) }
        }
        return JsonObject(m)
    }

    fun grid(g: AvailabilityGrid): List<Pair<String, JsonValue>> =
        listOf(
            "H0" to number(g.slots.startEpoch),
            "HN" to number(g.slots.endEpoch),
            "n" to number(g.count),
            "type" to runs(g.type),
            "load" to runs(g.load),
            "tag" to runs(g.tag),
            "bwcOf" to runs(g.wakeCycle),
            "blocks" to array(g.blocks.map(::block)),
        )

    private fun wakeCycle(bw: WakeCycle): JsonValue =
        obj(
            "index" to number(bw.index),
            "id" to string(bw.id),
            "wakeEpoch" to number(bw.wakeEpoch),
            "sleepEpoch" to number(bw.sleepEpoch),
            "startSlot" to number(bw.startSlot),
            "endSlot" to number(bw.endSlot),
            "sleepBeforeMin" to number(bw.sleepBeforeMin),
            "dateKey" to string(bw.dateKey),
            "dow" to number(bw.weekday),
            "label" to JsonNull,
            "truncatedStart" to bool(bw.truncatedStart),
            "heavyShiftEndSlot" to number(bw.heavyShiftEndSlot),
            // the placement counters a waking day starts with; placement (WP-LD4) changes them
            "goalMin" to number(0),
            "highCount" to number(0),
            "perKey" to JsonObject.EMPTY,
            "recoverySlots" to number(0),
            "freeSlots" to number(0),
            "maintBlocks" to array(bw.skippedMeals.map { obj("skipped" to string(it)) }),
        )

    private fun meta(value: JsonValue?): BlockMeta? {
        val o = value as? JsonObject ?: return null
        return BlockMeta(
            kind = o["kind"],
            title = o["title"],
            refId = (o["refId"] as? JsonString)?.value,
            load = (o["load"] as? JsonNumber)?.value?.toInt(),
            restorative = (o["restorative"] as? com.lifemax.rpg.core.save.JsonBool)?.value,
        )
    }

    /** Which dates from [start] a rule question answers yes to, one character per date. */
    private fun dates(start: CalendarDate, count: Int, test: (CalendarDate) -> Boolean): JsonValue =
        string(
            (0 until count).joinToString("") {
                if (test(requireNotNull(CalendarMath.plusDays(start, it)))) "1" else "0"
            }
        )

    /** The real adapters, by page name. */
    val ADAPTERS: Map<String, (Args) -> JsonValue> =
        mapOf(
            "clamp" to { a -> PageValues.clamp(a[0]!!, a.number(1), a.number(2)) },
            "cmpStr" to
                { a ->
                    number((a[0] as JsonString).value.compareTo((a[1] as JsonString).value).sign)
                },
            "normalizeRule" to
                { a ->
                    rule(
                        AvailabilityRule.of(
                            a[0],
                            a[1]?.let { (it as JsonNumber).value.toInt() } ?: 0,
                        )
                    )
                },
            "ruleAppliesOn" to
                { a ->
                    val r = AvailabilityRule.of(a[0], 0)
                    dates(a.date(1), a.int(2)) { r.appliesOn(it) }
                },
            "ruleOn" to { a -> dates(a.date(1), a.int(2)) { PageRuleCheck.appliesOn(a[0], it) } },
            "materializeRule" to
                { a ->
                    val r = AvailabilityRule.of(a[0], 0)
                    array(
                        r.intervals(a.days(1), a.zone(3)).map {
                            interval(
                                r.id,
                                r.title,
                                r.kind.name,
                                it.startEpoch,
                                it.endEpoch,
                                it.date,
                            )
                        }
                    )
                },
            "defaultPreferences" to { _ -> prefs(PlanPreferences.defaults()) },
            "normalizePreferences" to { a -> prefs(PlanPreferences.of(a[0])) },
            "deriveSleep" to
                { a ->
                    val rules =
                        (a[0] as JsonArray).items.mapIndexed { i, r -> AvailabilityRule.of(r, i) }
                    val hard =
                        (a[6] as JsonArray).items.map {
                            val o = it as JsonObject
                            HardInterval(
                                "",
                                o["title"] ?: JsonNull,
                                string("WORK"),
                                (o["startEpoch"] as JsonNumber).value,
                                (o["endEpoch"] as JsonNumber).value,
                                o["commuteMin"],
                                null,
                                null,
                                "test",
                            )
                        }
                    val plan =
                        SleepWindows.derive(
                            rules,
                            a.days(1),
                            a.zone(3),
                            PlanPreferences.of(a[4]),
                            PageValues.number(a[5]),
                            hard,
                        )
                    obj(
                        "intervals" to
                            array(
                                plan.intervals.map {
                                    interval(
                                        it.ruleId,
                                        it.title,
                                        "SLEEP",
                                        it.startEpoch,
                                        it.endEpoch,
                                        it.date,
                                    )
                                }
                            ),
                        "warnings" to array(plan.warnings.map(::warning)),
                    )
                },
            "paint" to
                { a ->
                    val zone = a.zone(2)
                    val g =
                        AvailabilityGrid(
                            SlotGrid.of(PlanHorizon.days(a.number(0), a.int(1), zone), zone)
                        )
                    val returns =
                        (a[3] as JsonArray).items.map {
                            val op = (it as JsonArray).items
                            g.paint(
                                    (op[0] as JsonNumber).value,
                                    (op[1] as JsonNumber).value,
                                    (op[2] as JsonNumber).value.toInt(),
                                    meta(op[3]),
                                )
                                ?.let(::number) ?: JsonNull
                        }
                    JsonObject(linkedMapOf("returns" to array(returns)) + grid(g))
                },
            "deriveAvailability" to
                { a ->
                    val plan = Availability.derive(a[0])
                    JsonObject(
                        linkedMapOf(
                            "tz" to plan.zoneValue,
                            "ruleIds" to array(plan.rules.map { string(it.id) }),
                            "warnings" to array(plan.warnings.map(::warning)),
                            "wakeCycles" to array(plan.wakeCycles.map(::wakeCycle)),
                        ) +
                            grid(plan.grid) +
                            ("preferred" to
                                runs(
                                    IntArray(plan.grid.preferred.size) {
                                        if (plan.grid.preferred[it]) 1 else 0
                                    }
                                ))
                    )
                },
        )

    /** What [case] gives through the adapters, as the page's JSON. */
    fun run(case: KernelGolden.Case, mutation: Mutation = Mutation()): JsonValue {
        val adapter =
            mutation.replace[case.fn] ?: ADAPTERS[case.fn] ?: error("no adapter for ${case.fn}")
        return try {
            adapter(Args(case.args))
        } catch (e: PageTypeError) {
            threw("TypeError")
        } catch (e: PageRangeError) {
            threw("RangeError")
        } catch (e: EngineRefusal) {
            threw("Error")
        }
    }

    /** A difference between [case]'s recorded output and native's, or null when they agree. */
    fun difference(case: KernelGolden.Case, mutation: Mutation = Mutation()): String? {
        val got = run(case, mutation)
        return if (got == case.out) null
        else "${case.id}: expected ${KernelGolden.show(case.out)}, got ${KernelGolden.show(got)}"
    }
}
