package com.lifemax.rpg.core.lifedirector

import com.lifemax.rpg.core.lifedirector.LedgerValues.add
import com.lifemax.rpg.core.lifedirector.LedgerValues.field
import com.lifemax.rpg.core.lifedirector.LedgerValues.isSet
import com.lifemax.rpg.core.lifedirector.LedgerValues.isText
import com.lifemax.rpg.core.lifedirector.LedgerValues.iterate
import com.lifemax.rpg.core.lifedirector.LedgerValues.lookup
import com.lifemax.rpg.core.lifedirector.LedgerValues.num
import com.lifemax.rpg.core.lifedirector.LedgerValues.put
import com.lifemax.rpg.core.lifedirector.LedgerValues.str
import com.lifemax.rpg.core.lifedirector.PageValues.less
import com.lifemax.rpg.core.lifedirector.PageValues.number
import com.lifemax.rpg.core.lifedirector.PageValues.or
import com.lifemax.rpg.core.lifedirector.PageValues.text
import com.lifemax.rpg.core.lifedirector.PageValues.truthy
import com.lifemax.rpg.core.lifedirector.ScheduleJson.clone
import com.lifemax.rpg.core.lifedirector.ScheduleJson.obj
import com.lifemax.rpg.core.save.JsonArray
import com.lifemax.rpg.core.save.JsonBool
import com.lifemax.rpg.core.save.JsonNull
import com.lifemax.rpg.core.save.JsonNumber
import com.lifemax.rpg.core.save.JsonObject
import com.lifemax.rpg.core.save.JsonString
import com.lifemax.rpg.core.save.JsonValue

/**
 * What [MissConsequences.apply] gave: the [ledger] after it (the one given when the miss was
 * already processed), the penalty's id, the session's new [state], the [consequence] (or null),
 * whether the regroup prompt opens now, and a [note] saying why there was no consequence.
 */
class MissOutcome(
    val ledger: JsonValue?,
    val penaltyId: String,
    val state: String,
    val consequence: JsonValue?,
    val regroupPrompt: Boolean,
    val note: String?,
) {
    fun json(): JsonObject = obj {
        this["ledger"] = ledger
        this["penaltyId"] = str(penaltyId)
        this["state"] = str(state)
        this["consequence"] = consequence ?: JsonNull
        this["regroupPrompt"] = JsonBool.of(regroupPrompt)
        this["note"] = note?.let(::str) ?: JsonNull
    }
}

/**
 * Misses, their consequences and the lifecycle (`engine.js:2047-2084`): LED-17 to LED-20. A
 * consequence is bounded (one per session for ever, one per waking day, none while regrouping, a
 * rest token first) and never touches XP: the host applies an HP drain, if at all.
 */
object MissConsequences {
    /** LED-17: the default grace after a session's end, in minutes. */
    const val GRACE_MINUTES = 60.0

    /** LED-18: misses in a row that open the regroup prompt. */
    const val REGROUP_AFTER = 3.0

    /** LED-19: tiers at or below this are above the minimum and never penalised. */
    const val FREE_TIER = 2.0

    /** LED-19: the HP drain of the hardcore mode. */
    const val HP_DRAIN = 10.0

    private const val MINUTE = 60.0

    /** LED-20: the transitions the engine allows; anything else is refused. */
    val TRANSITIONS: Map<String, List<String>> =
        mapOf(
            "PLANNED" to
                listOf(
                    "IN_PROGRESS",
                    "COMPLETED_EARLY",
                    "COMPLETED_ON_TIME",
                    "COMPLETED_LATE",
                    "PARTIALLY_COMPLETED",
                    "SKIPPED_VOLUNTARY",
                    "MISSED_UNEXPLAINED",
                    "EXCUSED_DISPLACED",
                    "EXCUSED_BY_TOKEN",
                    "CANCELLED_BY_QUEST",
                ),
            "IN_PROGRESS" to
                listOf(
                    "COMPLETED_ON_TIME",
                    "COMPLETED_LATE",
                    "COMPLETED_EARLY",
                    "PARTIALLY_COMPLETED",
                    "EXCUSED_DISPLACED",
                ),
            "MISSED_UNEXPLAINED" to
                listOf("COMPLETED_LATE", "EXCUSED_BY_TOKEN", "PARTIALLY_COMPLETED"),
            "EXCUSED_BY_TOKEN" to listOf("COMPLETED_LATE", "PARTIALLY_COMPLETED"),
        )

    /**
     * LED-20 `canTransition`: whether [to] (the same text) is allowed from [from] (read as text). A
     * [from] named like an `Object.prototype` member finds a function or an object, which has no
     * `includes`: the page throws `TypeError` (LG04), and so does native.
     */
    fun canTransition(from: JsonValue?, to: JsonValue?): Boolean {
        val key = text(from)
        if (key in LedgerValues.INHERITED) throw PageTypeError("includes is not a function")
        return to is JsonString && TRANSITIONS[key].orEmpty().contains(to.value)
    }

    /**
     * LED-17 `detectMisses`: the sessions still `PLANNED` whose end plus the grace (`graceMin ||
     * 60` minutes) is before [now], in their order.
     */
    fun detect(instances: JsonValue?, now: JsonValue?, graceMin: JsonValue?): List<JsonValue> {
        val grace = number(or(graceMin, JsonNumber(GRACE_MINUTES))) * MINUTE
        val list = if (truthy(instances)) instances else JsonArray.EMPTY
        if (list is JsonString) throw PageTypeError("filter is not a function")
        return iterate(list).filter {
            isText(field(it, "lifecycleState"), "PLANNED") &&
                less(add(field(it, "endEpoch"), grace), now)
        }
    }

    /**
     * LED-18, LED-19 `applyMissConsequence`: the consequence of a missed ([kind] other than `SKIP`)
     * or skipped session [inst], recorded once under `stableId('pen', 'MISS_PENALTY:' +
     * instanceId)`.
     */
    fun apply(
        ledgerIn: JsonValue?,
        inst: JsonValue?,
        prefs: JsonValue?,
        now: JsonValue?,
        kind: JsonValue?,
    ): MissOutcome {
        var ledger = ledgerIn?.let(::clone)
        val mode = (PlanPreferences.of(prefs)["consequenceMode"] as JsonString).value
        val instanceId = field(inst, "instanceId")
        val penaltyId = EngineHash.stableId("pen", "MISS_PENALTY:" + text(instanceId))
        val skip = isText(kind, "SKIP")
        var state = if (skip) "SKIPPED_VOLUNTARY" else "MISSED_UNEXPLAINED"
        if (isSet(field(ledger, "penalties"), penaltyId))
            return MissOutcome(ledgerIn, penaltyId, state, null, false, "ALREADY_PROCESSED")
        var prompt = false
        if (!skip) {
            val misses = number(field(ledger, "consecutiveMisses")) + 1
            ledger = put(ledger, "consecutiveMisses", num(misses))
            if (misses >= REGROUP_AFTER && !truthy(field(ledger, "regroupActive"))) {
                ledger = put(ledger, "regroupActive", JsonBool.TRUE)
                prompt = true
            }
        }
        fun record(c: JsonValue?) {
            val entry = obj {
                this["instanceId"] = instanceId
                this["atEpoch"] = now
                this["consequence"] = c ?: JsonNull
            }
            ledger = put(ledger, "penalties", put(field(ledger, "penalties"), penaltyId, entry))
        }
        fun none(note: String): MissOutcome {
            record(null)
            return MissOutcome(ledger, penaltyId, state, null, prompt, note)
        }
        if (mode == "LENIENT") return none("LENIENT_MODE")
        if (PageValues.lessOrEqual(field(inst, "tier"), JsonNumber(FREE_TIER)))
            return none("ABOVE_MINIMUM_NO_PENALTY")
        if (truthy(field(ledger, "regroupActive")) && !prompt)
            return none("TACTICAL_REGROUP_PAUSED")
        val day = text(field(inst, "bwcId"))
        if (isSet(field(ledger, "consequencesByBwc"), day)) return none("ONE_PER_DAY_LIMIT")
        ledger =
            put(
                ledger,
                "consequencesByBwc",
                put(field(ledger, "consequencesByBwc"), day, str(penaltyId)),
            )
        val tokens = field(ledger, "restTokens")
        if (PageValues.order(tokens, JsonNumber(0.0)) == 1) {
            val left = number(tokens) - 1
            ledger = put(ledger, "restTokens", num(left))
            if (!skip) state = "EXCUSED_BY_TOKEN"
            val c = obj {
                this["type"] = str("REST_TOKEN_SPENT")
                this["tokensLeft"] = num(left)
            }
            record(c)
            return MissOutcome(ledger, penaltyId, state, c, prompt, null)
        }
        val spacingKey = field(inst, "spacingKey")
        if (truthy(spacingKey)) {
            val streaks = field(ledger, "streaks")
            val key = text(spacingKey)
            val decayed = maxOf(0.0, number(or(lookup(streaks, key), JsonNumber(0.0))) - 1)
            ledger = put(ledger, "streaks", put(streaks, key, num(decayed)))
        }
        val c =
            if (mode == "HARDCORE_RPG")
                obj {
                    this["type"] = str("HP_DRAIN")
                    this["amount"] = num(HP_DRAIN)
                    this["floorHp"] = num(1.0)
                    this["hostApplied"] = JsonBool.FALSE
                }
            else
                obj {
                    this["type"] = str("STREAK_DECAY")
                    this["key"] = spacingKey
                }
        record(c)
        return MissOutcome(ledger, penaltyId, state, c, prompt, null)
    }
}
