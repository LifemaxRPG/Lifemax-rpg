package com.lifemax.rpg.core.shadow

import com.lifemax.rpg.core.economy.EconomyClock
import com.lifemax.rpg.core.save.JsonArray
import com.lifemax.rpg.core.save.JsonNull
import com.lifemax.rpg.core.save.JsonNumber
import com.lifemax.rpg.core.save.JsonObject
import com.lifemax.rpg.core.save.JsonString
import com.lifemax.rpg.core.save.JsonValue
import com.lifemax.rpg.core.save.SaveFields
import com.lifemax.rpg.core.save.sameStoredValue
import java.time.ZoneOffset

/**
 * One place where the page's outcome and native's differ, named twice: [shown] for the ring
 * (owner-chosen names as `*#a1b2c3`, new records by position, `activities[+1].xp`) and [pattern]
 * for the allowlist (owner-chosen names as `*`, records as `[+]` or `[]`).
 */
data class Difference(val shown: String, val pattern: String)

/**
 * The comparison of one action's after-states (ECONOMY_SHADOW_PLAN 1.3, 1.4): every owned path of
 * the tracked keys ([SaveFields.TRACKED], inside [SaveFields.PARTIAL] keys only the listed
 * sub-paths) and the Daily Hand's own keys whole (`dailyDraw`, `classContent`, `dhMig`: the hand's
 * deal and class packs are native's from WP6c), compared by the golden rule (JSON-value equality,
 * key order ignored, array order kept, numbers as binary64), with the two normalisations a value
 * may need:
 * - N1 clock ids: `quest_1793700000123` (and `act_<ms>_<token>`) are equal when the prefix and
 *   token agree, native's milliseconds are `t0` and the page's lie in `[t0, t1]`;
 * - N2 instants at the declared paths ([INSTANTS]): equal when native's is `t0` and the page's lies
 *   in `[t0, t1]`.
 *
 * The other normalisations are decided before a run (N3 midnight, N4 zone) or by the id source (N1
 * minted UUIDs and tokens, [ReplayIds]). Names pass through [ShadowRedactor].
 */
class ShadowDiff(private val redactor: ShadowRedactor) {
    /** Where [page] and [native] differ, against the shared [before]; at most [MAX_FOUND]. */
    fun state(
        before: Map<String, JsonValue>,
        page: Map<String, JsonValue>,
        native: Map<String, JsonValue>,
        t0: Long,
        t1: Long,
    ): List<Difference> {
        val walk = Walk(t0, t1)
        for (key in KEYS) {
            val tree = OWNED[key]
            val p = page[key]?.let { owned(it, tree) }
            val n = native[key]?.let { owned(it, tree) }
            val b = before[key]?.let { owned(it, tree) }
            walk.value(root(key, tree), p, n, b)
        }
        return walk.found
    }

    /** Where two return values differ (`returns`, `returns.xp`). */
    fun value(name: String, page: JsonValue, native: JsonValue): List<Difference> =
        Walk(0, 0).apply { value(Step(name, name, null, false), page, native, null) }.found

    private inner class Walk(val t0: Long, val t1: Long) {
        val found = ArrayList<Difference>()

        fun value(at: Step, page: JsonValue?, native: JsonValue?, before: JsonValue?) {
            if (found.size >= MAX_FOUND) return
            when {
                // the mirror and the codec's merge both keep an unchanged value as the very same
                // instance, so a large ledger is compared in the time of what changed
                page === native -> Unit
                page == null && native == null -> Unit
                page is JsonObject && native is JsonObject ->
                    members(at, page, native, before as? JsonObject)
                page is JsonArray && native is JsonArray ->
                    array(at, page, native, before as? JsonArray)
                page == null || native == null -> add(at)
                page.sameStoredValue(native) -> Unit
                at.instant() && instantInWindow(page, native) -> Unit
                clockIdsAgree(page, native) -> Unit
                else -> add(at)
            }
        }

        private fun members(at: Step, page: JsonObject, native: JsonObject, before: JsonObject?) {
            val names = LinkedHashSet(page.members.keys).apply { addAll(native.members.keys) }
            for (name in names) value(at.member(name), page[name], native[name], before?.get(name))
        }

        /**
         * Records are new when the before-state has no record of their id (or, without ids, past
         * its end), so a record added after one was removed is still `[+1]`; the others are `[]`.
         */
        private fun array(at: Step, page: JsonArray, native: JsonArray, before: JsonArray?) {
            val old = before?.items.orEmpty()
            val ids = old.mapNotNull(::idOf).toSet()
            val byId = ids.isNotEmpty() && ids.size == old.size
            if (page.items.size != native.items.size) add(at.length())
            var added = 0
            for (i in 0 until minOf(page.items.size, native.items.size)) {
                val item = page.items[i]
                val new = if (byId) idOf(item)?.let { it !in ids } ?: true else i >= old.size
                val step = if (new) at.added(++added) else at.kept()
                value(step, item, native.items[i], old.getOrNull(i))
            }
        }

        private fun add(at: Step) {
            found += Difference(at.shown, at.pattern)
        }

        private fun instantInWindow(page: JsonValue, native: JsonValue): Boolean {
            val p = instant(page) ?: return false
            return instant(native) == t0 && p in t0..t1
        }

        private fun clockIdsAgree(page: JsonValue, native: JsonValue): Boolean {
            val p =
                CLOCK_ID.matchEntire((page as? JsonString)?.value ?: return false) ?: return false
            val n =
                CLOCK_ID.matchEntire((native as? JsonString)?.value ?: return false) ?: return false
            val (pp, pm, pt) = p.destructured
            val (np, nm, nt) = n.destructured
            return pp == np && pt == nt && nm.toLong() == t0 && pm.toLong() in t0..t1
        }
    }

    /** A place in the walk: its shown and allowlist names, and where it is in [OWNED]'s tree. */
    private inner class Step(
        val shown: String,
        val pattern: String,
        val tree: Tree?,
        /** Whether this is a top-level key (its members' names are shown only when fixed). */
        val top: Boolean,
    ) {
        /**
         * A member: inside a PARTIAL key's owned paths a `*` place is owner-chosen; a top-level
         * key's own members are shown only when they are the app's fixed words (attribute ids,
         * settings); deeper (a record's fields) any short schema identifier is shown. Everything
         * else is a salted hash.
         */
        fun member(name: String): Step {
            val exact = tree?.children?.get(name)
            val chosen =
                when {
                    tree?.children != null -> exact == null
                    top -> name !in ShadowKeys.FIXED_MEMBERS
                    else -> name !in ShadowKeys.FIXED_MEMBERS && !IDENTIFIER.matches(name)
                }
            val shownName = if (chosen) "*#" + redactor.hash(name) else name
            val patternName = if (chosen) ANY else name
            val child = exact ?: tree?.children?.get(ANY)
            return Step("$shown.$shownName", "$pattern.$patternName", child, false)
        }

        /** The [n]th record the action added. */
        fun added(n: Int) = Step("$shown[+$n]", "$pattern[+]", tree?.children?.get(ITEMS), false)

        /** A record that was there before the action. */
        fun kept() = Step("$shown[]", "$pattern[]", tree?.children?.get(ITEMS), false)

        fun length() = Step("$shown[#]", "$pattern[#]", null, false)

        fun instant(): Boolean = pattern in INSTANTS
    }

    private fun root(key: String, tree: Tree?) = Step(key, key, tree, true)

    /** The owned sub-paths of a PARTIAL key as a tree; a leaf owns everything under it. */
    class Tree(val children: Map<String, Tree>?)

    companion object {
        /** Most differences one comparison collects (the ring keeps 12 of them). */
        const val MAX_FOUND = 64

        private const val ANY = "*"
        private val UTC_CLOCK = EconomyClock(0, ZoneOffset.UTC)
        private const val ITEMS = "[]"

        /** N2: the paths where a value is the action's instant (`ShadowInstants`, plan 1.4). */
        val INSTANTS: Set<String> =
            setOf(
                "activities[+].date",
                "money.tx[+].date",
                "restBoughtAt",
                "shieldBoughtAt",
                "health.last",
                "health.since",
            )

        /** N1: a clock id, `prefix_<milliseconds>` with an optional `_<token>`. */
        private val CLOCK_ID = Regex("([A-Za-z]+)_(\\d{12,14})((?:_[0-9a-z]{4})?)")

        /** A schema word: a short identifier the app's code wrote, never free text. */
        private val IDENTIFIER = Regex("[A-Za-z][A-Za-z0-9]{0,30}")

        /** Every key compared: the tracked keys, then the Daily Hand's keys outside them. */
        val KEYS: List<String> = SaveFields.TRACKED + ShadowRunner.HAND_KEYS

        /** Keys compared whole although the economy owns only part of them (the Daily Hand's). */
        private val WHOLE = setOf("dailyDraw")

        /** [SaveFields.PARTIAL] as trees, by key ([WHOLE] keys excepted). */
        val OWNED: Map<String, Tree> =
            SaveFields.PARTIAL.filterKeys { it !in WHOLE }
                .mapValues { (_, paths) -> tree(paths.map { split(it) }) }

        private fun split(path: String): List<String> =
            path.replace("[].", "[]\u0000").split('.', '\u0000').filter { it.isNotEmpty() }

        private fun tree(paths: List<List<String>>): Tree {
            if (paths.any { it.isEmpty() }) return Tree(null)
            return Tree(
                paths.groupBy({ it.first() }, { it.drop(1) }).mapValues { (_, rest) -> tree(rest) }
            )
        }

        /** The owned part of [value] under [tree]; a value not shaped like the tree stays whole. */
        fun owned(value: JsonValue, tree: Tree?): JsonValue {
            val children = tree?.children ?: return value
            return when {
                value is JsonArray && ITEMS in children ->
                    JsonArray(value.items.map { owned(it, children.getValue(ITEMS)) })
                value is JsonObject -> {
                    val out = LinkedHashMap<String, JsonValue>()
                    for ((name, v) in value.members) {
                        val child = children[name] ?: children[ANY] ?: continue
                        out[name] = owned(v, child)
                    }
                    JsonObject(out)
                }
                else -> value
            }
        }

        /** A record's `id` when it is text. */
        private fun idOf(v: JsonValue): String? =
            ((v as? JsonObject)?.get("id") as? JsonString)?.value

        /** An instant as milliseconds: an ISO text the page's `Date` reads, or a number. */
        fun instant(value: JsonValue): Long? =
            when (value) {
                is JsonNumber ->
                    value.value.takeIf { it.isFinite() && it == Math.floor(it) }?.toLong()
                is JsonString -> UTC_CLOCK.parseInstant(value.value)
                JsonNull -> null
                else -> null
            }
    }
}
