package com.lifemax.rpg.feature.journal.data

import com.lifemax.rpg.feature.journal.domain.JournalAction
import com.lifemax.rpg.feature.journal.domain.JournalCommand
import com.lifemax.rpg.feature.journal.domain.JournalReply
import org.json.JSONObject

/** A command's payload as 50-native-journal.js reads it (plan §3.4). */
fun JournalCommand.toJson(): JSONObject =
    when (this) {
        is JournalCommand.Select -> JSONObject().put("key", key)
        is JournalCommand.Write -> JSONObject().put("key", key)
        is JournalCommand.Swap -> JSONObject().put("d", d).put("from", from)
        JournalCommand.Unlock,
        JournalCommand.Backup,
        JournalCommand.DismissTip -> JSONObject()
    }

/**
 * The page's action name and id for an action (50-native-journal.js onAction); "navigate" carries a
 * page view, which the kit opens make-before-break.
 */
fun JournalAction.toEvent(): Pair<String, String> =
    when (this) {
        is JournalAction.Claim -> "claim" to id
        JournalAction.CatchUp -> "catchUp" to ""
        is JournalAction.OpenView -> "navigate" to view
    }

/** A reply the kit sent with an update; null without a request id (not this screen's). */
fun JSONObject.toJournalReply(): JournalReply? {
    val requestId = optString("requestId")
    if (requestId.isEmpty()) return null
    return JournalReply(
        optString("command"),
        requestId,
        optBoolean("ok"),
        optBoolean("changed"),
        optString("message"),
    )
}
