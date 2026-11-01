package com.lifemax.rpg.feature.overview.data

import com.lifemax.rpg.feature.overview.domain.OverviewCommands
import org.json.JSONArray
import org.json.JSONObject

/**
 * The cards' commands as the page's 50-native-overview-cards.js takes them (plan §3.4): each
 * command's wire name and payload, sent through [send] (the plugin's sendCommand), which answers
 * the request id. Destinations that are plain page views are not commands but the action "navigate"
 * with the view ([OverviewViews]).
 */
class OverviewCommandSender(private val send: (command: String, payload: JSONObject) -> String) :
    OverviewCommands {
    override fun plan(act: String, id: String) =
        send(PLAN, JSONObject().put("act", act).put("id", id))

    override fun attr(id: String, tile: String) =
        send(ATTR, JSONObject().put("id", id).put("tile", tile))

    override fun luck(op: String) = send(LUCK, JSONObject().put("op", op))

    override fun shop() = send(SHOP, JSONObject())

    override fun health() = send(HEALTH, JSONObject())

    override fun appLaunch(pkg: String) = send(APP_LAUNCH, JSONObject().put("pkg", pkg))

    override fun appAdd() = send(APP_ADD, JSONObject())

    override fun appsEdit() = send(APPS_EDIT, JSONObject())

    override fun tip(op: String) = send(TIP, JSONObject().put("op", op))

    override fun layout(order: List<String>, hidden: List<String>) =
        send(LAYOUT, JSONObject().put("order", JSONArray(order)).put("hidden", JSONArray(hidden)))

    override fun layoutReset() = send(LAYOUT_RESET, JSONObject())

    companion object {
        const val PLAN = "plan"
        const val ATTR = "attr"
        const val LUCK = "luck"
        const val SHOP = "shop"
        const val HEALTH = "health"
        const val APP_LAUNCH = "appLaunch"
        const val APP_ADD = "appAdd"
        const val APPS_EDIT = "appsEdit"
        const val TIP = "tip"
        const val LAYOUT = "layout"
        const val LAYOUT_RESET = "layoutReset"
    }
}

/** The page views the cards hand over to with the action "navigate" (plan §3.4). */
object OverviewViews {
    /** Hero, Attributes' "Details ›". */
    const val CHARACTER = "character"
    /** Recent's "History ›": the Journal. */
    const val HISTORY = "history"
    /** Health off's "Set up health sync". */
    const val SETTINGS = "settings"
    /** The cat card: the live room. */
    const val PET = "pet"
}
