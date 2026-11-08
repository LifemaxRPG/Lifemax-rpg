package com.lifemax.rpg.core.adapters

import com.lifemax.rpg.core.economy.DailyHand
import com.lifemax.rpg.core.economy.EconomyClock
import com.lifemax.rpg.core.golden.GoldenFiles
import com.lifemax.rpg.core.save.EconomySaveCodec
import com.lifemax.rpg.core.save.JsonArray
import com.lifemax.rpg.core.save.JsonBool
import com.lifemax.rpg.core.save.JsonNull
import com.lifemax.rpg.core.save.JsonNumber
import com.lifemax.rpg.core.save.JsonObject
import com.lifemax.rpg.core.save.JsonRead
import com.lifemax.rpg.core.save.JsonReader
import com.lifemax.rpg.core.save.JsonString
import com.lifemax.rpg.core.save.JsonValue
import com.lifemax.rpg.core.save.JsonWriter
import com.lifemax.rpg.core.save.SaveDecoding
import com.lifemax.rpg.core.save.SaveReading
import java.time.Instant
import java.time.ZoneId

/**
 * Replays `device-context-golden.json` (recorded from the page, format in
 * `tools/gen_device_context_golden.js`) through the adapters: the clock cases through
 * [DeviceClock], the facts cases through [ContextFactory] over the recorded save with a plan that
 * says what the page's plan said. Each difference is returned as text.
 */
object DeviceContextReplay {
    const val FILE = "device-context-golden.json"

    /** Every case in the file, as the page wrote it. */
    val cases: List<JsonObject> by lazy {
        val read = JsonReader.read(GoldenFiles.text(FILE))
        check(read is JsonRead.Parsed) { "$FILE does not parse: $read" }
        (read.value as JsonArray).items.map { it as JsonObject }
    }

    fun id(case: JsonObject) = text(case["id"])

    fun family(case: JsonObject) = text(case["family"])

    /** A change to an adapter's input, to check that a replay would notice it (mutation checks). */
    class Mutation(
        val device: (DeviceClock) -> DeviceClock = { it },
        val save: (SaveView) -> SaveView = { it },
        val plan: (PlanFacts) -> PlanFacts = { it },
    )

    /** What differs from the page in [case], as `call i key: expected ..., got ...` lines. */
    fun differences(case: JsonObject, mutation: Mutation = Mutation()): List<String> {
        val found = mutableListOf<String>()
        walk(case, mutation) { i, key, want, have, _ ->
            found += "call $i $key: expected ${show(want)}, got ${have?.let(::show)}"
        }
        return found
    }

    /**
     * The single values that differ from the page: a record date of a probe, or a check text of a
     * facts call (a whole answer that differs counts as its name).
     */
    fun differingValues(case: JsonObject, mutation: Mutation = Mutation()): List<String> {
        val found = mutableListOf<String>()
        walk(case, mutation) { _, key, want, have, call ->
            val given = (call["probe"] as? JsonObject)?.get("dates") as? JsonArray
            if (want is JsonArray && have is JsonArray && given != null)
                want.items.indices
                    .filter { show(want.items[it]) != show(have.items[it]) }
                    .forEach { found += show(given.items[it]).trim('"') }
            else if (want is JsonObject && have is JsonObject)
                found += want.members.keys.filter { show(want[it]!!) != have[it]?.let(::show) }
            else found += key
        }
        return found
    }

    private fun walk(
        case: JsonObject,
        mutation: Mutation,
        onDifference: (Int, String, JsonValue, JsonValue?, JsonObject) -> Unit,
    ) {
        val input = case["input"] as JsonObject
        val device = FixedDeviceClock(text(input["now"]), text(input["zone"]))
        val outs = ((case["output"] as JsonObject)["calls"] as JsonArray).items
        val calls = (input["calls"] as JsonArray).items
        check(calls.size == outs.size) { "${calls.size} calls but ${outs.size} outputs" }
        for ((i, call) in calls.withIndex()) {
            call as JsonObject
            (call["at"] as? JsonString)?.let { device.now = Instant.parse(it.value).toEpochMilli() }
            (call["zone"] as? JsonString)?.let { device.zone = ZoneId.of(it.value) }
            val expected = outs[i] as JsonObject
            val mutant = mutation.device(device)
            val got =
                if (call["probe"] != null) probe(call["probe"] as JsonObject, mutant)
                else facts(call["facts"] as JsonObject, expected["plan"], input, mutant, mutation)
            for ((key, want) in expected.members) {
                if (key == "plan") continue // the recorded plan is what the fake plan says
                val have = got[key]
                if (have == null || show(have) != show(want)) onDifference(i, key, want, have, call)
            }
        }
    }

    private fun probe(args: JsonObject, device: DeviceClock): Map<String, JsonValue> {
        val clock = device.reading()
        val board = device.boardReading()
        val dates = (args["dates"] as JsonArray).items
        return linkedMapOf(
            "today" to JsonString(clock.today),
            "weekday" to JsonNumber(board.weekday.toDouble()),
            "weekStart" to JsonString(board.weekKey),
            "since6" to JsonString(clock.daysAgo(6)),
            "dayNum" to JsonNumber(EconomyClock.dayNumber(clock.today)!!.toDouble()),
            "iso" to JsonString(EconomyClock.iso(clock.now)),
            "now" to JsonNumber(clock.now.toDouble()),
            "days" to JsonArray(dates.map { JsonString(PageDays.dayOf(dateValue(it), clock)) }),
        )
    }

    /** The recorded `"<absent>"` is a record without a date. */
    private fun dateValue(recorded: JsonValue): JsonValue? =
        if (recorded == JsonString("<absent>")) null else recorded

    private fun facts(
        args: JsonObject,
        recordedPlan: JsonValue?,
        input: JsonObject,
        device: DeviceClock,
        mutation: Mutation,
    ): Map<String, JsonValue> {
        val decoded =
            EconomySaveCodec.decode(input["state"] as JsonObject, SaveReading.AS_PAGE)
                as? SaveDecoding.Decoded ?: error("the recorded save does not decode")
        val view = mutation.save(SaveView.of(decoded))
        val recorded = planOf(recordedPlan)
        val plan =
            mutation.plan(
                FakePlanFacts(
                    done = recorded.filterValues { it.first }.keys,
                    rest = { day -> recorded[day]?.second == true },
                )
            )
        val inputs = ContextFactory(device, { view }, plan).begin()
        val hand = inputs.hand!!
        val verify =
            (args["verify"] as JsonArray).items.associate { check ->
                val text = text(check)
                text to JsonBool.of(DailyHand.isMet(view.economy, text, inputs.economy))
            }
        val rest =
            (args["rest"] as JsonArray).items.associate { day ->
                text(day) to JsonBool.of(hand.isRestDay(text(day)))
            }
        return linkedMapOf(
            "today" to JsonString(inputs.economy.today),
            "verify" to JsonObject(verify),
            "luck" to (hand.luckScore?.let { JsonNumber(it) } ?: JsonNull),
            "rest" to JsonObject(rest),
        )
    }

    /** The plan days the page answered for, `day to (done, rest)`. */
    private fun planOf(recorded: JsonValue?): Map<String, Pair<Boolean, Boolean>> =
        (recorded as JsonObject).members.mapValues { (_, day) ->
            day as JsonObject
            (day["done"] == JsonBool.TRUE) to (day["rest"] == JsonBool.TRUE)
        }

    private fun show(value: JsonValue): String = JsonWriter.write(value)

    private fun text(value: JsonValue?): String = (value as JsonString).value
}
