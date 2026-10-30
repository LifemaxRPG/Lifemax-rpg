package com.lifemax.rpg.feature.plan.data

import com.lifemax.rpg.feature.plan.domain.DayStatus
import com.lifemax.rpg.feature.plan.domain.PlanDay
import com.lifemax.rpg.feature.plan.domain.PlanItem
import com.lifemax.rpg.feature.plan.domain.PlanKeyTag
import com.lifemax.rpg.feature.plan.domain.PlanLauncher
import com.lifemax.rpg.feature.plan.domain.PlanLog
import com.lifemax.rpg.feature.plan.domain.PlanProjection
import com.lifemax.rpg.feature.plan.domain.PlanRepository
import com.lifemax.rpg.feature.plan.domain.PlanRule
import com.lifemax.rpg.feature.plan.domain.PlanValue
import com.lifemax.rpg.feature.plan.domain.PlanWeek
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

/**
 * Decodes the page's Workout plan projection (50-native-plan.js). Malformed data is rejected and
 * the last good state kept; an update older than the one held is ignored; a projection from another
 * page session is taken only on an explicit open.
 */
class WebPlanRepository : PlanRepository {
    private val mutable = MutableStateFlow<PlanProjection?>(null)
    override val state = mutable.asStateFlow()

    fun accept(j: JSONObject, establishSession: Boolean = false) {
        require(j.getInt("protocol") == 1) { "Unsupported plan protocol" }
        val session = j.getString("webSessionId")
        val sequence = j.getLong("sequence")
        require(session.isNotBlank() && sequence > 0)
        val old = mutable.value
        if (old != null && old.session != session && !establishSession) return
        if (old?.session == session && sequence <= old.sequence) return

        val weeks = j.getJSONArray("weeks").objects().map(::week)
        require(weeks.size == 8) { "Expected the eight weeks" }
        val dates = weeks.flatMap { it.days }.mapNotNull { it?.date }
        require(dates.size == dates.toSet().size) { "A date appears twice" }
        val sessions = j.getJSONObject("sessions")
        val selected = j.getJSONObject("selected")
        val rules = j.getJSONObject("rules")
        val colors = j.getJSONObject("tokens")
        val projection =
            PlanProjection(
                session,
                sequence,
                j.getString("program"),
                j.getString("status"),
                j.getString("summary"),
                sessions.getInt("done"),
                sessions.getInt("total"),
                weeks,
                selected.getString("date"),
                selected.getLong("revision"),
                j.optJSONObject("session")?.let(::launcher),
                j.getJSONArray("key").objects().map {
                    PlanKeyTag(it.getString("icon"), it.getString("label"))
                },
                rules.getJSONArray("items").objects().map(::rule),
                rules.getJSONArray("progression").objects().map(::rule),
                j.getBoolean("reducedMotion"),
                colors.keys().asSequence().associateWith { colors.getLong(it) },
                j.getBoolean("light"),
            )
        require(projection.sessionsDone in 0..projection.sessionsTotal)
        require(projection.day(projection.selectedDate) != null) {
            "The selected day is not a plan day"
        }
        mutable.value = projection
    }

    private fun week(w: JSONObject): PlanWeek {
        val days =
            w.getJSONArray("days").let { a ->
                (0 until a.length()).map { if (a.isNull(it)) null else day(a.getJSONObject(it)) }
            }
        require(days.size == 7) { "A week has seven days" }
        return PlanWeek(
            w.getString("number"),
            w.getString("label"),
            w.getString("range"),
            w.getString("meta"),
            w.getInt("done"),
            w.getInt("total"),
            w.getBoolean("complete"),
            w.getBoolean("current"),
            days,
        )
    }

    private fun day(d: JSONObject): PlanDay {
        val items = d.getJSONArray("items").objects().map(::item)
        require(items.size == items.map { it.key }.toSet().size) { "An exercise appears twice" }
        return PlanDay(
            d.getString("date"),
            d.getString("weekday"),
            d.getString("dayOfMonth"),
            d.getString("kind"),
            d.getString("kindLabel"),
            d.getString("glyph"),
            d.getString("icon"),
            DayStatus.valueOf(d.getString("status").uppercase()),
            d.getBoolean("today"),
            d.getString("title"),
            d.getString("description"),
            d.getString("tag"),
            d.getString("note"),
            d.getBoolean("locked"),
            items,
            launcher(d.getJSONObject("guided")),
        )
    }

    private fun item(it: JSONObject): PlanItem {
        val sets = it.getJSONArray("sets").let { a -> (0 until a.length()).map(a::getBoolean) }
        require(sets.isNotEmpty()) { "An exercise without sets" }
        val log = it.getJSONObject("log")
        return PlanItem(
            it.getString("key"),
            it.getString("title"),
            it.getString("detail"),
            it.getBoolean("optional"),
            sets,
            it.optJSONObject("value")?.let { v ->
                PlanValue(v.getString("text"), v.getString("placeholder"), v.getString("last"))
            },
            PlanLog(log.getString("text"), log.getString("previous")),
        )
    }

    private fun launcher(l: JSONObject) =
        PlanLauncher(
            l.getString("title"),
            l.getString("detail"),
            l.getString("button"),
            l.getBoolean("enabled"),
        )

    private fun rule(r: JSONObject) = PlanRule(r.getString("lead"), r.getString("text"))
}

internal fun JSONArray.objects() = (0 until length()).map { getJSONObject(it) }
