package com.lifemax.rpg.feature.plan.data

import com.lifemax.rpg.feature.plan.domain.Celebration
import com.lifemax.rpg.feature.plan.domain.PlanPageAction
import com.lifemax.rpg.feature.plan.domain.PlanReply
import com.lifemax.rpg.feature.plan.domain.SetRef
import com.lifemax.rpg.feature.plan.domain.XpGain
import org.json.JSONObject

/** The toggle command's payload, in the shape the page's command reads. */
fun SetRef.toToggleJson(checked: Boolean): JSONObject =
    JSONObject().put("date", date).put("key", item).put("set", index).put("checked", checked)

/**
 * The page's reply to a command, or null when it answers something other than a toggle. A
 * celebration the screen cannot read is not drawn; the reply still settles its box.
 */
fun JSONObject.toPlanReply(): PlanReply? {
    if (optString("command") != "toggle") return null
    val celebrations =
        runCatching {
                optJSONArray("celebrations")?.objects()?.map { c ->
                    Celebration(
                        c.getInt("xp"),
                        c.getJSONArray("gains").objects().map {
                            XpGain(
                                it.getString("attribute"),
                                it.getString("short"),
                                it.getInt("xp"),
                                it.getLong("color"),
                            )
                        },
                    )
                }
            }
            .getOrNull()
            .orEmpty()
            .filter { it.gains.isNotEmpty() }
    return PlanReply(
        getString("requestId"),
        optBoolean("ok"),
        optBoolean("changed"),
        optString("message"),
        celebrations,
    )
}

/** The page's action name and id for an action it runs itself (50-native-plan.js onAction). */
fun PlanPageAction.toEvent(): Pair<String, String> =
    when (this) {
        is PlanPageAction.Guided -> "guided" to date
        is PlanPageAction.EditValue -> "value" to "$date/$item"
        is PlanPageAction.EditLog -> "sessionLog" to "$date/$item"
        PlanPageAction.Leave -> "leave" to ""
    }
