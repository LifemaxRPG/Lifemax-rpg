package com.lifemax.rpg.feature.pet.data

import com.lifemax.rpg.feature.pet.domain.PetCommands
import com.lifemax.rpg.feature.pet.domain.PetReply
import org.json.JSONObject

/**
 * The Cat tab's commands as the page's 50-native-pet.js takes them: each command's wire name and
 * payload, sent through [send] (the plugin's sendCommand), which answers the request id.
 */
class PetCommandSender(private val send: (command: String, payload: JSONObject) -> String) :
    PetCommands {
    override fun feed(op: String) = send(FEED, JSONObject().put("op", op))

    override fun blink() = send(BLINK, JSONObject())

    override fun vacation(on: Boolean) = send(VACATION, JSONObject().put("on", on))

    override fun rename(name: String) = send(RENAME, JSONObject().put("name", name))

    companion object {
        const val FEED = "feed"
        const val BLINK = "blink"
        const val VACATION = "vacation"
        const val RENAME = "rename"
        val ALL = setOf(FEED, BLINK, VACATION, RENAME)
    }
}

private val REPLY_KEYS =
    setOf("command", "requestId", "ok", "changed", "message", "why", "inHours", "reaction")
private val WHY = setOf("", "vacation", "full")
private val REACTION = setOf("", "blink", "bowl")

/**
 * The page's reply to one of the Cat tab's commands, read strictly: null for anything else (the
 * reply to another command, a field it does not know, a word outside the ones the page uses), so a
 * malformed answer is dropped rather than guessed at.
 */
fun JSONObject.toPetReply(): PetReply? =
    try {
        val command = str("command")
        if (command !in PetCommandSender.ALL) null
        else {
            exactly(REPLY_KEYS, "reply")
            val why = str("why")
            val reaction = str("reaction")
            val inHours =
                (get("inHours") as? Number)?.toDouble() ?: throw IllegalArgumentException("inHours")
            require(why in WHY && reaction in REACTION && inHours >= 0.0 && inHours.isFinite())
            PetReply(
                command,
                str("requestId"),
                bool("ok"),
                bool("changed"),
                str("message"),
                why,
                inHours,
                reaction,
            )
        }
    } catch (e: IllegalArgumentException) {
        null
    } catch (e: org.json.JSONException) {
        null
    }
