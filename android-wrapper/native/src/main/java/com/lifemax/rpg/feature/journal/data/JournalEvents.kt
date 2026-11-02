package com.lifemax.rpg.feature.journal.data

import com.lifemax.rpg.core.data.objects
import com.lifemax.rpg.core.designsystem.feedback.XpAward
import com.lifemax.rpg.core.designsystem.feedback.XpGain
import com.lifemax.rpg.feature.journal.domain.Answer
import com.lifemax.rpg.feature.journal.domain.CatchUpSentence
import com.lifemax.rpg.feature.journal.domain.ClaimChip
import com.lifemax.rpg.feature.journal.domain.JournalAction
import com.lifemax.rpg.feature.journal.domain.JournalCommand
import com.lifemax.rpg.feature.journal.domain.JournalReply
import com.lifemax.rpg.feature.journal.domain.Reflect
import org.json.JSONObject

/** A command's payload as 50-native-journal.js reads it (plan §3.4). */
fun JournalCommand.toJson(): JSONObject =
    when (this) {
        is JournalCommand.Select -> JSONObject().put("key", key)
        is JournalCommand.Write -> JSONObject().put("key", key)
        is JournalCommand.Swap -> JSONObject().put("d", d).put("from", from)
        is Reflect ->
            JSONObject().put("d", d).put("text", text).put("base", base).put("final", final)
        is Answer ->
            JSONObject().put("key", key).put("text", text).put("base", base).put("final", final)
        is ClaimChip -> JSONObject().put("kind", kind)
        is CatchUpSentence -> JSONObject().put("text", text)
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

/**
 * A reply the kit sent with an update; null without a request id (not this screen's). The write
 * commands' extras are read as the page sent them: a `stored` that is not text, or a celebration
 * that is malformed, is left out (the reply must still settle the write it answers).
 */
fun JSONObject.toJournalReply(): JournalReply? {
    val requestId = optString("requestId")
    if (requestId.isEmpty()) return null
    return JournalReply(
        optString("command"),
        requestId,
        optBoolean("ok"),
        optBoolean("changed"),
        optString("message"),
        optBoolean("paid"),
        optBoolean("conflict"),
        (opt("stored") as? String),
        runCatching { celebrations() }.getOrDefault(emptyList()),
    )
}

/**
 * `celebrations [{xp, gains: [{attribute, short, xp, color}]}]` as NativeTabKit.celebrations sends
 * them (the same shape as Workout plan's and Life Director's); an award with no gains is not drawn.
 */
private fun JSONObject.celebrations(): List<XpAward> {
    val list = optJSONArray("celebrations") ?: return emptyList()
    return list.objects().map { c ->
        XpAward(
            c.getInt("xp"),
            c.getJSONArray("gains").objects().map {
                val color = it.getLong("color")
                require(color in 0..0xFFFFFFFFL) { "A colour is 0xAARRGGBB" }
                XpGain(it.getString("attribute"), it.getString("short"), it.getInt("xp"), color)
            },
        )
    }
}
