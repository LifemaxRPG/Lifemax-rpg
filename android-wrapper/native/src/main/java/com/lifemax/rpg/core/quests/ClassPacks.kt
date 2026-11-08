package com.lifemax.rpg.core.quests

import com.lifemax.rpg.core.quests.Js.with
import com.lifemax.rpg.core.save.JsonArray
import com.lifemax.rpg.core.save.JsonNull
import com.lifemax.rpg.core.save.JsonNumber
import com.lifemax.rpg.core.save.JsonObject
import com.lifemax.rpg.core.save.JsonString
import com.lifemax.rpg.core.save.JsonValue

/**
 * `applyClassContent()` (QST-9): swaps the starter and class quest packs when the class changed. It
 * works on the save as a [JsonObject], keeping every field and key order, and writes the quests as
 * the page's objects: the same keys in the same order. Content comes from [QuestContent].
 *
 * Its migration is the page's, defects included: a profile with no class returns early when
 * `classContent` is also unset, so the starter dailies stay [QD01]; custom quests, Hand cards and
 * every other id survive; and today's Hand cards are not redrawn [QD02].
 */
class ClassPacks(private val content: QuestContent) {
    /**
     * Applies the class pack for `classId` if `classContent` names another. Returns the save the
     * page would hold afterwards. Where the page throws (a quest that is `null`, `quests` that is
     * not an array, a class id naming an `Object.prototype` member) the step is [HandStep.Threw]
     * with the changes the page made before it threw.
     */
    fun apply(save: JsonObject): HandStep<Unit> =
        try {
            applyIn(save)
        } catch (thrown: PageThrew) {
            HandStep.Threw(save, thrown.message.orEmpty())
        }

    private fun applyIn(save: JsonObject): HandStep<Unit> {
        val classId = save["classId"]
        val key = Js.string(classId)
        val pack = content.classQuests[key]
        val inherited = pack == null && key in Js.OBJECT_PROTOTYPE_KEYS
        val has = pack != null || inherited
        val stored = save["classContent"]
        val current: JsonValue = if (Js.truthy(stored)) stored!! else JsonNull
        val wanted: JsonValue = if (has) classId ?: JsonNull else JsonNull
        if (Js.strictEquals(wanted, current)) return HandStep.Done(save, Unit)

        val old = save["quests"]
        val kept =
            when {
                !Js.truthy(old) -> emptyList()
                old is JsonArray -> old.items.filterNot { isStarterId(Js.prop(it, "id")) }
                else -> throw PageThrew("(state.quests || []).filter is not a function")
            }
        val quests = ArrayList<JsonValue>()
        val stripped = save.with("quests", JsonArray(kept))
        if (has) {
            if (pack == null)
                return HandStep.Threw(
                    stripped,
                    "Cannot read properties of undefined (reading 'filter')",
                )
            pack
                .filterNot {
                    it.cadence == "daily" && it.kind != "nutrition" && it.kind != "hydration"
                }
                .forEach { quests += classQuest(it) }
            quests += kept
        } else {
            quests += kept
            for (d in content.defaultQuests.filter { it.cadence != "daily" }) if (
                quests.none { Js.isText(Js.prop(it, "id"), d.id) }
            )
                quests += defaultQuest(d)
        }
        val classContent = if (has) classId ?: JsonNull else JsonNull
        return HandStep.Done(
            stripped.with("quests", JsonArray(quests)).with("classContent", classContent),
            Unit,
        )
    }

    /** `/^(q[1-6]|cc_.*)$/.test(id)`: `.` stops at a line terminator and `$` only at the end. */
    private fun isStarterId(id: JsonValue?): Boolean {
        val s = Js.string(id)
        if (s.length == 2 && s[0] == 'q' && s[1] in '1'..'6') return true
        return s.startsWith("cc_") && s.substring(3).none { it in LINE_TERMINATORS }
    }

    /** A default quest as `defaultQuests` holds it: `id` first. */
    private fun defaultQuest(q: ContentQuest): JsonObject =
        JsonObject(
            linkedMapOf(
                "id" to JsonString(q.id),
                "name" to JsonString(q.name),
                "desc" to JsonString(q.desc),
                "cadence" to JsonString(q.cadence),
                "xp" to JsonNumber(q.xp.toDouble()),
                "kind" to JsonString(q.kind),
            )
        )

    /** A class quest as `CLASS_CONTENT` holds it: `day` after `cadence`, `stage` before `id`. */
    private fun classQuest(q: ContentQuest): JsonObject {
        val fields = linkedMapOf<String, JsonValue>()
        fields["name"] = JsonString(q.name)
        fields["desc"] = JsonString(q.desc)
        fields["cadence"] = JsonString(q.cadence)
        q.day?.let { fields["day"] = JsonString(it) }
        fields["xp"] = JsonNumber(q.xp.toDouble())
        fields["kind"] = JsonString(q.kind)
        q.attribute?.let { fields["attribute"] = JsonString(it) }
        q.stage?.let { fields["stage"] = JsonNumber(it.toDouble()) }
        fields["id"] = JsonString(q.id)
        return JsonObject(fields)
    }

    private companion object {
        val LINE_TERMINATORS = setOf('\n', '\r', ' ', ' ')
    }
}
