package com.lifemax.rpg.core.lifedirector.hostadapters

import com.lifemax.rpg.core.save.JsonArray
import com.lifemax.rpg.core.save.JsonBool
import com.lifemax.rpg.core.save.JsonNull
import com.lifemax.rpg.core.save.JsonNumber
import com.lifemax.rpg.core.save.JsonObject
import com.lifemax.rpg.core.save.JsonString
import com.lifemax.rpg.core.save.JsonValue
import com.lifemax.rpg.core.save.PageReads

/**
 * One quest of the board (`state.quests[i]`) as the host reads it. Text members are text, or null
 * where the save has no such member or holds `null`; [xp] is `Number(q.xp) || 0`; [hand] is `q.hand
 * === true`.
 */
class BoardQuest(
    /** `q.id` when it is text (null when absent or `null`). */
    val id: String?,
    /** `String(q.id)`. */
    val idText: String,
    val name: String?,
    val desc: String?,
    val cadence: String?,
    val kind: String?,
    val attribute: String?,
    val day: String?,
    val verify: String?,
    val hand: Boolean,
    val xp: Double,
    /** `q.stage` when it is a whole number, else null (absent, `null`). */
    val stage: Int?,
) {
    /** `q.name + ' ' + (q.desc || '')`. */
    val words: String
        get() = (name ?: "undefined") + " " + desc.orEmpty()

    /** `String(q.name || '') + ' ' + String(q.desc || '')` (HA-8). */
    val untimedText: String
        get() = name.orEmpty() + " " + desc.orEmpty()

    companion object {
        /**
         * A Daily Hand template as `isUntimedQuest` sees it once given `hand` and `daily` (HA-19).
         */
        fun ofTemplate(id: String, name: String, desc: String, verify: String): BoardQuest =
            BoardQuest(id, id, name, desc, "daily", "hand", null, null, verify, true, 0.0, null)
    }
}

/** The board read from the save: its entries in order, null where an entry is falsy. */
class Board(val entries: List<BoardQuest?>) {
    /** The quests (not the falsy entries). */
    val quests: List<BoardQuest>
        get() = entries.filterNotNull()
}

/** Reads the save's quest list strictly (DV-A1: a value the page never writes is refused). */
object BoardQuests {
    /**
     * The board, null when [value] is not a list (the host is then not connected), or a refusal
     * naming the first entry the reader does not take.
     */
    fun read(value: JsonValue?): HostAnswer<Board?> {
        if (value !is JsonArray) return HostAnswer.Answer(null)
        val out = ArrayList<BoardQuest?>(value.items.size)
        for ((i, item) in value.items.withIndex()) {
            if (!PageReads.truthy(item)) {
                out += null
                continue
            }
            val q =
                (item as? JsonObject)?.let(::quest)
                    ?: return HostAnswer.Refused(HostRefusal.UNREADABLE_QUEST, "quests[$i]")
            out +=
                q.takeIf { it.second == null }?.first
                    ?: return HostAnswer.Refused(
                        HostRefusal.UNREADABLE_QUEST,
                        "quests[$i].${q.second}",
                    )
        }
        return HostAnswer.Answer(Board(out))
    }

    private class Unreadable(val member: String) : Exception(member, null, false, false)

    private fun quest(o: JsonObject): Pair<BoardQuest?, String?> =
        try {
            val id = text(o, "id")
            val idText = id ?: if (o["id"] == JsonNull) "null" else "undefined"
            val desc = o["desc"]
            Pair(
                BoardQuest(
                    id = id,
                    idText = idText,
                    name =
                        text(o, "name").also {
                            if (o["name"] == JsonNull) throw Unreadable("name")
                        },
                    desc =
                        when {
                            !PageReads.truthy(desc) -> null
                            desc is JsonString -> desc.value
                            else -> throw Unreadable("desc")
                        },
                    cadence = text(o, "cadence"),
                    kind = text(o, "kind"),
                    attribute = text(o, "attribute"),
                    day = text(o, "day"),
                    verify = text(o, "verify"),
                    hand = o["hand"] == JsonBool.TRUE,
                    xp =
                        PageReads.pageNumber(o["xp"]).let {
                            if (it.isNaN() || it == 0.0) 0.0 else it
                        },
                    stage = stage(o["stage"]),
                ),
                null,
            )
        } catch (e: Unreadable) {
            Pair(null, e.member)
        }

    private fun text(o: JsonObject, key: String): String? =
        when (val v = o[key]) {
            null,
            JsonNull -> null
            is JsonString -> v.value
            else -> throw Unreadable(key)
        }

    private fun stage(v: JsonValue?): Int? =
        when (v) {
            null,
            JsonNull -> null
            is JsonNumber ->
                v.value.takeIf { it == Math.rint(it) && kotlin.math.abs(it) < MAX_STAGE }?.toInt()
                    ?: throw Unreadable("stage")
            else -> throw Unreadable("stage")
        }

    private const val MAX_STAGE = 1_000_000.0
}
