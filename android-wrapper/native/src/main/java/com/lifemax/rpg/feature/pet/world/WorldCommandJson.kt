package com.lifemax.rpg.feature.pet.world

import com.lifemax.rpg.feature.pet.world.WorldContract.CAT_ID
import com.lifemax.rpg.feature.pet.world.WorldContract.DAY
import com.lifemax.rpg.feature.pet.world.WorldContract.OP_ID
import com.lifemax.rpg.feature.pet.world.WorldContract.SPOTS
import com.lifemax.rpg.feature.pet.world.WorldContract.TIME_MAX
import com.lifemax.rpg.feature.pet.world.WorldContract.TOYS
import org.json.JSONObject

/**
 * The world commands on the wire: [encode] writes the payload native sends, [decode] reads one
 * strictly (exactly its keys, ids in their form, numbers in range), as the page does before it
 * records anything, and [reply] reads the page's answer. A payload that is not its command's shape
 * throws [WorldFormatException].
 */
object WorldCommandJson {
    /** The longest stroke a `pet` may claim (the page's HOLD_MAX_S). */
    const val HOLD_MAX_MS = 600_000L

    /** The longest answer the page words. */
    const val MESSAGE_MAX = 400

    private val CAT_WORDS = Regex("[A-Za-z0-9_]{1,32}")
    private val ANY_NAME = Regex("[\\s\\S]{1,40}")

    /** The command names native sends to the page. */
    val NAMES =
        setOf(
            "world",
            "pet",
            "play",
            "gift",
            "moment",
            "social",
            "dwell",
            "feed",
            "vacation",
            "rename",
        )

    fun encode(c: WorldCommand): JSONObject =
        when (c) {
            is SaveWorld ->
                JSONObject()
                    .put("op", c.op)
                    .put("epoch", c.epoch)
                    .put("rev", c.rev)
                    .put("blob", WorldBlobJson.encode(c.blob))
            is PetDone -> JSONObject().put("op", c.op).put("cat", c.cat).put("heldMs", c.heldMs)
            is PlayDone ->
                JSONObject()
                    .put("op", c.op)
                    .put("cat", c.cat)
                    .put("toy", c.toy)
                    .put("played", c.played)
            is GiftTaken -> JSONObject().put("op", c.op).put("cat", c.cat)
            is MomentReport ->
                JSONObject()
                    .put("op", c.op)
                    .put("cat", c.cat)
                    .put("kind", c.kind)
                    .put("at", c.at)
                    .also { if (c.quirk != null) it.put("quirk", c.quirk) }
            is SocialReport ->
                JSONObject()
                    .put("op", c.op)
                    .put("kind", c.kind)
                    .put("cats", org.json.JSONArray(c.cats))
                    .put("at", c.at)
                    .also { if (c.winner != null) it.put("winner", c.winner) }
            is DwellReport ->
                JSONObject()
                    .put("op", c.op)
                    .put("cat", c.cat)
                    .put("day", c.day)
                    .put("spots", JSONObject(c.spots as Map<*, *>))
                    .put("toys", JSONObject(c.toys as Map<*, *>))
            is CareFeed -> JSONObject().put("op", c.op)
            is CareVacation -> JSONObject().put("on", c.on)
            is CareRename -> JSONObject().put("name", c.newName)
        }

    /** Reads command [name]'s payload [p] strictly. */
    fun decode(name: String, p: JSONObject): WorldCommand =
        when (name) {
            "world" -> {
                p.exactKeys(setOf("op", "epoch", "rev", "blob"), "world")
                val blob = p.get("blob") as? JSONObject ?: fail("type", "blob is not an object")
                SaveWorld(
                    p.op(),
                    p.whole("epoch", 0, Long.MAX_VALUE),
                    p.whole("rev", 1, Long.MAX_VALUE),
                    WorldBlobJson.decode(blob),
                )
            }
            "pet" -> {
                p.exactKeys(setOf("op", "cat", "heldMs"), "pet")
                PetDone(p.op(), p.cat(), p.whole("heldMs", 0, HOLD_MAX_MS))
            }
            "play" -> {
                p.exactKeys(setOf("op", "cat", "toy", "played"), "play")
                PlayDone(p.op(), p.cat(), p.oneOf("toy", TOYS), p.flag("played"))
            }
            "gift" -> {
                p.exactKeys(setOf("op", "cat"), "gift")
                GiftTaken(p.op(), p.cat())
            }
            "moment" -> moment(p)
            "social" -> social(p)
            "dwell" -> {
                p.exactKeys(setOf("op", "cat", "day", "spots", "toys"), "dwell")
                DwellReport(
                    p.op(),
                    p.cat(),
                    p.text("day", 10, DAY),
                    p.seconds("spots", SPOTS),
                    p.seconds("toys", TOYS),
                )
            }
            "feed" -> {
                p.exactKeys(setOf("op"), "feed")
                CareFeed(p.op())
            }
            "vacation" -> {
                p.exactKeys(setOf("on"), "vacation")
                CareVacation(p.flag("on"))
            }
            "rename" -> {
                p.exactKeys(setOf("name"), "rename")
                val n = p.text("name", WorldContract.MAX_NAME, ANY_NAME).trim()
                ensure(n.isNotEmpty() && n.length <= CareRename.MAX, "range") {
                    "name is not a name"
                }
                CareRename(n)
            }
            else -> fail("shape", "unknown command")
        }

    private fun moment(p: JSONObject): MomentReport {
        val quirk = p.has("quirk")
        p.exactKeys(setOf("op", "cat", "kind", "at"), "moment", setOf("quirk"))
        val kind = p.oneOf("kind", MomentReport.MOMENT_KINDS)
        ensure(quirk == (kind == "quirk"), "shape") { "only a quirk moment names a quirk" }
        val id = if (quirk) p.oneOf("quirk", WorldContract.QUIRKS_ACQUIRED) else null
        return MomentReport(p.op(), p.cat(), kind, p.whole("at", 0, TIME_MAX), id)
    }

    private fun social(p: JSONObject): SocialReport {
        p.exactKeys(setOf("op", "kind", "cats", "at"), "social", setOf("winner"))
        val kind = p.oneOf("kind", SocialReport.KINDS)
        val list = p.list("cats", 2)
        ensure(list.length() == 2, "shape") { "a pair is two cats" }
        val cats = (0 until 2).map { list.textAt(it, 13, CAT_ID, "cats") }
        ensure(cats[0] != cats[1], "shape") { "a pair is two different cats" }
        ensure(p.has("winner") == (kind == "standoff"), "shape") { "only a standoff has a winner" }
        val winner = if (p.has("winner")) p.text("winner", 13, CAT_ID) else null
        ensure(winner == null || winner in cats, "range") { "the winner is not of the pair" }
        return SocialReport(p.op(), kind, cats, winner, p.whole("at", 0, TIME_MAX))
    }

    private fun JSONObject.op(): String = text("op", 80, OP_ID)

    private fun JSONObject.cat(): String = text("cat", 13, CAT_ID)

    private fun JSONObject.oneOf(key: String, allowed: Set<String>): String =
        text(key, 32, CAT_WORDS).also {
            ensure(it in allowed, "range") { "$key is not one the page knows" }
        }

    private fun JSONObject.seconds(key: String, ids: Set<String>): Map<String, Int> {
        val m = map(key, ids.size, CAT_WORDS)
        return m.keys().asSequence().associateWith {
            ensure(it in ids, "range") { "$key names one the page does not know" }
            m.int(it, 0, DwellReport.DAY_SECONDS)
        }
    }

    /** The commands whose answers are read here (the care commands' are the Cat tab's PetReply). */
    private val REPLY_NAMES = setOf("world", "pet", "play", "gift", "moment", "social", "dwell")
    private val WHY_FORM = Regex("[a-z]{0,16}")
    private val FREE_TEXT = Regex("[\\s\\S]*")
    private val REPLY_KEYS = setOf("command", "requestId", "ok", "changed", "message", "why")
    private val REPLY_OPTIONAL = setOf("epoch", "rev", "levelUp")

    /**
     * The page's answer to a world command, read strictly; null for the answer to any other command
     * or a malformed one, so it is dropped rather than guessed at.
     */
    fun reply(j: JSONObject): WorldReply? =
        try {
            val command = j.text("command", 32, CAT_WORDS)
            if (command !in REPLY_NAMES) null
            else {
                j.exactKeys(REPLY_KEYS, "reply", REPLY_OPTIONAL)
                val why = j.text("why", 16, WHY_FORM)
                ensure(why in WorldReply.WHY, "range") { "why is not one the page gives" }
                WorldReply(
                    command = command,
                    requestId = j.text("requestId", 80, FREE_TEXT),
                    ok = j.flag("ok"),
                    changed = j.flag("changed"),
                    message = j.text("message", MESSAGE_MAX, FREE_TEXT),
                    why = why,
                    epoch = if (j.has("epoch")) j.whole("epoch", 0, Long.MAX_VALUE) else null,
                    rev = if (j.has("rev")) j.whole("rev", 0, Long.MAX_VALUE) else null,
                    levelUp =
                        if (j.has("levelUp")) j.int("levelUp", 0, WorldContract.BOND_MAX) else 0,
                )
            }
        } catch (e: WorldFormatException) {
            null
        } catch (e: org.json.JSONException) {
            null
        }
}
