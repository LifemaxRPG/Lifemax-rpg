package com.lifemax.rpg.feature.lifedirector.data

import com.lifemax.rpg.core.data.objects
import com.lifemax.rpg.feature.lifedirector.domain.GuideSpan
import com.lifemax.rpg.feature.lifedirector.domain.MealChip
import com.lifemax.rpg.feature.lifedirector.domain.MealChipKind
import com.lifemax.rpg.feature.lifedirector.domain.MealSheetModel
import com.lifemax.rpg.feature.lifedirector.domain.TimerSheetModel
import org.json.JSONObject

/*
 * The timer and meal sheets of the projection (phase 4A), as strict as the session sheet: a missing
 * or wrong-kind field, an unknown chip, or times that cannot belong to one timer throw, and the
 * projection is refused. Numbers must be numbers (a quoted time would be read as one by getLong).
 */

/** The page's focusModel: a running or paused timer of one session. */
internal fun timerSheet(s: JSONObject): TimerSheetModel {
    val paused = s.getBoolean("paused")
    val pausedAt = if (s.isNull("pausedAt")) null else s.ms("pausedAt")
    // the page's label and button follow its paused flag, and a paused timer says since when
    require(paused == (pausedAt != null)) { "A timer paused without its time, or the reverse" }
    val t =
        TimerSheetModel(
            id = s.text("id").also { require(it.isNotEmpty()) { "A timer without its session" } },
            title = s.text("title"),
            label = s.text("label"),
            paused = paused,
            startedAt = s.ms("startedAt"),
            pausedMs = s.ms("pausedMs"),
            pausedAt = pausedAt,
            plannedMs =
                s.ms("plannedMs").also { require(it > 0) { "A timer planned for nothing" } },
            planned = s.text("planned"),
            xp =
                if (s.isNull("xp")) null
                else
                    s.getJSONArray("xp").objects().map {
                        GuideSpan(it.text("t"), it.getBoolean("b"))
                    },
            finish = s.text("finish"),
            pause = s.text("pause"),
            stop = s.text("stop"),
            note = s.text("note"),
        )
    require(t.startedAt > 0 && t.pausedMs >= 0 && (pausedAt == null || pausedAt >= t.startedAt)) {
        "Timer times out of order"
    }
    return t
}

/** The page's mealModel: a planned meal, named by its block's first minute. */
internal fun mealSheet(s: JSONObject): MealSheetModel {
    val chips =
        s.getJSONArray("chips").objects().map {
            MealChip(
                when (val k = it.text("kind")) {
                    "meal" -> MealChipKind.MEAL
                    "kcal" -> MealChipKind.KCAL
                    "state" -> MealChipKind.STATE
                    else -> throw IllegalArgumentException("Unknown meal chip $k")
                },
                it.text("text"),
            )
        }
    // the meal's own name comes first, always; the recipe's calories and state only with a recipe
    require(chips.firstOrNull()?.kind == MealChipKind.MEAL) { "A meal sheet without its meal" }
    return MealSheetModel(
        start = s.ms("start").also { require(it > 0) { "A meal at no time" } },
        title = s.text("title"),
        `when` = s.text("when"),
        chips = chips,
        open = s.text("open"),
    )
}

/** A whole number of the page's: epoch milliseconds or seconds, or a length in milliseconds. */
private fun JSONObject.ms(key: String): Long {
    val n = get(key) as? Number ?: throw IllegalArgumentException("$key is not a number")
    val d = n.toDouble()
    require(d.isFinite() && d == Math.rint(d)) { "$key is not a whole number" }
    return n.toLong()
}

private fun JSONObject.text(key: String): String =
    get(key) as? String ?: throw IllegalArgumentException("$key is not text")
