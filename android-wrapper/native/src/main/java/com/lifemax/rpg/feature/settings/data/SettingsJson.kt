package com.lifemax.rpg.feature.settings.data

import com.lifemax.rpg.feature.settings.domain.SettingsCommand
import com.lifemax.rpg.feature.settings.domain.SettingsPageAction
import com.lifemax.rpg.feature.settings.domain.SettingsReply
import org.json.JSONObject

/** A command's payload, in the shape the page's command reads (50-native-settings.js). */
fun SettingsCommand.toJson(): JSONObject =
    when (this) {
        is SettingsCommand.Toggle -> JSONObject().put("id", id).put("on", on)
        is SettingsCommand.Choose -> JSONObject().put("id", id).put("value", value)
        is SettingsCommand.Press -> JSONObject().put("id", id)
        is SettingsCommand.Category -> JSONObject().put("id", id)
        is SettingsCommand.SetField -> JSONObject().put("id", id).put("value", value)
        is SettingsCommand.SaveForm ->
            JSONObject().put("group", group).put("values", JSONObject(values))
    }

/** The page's action name and id for what it does after the screen has closed (onAction). */
fun SettingsPageAction.toEvent(): Pair<String, String> =
    when (this) {
        is SettingsPageAction.Reveal -> "reveal" to id
        SettingsPageAction.Classic -> "classic" to ""
    }

/**
 * The page's answer to a command, or null when it has no request id to settle. A reveal that is not
 * text is left out; the answer still settles its control.
 */
fun JSONObject.toSettingsReply(): SettingsReply? {
    val requestId = optString("requestId")
    if (requestId.isEmpty()) return null
    return SettingsReply(
        optString("command"),
        requestId,
        optBoolean("ok"),
        optBoolean("changed"),
        optString("message"),
        optBoolean("saved", true),
        (opt("reveal") as? String)?.takeIf { it.isNotBlank() },
    )
}
