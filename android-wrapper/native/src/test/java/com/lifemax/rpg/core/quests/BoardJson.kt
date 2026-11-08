package com.lifemax.rpg.core.quests

import com.lifemax.rpg.core.save.JsonArray
import com.lifemax.rpg.core.save.JsonBool
import com.lifemax.rpg.core.save.JsonNull
import com.lifemax.rpg.core.save.JsonObject
import com.lifemax.rpg.core.save.JsonString
import com.lifemax.rpg.core.save.JsonValue

/**
 * The board and ledger views in the shape the golden recorder reads them back from the page's
 * markup (key order included, because the test compares JSON text). A locked epic shows no
 * checkbox, so the page shows neither its id nor its done state.
 */
internal object BoardJson {
    fun board(view: BoardView): JsonValue {
        val s = view.summary
        return obj(
            "daily" to list(view.daily, ::questRow),
            "weekly" to list(view.weekly, ::questRow),
            "nutrition" to
                obj(
                    "visible" to JsonBool.of(view.nutrition.isNotEmpty()),
                    "rows" to JsonArray(view.nutrition.map(::questRow)),
                ),
            "epics" to list(view.epics, ::epicRow),
            "summary" to
                obj(
                    "progress" to str(s.progress),
                    "percent" to str(s.percent),
                    "dailyTotal" to str(s.dailyTotal.toString()),
                    "weeklyTotal" to str(s.weeklyTotal.toString()),
                    "available" to str(s.availableXp),
                    "dailyCount" to str(s.dailyCount),
                    "weeklyCount" to str(s.weeklyCount),
                    "nutritionCount" to str(s.nutritionCount),
                    "motivation" to str(s.motivation),
                    "fill" to str(s.percent),
                ),
        )
    }

    fun ledger(view: LedgerView): JsonValue =
        obj(
            "sections" to
                JsonArray(
                    view.sections.map { sec ->
                        obj(
                            "title" to str(sec.title),
                            "rows" to JsonArray(sec.rows.map(::ledgerRow)),
                            "empty" to (sec.empty?.let(::str) ?: JsonNull),
                        )
                    }
                )
        )

    private fun <T> list(list: BoardList<T>, row: (T) -> JsonValue): JsonValue =
        obj(
            "rows" to JsonArray(list.rows.map(row)),
            "empty" to (list.empty?.let(::str) ?: JsonNull),
        )

    private fun questRow(r: QuestRow): JsonValue =
        obj(
            "id" to str(r.id),
            "name" to str(r.name),
            "desc" to (r.desc?.let(::str) ?: JsonNull),
            "reward" to str(r.reward),
            "done" to JsonBool.of(r.done),
            "note" to JsonBool.of(r.noteField),
        )

    private fun epicRow(r: EpicRow): JsonValue =
        obj(
            "id" to if (r.locked) JsonNull else str(r.id),
            "name" to str(r.name),
            "sub" to str(r.sub),
            "reward" to str(r.reward),
            "locked" to JsonBool.of(r.locked),
            "done" to if (r.locked) JsonNull else JsonBool.of(r.done),
            "icon" to str(r.icon),
        )

    private fun ledgerRow(r: LedgerRow): JsonValue =
        obj(
            "id" to str(r.id),
            "name" to str(r.name),
            "sub" to str(r.sub),
            "reward" to str(r.reward),
            "done" to JsonBool.of(r.done),
            "locked" to JsonBool.of(r.locked),
            "mark" to str(r.mark),
        )

    private fun str(text: String) = JsonString(text)

    private fun obj(vararg pairs: Pair<String, JsonValue>) = JsonObject(linkedMapOf(*pairs))
}
