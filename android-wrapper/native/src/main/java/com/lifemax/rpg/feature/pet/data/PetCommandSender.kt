package com.lifemax.rpg.feature.pet.data

import com.lifemax.rpg.feature.pet.domain.PetCommands
import com.lifemax.rpg.feature.pet.domain.PetReply
import com.lifemax.rpg.feature.pet.domain.PetStroke
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

    override fun focus(index: Int) = send(FOCUS, JSONObject().put("i", index))

    override fun buyToy(id: String) = send(BUY_TOY, JSONObject().put("id", id))

    override fun expandGenome(open: Boolean) = send(EXPAND, JSONObject().put("open", open))

    override fun stroke(op: String, stroke: PetStroke) =
        send(
            TOUCH,
            JSONObject()
                .put("kind", "stroke")
                .put("op", op)
                .put("cat", stroke.cat)
                .put("heldMs", stroke.heldMs)
                .put("left", stroke.left)
                .put("ignored", stroke.ignored)
                .put("spam", stroke.spam)
                .put("keepSleep", stroke.keepSleep),
        )

    override fun purr(cat: Int, level: Float) =
        send(
            TOUCH,
            JSONObject()
                .put("kind", "purr")
                .put("cat", cat)
                .put("on", level > 0f)
                .put("level", level.toDouble()),
        )

    companion object {
        const val FEED = "feed"
        const val BLINK = "blink"
        const val VACATION = "vacation"
        const val RENAME = "rename"
        const val FOCUS = "focus"
        const val BUY_TOY = "buyToy"
        const val EXPAND = "expand"
        const val TOUCH = "touch"
        val ALL = setOf(FEED, BLINK, VACATION, RENAME, FOCUS, BUY_TOY, EXPAND, TOUCH)
    }
}

private val REPLY_KEYS =
    setOf("command", "requestId", "ok", "changed", "message", "why", "inHours", "reaction")

/** A reply to a stroke also names the level the cat reached, when it reached one. */
private val OPTIONAL_REPLY_KEYS = setOf("levelUp")
private const val MAX_LEVEL = 5
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
            exactly(REPLY_KEYS, "reply", OPTIONAL_REPLY_KEYS)
            val why = str("why")
            val reaction = str("reaction")
            val inHours =
                (get("inHours") as? Number)?.toDouble() ?: throw IllegalArgumentException("inHours")
            val levelUp = if (has("levelUp")) int("levelUp") else 0
            require(why in WHY && reaction in REACTION && inHours >= 0.0 && inHours.isFinite())
            require(levelUp in 0..MAX_LEVEL) { "levelUp is not a trust level" }
            PetReply(
                command,
                str("requestId"),
                bool("ok"),
                bool("changed"),
                str("message"),
                why,
                inHours,
                reaction,
                levelUp,
            )
        }
    } catch (e: IllegalArgumentException) {
        null
    } catch (e: org.json.JSONException) {
        null
    }
