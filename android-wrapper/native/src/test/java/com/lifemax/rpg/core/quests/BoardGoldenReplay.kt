package com.lifemax.rpg.core.quests

import com.lifemax.rpg.core.economy.EconomyClock
import com.lifemax.rpg.core.golden.GoldenFiles
import com.lifemax.rpg.core.quests.HandGoldenReplay.THREW
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
import java.time.Instant
import java.time.ZoneId

/**
 * Replays the quest board's golden cases
 * (`lifemax_modular_v73/tests/fixtures/quests-board-golden.json`, format in
 * `tools/gen_quests_board_golden.js`) through [QuestRules], [QuestBoard], [ClassPacks] and
 * [CustomQuests]. This is the adapter table: each page name becomes a native call, and each result
 * is written the way the recorder wrote the page's markup, so everything is compared as JSON text.
 */
object BoardGoldenReplay {
    const val FILE = "quests-board-golden.json"

    /** The fields a call's output reports (`OUT` in the generator). */
    val OUT = listOf("quests", "questDone", "classId", "classContent")

    /** Every case in the file, as the page wrote it. */
    val cases: List<JsonObject> by lazy {
        val read = JsonReader.read(GoldenFiles.text(FILE))
        check(read is JsonRead.Parsed) { "$FILE does not parse: $read" }
        (read.value as JsonArray).items.map { it as JsonObject }
    }

    /** The first difference in one case, or null when every call matches the page. */
    fun replay(
        case: JsonObject,
        content: QuestContent,
        tuning: BoardTuning = BoardTuning.PAGE,
        clockFor: (EconomyClock) -> BoardClock = BoardClock::of,
        checkClock: Boolean = true,
    ): String? {
        val adapters =
            Adapters(
                ClassPacks(content),
                QuestRules(tuning),
                QuestBoard(tuning),
                CustomQuests(tuning),
            )
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
            val clock = clockFor(EconomyClock(Instant.parse(now).toEpochMilli(), zone))
            val ctx = call["ctx"] as JsonObject
            if (checkClock)
                clockDifference(ctx, clock)?.let {
                    return "call $i: the clock gives $it"
                }
            val level = (ctx["level"] as? JsonNumber)?.value?.toInt()
            CharacterLevel.of(save)?.let {
                if (it != level) return "call $i: level is $level, the save says $it"
            }
            val fn = text(call["fn"])
            val args = (call["args"] as? JsonArray)?.items.orEmpty()
            val (returns, after) = dispatch(fn, args, save, clock, level, ctx, adapters)
            val where = "call $i ($fn)"
            HandGoldenReplay.compareReturns(returns, expected["returns"])?.let {
                return "$where returns: $it"
            }
            HandGoldenReplay.compareState(save, after.save, expected, OUT)?.let {
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

    /** What the clock adapter gives that the page's clock did not, or null. */
    private fun clockDifference(ctx: JsonObject, clock: BoardClock): String? {
        val recorded =
            listOf(
                text(ctx["today"]),
                (ctx["day"] as JsonNumber).value.toInt(),
                text(ctx["week"]),
                (ctx["now"] as JsonNumber).value.toLong(),
            )
        val own = listOf(clock.today, clock.weekday, clock.weekKey, clock.nowMillis)
        return if (recorded == own) null else "$own instead of $recorded"
    }

    private fun dispatch(
        fn: String,
        args: List<JsonValue>,
        save: JsonObject,
        clock: BoardClock,
        level: Int?,
        ctx: JsonObject,
        a: Adapters,
    ): Pair<JsonValue?, HandStep<*>> {
        fun same(value: JsonValue?) = value to HandStep.Done(save, Unit)
        fun <T> step(s: HandStep<T>, show: (T) -> JsonValue?) =
            (if (s is HandStep.Done<T>) show(s.value) else THREW) to s
        fun guarded(body: () -> JsonValue?): Pair<JsonValue?, HandStep<*>> =
            try {
                same(body())
            } catch (e: PageThrew) {
                THREW to HandStep.Threw(save, e.message.orEmpty())
            }
        return when (fn) {
            "questToday" -> guarded { JsonBool.of(a.rules.questToday(args[0], clock)) }
            "questKey" -> guarded { JsonString(a.rules.questKey(args[0], clock)) }
            "isQuestDone" -> guarded { JsonBool.of(a.rules.isQuestDone(save, args[0], clock)) }
            "stageLvl" -> guarded { JsonNumber(a.rules.stageLvl(literal(args[0])).toDouble()) }
            "stageOf" ->
                guarded { JsonNumber(a.rules.stageLvl(Js.prop(args[0], "stage")).toDouble()) }
            "questLocked" -> guarded { JsonBool.of(a.rules.questLocked(args[0], level)) }
            "applyClassContent" -> step(a.packs.apply(save)) { JsonNull }
            "createQuest" -> {
                val fields = ctx["fields"] as JsonObject
                step(
                    a.custom.create(
                        save,
                        clock,
                        text(fields["name"]),
                        text(fields["cadence"]),
                        text(fields["xp"]),
                    )
                ) {
                    JsonNull
                }
            }
            "renderQuests" -> step(a.board.project(save, clock, level), BoardJson::board)
            "renderLedger" -> step(a.board.ledger(save, clock, level), BoardJson::ledger)
            "assign" -> JsonNull to HandStep.Done(save.with(text(args[0]), args[1]), Unit)
            else -> error("no adapter for $fn")
        }
    }

    /**
     * `{"$": "undefined" | "NaN" | "-0" | "Infinity"}` is that JavaScript value, which JSON cannot
     * hold.
     */
    private fun literal(value: JsonValue): JsonValue? {
        val name = ((value as? JsonObject)?.get("$") as? JsonString)?.value ?: return value
        return when (name) {
            "undefined" -> null
            "NaN" -> JsonNumber(Double.NaN)
            "-0" -> JsonNumber(-0.0)
            "Infinity" -> JsonNumber(Double.POSITIVE_INFINITY)
            else -> error("unknown literal $name")
        }
    }

    private fun text(value: JsonValue?): String = (value as JsonString).value

    /** The native pieces a case is replayed through. */
    private class Adapters(
        val packs: ClassPacks,
        val rules: QuestRules,
        val board: QuestBoard,
        val custom: CustomQuests,
    )
}
