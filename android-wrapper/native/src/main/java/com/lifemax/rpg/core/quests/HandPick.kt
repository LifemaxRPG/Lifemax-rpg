package com.lifemax.rpg.core.quests

import com.lifemax.rpg.core.save.JsonArray
import com.lifemax.rpg.core.save.JsonBool
import com.lifemax.rpg.core.save.JsonNumber
import com.lifemax.rpg.core.save.JsonObject
import com.lifemax.rpg.core.save.JsonString
import com.lifemax.rpg.core.save.JsonValue
import java.time.LocalDate
import kotlin.math.max

/**
 * One card's draw (QST-35 to QST-40): the recent attribute XP, the weakest attribute, the pool, the
 * weights and the pick, read-only on the save. Every function may throw [PageThrew] where the page
 * throws; [HandDealer] is the public face that catches it.
 */
internal class HandPick(private val content: QuestContent, private val odds: HandOdds) {
    /** `dhLuckScore()`: the score, or the fallback when there is no roll or it is falsy. */
    fun luckScore(ctx: HandContext): Double =
        ctx.luckScore?.takeUnless { it.isNaN() || it == 0.0 } ?: odds.luckFallback

    /**
     * `dhRecentAttrXP(days)`: XP per attribute over the last [days] local days (from `days - 1`
     * days ago, with no upper bound [QD13]), quest records left out, passive ones counted, an empty
     * `xpSplit` counting nothing [QD14]. Sums are added in record order, as the page adds.
     */
    fun recentAttrXp(save: JsonObject, days: Int, ctx: HandContext): Map<String, Double> {
        val since = LocalDate.parse(ctx.today).minusDays(days - 1L).toString()
        val out = LinkedHashMap<String, Double>()
        content.attributes.forEach { out[it] = 0.0 }
        val activities =
            save["activities"] as? JsonArray
                ?: throw PageThrew("state.activities.forEach is not a function")
        for (a in activities.items) {
            if (Js.isText(Js.prop(a, "type"), QUEST_RECORD)) continue
            if (ctx.dayOf(Js.prop(a, "date")) < since) continue
            val split = Js.prop(a, "xpSplit")
            val shares: List<JsonValue> =
                when {
                    !Js.truthy(split) -> listOf(defaultShare(a))
                    split is JsonArray -> split.items
                    else -> throw PageThrew("(a.xpSplit || []).forEach is not a function")
                }
            for (x in shares) {
                if (!Js.truthy(x)) continue
                val attr = Js.string(Js.prop(x, "attr"))
                val sum = out[attr] ?: continue
                out[attr] = sum + max(0.0, Js.numberOrZero(Js.prop(x, "xp")))
            }
        }
        return out
    }

    /** `{attr: a.primary, xp: a.xp}`, the share of a record without a split. */
    private fun defaultShare(a: JsonValue): JsonObject {
        val share = LinkedHashMap<String, JsonValue>()
        Js.prop(a, "primary")?.let { share["attr"] = it }
        Js.prop(a, "xp")?.let { share["xp"] = it }
        return JsonObject(share)
    }

    /** `dhWeakestAttr()`: the attribute with the least XP in 7 days; ties go to `ATTRS` order. */
    fun weakestAttr(save: JsonObject, ctx: HandContext): String {
        val xp = recentAttrXp(save, WEAKEST_WINDOW_DAYS, ctx)
        var best = content.attributes.first()
        for (attr in content.attributes) if (xp.getValue(attr) < xp.getValue(best)) best = attr
        return best
    }

    /**
     * `dhClassAttrs()`: `DH_CLASS_ATTR[state.classId] || null`, looked up by the class id's text. A
     * name every object inherits (`toString`, `__proto__`) finds something that is not a list, and
     * the page throws as soon as it filters with it.
     */
    private fun classAttrs(save: JsonObject): AttrFilter {
        val key = Js.string(save["classId"])
        if (key in Js.OBJECT_PROTOTYPE_KEYS) return AttrFilter.NotAList
        return content.hand.classAttrs[key]?.let { AttrFilter.Of(it) } ?: AttrFilter.Any
    }

    /**
     * `dhDraw(date, nonce, slotIdx, taken)`: seeds a generator from the day, the luck, the nonce
     * and the slot, picks the slot's attributes, then [pick]s. [nonceText] is `String(nonce)`;
     * [taken] holds the template ids already in the hand (any value the save holds). Null when the
     * pool is empty (every template taken), where the page gets `undefined`.
     */
    fun draw(
        save: JsonObject,
        date: String,
        nonceText: String,
        slotIndex: Int,
        taken: List<JsonValue?>,
        ctx: HandContext,
    ): HandTemplate? {
        val slot = HandSlot.at(slotIndex)
        val luckText = Js.numberText(luckScore(ctx))
        val rng = HandRng(HandRandom.seed(HandIds.seedText(date, luckText, nonceText, slotIndex)))
        val attrs =
            when (slot) {
                HandSlot.MAINTENANCE -> AttrFilter.Of(listOf(weakestAttr(save, ctx)))
                HandSlot.CLASS -> classAttrs(save)
                else -> AttrFilter.Any
            }
        return pick(rng, attrs, taken, date, slot, ctx)
    }

    /**
     * `dhPick` (QST-38): the library in its order, filtered by the attributes, what is taken and
     * (on a rest day) tier 3; without the attribute filter when that leaves nothing. One draw `r =
     * rng() * total`, then the weights are subtracted in pool order and the first template that
     * takes `r` to zero or below is picked, else the last.
     */
    private fun pick(
        rng: HandRng,
        attrs: AttrFilter,
        taken: List<JsonValue?>,
        date: String,
        slot: HandSlot?,
        ctx: HandContext,
    ): HandTemplate? {
        val rest = ctx.isRestDay(date)
        val library = content.hand.templates
        fun free(t: HandTemplate) =
            taken.none { Js.isText(it, t.id) } && !(rest && t.tier == HandOdds.TOP_TIER)
        if (attrs == AttrFilter.NotAList && library.isNotEmpty())
            throw PageThrew("attrs.indexOf is not a function")
        var pool = library.filter { (attrs !is AttrFilter.Of || it.attr in attrs.ids) && free(it) }
        if (pool.isEmpty()) pool = library.filter(::free)
        val luck = odds.luck(luckScore(ctx))
        val total = pool.fold(0.0) { sum, t -> sum + odds.weight(t.tier, luck, slot) }
        var r = rng.next() * total
        for (t in pool) {
            r -= odds.weight(t.tier, luck, slot)
            if (r <= 0) return t
        }
        return pool.lastOrNull()
    }

    /**
     * `dhMakeQuest(t, date, slotIdx, nonce)` (QST-40): the card's quest, keys in the page's order.
     */
    fun makeQuest(t: HandTemplate, date: String, slot: Int, nonceText: String): JsonObject =
        JsonObject(
            linkedMapOf(
                "id" to JsonString(HandIds.card(date, slot, nonceText)),
                "hand" to JsonBool.TRUE,
                "slot" to JsonNumber(slot.toDouble()),
                "tpl" to JsonString(t.id),
                "name" to JsonString(t.name),
                "desc" to JsonString(t.desc),
                "cadence" to JsonString(DAILY),
                "xp" to JsonNumber(t.xp.toDouble()),
                "kind" to JsonString(HAND_KIND),
                "attribute" to JsonString(t.attr),
                "verify" to JsonString(t.verify),
                "tier" to JsonNumber(t.tier.toDouble()),
            )
        )

    /** The attributes a card may draw from. */
    private sealed interface AttrFilter {
        /** No filter (`null`). */
        data object Any : AttrFilter

        data class Of(val ids: List<String>) : AttrFilter

        /** Something truthy that is not a list: the page throws when it filters with it. */
        data object NotAList : AttrFilter
    }

    companion object {
        /** `dhWeakestAttr` looks at the last 7 days. */
        const val WEAKEST_WINDOW_DAYS = 7
        const val QUEST_RECORD = "quest"
        const val DAILY = "daily"
        const val HAND_KIND = "hand"
    }
}
