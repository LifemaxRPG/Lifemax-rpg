package com.lifemax.rpg.core.quests

import com.lifemax.rpg.core.save.JsonObject
import com.lifemax.rpg.core.save.JsonValue

/**
 * The quest board's small rules, each a page function read from the save as the page holds it
 * (QUESTS_SPEC section 3.3 and 4.1): which quests show today, the period key a tick is filed under,
 * whether a quest is done, and the stage locks. A quest is a [JsonValue] because the save may hold
 * anything; where the page would throw (a `null` quest, a missing `questDone`) so does [PageThrew],
 * which the board turns into a step that threw.
 */
internal class QuestRules(private val tuning: BoardTuning = BoardTuning.PAGE) {

    /**
     * `questToday(q)` (QST-11): true when the quest has no day, `'any'`, or today's weekday name
     * (case-sensitive, local time).
     */
    fun questToday(q: JsonValue?, clock: BoardClock): Boolean {
        val day = Js.prop(q, "day")
        return !Js.truthy(day) ||
            Js.isText(day, "any") ||
            Js.isText(day, tuning.weekdays[clock.weekday])
    }

    /**
     * `stageLvl(s)` (QST-15): `[0, 1, 3, 5, 8, 12][s] || 1`. The stage is a property key of that
     * array: `3`, `'3'` and `[3]` find 5; `'length'` finds 6; anything else (7, 2.5, `'abc'`) finds
     * nothing and gives 1, so such a quest never locks [QD24]. (Array method names such as `'map'`
     * find a function, which this port does not model: no stage in the content or the page's code
     * is one.)
     */
    fun stageLvl(stage: JsonValue?): Int {
        val key = Js.string(stage)
        if (key == "length") return tuning.stageLevels.size
        val index = STAGE_INDEX.matchEntire(key)?.value?.toInt() ?: return 1
        return tuning.stageLevels.getOrNull(index)?.takeIf { it != 0 } ?: 1
    }

    /**
     * `questLocked(q)` (QST-15): a quest with a stage above 1 is locked while the character level
     * is below the stage's level. [level] is `characterLevel().level`, null when the page's call
     * would throw; it is read only when the stage asks for it, as the page's `&&` does.
     */
    fun questLocked(q: JsonValue?, level: Int?): Boolean {
        val stage = Js.prop(q, "stage")
        val effective = if (Js.truthy(stage)) Js.number(stage) else 1.0
        if (!(effective > 1)) return false
        val current =
            level ?: throw PageThrew("Cannot read properties of undefined (reading 'level')")
        return current < stageLvl(stage)
    }

    /**
     * `questKey(q)` (QST-12): the key a tick is filed under in `questDone`. Daily quests reset at
     * local midnight, weekly ones at the local Monday, every other cadence (epics included) never.
     */
    fun questKey(q: JsonValue?, clock: BoardClock): String {
        val cadence = Js.prop(q, "cadence")
        val id = Js.string(Js.prop(q, "id"))
        return when {
            Js.isText(cadence, "daily") -> "${id}_${clock.today}"
            Js.isText(cadence, "weekly") -> "${id}_${clock.weekKey}"
            else -> "${id}_once"
        }
    }

    /**
     * `isQuestDone(q)` (QST-12): `!!state.questDone[questKey(q)]`. Keys of earlier periods are
     * never read, never pruned [QD03].
     */
    fun isQuestDone(save: JsonObject, q: JsonValue?, clock: BoardClock): Boolean {
        val key = questKey(q, clock)
        return when (val done = save["questDone"]) {
            is JsonObject -> Js.truthy(done[key])
            null,
            com.lifemax.rpg.core.save.JsonNull ->
                throw PageThrew("Cannot read properties of ${Js.string(done)} (reading '$key')")
            else -> false
        }
    }

    /** `Math.max(0, Number(q.xp) || 0)`: the XP a row lists and a tick pays before the caps. */
    fun listedXp(q: JsonValue?): Double = Js.numberOrZero(Js.prop(q, "xp")).coerceAtLeast(0.0)

    private companion object {
        val STAGE_INDEX = Regex("0|[1-9][0-9]{0,8}")
    }
}
