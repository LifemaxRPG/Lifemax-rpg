package com.lifemax.rpg.feature.character.data

import com.lifemax.rpg.core.data.objects
import com.lifemax.rpg.core.designsystem.feedback.XpAward
import com.lifemax.rpg.core.designsystem.feedback.XpGain
import com.lifemax.rpg.feature.character.domain.BenchmarkLimits
import com.lifemax.rpg.feature.character.domain.BenchmarkWords
import com.lifemax.rpg.feature.character.domain.CharacterCommands
import com.lifemax.rpg.feature.character.domain.CharacterReply
import com.lifemax.rpg.feature.character.domain.CharacterWords
import com.lifemax.rpg.feature.character.domain.LuckRoll
import com.lifemax.rpg.feature.character.domain.LuckWords
import org.json.JSONObject

/*
 * The actions' half of the projection and the commands, as 50-native-character.js sends and takes
 * them. A page without the actions (an older build) sends none of these fields: that is the one
 * place a missing field is allowed, and the screen then hands every tap over as it always did.
 * Once the page says `actions`, every field is strict.
 */

/** A roll as the page's luckView() sends it; the dice are four faces of 1..6. */
internal fun JSONObject.toLuckRoll(): LuckRoll {
    val d = getJSONArray("dice")
    require(d.length() == 4) { "A roll is four dice" }
    val dice = (0 until 4).map { d.getInt(it).also { f -> require(f in 1..6) { "A die is 1..6" } } }
    return LuckRoll(
        getString("date"),
        dice,
        getString("score"),
        getString("tier"),
        getString("message"),
        getLong("color"),
    )
}

internal fun JSONObject.toLuckWords() =
    getJSONObject("luck").let {
        LuckWords(
            it.getString("heading"),
            it.getString("rolling"),
            it.getString("retry"),
            it.getString("close"),
        )
    }

internal fun JSONObject.toBenchmarkWords() =
    getJSONObject("benchmarks").let {
        BenchmarkWords(
            it.getString("heading"),
            it.getString("nameLabel"),
            it.getString("namePlaceholder"),
            it.getString("valueLabel"),
            it.getString("valuePlaceholder"),
            it.getString("add"),
            it.getString("edit"),
            it.getString("save"),
            it.getString("cancel"),
            it.getString("remove"),
            it.getString("close"),
        )
    }

/** What the projection says about the actions: off, or all of it. */
internal class ActionsPart(
    val actions: Boolean,
    val today: String,
    val luckRoll: LuckRoll?,
    val limits: BenchmarkLimits,
    val words: CharacterWords,
)

internal fun JSONObject.actionsPart(): ActionsPart {
    if (!optBoolean("actions", false))
        return ActionsPart(false, "", null, BenchmarkLimits(0, 0), CharacterWords.NONE)
    val limits = getJSONObject("benchmarkLimits")
    // null is a day not rolled yet; a missing key is a malformed projection
    val roll =
        if (get("luckRoll") == JSONObject.NULL) null else getJSONObject("luckRoll").toLuckRoll()
    val words = getJSONObject("words")
    val n = limits.getInt("name")
    val v = limits.getInt("value")
    require(n > 0 && v > 0) { "A benchmark's limits are positive" }
    return ActionsPart(
        true,
        getString("today").also { require(it.isNotEmpty()) { "The page's day is missing" } },
        roll,
        BenchmarkLimits(n, v),
        CharacterWords(words.toLuckWords(), words.toBenchmarkWords()),
    )
}

/**
 * The page's reply to a command. A celebration the screen cannot read is not drawn, and a roll it
 * cannot read is not shown (the next projection carries it); the reply still settles what it
 * answers, so a throw here never leaves a sheet waiting for an answer that has come.
 */
fun JSONObject.toCharacterReply(): CharacterReply? {
    val command = optString("command")
    if (command.isEmpty()) return null
    val celebrations =
        runCatching {
                optJSONArray("celebrations")?.objects()?.map { c ->
                    XpAward(
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
    val luck = runCatching { optJSONObject("luck")?.toLuckRoll() }.getOrNull()
    return CharacterReply(
        command,
        optString("requestId"),
        optBoolean("ok"),
        optBoolean("changed"),
        optString("message"),
        luck,
        celebrations,
    )
}

/** The commands' wire names and payloads, sent through [send] (the plugin's sendCommand). */
class CharacterCommandSender(private val send: (command: String, payload: JSONObject) -> String) :
    CharacterCommands {
    override fun luck(op: String, day: String) =
        send(LUCK, JSONObject().put("op", op).put("day", day))

    override fun benchmarkAdd(op: String, name: String, value: String) =
        send(ADD, JSONObject().put("op", op).put("name", name).put("value", value))

    override fun benchmarkSet(index: Int, name: String, value: String) =
        send(SET, JSONObject().put("index", index).put("name", name).put("value", value))

    override fun benchmarkRemove(index: Int, name: String) =
        send(REMOVE, JSONObject().put("index", index).put("name", name))

    override fun log(attribute: String) = send(LOG, JSONObject().put("attribute", attribute))

    companion object {
        const val LUCK = "luck"
        const val ADD = "benchmarkAdd"
        const val SET = "benchmarkSet"
        const val REMOVE = "benchmarkRemove"
        const val LOG = "log"
    }
}
