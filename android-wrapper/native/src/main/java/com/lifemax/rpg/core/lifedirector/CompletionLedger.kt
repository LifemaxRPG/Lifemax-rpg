package com.lifemax.rpg.core.lifedirector

import com.lifemax.rpg.core.lifedirector.LedgerValues.add
import com.lifemax.rpg.core.lifedirector.LedgerValues.field
import com.lifemax.rpg.core.lifedirector.LedgerValues.includes
import com.lifemax.rpg.core.lifedirector.LedgerValues.isSet
import com.lifemax.rpg.core.lifedirector.LedgerValues.isText
import com.lifemax.rpg.core.lifedirector.LedgerValues.items
import com.lifemax.rpg.core.lifedirector.LedgerValues.lengthOf
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
import com.lifemax.rpg.core.save.JsonNull
import com.lifemax.rpg.core.save.JsonNumber
import com.lifemax.rpg.core.save.JsonObject
import com.lifemax.rpg.core.save.JsonString
import com.lifemax.rpg.core.save.JsonValue

/**
 * What [CompletionLedger.record] gave (LED-3 to LED-10): applied with the new [event], the [ledger]
 * after it, its [lifecycleState], a [residualTask] (or null) and whether a rest token was earned;
 * or refused with a [reason] and the [ledger] it was given, unchanged.
 */
class Recorded(
    val applied: Boolean,
    val eventId: String,
    val ledger: JsonValue?,
    val reason: String? = null,
    val event: JsonObject? = null,
    val lifecycleState: String? = null,
    val residualTask: JsonValue? = null,
    val tokenEarned: Boolean = false,
) {
    /** The page's result object. */
    fun json(): JsonObject = obj {
        this["applied"] = LedgerValues.bool(applied)
        if (reason != null) this["reason"] = str(reason)
        this["eventId"] = str(eventId)
        if (event != null) this["event"] = event
        this["ledger"] = ledger
        if (applied) {
            this["lifecycleState"] = str(lifecycleState!!)
            this["residualTask"] = residualTask ?: JsonNull
            this["tokenEarned"] = LedgerValues.bool(tokenEarned)
        }
    }
}

/**
 * The completion ledger (`engine.js:1904-1985`): one immutable event per completion, keyed by an id
 * that never changes for the same session (LED-2), so a completion recorded twice is refused, never
 * paid twice. The ledger is the stored JSON object; every function returns a new one.
 */
object CompletionLedger {
    const val SCHEMA_VERSION = 1.0

    /** LED-8: a rest token is earned every this many completions. */
    const val COMPLETIONS_PER_TOKEN = 5.0

    /** LED-8: the most rest tokens a player holds. */
    const val MAX_TOKENS = 3.0

    /** LED-5: the share of the planned time below which a completion is partial. */
    const val SATISFIED_RATIO = 0.75

    /** LED-11: event bodies are kept this many days once the ledger is long. */
    const val KEEP_DAYS = 400.0

    /** LED-11: the number of event ids from which the ledger is compacted. */
    const val COMPACT_FROM = 2000.0

    private const val DAY_SECONDS = 86400.0
    private const val MINUTE = 60.0
    private const val PERCENT = 100.0
    private const val XP_STEP = 0.1
    private const val LOWEST_RATING = 1.0
    private const val HIGHEST_RATING = 5.0
    private const val RESIDUAL_TIER = 3.0

    /** LED-4: the states a session can be completed from. */
    val COMPLETABLE = setOf("PLANNED", "IN_PROGRESS", "MISSED_UNEXPLAINED", "EXCUSED_BY_TOKEN")

    /** LED-1 `emptyLedger`: a new ledger for [playerId] (`String(playerId || 'player')`). */
    fun empty(playerId: JsonValue?): JsonObject = obj {
        this["schemaVersion"] = num(SCHEMA_VERSION)
        this["playerId"] = str(text(or(playerId, "player")))
        this["events"] = JsonObject.EMPTY
        this["order"] = JsonArray.EMPTY
        this["bounties"] = JsonObject.EMPTY
        this["penalties"] = JsonObject.EMPTY
        this["restTokens"] = num(1.0)
        this["completionsSinceToken"] = num(0.0)
        this["consecutiveMisses"] = num(0.0)
        this["consequencesByBwc"] = JsonObject.EMPTY
        this["regroupActive"] = LedgerValues.bool(false)
        this["streaks"] = JsonObject.EMPTY
    }

    /**
     * LED-2 `completionEventId`: with a truthy [instanceId], `stableId('ev', 'DONE:' + playerId +
     * ':' + instanceId)` plus `:rev<revision>` for a truthy [revision]; else with a truthy
     * [clientEventId], `stableId('ev', 'ADHOC:' + playerId + ':' + clientEventId)`; else the engine
     * refuses. Values join as JavaScript writes them (`String(v)`).
     */
    fun eventId(
        playerId: JsonValue?,
        instanceId: JsonValue?,
        clientEventId: JsonValue?,
        revision: JsonValue?,
    ): String {
        if (truthy(instanceId)) {
            val rev = if (truthy(revision)) ":rev" + text(revision) else ""
            return EngineHash.stableId(
                "ev",
                "DONE:" + text(playerId) + ":" + text(instanceId) + rev,
            )
        }
        if (!truthy(clientEventId)) throw EngineRefusal("Ad-hoc completions need a clientEventId")
        return EngineHash.stableId("ev", "ADHOC:" + text(playerId) + ":" + text(clientEventId))
    }

    /** LED-12 `ledgerHas`: the id has an event body, or was archived by [compact]. */
    fun has(ledger: JsonValue?, eventId: JsonValue?): Boolean =
        isSet(field(ledger, "events"), text(eventId)) ||
            includes(or(field(ledger, "archivedIds"), JsonArray.EMPTY), eventId)

    /**
     * LED-13 `revokeEvent`: marks a recorded event revoked (`{reason, atEpoch}`), once. The event
     * and its id stay; an unknown or already revoked id gives back the ledger as given, unrevoked.
     */
    fun revoke(
        ledgerIn: JsonValue?,
        eventId: JsonValue?,
        reason: JsonValue?,
        now: JsonValue?,
    ): Pair<JsonValue?, Boolean> {
        val key = text(eventId)
        var ledger = ledgerIn?.let(::clone)
        if (!isSet(field(ledger, "events"), key)) return ledgerIn to false
        val revoked = or(field(ledger, "revoked"), JsonObject.EMPTY)
        ledger = put(ledger, "revoked", revoked)
        if (isSet(revoked, key)) return ledgerIn to false
        val mark = obj {
            this["reason"] = or(reason, "HOST_UNDO")
            this["atEpoch"] = or(now, JsonNumber(0.0))
        }
        return put(ledger, "revoked", put(revoked, key, mark)) to true
    }

    /**
     * LED-11 `compactLedger`: once the order holds [COMPACT_FROM] ids or more, every event recorded
     * before `now - 400 days` loses its body and its place in the order, and its id moves to
     * `archivedIds` (which is created, empty, even when nothing moves).
     */
    fun compact(ledgerIn: JsonValue?, now: JsonValue?): JsonValue? {
        val cutoff = JsonNumber(number(now) - KEEP_DAYS * DAY_SECONDS)
        var ledger = ledgerIn
        val order = field(ledger, "order")
        if (less(lengthOf(order), JsonNumber(COMPACT_FROM))) return ledger
        var archived = or(field(ledger, "archivedIds"), JsonArray.EMPTY)
        ledger = put(ledger, "archivedIds", archived)
        val ids = items(order)
        var events = field(ledger, "events")
        val kept = ArrayList<JsonValue>()
        for (id in ids) {
            val key = text(id)
            val e = lookup(events, key)
            if (truthy(e) && less(field(e, "recordedAtEpoch"), cutoff)) {
                events = LedgerValues.remove(events as JsonObject, key)
                archived = JsonArray(items(archived) + id)
            } else kept += id
        }
        if (kept.size == ids.size) return put(ledger, "order", JsonArray(kept))
        return put(
            put(put(ledger, "events", events), "archivedIds", archived),
            "order",
            JsonArray(kept),
        )
    }

    /**
     * LED-3 to LED-10 `recordCompletion(ci)`: the completion [ci] (`ledger`, `playerId`, `instance`
     * or `adhoc` with `clientEventId`, `actualStartEpoch`, `actualDurationMinutes`, `nowEpoch`,
     * `satisfiedRatio`, `rpe`) recorded in a copy of its ledger (or a new one).
     */
    fun record(ci: JsonValue?): Recorded {
        val given = field(ci, "ledger")
        var ledger: JsonValue? = if (truthy(given)) clone(given!!) else empty(field(ci, "playerId"))
        val inst = field(ci, "instance").takeIf(::truthy)
        val playerId = field(ledger, "playerId")
        val eventId =
            eventId(
                playerId,
                inst?.let { field(it, "instanceId") },
                field(ci, "clientEventId"),
                inst?.let { field(it, "revision") },
            )
        if (isSet(field(ledger, "events"), eventId))
            return Recorded(false, eventId, given, reason = "DUPLICATE_EVENT_ID")
        if (inst != null) {
            val state = field(inst, "lifecycleState")
            if (!(state is JsonString && state.value in COMPLETABLE))
                return Recorded(false, eventId, given, reason = "NOT_COMPLETABLE_" + text(state))
        }
        val actualMin = maxOf(1.0, PageValues.round(number(field(ci, "actualDurationMinutes"))))
        val schedMin =
            if (inst != null)
                PageValues.round(
                    (number(field(inst, "endEpoch")) - number(field(inst, "startEpoch"))) / MINUTE
                )
            else actualMin
        val ratio = PageValues.clamp(actualMin / schedMin, 0.0, 1.0)
        val partial =
            inst != null &&
                ratio < number(or(field(ci, "satisfiedRatio"), JsonNumber(SATISFIED_RATIO)))
        val start =
            or(
                field(ci, "actualStartEpoch"),
                if (inst != null) field(inst, "startEpoch") else field(ci, "nowEpoch"),
            )
        val state =
            when {
                partial -> "PARTIALLY_COMPLETED"
                inst != null &&
                    PageValues.lessOrEqual(
                        add(start, actualMin * MINUTE),
                        field(inst, "startEpoch"),
                    ) -> "COMPLETED_EARLY"
                inst != null && PageValues.atLeast(start, field(inst, "endEpoch")) ->
                    "COMPLETED_LATE"
                else -> "COMPLETED_ON_TIME"
            }
        val src = inst ?: field(ci, "adhoc").takeIf(::truthy) ?: JsonObject.EMPTY
        val event =
            event(ci, inst, src, eventId, playerId, start, actualMin, schedMin, ratio, state)
        ledger = put(ledger, "events", put(field(ledger, "events"), eventId, event))
        ledger = put(ledger, "order", JsonArray(items(field(ledger, "order")) + str(eventId)))
        var sinceToken = number(field(ledger, "completionsSinceToken")) + 1
        ledger = put(ledger, "completionsSinceToken", num(sinceToken))
        var tokenEarned = false
        if (sinceToken >= COMPLETIONS_PER_TOKEN) {
            sinceToken = 0.0
            ledger = put(ledger, "completionsSinceToken", num(sinceToken))
            val tokens = field(ledger, "restTokens")
            if (less(tokens, JsonNumber(MAX_TOKENS))) {
                ledger = put(ledger, "restTokens", num(number(tokens) + 1))
                tokenEarned = true
            }
        }
        ledger = put(ledger, "consecutiveMisses", num(0.0))
        ledger = put(ledger, "regroupActive", LedgerValues.bool(false))
        val spacingKey = event["spacingKey"]
        if (truthy(spacingKey) && !partial) {
            val streaks = field(ledger, "streaks")
            val key = text(spacingKey)
            val next = add(or(lookup(streaks, key), JsonNumber(0.0)), 1.0)
            ledger = put(ledger, "streaks", put(streaks, key, next))
        }
        if (inst != null && isText(field(inst, "lifecycleState"), "EXCUSED_BY_TOKEN")) {
            val refunded = minOf(MAX_TOKENS, number(add(field(ledger, "restTokens"), 1.0)))
            ledger = put(ledger, "restTokens", num(refunded))
        }
        val residual = if (partial) residual(ci, inst!!, schedMin, actualMin) else null
        ledger = compact(ledger, field(ci, "nowEpoch"))
        return Recorded(true, eventId, ledger, null, event, state, residual, tokenEarned)
    }

    /** LED-7: the event's members. */
    private fun event(
        ci: JsonValue?,
        inst: JsonValue?,
        src: JsonValue,
        eventId: String,
        playerId: JsonValue?,
        start: JsonValue?,
        actualMin: Double,
        schedMin: Double,
        ratio: Double,
        state: String,
    ): JsonObject {
        val tag = field(src, "tag")
        val profile by lazy { profile(tag) }
        val ip = or(field(src, "IP"), JsonNumber(profile.physical))
        val ic = or(field(src, "IC"), JsonNumber(profile.cognitive))
        val most = maxOf(number(ip), number(ic))
        val rpe = field(ci, "rpe")
        val rating = PageValues.round(if (truthy(rpe)) number(rpe) else most)
        return obj {
            this["eventId"] = str(eventId)
            this["playerId"] = playerId
            this["instanceId"] = if (inst != null) field(inst, "instanceId") else JsonNull
            this["clientEventId"] = if (inst != null) JsonNull else field(ci, "clientEventId")
            this["activityTypeTag"] = or(tag, "general")
            this["templateId"] = or(field(src, "templateId"), null) ?: JsonNull
            this["spacingKey"] = or(field(src, "spacingKey"), null) ?: JsonNull
            this["title"] = or(field(src, "title"), "Activity")
            this["linkedGoalIds"] = goalIds(field(src, "linkedGoalIds"))
            this["linkedQuestRequirementIds"] = questLinks(field(src, "linkedQuestRequirementIds"))
            this["actualStartEpoch"] = start
            this["actualDurationMinutes"] = num(actualMin)
            this["scheduledDurationMinutes"] = num(schedMin)
            this["completionRatio"] = num(PageValues.round(ratio * PERCENT) / PERCENT)
            this["lifecycleState"] = str(state)
            this["intensityRating"] = num(PageValues.clamp(rating, LOWEST_RATING, HIGHEST_RATING))
            this["recordedAtEpoch"] = field(ci, "nowEpoch")
            this["source"] = str(if (inst != null) "SCHEDULE" else "ADHOC")
            this["suggestedEffort"] = obj {
                this["minutes"] = num(actualMin)
                this["IP"] = ip
                this["IC"] = ic
                this["xpHint"] = num(PageValues.round(actualMin * (1 + XP_STEP * (most - 1))))
            }
        }
    }

    /**
     * LED-7 `tagProfile(src.tag)`: the tag's root's default demand. A root named like an
     * `Object.prototype` member gives the page a function, not a profile (LG03); native refuses it.
     */
    private fun profile(tag: JsonValue?): TagProfile {
        val root = (if (truthy(tag)) text(tag) else "general").substringBefore('.')
        if (root in LedgerValues.INHERITED) throw LedgerRefusal("the tag root '$root'")
        return ActivityTags.profile(tag)
    }

    /** `(ids || []).slice()`: a list copied, a text kept, nothing else has `slice`. */
    private fun goalIds(v: JsonValue?): JsonValue =
        when {
            !truthy(v) -> JsonArray.EMPTY
            v is JsonArray || v is JsonString -> v!!
            else -> throw PageTypeError("slice is not a function")
        }

    /** `(links || []).map(x => ({ questId: x.questId, reqId: x.reqId }))`. */
    private fun questLinks(v: JsonValue?): JsonValue {
        if (!truthy(v)) return JsonArray.EMPTY
        return JsonArray(
            items(v).map { x ->
                obj {
                    this["questId"] = field(x, "questId")
                    this["reqId"] = field(x, "reqId")
                }
            }
        )
    }

    /**
     * LED-10: the rest of a partial session as a new task, when the session carries its task
     * (`source`) and is tier 3 or more or counts for a quest: the task's members, then a new id
     * (`stableId('t', 'residual:' + instanceId)`), `<title> (finish)`, the missing minutes rounded
     * up to slots (at least 15) as every duration, `RESIDUAL`, a window from now, and its parent.
     */
    private fun residual(
        ci: JsonValue?,
        inst: JsonValue,
        schedMin: Double,
        actualMin: Double,
    ): JsonValue? {
        val source = field(inst, "source")
        if (!truthy(source)) return null
        val due =
            PageValues.atLeast(field(inst, "tier"), JsonNumber(RESIDUAL_TIER)) ||
                truthy(lengthOf(field(inst, "linkedQuestRequirementIds")))
        if (!due) return null
        val rem = num(QuarterHours.roundUp(schedMin - actualMin))
        val out = LedgerValues.ownMembers(source)
        val set = { k: String, v: JsonValue? -> if (v == null) out.remove(k) else out[k] = v }
        set("id", str(EngineHash.stableId("t", "residual:" + text(field(inst, "instanceId")))))
        set("title", add(field(inst, "title"), JsonString(" (finish)")))
        set("dOpt", rem)
        set("dMin", rem)
        set("dMax", rem)
        set("tierReason", str("RESIDUAL"))
        set("windowStart", field(ci, "nowEpoch"))
        set("parentInstanceId", field(inst, "instanceId"))
        return JsonObject(out)
    }
}
