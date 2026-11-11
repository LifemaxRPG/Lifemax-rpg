package com.lifemax.rpg.core.lifedirector.hostadapters

import com.lifemax.rpg.core.economy.EconomyClock
import com.lifemax.rpg.core.economy.EconomyContext
import com.lifemax.rpg.core.economy.LifeDirector
import com.lifemax.rpg.core.economy.Storage
import com.lifemax.rpg.core.lifedirector.hostadapters.AdapterGolden.Case
import com.lifemax.rpg.core.lifedirector.hostadapters.AdapterGolden.jsNumber
import com.lifemax.rpg.core.lifedirector.hostadapters.AdapterGolden.jsString
import com.lifemax.rpg.core.lifedirector.hostadapters.AdapterGolden.text
import com.lifemax.rpg.core.lifedirector.hostadapters.AdapterGolden.truthyText
import com.lifemax.rpg.core.save.EconomySaveCodec
import com.lifemax.rpg.core.save.JsonArray
import com.lifemax.rpg.core.save.JsonBool
import com.lifemax.rpg.core.save.JsonNull
import com.lifemax.rpg.core.save.JsonObject
import com.lifemax.rpg.core.save.JsonString
import com.lifemax.rpg.core.save.JsonValue
import com.lifemax.rpg.core.save.JsonWriter
import com.lifemax.rpg.core.save.PageReads
import com.lifemax.rpg.core.save.SaveDecoding
import com.lifemax.rpg.core.save.SaveEncoding
import com.lifemax.rpg.core.save.SaveReading
import com.lifemax.rpg.core.save.sameStoredValue
import java.time.Instant
import java.time.ZoneId

/**
 * Runs one golden case through [EconomyHost]: the save decoded by the production codec, each call
 * mapped onto [HostPort] with its arguments read as the page reads them, the answers written as the
 * page's JSON, and the economy the calls leave encoded back and compared field by field.
 */
object AdapterReplay {
    /** A deliberate change to the run, for the mutation checks; none by default. */
    class Mutation(
        val port: (HostPort) -> HostPort = { it },
        val context: (EconomyContext) -> EconomyContext = { it },
    )

    /** Why a case could not be run natively: a refusal of the strict readers (DV-A1). */
    const val REFUSED = "refused"

    /**
     * The first difference between the page's answer and native's, null when there is none; a case
     * native refuses gives `<id>: refused (...)`.
     */
    fun difference(c: Case, mutation: Mutation = Mutation()): String? {
        val before = c.storedBefore(AdapterGolden.base)
        val decoded =
            when (val d = EconomySaveCodec.decode(before, SaveReading.AS_PAGE)) {
                is SaveDecoding.Decoded -> d
                is SaveDecoding.Refused -> return "${c.id}: $REFUSED (save ${d.refusal.reason})"
            }
        val zone = ZoneId.of(text(c.input["zone"]))
        var now = Instant.parse(text(c.input["now"])).toEpochMilli()
        var storage = Storage.Writable
        val ids = AdapterGolden.CountedIds()
        val plans = AdapterGolden.RecordedPlanDays(c.outside("planDay"), c.outside("dayDone"))
        val meals = AdapterGolden.RecordedMealDays(c.outside("sdDay"))
        val context = { mutation.context(EconomyContext(EconomyClock(now, zone), ids, storage)) }
        val host =
            when (
                val h =
                    EconomyHost.of(
                        decoded.document.stored,
                        decoded.save,
                        context,
                        AdapterGolden.library,
                        plans,
                        meals,
                    )
            ) {
                is HostAnswer.Answer -> h.value
                is HostAnswer.Refused -> return "${c.id}: $REFUSED (${h.reason} ${h.detail})"
            }
        val port = mutation.port(host)
        for ((i, call) in c.calls.withIndex()) {
            (call["at"] as? JsonString)?.let { now = Instant.parse(it.value).toEpochMilli() }
            if (call["at"] == null) now = Instant.parse(text(c.input["now"])).toEpochMilli()
            storage =
                when ((call["env"] as? JsonObject)?.get("failSave")) {
                    JsonString("storage") -> Storage.Full
                    JsonString("readonly") -> Storage.ReadOnly
                    else -> Storage.Writable
                }
            val fn = text(call["fn"])
            val args = (call["args"] as JsonArray).items
            val got =
                when (val r = run(port, fn, args)) {
                    is HostAnswer.Answer -> r.value
                    is HostAnswer.Refused ->
                        when (r.reason) {
                            HostRefusal.UNKNOWN_ZONE,
                            HostRefusal.INVALID_TIME -> AdapterGolden.threw("RangeError")
                            HostRefusal.NO_BOARD -> AdapterGolden.threw("TypeError")
                            HostRefusal.UNREADABLE_QUEST ->
                                return "${c.id}: $REFUSED (${r.reason} ${r.detail})"
                        }
                }
            val want = c.returns[i]
            if (!same(got, want))
                return "${c.id}: call $i $fn gave ${show(got)}, the page ${show(want)}"
        }
        val unknown = plans.unknown + meals.unknown
        if (unknown.isNotEmpty()) return "${c.id}: asked what the page did not: $unknown"
        val expected = c.storedAfter(before)
        val after =
            when (val e = EconomySaveCodec.encode(decoded.document, host.save)) {
                is SaveEncoding.Encoded -> e.document.stored
                is SaveEncoding.Refused -> return "${c.id}: encoding refused ${e.refusal}"
            }
        for (key in AdapterGolden.FIELDS) {
            val a = after[key]
            val b = expected[key]
            if ((a == null) != (b == null) || (a != null && !same(a, b!!)))
                return "${c.id}: save field $key is ${a?.let(::show)}, the page's ${b?.let(::show)}"
        }
        if (host.toasts != c.toasts) return "${c.id}: toasts ${host.toasts}, the page's ${c.toasts}"
        return null
    }

    private fun same(a: JsonValue, b: JsonValue): Boolean = a.sameStoredValue(b)

    fun show(v: JsonValue): String = JsonWriter.write(v).take(SHOWN)

    private const val SHOWN = 600

    private fun answer(v: JsonValue): HostAnswer<JsonValue> = HostAnswer.Answer(v)

    private fun <T> HostAnswer<T>.json(write: (T) -> JsonValue): HostAnswer<JsonValue> =
        when (this) {
            is HostAnswer.Answer -> HostAnswer.Answer(write(value))
            is HostAnswer.Refused -> this
        }

    private fun quest(v: JsonValue?): HostAnswer<BoardQuest?> =
        when (val r = BoardQuests.read(JsonArray(listOf(v ?: JsonNull)))) {
            is HostAnswer.Answer -> HostAnswer.Answer(r.value!!.entries[0])
            is HostAnswer.Refused -> r
        }

    private fun bool(b: Boolean): JsonValue = JsonBool.of(b)

    private fun str(s: String): JsonValue = JsonString(s)

    /** Runs the page function [fn] natively with the page's arguments. */
    fun run(port: HostPort, fn: String, a: List<JsonValue>): HostAnswer<JsonValue> {
        fun arg(i: Int): JsonValue? = a.getOrNull(i)
        return when (fn) {
            "LifeMaxHost.typeForTag" -> answer(str(port.typeForTag(jsString(arg(0)))))
            "LifeMaxHost.tagForType" -> answer(str(port.tagForType(jsString(arg(0)))))
            "LifeMaxHost._test.parseCount" ->
                answer(num(HostTexts.parseCount(truthyText(arg(0)).orEmpty())))
            "LifeMaxHost._test.parseMinutes" ->
                answer(num(HostTexts.parseMinutes(truthyText(arg(0)).orEmpty())))
            "LifeMaxHost._test.weekBounds" ->
                QuestReading.weekBounds(jsNumber(arg(0)), jsString(arg(1))).json { it.json() }
            "LifeMaxHost.mapQuestTag" -> quest(arg(0)).json { str(HostTexts.mapQuestTag(it!!)) }
            "LifeMaxHost.isMilestone" -> quest(arg(0)).json { bool(HostTexts.isMilestone(it)) }
            "LifeMaxHost.isProjectMilestone" ->
                quest(arg(0)).json { bool(HostTexts.isProjectMilestone(it)) }
            "LifeMaxHost.isUntimedQuest" ->
                quest(arg(0)).json { bool(HostTexts.isUntimedQuest(it)) }
            "LifeMaxHost.playerId" -> answer(str(port.playerId()))
            "LifeMaxHost.connected" -> answer(bool(port.connected))
            "LifeMaxHost.xpFor" ->
                answer(
                    port
                        .xpFor(
                            jsString(arg(0)),
                            jsNumber(arg(1)),
                            jsNumber(arg(2)),
                            truthyText(arg(3)),
                            PageReads.truthy(arg(4)),
                        )
                        .json()
                )
            "LifeMaxHost.awardActivity" -> {
                val e = arg(0) as JsonObject
                val info = arg(1)
                val event =
                    AwardEvent(
                        eventId = jsString(e["eventId"]),
                        activityTypeTag = jsString(e["activityTypeTag"]),
                        actualStartEpoch = jsNumber(e["actualStartEpoch"]),
                        actualDurationMinutes = jsNumber(e["actualDurationMinutes"]),
                        title = jsString(e["title"]),
                        lifecycleState = jsString(e["lifecycleState"]),
                    )
                val title =
                    if (PageReads.truthy(info)) truthyText((info as JsonObject)["title"]) else null
                answer(port.awardActivity(event, title).json())
            }
            "LifeMaxHost.completeQuest" -> {
                val r = arg(0) as? JsonObject
                val ref =
                    r?.let {
                        LifeDirector.QuestRef(
                            truthyText(it["questId"]),
                            (it["key"] as? JsonString)?.value,
                            PageReads.truthy(it["future"]),
                        )
                    }
                answer(port.completeQuest(ref).json())
            }
            "LifeMaxHost.readQuests" ->
                port
                    .readQuests(
                        jsNumber(arg(0)),
                        jsString(arg(1)),
                        overlay(arg(2)),
                        revoked(arg(3)),
                    )
                    .json { list -> JsonArray(list.map { it.json() }) }
            "LifeMaxHost.readPlanSessions" ->
                port.readPlanSessions(jsNumber(arg(0)), jsString(arg(1)), jsNumber(arg(2))).json {
                    list ->
                    JsonArray(list.map { it.json() })
                }
            "LifeMaxHost.isPlanDayDone" -> answer(bool(port.isPlanDayDone(jsString(arg(0)))))
            "LifeMaxHost.milestones" ->
                port.milestones().json { list -> JsonArray(list.map { it.json() }) }
            "LifeMaxHost.retiredUntimedQuest" -> {
                val r = arg(0) as? JsonObject
                val link =
                    r?.let {
                        QuestLink(
                            truthyText(it["questId"]).orEmpty(),
                            (it["questTitle"] as? JsonString)?.value,
                        )
                    }
                port.retiredUntimedQuest(link).json { it?.json() ?: JsonNull }
            }
            "LifeMaxHost.activitiesSince" ->
                answer(JsonArray(port.activitiesSince(truthyText(arg(0))).map { it.json() }))
            "LifeMaxHost.activityExists" -> answer(bool(port.activityExists(jsString(arg(0)))))
            "LifeMaxHost.activityDescription" -> {
                val s = arg(0) as? JsonObject
                val session =
                    s?.let {
                        DescribedSession(
                            (it["title"] as? JsonString)?.value,
                            ((it["linkedQuestRequirementIds"] as? JsonArray)?.items.orEmpty())
                                .map { l ->
                                    val o = l as JsonObject
                                    QuestLink(
                                        truthyText(o["questId"]).orEmpty(),
                                        (o["questTitle"] as? JsonString)?.value,
                                    )
                                },
                        )
                    }
                port.activityDescription(session).json(::str)
            }
            "LifeMaxMeals.resolveScheduled" ->
                answer(port.scheduledMeal(mealRef(arg(0)))?.json() ?: JsonNull)
            "mealFor" -> {
                val b = arg(0) as JsonObject
                val block =
                    MealBlock(
                        (b["title"] as? JsonString)?.value,
                        jsNumber(b["startEpoch"]),
                        b["mealStartEpoch"]?.let(::jsNumber),
                    )
                port.mealOfBlock(block, jsString(arg(1))).json { it?.json() ?: JsonNull }
            }
            else -> error("no adapter for $fn")
        }
    }

    private fun overlay(v: JsonValue?): QuestOverlay {
        val o = v as? JsonObject ?: return QuestOverlay()
        val m = LinkedHashMap<String, List<String>>()
        for ((k, e) in o.members) {
            if (!PageReads.truthy(e)) continue
            val ids = (e as JsonObject)["eventIds"]
            if (!PageReads.truthy(ids)) continue
            m[k] = (ids as JsonArray).items.map(::jsString)
        }
        return QuestOverlay(m)
    }

    private fun revoked(v: JsonValue?): Set<String> =
        (v as? JsonObject)?.members?.filterValues { PageReads.truthy(it) }?.keys.orEmpty()

    private fun mealRef(v: JsonValue?): MealRef? {
        val r = v as? JsonObject ?: return null
        return MealRef(
            title = truthyText(r["title"]),
            slot = truthyText(r["slot"]),
            day = truthyText(r["day"]),
            startEpoch = jsNumber(r["startEpoch"]),
        )
    }
}
