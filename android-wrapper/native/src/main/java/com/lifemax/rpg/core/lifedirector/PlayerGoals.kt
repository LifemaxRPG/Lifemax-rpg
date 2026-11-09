package com.lifemax.rpg.core.lifedirector

import com.lifemax.rpg.core.lifedirector.PageValues.clamp
import com.lifemax.rpg.core.lifedirector.PageValues.index
import com.lifemax.rpg.core.lifedirector.PageValues.member
import com.lifemax.rpg.core.lifedirector.PageValues.number
import com.lifemax.rpg.core.lifedirector.PageValues.or
import com.lifemax.rpg.core.lifedirector.PageValues.orZero
import com.lifemax.rpg.core.lifedirector.PageValues.present
import com.lifemax.rpg.core.lifedirector.PageValues.round
import com.lifemax.rpg.core.lifedirector.PageValues.text
import com.lifemax.rpg.core.lifedirector.PageValues.truthy
import com.lifemax.rpg.core.save.JsonArray
import com.lifemax.rpg.core.save.JsonBool
import com.lifemax.rpg.core.save.JsonNull
import com.lifemax.rpg.core.save.JsonNumber
import com.lifemax.rpg.core.save.JsonObject
import com.lifemax.rpg.core.save.JsonString
import com.lifemax.rpg.core.save.JsonValue

/**
 * LDD-4: a goal as the engine reads it. Members the page keeps as stored are [JsonValue]s:
 * [archetypeId], [target] (the stored target, a target built from the legacy members, or null),
 * [createdEpoch] and [status]; the rest are read into their kinds.
 */
class PlayerGoal(
    val id: String,
    val title: String,
    val archetypeId: JsonValue,
    val subject: String?,
    val priority: Double,
    val level: String,
    val customTemplates: List<JsonValue>,
    val disabledTemplates: List<JsonValue>,
    val target: JsonValue,
    val createdEpoch: JsonValue,
    val status: JsonValue,
) {
    /** Whether the plan makes sessions for it (status exactly `ACTIVE`). */
    val active: Boolean
        get() = status == JsonString(ACTIVE)

    companion object {
        const val ACTIVE = "ACTIVE"
    }
}

/** Goals and their templates: LDD-4 to LDD-7. */
object PlayerGoals {
    private const val NOVICE = "NOVICE"
    private val LEVELS = setOf(NOVICE, "INTERMEDIATE", "ADVANCED")
    private const val CUSTOM = "CUSTOM"
    private const val LANGUAGE = "language."
    private const val SUGGESTIONS = 3
    private const val DEFAULT_PRIORITY = 3.0
    private const val LOWEST_PRIORITY = 1.0
    private const val HIGHEST_PRIORITY = 5.0
    private const val DEFAULT_MINUTES = 30.0
    private const val DEFAULT_PER_WEEK = 3.0
    private const val FEWEST_PER_WEEK = 1.0
    private const val MOST_PER_WEEK = 14.0
    private const val DAILY_PER_WEEK = 7.0
    private const val DAILY_SPACING_HOURS = 8.0
    private const val SPACING_HOURS = 16.0
    private const val WEEK_HOURS = 168.0
    private const val SHORTEST_IDEAL_HOURS = 12.0
    private const val SPREAD_MINUTES = 15.0
    private const val LOWEST_INTENSITY = 1.0
    private const val HIGHEST_INTENSITY = 5.0

    /**
     * LDD-4 `normalizeGoal`: [raw] (a stored goal; `null` throws `TypeError`, any other value that
     * is not an object has no members) in the engine's form.
     */
    fun of(raw: JsonValue?): PlayerGoal {
        if (!present(raw)) throw PageTypeError("Cannot read properties of null")
        fun m(key: String) = member(raw, key)
        val domain = m("domainArchetype")
        val levelValue = or(m("level"), m("experienceLevel"))
        val level = (levelValue as? JsonString)?.value?.takeIf { it in LEVELS } ?: NOVICE
        return PlayerGoal(
            id = text(or(m("id"), m("goalId"))),
            title = text(or(m("title"), "Goal")),
            archetypeId =
                or(
                    m("archetypeId"),
                    if (truthy(domain) && domain != JsonString(CUSTOM)) domain else null,
                ) ?: JsonNull,
            subject = m("subject")?.takeIf(::truthy)?.let { subject(text(it)) },
            priority =
                clamp(
                    round(number(or(m("priority"), JsonNumber(DEFAULT_PRIORITY)))),
                    LOWEST_PRIORITY,
                    HIGHEST_PRIORITY,
                ),
            level = level,
            customTemplates = (m("customTemplates") as? JsonArray)?.items ?: emptyList(),
            disabledTemplates = (m("disabledTemplates") as? JsonArray)?.items ?: emptyList(),
            target = or(m("target"), legacyTarget(raw)) ?: JsonNull,
            createdEpoch = or(or(m("createdEpoch"), m("startEpoch")), null) ?: JsonNull,
            status = or(or(m("status"), m("schedulingState")), PlayerGoal.ACTIVE),
        )
    }

    /** The target the legacy members describe, when `targetMetricValue` is truthy. */
    private fun legacyTarget(raw: JsonValue?): JsonValue? {
        val units = member(raw, "targetMetricValue")
        if (!truthy(units)) return null
        val out = linkedMapOf<String, JsonValue>("units" to units!!)
        out["completed"] = orZero(member(raw, "currentMetricValue"))
        member(raw, "metricUnit")?.let { out["unit"] = it }
        member(raw, "deadlineEpoch")?.let { out["deadlineEpoch"] = it }
        out["unitsPerHour"] = or(member(raw, "unitsPerHour"), null) ?: JsonNull
        return JsonObject(out)
    }

    /** A language subject: lower case, then only `a-z`, `0-9` and `_` kept. */
    private fun subject(text: String): String =
        text.lowercase().filter { it in 'a'..'z' || it in '0'..'9' || it == '_' }

    /**
     * LDD-5 `suggestTemplates`: up to three archetype ids for a goal [title], those whose most
     * keywords occur in the lower-cased title, then by id.
     */
    fun suggestTemplates(title: JsonValue?): List<String> {
        val t = text(or(title, "")).lowercase()
        return DemandTables.archetypes.entries
            .map { (id, a) -> id to a.keywords.count { t.contains(it) } }
            .filter { it.second > 0 }
            .sortedWith(compareByDescending<Pair<String, Int>> { it.second }.thenBy { it.first })
            .take(SUGGESTIONS)
            .map { it.first }
    }

    /** LDD-6, LDD-7 `goalTemplates`: the archetype's templates, then the custom ones. */
    fun templates(goal: PlayerGoal): List<ActivityTemplate> {
        val out = ArrayList<ActivityTemplate>()
        val archetype =
            if (truthy(goal.archetypeId)) DemandTables.archetypes[text(goal.archetypeId)] else null
        for (t in archetype?.templates.orEmpty()) {
            if (goal.disabledTemplates.any { PageValues.same(it, JsonString(t.key)) }) continue
            val tag = (t.tag as JsonString).value
            val subject = goal.subject
            val ownTag =
                if (!subject.isNullOrEmpty() && tag.startsWith(LANGUAGE))
                    tag.replaceFirst(LANGUAGE, "$LANGUAGE$subject.")
                else tag
            val id = goal.id + ":" + t.key
            out +=
                t.with(
                    "templateId" to JsonString(id),
                    "goalId" to JsonString(goal.id),
                    "tag" to JsonString(ownTag),
                    "spacingKey" to JsonString(id),
                )
        }
        for (c in goal.customTemplates) custom(goal, c)?.let { out += it }
        return out
    }

    /** LDD-7: a custom template, or null when it has no tag, or neither minutes nor durations. */
    private fun custom(goal: PlayerGoal, c: JsonValue): ActivityTemplate? {
        fun m(key: String) = member(c, key)
        val durations = m("d")
        if (!truthy(c) || !truthy(m("tag")) || !truthy(or(m("minutes"), durations))) return null
        val middle = if (truthy(durations)) index(durations, 1) else durations
        val dOpt =
            QuarterHours.roundUp(number(or(or(m("minutes"), middle), JsonNumber(DEFAULT_MINUTES))))
        val frequencies = m("f")
        val middleFrequency = if (truthy(frequencies)) index(frequencies, 1) else frequencies
        val freq =
            clamp(
                round(number(or(or(m("perWeek"), middleFrequency), JsonNumber(DEFAULT_PER_WEEK)))),
                FEWEST_PER_WEEK,
                MOST_PER_WEEK,
            )
        val key = text(or(m("key"), m("tag"))).filter(::keyCharacter)
        val tag = m("tag")!!
        val profile = ActivityTags.profile(tag)
        val id = goal.id + ":" + key
        val out = linkedMapOf<String, JsonValue>()
        out["key"] = JsonString(key)
        out["title"] = or(m("title"), JsonString(goal.title))!!
        out["tag"] = tag
        out["templateId"] = JsonString(id)
        out["goalId"] = JsonString(goal.id)
        out["spacingKey"] = JsonString(id)
        out["d"] =
            or(
                durations,
                numbers(
                    maxOf(QuarterHours.MINIMUM_MINUTES, dOpt - SPREAD_MINUTES),
                    dOpt,
                    dOpt + SPREAD_MINUTES,
                ),
            )!!
        out["f"] =
            or(
                frequencies,
                numbers(maxOf(FEWEST_PER_WEEK, freq - 1), freq, minOf(MOST_PER_WEEK, freq + 1)),
            )!!
        out["IP"] =
            clamp(or(m("IP"), JsonNumber(profile.physical))!!, LOWEST_INTENSITY, HIGHEST_INTENSITY)
        out["IC"] =
            clamp(or(m("IC"), JsonNumber(profile.cognitive))!!, LOWEST_INTENSITY, HIGHEST_INTENSITY)
        out["spacingMinH"] =
            m("spacingMinH")?.takeIf(::present)
                ?: JsonNumber(if (freq >= DAILY_PER_WEEK) DAILY_SPACING_HOURS else SPACING_HOURS)
        out["spacingIdealH"] =
            or(
                m("spacingIdealH"),
                JsonNumber(maxOf(SHORTEST_IDEAL_HOURS, round(WEEK_HOURS / freq))),
            )!!
        out["maxPerBwc"] = or(m("maxPerBwc"), JsonNumber(1.0))!!
        out["phases"] = or(m("phases"), JsonArray(listOf(JsonString("ANY"))))!!
        out["volumeDriver"] = JsonBool.of(truthy(m("volumeDriver")))
        return ActivityTemplate(out)
    }

    private fun keyCharacter(c: Char): Boolean =
        c in 'a'..'z' || c in 'A'..'Z' || c in '0'..'9' || c == '_' || c == '.' || c == '-'

    private fun numbers(vararg values: Double) = JsonArray(values.map(::JsonNumber))
}
