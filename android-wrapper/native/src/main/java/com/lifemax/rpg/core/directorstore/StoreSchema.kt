package com.lifemax.rpg.core.directorstore

import com.lifemax.rpg.core.save.JsonArray
import com.lifemax.rpg.core.save.JsonBool
import com.lifemax.rpg.core.save.JsonNull
import com.lifemax.rpg.core.save.JsonNumber
import com.lifemax.rpg.core.save.JsonObject
import com.lifemax.rpg.core.save.JsonString
import com.lifemax.rpg.core.save.JsonValue

/**
 * What logic.js passes to the store's `load` and `clear` (`{ playerId, timezone, nowEpoch }`). Each
 * is held as a JSON value because the page only tests it for truthiness (`o.playerId || 'player'`);
 * a missing or falsy one takes the page's default.
 */
data class StoreOptions(
    val playerId: JsonValue? = null,
    val timezone: JsonValue? = null,
    val nowEpoch: JsonValue? = null,
) {
    companion object {
        /** The options as the page's host gives them: a player id, a zone name, epoch seconds. */
        fun of(playerId: String, timezone: String, nowEpoch: Double): StoreOptions =
            StoreOptions(JsonString(playerId), JsonString(timezone), JsonNumber(nowEpoch))
    }
}

/** What `migrate` gave: the value, or the exception the page's `migrate` throws. */
sealed interface Migration {
    /** The migrated value; JavaScript's `undefined` is null. */
    data class Done(val value: JsonValue?) : Migration

    /** The page's `migrate` throws ([name] and [message] as V8 gives them). */
    data class Threw(val name: String, val message: String) : Migration
}

/**
 * The store's schema functions, exactly as the page has them (`engine.js:2248-2281`;
 * LIFE_DIRECTOR_STORE_SPEC.md LST-10 to LST-15): `defaultState`, `validate` and `migrate`.
 */
object StoreSchema {
    /** `defaultState(o)`: a new player's state, with the page's member order (LST-15). */
    fun defaultState(options: StoreOptions?): JsonObject {
        val o = options ?: StoreOptions()
        val now = o.nowEpoch.takeIf(JsValues::truthy) ?: zero()
        return obj(
            "schemaVersion" to JsonNumber(StoreKeys.SCHEMA_VERSION),
            "playerId" to (o.playerId.takeIf(JsValues::truthy) ?: JsonString(DEFAULT_PLAYER)),
            "createdEpoch" to now,
            "updatedEpoch" to now,
            "setupComplete" to JsonBool.FALSE,
            "availability" to
                obj(
                    "timezone" to (o.timezone.takeIf(JsValues::truthy) ?: JsonString(DEFAULT_ZONE)),
                    "sleepRequirementMinutes" to JsonNumber(SLEEP_MINUTES),
                    "rules" to JsonArray(ArrayList()),
                    "blackouts" to JsonArray(ArrayList()),
                ),
            "fixedCommitments" to JsonArray(ArrayList()),
            "goals" to JsonArray(ArrayList()),
            "prefs" to JsonObject(LinkedHashMap()),
            "schedule" to JsonNull,
            "ledger" to JsonNull,
            "changeLog" to JsonArray(ArrayList()),
            "energyCheckin" to JsonNull,
            "questOverlay" to JsonObject(LinkedHashMap()),
            "labQuests" to JsonArray(ArrayList()),
            "bridge" to
                obj("enabled" to JsonBool.FALSE, "source" to JsonNull, "mapping" to JsonNull),
            "lastGeneratedWeekKey" to JsonNull,
            "ui" to obj("tab" to JsonString(DEFAULT_TAB)),
        )
    }

    /**
     * `validate(s)`: null when the page accepts the state, else its reason (LST-10). The checks, in
     * order: an object or array; `schemaVersion` the number 1; `availability` truthy with an array
     * `rules`; an array `goals`.
     */
    fun validate(state: JsonValue?): String? {
        if (state !is JsonObject && state !is JsonArray) return "not an object"
        val version = JsValues.member(state, "schemaVersion")
        if (!JsValues.isNumber(version, StoreKeys.SCHEMA_VERSION))
            return "wrong schemaVersion " + JsValues.text(version)
        val availability = JsValues.member(state, "availability")
        if (!JsValues.truthy(availability) || JsValues.member(availability, "rules") !is JsonArray)
            return "missing availability"
        if (JsValues.member(state, "goals") !is JsonArray) return "missing goals"
        return null
    }

    /**
     * `migrate(raw)` (LST-11 to LST-14): a value that is not an object or array comes back as it
     * is, and so does one whose `schemaVersion` is neither missing, `null` nor 0; the pre-release
     * prototype shape (`{sleep, work, goals}`) becomes a new state built from it.
     */
    fun migrate(raw: JsonValue?): Migration {
        if (raw !is JsonObject && raw !is JsonArray) return Migration.Done(raw)
        val version = JsValues.member(raw, "schemaVersion")
        if (!JsValues.isNullish(version) && !JsValues.isNumber(version, 0.0))
            return Migration.Done(raw)
        return fromPrototype(raw)
    }

    private fun fromPrototype(s: JsonValue): Migration {
        val base =
            defaultState(
                StoreOptions(JsValues.member(s, "playerId"), JsValues.member(s, "timezone"))
            )
        val members = base.members as LinkedHashMap<String, JsonValue>
        val availability = members.getValue("availability") as JsonObject
        val rules = (availability.members.getValue("rules") as JsonArray).items as ArrayList
        members["goals"] = JsValues.member(s, "goals") as? JsonArray ?: JsonArray(ArrayList())
        members["prefs"] =
            listOf(JsValues.member(s, "prefs"), JsValues.member(s, "settings"))
                .firstOrNull(JsValues::truthy) ?: JsonObject(LinkedHashMap())
        val sleep = JsValues.member(s, "sleep")
        if (JsValues.truthy(sleep) && !JsValues.isNullish(JsValues.member(sleep, "bed")))
            rules += sleepRule(sleep!!)
        val work = JsValues.member(s, "work").takeIf(JsValues::truthy) ?: JsonArray(ArrayList())
        if (work !is JsonArray) return Migration.Threw(TYPE_ERROR, WORK_NOT_A_LIST)
        work.items.forEachIndexed { i, w ->
            val rule = linkedMapOf<String, JsonValue>("id" to JsonString("work$i"), "kind" to WORK)
            assign(rule, w)
            rules += JsonObject(rule)
        }
        members["setupComplete"] = JsonBool.of(rules.isNotEmpty())
        members["migratedFrom"] = zero()
        return Migration.Done(base)
    }

    /**
     * The prototype's sleep as a rule; its length `((wake - bed + 1440) % 1440) || 480` (LST-12).
     */
    private fun sleepRule(sleep: JsonValue): JsonObject {
        val bed = JsValues.member(sleep, "bed")!!
        val wake = JsValues.member(sleep, "wake")
        val length = (JsValues.number(wake) - JsValues.number(bed) + DAY_MINUTES) % DAY_MINUTES
        return obj(
            "id" to JsonString("sleep"),
            "kind" to JsonString("SLEEP"),
            "title" to JsonString("Sleep"),
            "days" to JsonArray(WEEK.map { JsonNumber(it.toDouble()) }),
            "startMin" to bed,
            "durationMin" to
                JsonNumber(if (JsValues.truthy(JsonNumber(length))) length else SLEEP_MINUTES),
        )
    }

    /**
     * `Object.assign(target, source)` for a JSON source (LST-13): a string's UTF-16 units and an
     * array's items under their index keys, an object's members in JavaScript's key order, nothing
     * for a number, boolean or `null`. A `__proto__` member is not copied: assigning it sets the
     * target's prototype instead of a member.
     */
    private fun assign(target: LinkedHashMap<String, JsonValue>, source: JsonValue) {
        when (source) {
            is JsonString -> source.value.forEachIndexed { i, c -> target["$i"] = JsonString("$c") }
            is JsonArray -> source.items.forEachIndexed { i, item -> target["$i"] = item }
            is JsonObject ->
                for ((key, value) in PageJson.ordered(source)) if (key != PROTO) target[key] = value
            else -> Unit
        }
    }

    private fun obj(vararg members: Pair<String, JsonValue>): JsonObject =
        JsonObject(linkedMapOf(*members))

    private fun zero(): JsonNumber = JsonNumber(0.0)

    private const val DEFAULT_PLAYER = "player"
    private const val DEFAULT_ZONE = "UTC"
    private const val DEFAULT_TAB = "today"
    private const val SLEEP_MINUTES = 480.0
    private const val DAY_MINUTES = 1440.0
    private const val PROTO = "__proto__"
    private const val TYPE_ERROR = "TypeError"
    private const val WORK_NOT_A_LIST =
        "(s.work || []).entries is not a function or its return value is not iterable"
    private val WORK = JsonString("WORK")
    private val WEEK = 0..6
}
