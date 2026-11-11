package com.lifemax.rpg.core.lifedirector.hostadapters

import com.lifemax.rpg.core.economy.LifeDirector
import com.lifemax.rpg.core.economy.Rule
import com.lifemax.rpg.core.save.JsonArray
import com.lifemax.rpg.core.save.JsonBool
import com.lifemax.rpg.core.save.JsonNull
import com.lifemax.rpg.core.save.JsonNumber
import com.lifemax.rpg.core.save.JsonObject
import com.lifemax.rpg.core.save.JsonString
import com.lifemax.rpg.core.save.JsonValue

/** An answer of the host, or why there is none. */
sealed interface HostAnswer<out T> {
    data class Answer<out T>(val value: T) : HostAnswer<T>

    /** Where the page throws; [detail] names the value without repeating save content. */
    data class Refused(val reason: HostRefusal, val detail: String) : HostAnswer<Nothing>
}

/** Why the host has no answer. */
enum class HostRefusal {
    /** The page throws a `RangeError`: the zone is not one `Intl` knows (LDT-3). */
    UNKNOWN_ZONE,

    /** The page throws a `RangeError`: the instant is not a time (`Invalid time value`). */
    INVALID_TIME,

    /** The page throws a `TypeError`: the save's quest list is missing (HA-19, LA03). */
    NO_BOARD,

    /** Native refuses (DV-A1): a quest field holds a kind of value the page itself never writes. */
    UNREADABLE_QUEST,
}

/** The answer, or [fallback] when refused. */
fun <T> HostAnswer<T>.orElse(fallback: T): T =
    when (this) {
        is HostAnswer.Answer -> value
        is HostAnswer.Refused -> fallback
    }

/** A quest's reference back to the board, as `hostRef` (engine.js:2471-2482). */
data class QuestHostRef(
    val questId: String,
    val key: String,
    val cadence: String,
    /** Null on next week's copy, which has [future] instead. */
    val done: Boolean?,
    val future: Boolean = false,
)

/** A quest as Life Director plans it (HA-16; the engine's `normalizeQuest` input). */
data class HostQuest(
    val id: String,
    val status: String,
    val requiredCount: Double,
    val progressCount: Double? = null,
    val daily: Boolean = false,
    val onlyDays: List<Int>? = null,
    val windowStartEpoch: Double? = null,
    val deadlineEpoch: Double? = null,
    val hostRef: QuestHostRef? = null,
    val title: String?,
    val tier: String,
    val tag: String,
    val schedulingMode: String,
    val sessionMinutes: Double,
    val bountyXp: Double,
) {
    /** The page's object, members in its order. */
    fun json(): JsonObject {
        val m = LinkedHashMap<String, JsonValue>()
        m["id"] = JsonString(id)
        m["status"] = JsonString(status)
        if (daily) {
            m["recurrence"] = JsonString("DAILY")
            m["onlyDays"] =
                onlyDays?.let { d -> JsonArray(d.map { num(it.toDouble()) }) } ?: JsonNull
        }
        m["requiredCount"] = num(requiredCount)
        progressCount?.let { m["progressCount"] = num(it) }
        windowStartEpoch?.let { m["windowStartEpoch"] = num(it) }
        deadlineEpoch?.let { m["deadlineEpoch"] = num(it) }
        hostRef?.let { m["hostRef"] = it.json() }
        title?.let { m["title"] = JsonString(it) }
        m["tier"] = JsonString(tier)
        m["tag"] = JsonString(tag)
        m["schedulingMode"] = JsonString(schedulingMode)
        m["sessionMinutes"] = num(sessionMinutes)
        m["minSessionMinutes"] = num(sessionMinutes)
        m["bounty"] = JsonObject(linkedMapOf("xp" to num(bountyXp)))
        m["source"] = JsonString("lifemax")
        return JsonObject(m)
    }
}

private fun QuestHostRef.json(): JsonObject {
    val m =
        linkedMapOf<String, JsonValue>("questId" to JsonString(questId), "key" to JsonString(key))
    m["cadence"] = JsonString(cadence)
    done?.let { m["done"] = JsonBool.of(it) }
    if (future) m["future"] = JsonBool.TRUE
    return JsonObject(m)
}

/** A workout plan day as a date-bound session to place (HA-17). */
data class PlanSession(
    val key: String,
    val title: String,
    val tag: String,
    val done: Boolean,
    val sessionMinutes: Double,
    val windowStartEpoch: Double,
    val deadlineEpoch: Double,
) {
    fun json(): JsonObject =
        JsonObject(
            linkedMapOf(
                "id" to JsonString("plan:$key"),
                "title" to JsonString(title),
                "tier" to JsonString("CORE"),
                "tag" to JsonString(tag),
                "status" to JsonString(if (done) "COMPLETED" else "ACTIVE"),
                "requiredCount" to num(1.0),
                "progressCount" to num(if (done) 1.0 else 0.0),
                "sessionMinutes" to num(sessionMinutes),
                "exclusive" to JsonBool.TRUE,
                "windowStartEpoch" to num(windowStartEpoch),
                "deadlineEpoch" to num(deadlineEpoch),
                "hostRef" to
                    JsonObject(linkedMapOf("plan" to JsonString(key), "done" to JsonBool.of(done))),
                "source" to JsonString("lifemax-plan"),
            )
        )
}

/** A milestone of the board (HA-18): never planned, shown with its state. */
data class Milestone(
    val id: String?,
    val title: String?,
    val desc: String,
    val done: Boolean,
    val locked: Boolean,
) {
    fun json(): JsonObject {
        val m = LinkedHashMap<String, JsonValue>()
        id?.let { m["id"] = JsonString(it) }
        title?.let { m["title"] = JsonString(it) }
        m["desc"] = JsonString(desc)
        m["done"] = JsonBool.of(done)
        m["locked"] = JsonBool.of(locked)
        return JsonObject(m)
    }
}

/** A retired Daily Hand card recovered from its template (HA-19). */
data class RetiredQuest(val id: String, val title: String) {
    fun json(): JsonObject =
        JsonObject(
            linkedMapOf(
                "id" to JsonString(id),
                "title" to JsonString(title),
                "schedulingMode" to JsonString("NONE"),
                "anchor" to JsonString("WAKE"),
            )
        )
}

/** An activity logged outside Life Director (HA-20). */
data class HostActivity(
    val id: String,
    val type: String,
    val name: String?,
    val minutes: Double,
    val iso: String?,
    val tag: String,
    val planDate: String?,
) {
    fun json(): JsonObject {
        val m = LinkedHashMap<String, JsonValue>()
        m["id"] = JsonString(id)
        m["type"] = JsonString(type)
        name?.let { m["name"] = JsonString(it) }
        m["minutes"] = num(minutes)
        iso?.let { m["iso"] = JsonString(it) }
        m["tag"] = JsonString(tag)
        m["planDate"] = planDate?.let(::JsonString) ?: JsonNull
        return JsonObject(m)
    }
}

/** A Smart Diet meal found for a meal block (HA-22). */
data class ScheduledMeal(
    val day: String,
    val index: Int,
    val slot: String,
    val name: String?,
    val kcal: Double,
    val logged: Boolean,
    val skipped: Boolean,
) {
    fun json(): JsonObject {
        val m = LinkedHashMap<String, JsonValue>()
        m["day"] = JsonString(day)
        m["index"] = num(index.toDouble())
        m["slot"] = JsonString(slot)
        name?.let { m["name"] = JsonString(it) }
        m["kcal"] = num(kcal)
        m["logged"] = JsonBool.of(logged)
        m["skipped"] = JsonBool.of(skipped)
        return JsonObject(m)
    }
}

/** HA-9 the bounds of the plan zone's week (Monday to Monday). */
data class WeekBounds(val start: Double, val end: Double, val nextEnd: Double, val monKey: String) {
    fun json(): JsonObject =
        JsonObject(
            linkedMapOf(
                "start" to num(start),
                "end" to num(end),
                "nextEnd" to num(nextEnd),
                "monKey" to JsonString(monKey),
            )
        )
}

/** The page's `xpFor` answer: `{xp, rule, split}`. */
fun LifeDirector.SessionXp.json(): JsonObject =
    JsonObject(
        linkedMapOf(
            "xp" to num(xp),
            "rule" to rule.json(),
            "split" to
                JsonArray(
                    split.map {
                        JsonObject(linkedMapOf("attr" to JsonString(it.attr), "xp" to num(it.xp)))
                    }
                ),
        )
    )

/** A rule as the page's `rules` table holds it. */
fun Rule.json(): JsonObject =
    JsonObject(
        linkedMapOf(
            "name" to JsonString(name),
            "icon" to JsonString(icon),
            "base" to num(base),
            "skill" to JsonString(skill),
            "primary" to JsonString(primary),
            "secondary" to JsonString(secondary),
            "desc" to JsonString(desc),
        )
    )

/** The page's `awardActivity` answer. */
fun LifeDirector.AwardResult.json(): JsonObject {
    val m = linkedMapOf<String, JsonValue>("awarded" to JsonBool.of(awarded))
    reason?.let { m["reason"] = JsonString(it) }
    activityId?.let { m["activityId"] = JsonString(it) }
    xp?.let { m["xp"] = num(it) }
    return JsonObject(m)
}

/** The page's `completeQuest` answer. */
fun LifeDirector.BountyResult.json(): JsonObject {
    val m = linkedMapOf<String, JsonValue>("completed" to JsonBool.of(completed))
    reason?.let { m["reason"] = JsonString(it) }
    xp?.let { m["xp"] = num(it) }
    name?.let { m["name"] = JsonString(it) }
    return JsonObject(m)
}

internal fun num(value: Double): JsonValue = JsonNumber(value)
