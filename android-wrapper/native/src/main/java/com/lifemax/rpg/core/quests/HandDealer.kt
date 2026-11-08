package com.lifemax.rpg.core.quests

import com.lifemax.rpg.core.economy.jsRound
import com.lifemax.rpg.core.quests.Js.with
import com.lifemax.rpg.core.quests.Js.withIndexKey
import com.lifemax.rpg.core.quests.Js.withItem
import com.lifemax.rpg.core.save.JsonArray
import com.lifemax.rpg.core.save.JsonBool
import com.lifemax.rpg.core.save.JsonNull
import com.lifemax.rpg.core.save.JsonNumber
import com.lifemax.rpg.core.save.JsonObject
import com.lifemax.rpg.core.save.JsonString
import com.lifemax.rpg.core.save.JsonValue
import java.time.LocalDate
import kotlin.math.max

/**
 * The Daily Hand's deal, in Kotlin (QUESTS_SPEC sections 7 to 9): `dhEnsure`, `dhCards`,
 * `dhRerollCost` and `dhReroll` with the draw they run, on the save as the page holds it (a JSON
 * object, so every field and key order the page keeps is kept). It reproduces the page bit for bit,
 * its defects included (QD01, QD02, QD13 to QD16), and is accepted against the page's golden cases
 * (`tests/fixtures/quests-golden.json`). It writes nothing anywhere: each call returns the save the
 * page would hold after it. Only `quests`, `dailyDraw`, `dhMig` and `sparks` ever change.
 *
 * The page stays the only writer; nothing calls this yet.
 */
class HandDealer(private val content: QuestContent, private val odds: HandOdds = HandOdds.PAGE) {
    private val pick = HandPick(content, odds)

    /**
     * `dhEnsure()` (QST-41 to QST-43): the one-time migration, the day's stale cards dropped, a new
     * draw when the stored one is missing, from another day or without three cards (nothing else of
     * the old draw is kept), and a quest for every card that lacks one. Returns the draw. Nothing
     * is saved [QD15].
     */
    fun ensure(save: JsonObject, ctx: HandContext): HandStep<JsonObject> =
        run(save) { ensureIn(it, ctx) }

    /** `dhCards()` (QST-44): the quests that are Hand cards, in board order. */
    fun cards(save: JsonObject): HandStep<JsonArray> =
        run(save) { w ->
            JsonArray(w.quests("filter").items.filter { Js.truthy(Js.prop(it, "hand")) })
        }

    /**
     * `dhRerollCost()` (QST-45): free while fewer than the free rerolls are used, else the price
     * (also the price with no draw, or a count that is not a number).
     */
    fun rerollCost(save: JsonObject): Int {
        val draw = save["dailyDraw"]
        val used = if (Js.truthy(draw)) Js.number(Js.prop(draw, "rerollsUsed")) else Double.NaN
        return if (used < content.hand.freeRerolls) 0 else content.hand.rerollCost
    }

    /**
     * `dhReroll(slotIdx)` (QST-46): ensures first (so a new day deals before it rerolls), refuses a
     * missing or finished card and a price the Sparks cannot pay, then counts the reroll, draws the
     * slot again with every current template excluded and replaces the card and its quest. The card
     * is put face down (`up`), its `turned` flag is left [QD16]. It asks to save once.
     */
    fun reroll(save: JsonObject, slotIndex: Int, ctx: HandContext): HandStep<Boolean> =
        run(save) { rerollIn(it, slotIndex, ctx) }

    /** `dhRecentAttrXP(days)` (QST-35), by attribute in `ATTRS` order. */
    fun recentAttrXp(save: JsonObject, days: Int, ctx: HandContext): HandStep<Map<String, Double>> =
        run(save) { pick.recentAttrXp(it.snapshot(), days, ctx) }

    /** `dhWeakestAttr()` (QST-36). */
    fun weakestAttr(save: JsonObject, ctx: HandContext): HandStep<String> =
        run(save) { pick.weakestAttr(it.snapshot(), ctx) }

    /**
     * `dhDraw(date, nonce, slotIdx, taken)` (QST-39): the template drawn, or null when every
     * template is taken. [nonceText] is the nonce as the page writes it into the seed.
     */
    fun draw(
        save: JsonObject,
        date: String,
        nonceText: String,
        slotIndex: Int,
        taken: List<JsonValue?>,
        ctx: HandContext,
    ): HandStep<HandTemplate?> =
        run(save) { pick.draw(it.snapshot(), date, nonceText, slotIndex, taken, ctx) }

    /** `dhMakeQuest(t, date, slotIdx, nonce)` (QST-40). */
    fun makeQuest(t: HandTemplate, date: String, slot: Int, nonceText: String): JsonObject =
        pick.makeQuest(t, date, slot, nonceText)

    private fun ensureIn(w: HandWork, ctx: HandContext): JsonObject {
        val date = ctx.today
        if (!Js.truthy(w["quests"])) w["quests"] = JsonArray.EMPTY
        if (!Js.truthy(w["dhMig"])) {
            w["quests"] = JsonArray(w.quests("filter").items.filterNot(::isReplacedDaily))
            w["dhMig"] = JsonNumber(1.0)
        }
        val prefix = HandIds.dayPrefix(date)
        w["quests"] =
            JsonArray(
                w.quests("filter").items.filter {
                    !Js.truthy(Js.prop(it, "hand")) || startsWith(Js.prop(it, "id"), prefix)
                }
            )
        val stored = w["dailyDraw"]
        val draw: JsonObject =
            if (isTodaysDraw(stored, date)) stored as JsonObject
            else newDraw(w, date, ctx).also { w["dailyDraw"] = it }
        val cards =
            Js.prop(draw, "cards") as? JsonArray
                ?: throw PageThrew("d.cards.forEach is not a function")
        cards.items.forEachIndexed { i, c ->
            val quests = w.quests("some").items
            if (quests.any { Js.strictEquals(Js.prop(it, "id"), Js.prop(c, "id")) })
                return@forEachIndexed
            val tpl = Js.prop(c, "tpl")
            val t = content.hand.templates.firstOrNull { Js.isText(tpl, it.id) }
            if (t != null)
                w["quests"] =
                    JsonArray(quests + pick.makeQuest(t, date, i, Js.string(Js.prop(c, "n"))))
        }
        return draw
    }

    /** QST-42: an old fixed daily the Hand replaced (and not a nutrition or hydration one). */
    private fun isReplacedDaily(q: JsonValue): Boolean {
        val id = Js.string(Js.prop(q, "id"))
        if (!HandMigration.matchesPattern(id)) return false
        if (!Js.isText(Js.prop(q, "cadence"), HandPick.DAILY)) return false
        val kind = Js.prop(q, "kind")
        return HandMigration.KEPT_KINDS.none { Js.isText(kind, it) }
    }

    /** `q.id.indexOf(prefix) === 0`, for a string id or (as JavaScript allows) an array id. */
    private fun startsWith(id: JsonValue?, prefix: String): Boolean =
        when (id) {
            is JsonString -> id.value.startsWith(prefix)
            is JsonArray -> Js.isText(id.items.firstOrNull(), prefix)
            else -> throw PageThrew("q.id.indexOf is not a function")
        }

    /** The stored draw is today's and holds three cards (`length === 3`, whatever holds it). */
    private fun isTodaysDraw(stored: JsonValue?, date: String): Boolean {
        if (!Js.truthy(stored) || !Js.isText(Js.prop(stored, "date"), date)) return false
        val cards = Js.prop(stored, "cards")
        return Js.truthy(cards) && Js.length(cards) == HandSlot.COUNT.toDouble()
    }

    /** Three cards with nonce 0, each slot excluding the templates of the slots before it. */
    private fun newDraw(w: HandWork, date: String, ctx: HandContext): JsonObject {
        val taken = mutableListOf<JsonValue?>()
        val cards = mutableListOf<JsonValue>()
        for (slot in 0 until HandSlot.COUNT) {
            val t =
                pick.draw(w.snapshot(), date, FIRST_NONCE, slot, taken, ctx) ?: throw noTemplate()
            taken += JsonString(t.id)
            cards += card(t, date, slot, 0.0)
        }
        return JsonObject(
            linkedMapOf(
                "date" to JsonString(date),
                "nonce" to JsonNumber(0.0),
                "rerollsUsed" to JsonNumber(0.0),
                "cards" to JsonArray(cards),
            )
        )
    }

    private fun card(t: HandTemplate, date: String, slot: Int, nonce: Double): JsonObject =
        JsonObject(
            linkedMapOf(
                "tpl" to JsonString(t.id),
                "id" to JsonString(HandIds.card(date, slot, Js.numberText(nonce))),
                "n" to JsonNumber(nonce),
            )
        )

    private fun rerollIn(w: HandWork, slotIndex: Int, ctx: HandContext): Boolean {
        var draw = ensureIn(w, ctx)
        val c = (Js.prop(draw, "cards") as JsonArray).items.getOrNull(slotIndex)
        if (!Js.truthy(c)) return false
        val cardId = Js.prop(c, "id")
        val quest =
            w.quests("find").items.firstOrNull { Js.strictEquals(Js.prop(it, "id"), cardId) }
        if (Js.truthy(quest) && isDone(w, quest, ctx)) {
            w.toasts += HandIds.MESSAGE_FINISHED
            return false
        }
        val cost = rerollCost(w.snapshot())
        if (cost > 0 && !spend(w, cost)) return false
        fun put(key: String, value: JsonValue) {
            draw = draw.with(key, value)
            w["dailyDraw"] = draw
        }
        put("rerollsUsed", JsonNumber(Js.number(draw["rerollsUsed"]) + 1))
        val nonce = Js.number(draw["nonce"]) + 1
        put("nonce", JsonNumber(nonce))
        val date = Js.string(draw["date"])
        val cards = draw["cards"] as JsonArray
        val taken = cards.items.map { Js.prop(it, "tpl") }
        val t =
            pick.draw(w.snapshot(), date, Js.numberText(nonce), slotIndex, taken, ctx)
                ?: throw noTemplate()
        w["quests"] =
            JsonArray(
                w.quests("filter").items.filterNot { Js.strictEquals(Js.prop(it, "id"), cardId) }
            )
        put("cards", cards.withItem(slotIndex, card(t, date, slotIndex, nonce)))
        val up = draw["up"].takeIf { Js.truthy(it) } ?: FACE_DOWN.also { put("up", it) }
        when (up) {
            is JsonArray -> put("up", up.withItem(slotIndex, JsonBool.FALSE))
            is JsonObject -> put("up", up.withIndexKey(slotIndex, JsonBool.FALSE))
            else -> Unit // a primitive ignores the write, as the page's sloppy-mode code does
        }
        w["quests"] =
            JsonArray(
                w.quests("push").items + pick.makeQuest(t, date, slotIndex, Js.numberText(nonce))
            )
        w.toasts += if (cost > 0) HandIds.messagePaid(cost) else HandIds.MESSAGE_FREE
        w.saveRequests++
        return true
    }

    /** `isQuestDone(q)` (QST-12): the quest's period key is set in `questDone`. */
    private fun isDone(w: HandWork, quest: JsonValue?, ctx: HandContext): Boolean {
        val cadence = Js.prop(quest, "cadence")
        val period =
            when {
                Js.isText(cadence, HandPick.DAILY) -> ctx.today
                Js.isText(cadence, WEEKLY) -> weekMonday(ctx.today)
                else -> ONCE
            }
        val key = Js.string(Js.prop(quest, "id")) + "_" + period
        return when (val done = w["questDone"]) {
            null,
            JsonNull ->
                throw PageThrew("Cannot read properties of ${Js.string(done)} (reading '$key')")
            is JsonObject -> Js.truthy(done[key])
            else -> false // an array or a primitive has no member named like a period key
        }
    }

    /** `ecoSpend(n)` (SPARK-3, as `Sparks.spend`): refuses when the rounded balance is short. */
    private fun spend(w: HandWork, cost: Int): Boolean {
        val held = Js.numberOrZero(w["sparks"])
        if (max(0.0, jsRound(held)) < cost) {
            w.toasts += HandIds.MESSAGE_NO_SPARKS
            return false
        }
        w["sparks"] = JsonNumber(held - cost)
        return true
    }

    private fun weekMonday(today: String): String =
        LocalDate.parse(today).let { it.minusDays(it.dayOfWeek.value - 1L) }.toString()

    private fun noTemplate() = PageThrew("Cannot read properties of undefined (reading 'id')")

    private inline fun <T> run(save: JsonObject, body: (HandWork) -> T): HandStep<T> {
        val w = HandWork(save)
        return try {
            val value = body(w)
            HandStep.Done(w.snapshot(), value, w.toasts.toList(), w.saveRequests)
        } catch (threw: PageThrew) {
            HandStep.Threw(w.snapshot(), threw.message.orEmpty())
        }
    }

    private companion object {
        const val FIRST_NONCE = "0"
        const val WEEKLY = "weekly"
        const val ONCE = "once"
        val FACE_DOWN = JsonArray(List(HandSlot.COUNT) { JsonBool.FALSE })
    }
}

/**
 * The save while one call runs: the page changes `state` in place, so a call that throws halfway
 * leaves what it changed so far, and so does this. Top-level keys keep their place; a new one comes
 * last, as on a JavaScript object.
 */
internal class HandWork(save: JsonObject) {
    private val members = LinkedHashMap(save.members)
    val toasts = mutableListOf<String>()
    var saveRequests = 0

    operator fun get(key: String): JsonValue? = members[key]

    operator fun set(key: String, value: JsonValue) {
        members[key] = value
    }

    /** `state.quests` for an array method ([method]); anything else throws, as on the page. */
    fun quests(method: String): JsonArray =
        members["quests"] as? JsonArray ?: throw PageThrew("state.quests.$method is not a function")

    fun snapshot(): JsonObject = JsonObject(LinkedHashMap(members))
}
