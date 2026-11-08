package com.lifemax.rpg.core.shadow

import com.lifemax.rpg.core.save.JsonArray
import com.lifemax.rpg.core.save.JsonObject
import com.lifemax.rpg.core.save.JsonValue
import com.lifemax.rpg.core.save.sameStoredValue

/**
 * Names the save fields a delta changes, for the touch map (ECONOMY_SHADOW_PLAN WP6a): a top-level
 * key, one level of its members (`attrXP.physique`, `skillXP.*#3f09aa`), and for record keys the
 * position relative to the action (`activities[+1]`, the first new record; `activities[]`, an
 * existing record, with the member it changed: `activities[].xp`; `activities[-]`, records
 * removed). Names pass through [ShadowRedactor]: no value and no owner-chosen key is ever shown.
 */
class TouchPaths(private val redactor: ShadowRedactor) {
    /** The paths [delta] changes when laid over [current], sorted, without duplicates. */
    fun of(delta: ShadowDelta, current: Map<String, JsonValue>): List<String> {
        val out = sortedSetOf<String>()
        for ((key, value) in delta.set) {
            val old = current[key]
            when {
                old == null -> out += key
                old.sameStoredValue(value) -> Unit
                old is JsonObject && value is JsonObject ->
                    members(key, old, value, owned(key), out)
                else -> out += key
            }
        }
        for (key in delta.del) if (key in current) out += key
        for ((key, rec) in delta.rec) records(key, current[key], rec, out)
        return out.toList()
    }

    private fun records(
        key: String,
        current: JsonValue?,
        rec: RecordDelta,
        out: MutableSet<String>,
    ) {
        val old = (current as? JsonArray)?.items
        if (old == null) {
            out += key
            return
        }
        if (rec.length < old.size) out += "$key[-]"
        for (change in rec.changes) {
            if (change.index >= old.size) {
                out += "$key[+${change.index - old.size + 1}]"
                continue
            }
            val was = old[change.index]
            val now = change.value
            when {
                was.sameStoredValue(now) -> Unit
                was is JsonObject && now is JsonObject -> members("$key[]", was, now, false, out)
                else -> out += "$key[]"
            }
        }
    }

    private fun members(
        at: String,
        old: JsonObject,
        new: JsonObject,
        ownerKeyed: Boolean,
        out: MutableSet<String>,
    ) {
        val names = LinkedHashSet(old.members.keys).apply { addAll(new.members.keys) }
        for (name in names) {
            val a = old[name]
            val b = new[name]
            if (a != null && b != null && a.sameStoredValue(b)) continue
            out += "$at.${name(name, ownerKeyed)}"
        }
    }

    private fun name(member: String, ownerKeyed: Boolean): String =
        when {
            member in ShadowKeys.FIXED_MEMBERS -> member
            !ownerKeyed && IDENTIFIER.matches(member) -> member
            else -> redactor.member(member)
        }

    private fun owned(key: String) = key in OWNER_KEYED

    companion object {
        /**
         * Top-level objects whose member names the owner or the calendar chose (skills, quest keys,
         * days, custom activity types): their members are always hashed unless fixed.
         */
        val OWNER_KEYED: Set<String> =
            setOf(
                "skillXP",
                "questDone",
                "actXP",
                "customRules",
                "dayReflections",
                "restDays",
                "nutriDone",
                "careDay",
                "shieldDays",
                "journalCoach",
                "plan",
                "classContent",
                "dhMig",
                "waterLog",
            )

        /** A schema word: a short identifier the app's code wrote, never free text. */
        private val IDENTIFIER = Regex("[A-Za-z][A-Za-z0-9]{0,30}")

        /** At most [ShadowLimits.MAX_PATHS_PER_ENTRY] paths, then `+N more`. */
        fun capped(paths: List<String>): List<String> =
            if (paths.size <= ShadowLimits.MAX_PATHS_PER_ENTRY) paths
            else
                paths.take(ShadowLimits.MAX_PATHS_PER_ENTRY) +
                    "+${paths.size - ShadowLimits.MAX_PATHS_PER_ENTRY} more"
    }
}
