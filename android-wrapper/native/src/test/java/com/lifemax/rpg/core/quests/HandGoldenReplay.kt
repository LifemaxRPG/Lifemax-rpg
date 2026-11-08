package com.lifemax.rpg.core.quests

import com.lifemax.rpg.core.economy.EconomyClock
import com.lifemax.rpg.core.golden.GoldenFiles
import com.lifemax.rpg.core.quests.Js.with
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
import java.time.Instant
import java.time.ZoneId

/**
 * Replays the Daily Hand golden cases (`lifemax_modular_v73/tests/fixtures/quests-golden.json`,
 * format in `tools/gen_quests_golden.js`) through [HandDealer]. This is the adapter table: each
 * page name turns a recorded call into a call on the native draw. Everything is compared as JSON
 * text, so key order, number text and absent-versus-null all count.
 */
object HandGoldenReplay {
    const val FILE = "quests-golden.json"

    /** The fields a call's output reports (`OUT` in the generator). */
    val OUT = listOf("quests", "dailyDraw", "dhMig", "sparks", "questDone")

    /** Every case in the file, as the page wrote it. */
    val cases: List<JsonObject> by lazy {
        val read = JsonReader.read(GoldenFiles.text(FILE))
        check(read is JsonRead.Parsed) { "$FILE does not parse: $read" }
        (read.value as JsonArray).items.map { it as JsonObject }
    }

    /** The first difference in one case, or null when every call matches the page. */
    fun replay(case: JsonObject, dealer: HandDealer, content: QuestContent): String? {
        val input = case["input"] as JsonObject
        val zone = ZoneId.of(text(input["zone"]))
        var now = text(input["now"])
        var save = input["state"] as JsonObject
        val calls = (input["calls"] as JsonArray).items
        val outs = ((case["output"] as JsonObject)["calls"] as JsonArray).items
        if (calls.size != outs.size)
            return "the case has ${calls.size} calls and ${outs.size} outputs"
        for ((i, call) in calls.withIndex()) {
            call as JsonObject
            val expected = outs[i] as JsonObject
            (call["at"] as? JsonString)?.let { now = it.value }
            val clock = EconomyClock(Instant.parse(now).toEpochMilli(), zone)
            val ctx =
                context(call["ctx"] as JsonObject, clock)
                    ?: return "call $i: the clock adapter gives another day"
            val fn = text(call["fn"])
            val args = (call["args"] as? JsonArray)?.items.orEmpty()
            val (returns, after) = dispatch(fn, args, save, ctx, dealer, content)
            val where = "call $i ($fn)"
            compareReturns(returns, expected["returns"])?.let {
                return "$where returns: $it"
            }
            compareState(save, after.save, expected)?.let {
                return "$where state: $it"
            }
            val toasts = (expected["toasts"] as? JsonArray)?.items.orEmpty().map { text(it) }
            val requests = (expected["saves"] as? JsonNumber)?.value?.toInt() ?: 0
            if (after is HandStep.Done<*>) {
                if (after.toasts != toasts)
                    return "$where toasts: expected $toasts, got ${after.toasts}"
                if (after.saveRequests != requests)
                    return "$where saves: expected $requests, got ${after.saveRequests}"
            }
            save = after.save
        }
        return null
    }

    /**
     * The recorded outside inputs, as a [HandContext]. The day of a record is read with the
     * economy's clock (TIME-4), which must give the same today as the page did, or the case is
     * refused.
     */
    private fun context(ctx: JsonObject, clock: EconomyClock): HandContext? {
        val today = text(ctx["today"])
        if (clock.today != today) return null
        val rest = (ctx["rest"] as JsonObject).members
        return HandContext(
            today = today,
            luckScore = (ctx["luck"] as? JsonNumber)?.value,
            isRestDay = { day ->
                (rest[day] as? JsonBool)?.value
                    ?: error(
                        "the draw asked whether $day is a rest day; the page did not record it"
                    )
            },
            dayOf = { date -> dayOf(date, clock) },
        )
    }

    /** `new Date(v).toLocaleDateString('en-CA')` for the record dates the cases hold. */
    private fun dayOf(date: JsonValue?, clock: EconomyClock): String =
        when (date) {
            null -> EconomyClock.INVALID_DAY
            JsonNull -> clock.localDate(0)
            is JsonString -> clock.dayOf(date.value)
            is JsonNumber -> clock.localDate(date.value.toLong())
            else -> EconomyClock.INVALID_DAY
        }

    /** The adapter table: the page's function name to the native call. */
    private fun dispatch(
        fn: String,
        args: List<JsonValue>,
        save: JsonObject,
        ctx: HandContext,
        dealer: HandDealer,
        content: QuestContent,
    ): Pair<JsonValue?, HandStep<*>> {
        fun same(value: JsonValue?) = value to HandStep.Done(save, Unit)
        fun <T> step(s: HandStep<T>, show: (T) -> JsonValue?) =
            (if (s is HandStep.Done<T>) show(s.value) else THREW) to s
        return when (fn) {
            "dhSeed" -> same(JsonNumber(HandRandom.seed(text(args[0])).toDouble()))
            "dhRng" -> {
                val rng = HandRng(HandRandom.toUint32((args[0] as JsonNumber).value))
                same(JsonArray(List(int(args[1])) { JsonNumber(rng.next()) }))
            }
            "dhMakeQuest" ->
                same(
                    dealer.makeQuest(
                        content.template(text(args[0]))!!,
                        text(args[1]),
                        int(args[2]),
                        Js.string(args[3]),
                    )
                )
            "dhRecentAttrXP" ->
                step(dealer.recentAttrXp(save, int(args[0]), ctx)) { xp ->
                    JsonObject(xp.mapValuesTo(linkedMapOf()) { JsonNumber(it.value) })
                }
            "dhWeakestAttr" -> step(dealer.weakestAttr(save, ctx)) { JsonString(it) }
            "dhDraw" ->
                step(
                    dealer.draw(
                        save,
                        text(args[0]),
                        Js.string(args[1]),
                        int(args[2]),
                        (args[3] as JsonArray).items,
                        ctx,
                    )
                ) {
                    it?.let { t -> JsonString(t.id) } ?: JsonNull
                }
            "dhEnsure" -> step(dealer.ensure(save, ctx)) { it }
            "dhCards" -> step(dealer.cards(save)) { it }
            "dhRerollCost" -> same(JsonNumber(dealer.rerollCost(save).toDouble()))
            // a slot that is not a whole number names no card, as `d.cards[1.5]` does on the page
            "dhReroll" -> step(dealer.reroll(save, slotIndex(args[0]), ctx)) { JsonBool.of(it) }
            "assign" -> JsonNull to HandStep.Done(save.with(text(args[0]), args[1]), Unit)
            else -> error("no adapter for $fn")
        }
    }

    internal fun compareReturns(actual: JsonValue?, expected: JsonValue?): String? {
        val threw = expected is JsonObject && "threw" in expected
        if (threw || actual === THREW)
            return if (threw && actual === THREW) null
            else
                "expected ${show(expected)}, got ${if (actual === THREW) "a throw" else show(actual)}"
        return if (show(actual) == show(expected)) null
        else difference(show(expected), show(actual))
    }

    /** Where two texts first differ, with some text either side of that place. */
    internal fun difference(expected: String, actual: String): String {
        val at =
            expected
                .zip(actual)
                .indexOfFirst { it.first != it.second }
                .let { if (it < 0) minOf(expected.length, actual.length) else it }
        fun around(text: String) =
            text
                .substring(maxOf(0, at - CONTEXT), minOf(text.length, at + CONTEXT))
                .replace(Regex("[^\\x20-\\x7e]"), "?")
        return "differ at $at: expected ...${around(expected)}... got ...${around(actual)}..."
    }

    private const val CONTEXT = 80

    internal fun compareState(
        before: JsonObject,
        after: JsonObject,
        expected: JsonObject,
        fields: List<String> = OUT,
    ): String? {
        val changed = (expected["state"] as? JsonObject)?.members.orEmpty()
        val deleted = (expected["deleted"] as? JsonArray)?.items.orEmpty().map { text(it) }
        for (field in fields) {
            val was = show(before[field])
            val now = show(after[field])
            when {
                field in deleted ->
                    if (after[field] != null) return "$field should be gone, is $now"
                field in changed ->
                    if (now != show(changed[field]))
                        return "$field: ${difference(show(changed[field]), now)}"
                now != was -> return "$field should not change; it became $now"
            }
        }
        return null
    }

    internal fun show(value: JsonValue?): String = value?.let(JsonWriter::write) ?: "(absent)"

    private fun text(value: JsonValue?): String = (value as JsonString).value

    private fun int(value: JsonValue?): Int = (value as JsonNumber).value.toInt()

    private fun slotIndex(value: JsonValue?): Int =
        (value as JsonNumber).value.let { if (it == Math.rint(it)) it.toInt() else -1 }

    /** Marks a call that threw, where the page's output holds `{threw}`. */
    internal val THREW = JsonString("\u0000threw")
}
