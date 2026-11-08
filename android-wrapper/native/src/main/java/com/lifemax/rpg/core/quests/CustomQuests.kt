package com.lifemax.rpg.core.quests

import com.lifemax.rpg.core.economy.jsNumber
import com.lifemax.rpg.core.quests.Js.with
import com.lifemax.rpg.core.save.JsonArray
import com.lifemax.rpg.core.save.JsonNull
import com.lifemax.rpg.core.save.JsonNumber
import com.lifemax.rpg.core.save.JsonObject
import com.lifemax.rpg.core.save.JsonString

/**
 * Making a custom quest (QST-21 to QST-23: `createQuest` on the board and `dhRest.create` in the 3D
 * table, which do the same). Creating is the only thing the owner can do with one: no screen edits
 * or deletes a custom quest [QD10]. It reproduces the page's limits and defaults: the name is
 * trimmed and may not be empty, XP is `Number(field) || 25` kept between 1 and 500 and not rounded
 * [QD07], the quest always trains `discipline` [QD08] and shows every day, and two made in the same
 * millisecond share an id [QD09].
 */
class CustomQuests(private val tuning: BoardTuning = BoardTuning.PAGE) {
    /**
     * Creates the quest from what the form's fields hold when the button is pressed ([nameField],
     * [cadenceField] as the select's value, [xpField] as the number input's text). An empty name
     * shows [NAME_REFUSAL] and changes nothing. Otherwise the quest is appended, the page is asked
     * to save once (its result is ignored, no take-back [QD06]) and [CREATED] is shown.
     */
    fun create(
        save: JsonObject,
        clock: BoardClock,
        nameField: String,
        cadenceField: String,
        xpField: String,
    ): HandStep<Unit> {
        val name = Js.trim(nameField)
        if (name.isEmpty()) return HandStep.Done(save, Unit, listOf(NAME_REFUSAL))
        val quests = save["quests"]
        if (quests !is JsonArray)
            return HandStep.Threw(
                save,
                if (quests == null || quests == JsonNull)
                    "Cannot read properties of ${Js.string(quests)} (reading 'push')"
                else "state.quests.push is not a function",
            )
        val xp =
            minOf(
                tuning.maxXp,
                maxOf(
                    tuning.minXp,
                    jsNumber(xpField).let { if (it.isNaN() || it == 0.0) tuning.defaultXp else it },
                ),
            )
        val quest =
            JsonObject(
                linkedMapOf(
                    "id" to JsonString("custom_${clock.nowMillis}"),
                    "name" to JsonString(name),
                    "desc" to JsonString(DESC),
                    "cadence" to JsonString(cadenceField),
                    "xp" to JsonNumber(xp),
                    "kind" to JsonString("custom"),
                )
            )
        return HandStep.Done(
            save.with("quests", JsonArray(quests.items + quest)),
            Unit,
            listOf(CREATED),
            saveRequests = 1,
        )
    }

    companion object {
        /** The line shown for an empty name. */
        const val NAME_REFUSAL = "Give your quest a name"

        /** The line shown after a quest is made. */
        const val CREATED = "Quest created"

        /** The description every custom quest gets. */
        const val DESC = "Custom quest created by you."
    }
}
