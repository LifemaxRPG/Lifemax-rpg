package com.lifemax.rpg.core.shadow

import com.lifemax.rpg.core.economy.Attribute
import com.lifemax.rpg.core.economy.FixedAwards
import com.lifemax.rpg.core.quests.ContentQuest
import com.lifemax.rpg.core.quests.QuestContentLoader
import com.lifemax.rpg.core.quests.QuestContentRead
import com.lifemax.rpg.core.save.JsonArray
import com.lifemax.rpg.core.save.JsonNumber
import com.lifemax.rpg.core.save.JsonObject
import com.lifemax.rpg.core.save.JsonString
import com.lifemax.rpg.core.save.JsonValue

/**
 * Page content some economy actions need that is not save data and that native does not hold yet
 * (spec 1.3: content, not rules): a day of the workout plan (`planDay`), the catalog entries a plan
 * kind stands for (`PLAN_LINK`) and the attribute sheet's entries (`ACTS`). An action whose content
 * is missing is counted `skipped.content`, never guessed. The app has none of it today ([NONE]);
 * the tests give the golden cases' content.
 */
interface ShadowContent {
    /** `planDay(date)`, or null when unknown. */
    fun planDay(date: String): FixedAwards.PlanDay?

    /** `PLAN_LINK[kind]`: the catalog entries a plan kind stands for, or null when unknown. */
    fun planLinks(kind: String): List<String>?

    /** `actById(id)`, or null when unknown. */
    fun catalog(id: String): FixedAwards.CatalogEntry?

    companion object {
        /** No page content: the plan and catalog actions are skipped. */
        val NONE: ShadowContent =
            object : ShadowContent {
                override fun planDay(date: String): FixedAwards.PlanDay? = null

                override fun planLinks(kind: String): List<String>? = null

                override fun catalog(id: String): FixedAwards.CatalogEntry? = null
            }
    }
}

/**
 * The tracked part of the page's `freshState()` (SAVE-5), which `load()` spreads a stored save
 * over: `{...freshState(), ...saved}`, with `attrXP` merged one level deeper. Its quests are the
 * packaged content's `defaultQuests`, so a content that does not load gives none (and the boot's
 * load is then counted `skipped.content`).
 */
object PageFresh {
    /** The fresh tracked keys, or null when the packaged quest content did not load. */
    val tracked: Map<String, JsonValue>? by lazy {
        val content = (QuestContentLoader.packaged as? QuestContentRead.Loaded)?.content
        content?.let {
            linkedMapOf(
                "activities" to JsonArray.EMPTY,
                "attrXP" to JsonObject(Attribute.ids.associateWith { ZERO }),
                "skillXP" to JsonObject.EMPTY,
                "quests" to JsonArray(it.defaultQuests.map(::quest)),
                "questDone" to JsonObject.EMPTY,
                "customRules" to JsonObject.EMPTY,
            )
        }
    }

    /** What `load()` makes of [stored] before it migrates: the fresh keys spread under it. */
    fun under(stored: Map<String, JsonValue>): Map<String, JsonValue>? {
        val fresh = tracked ?: return null
        val out = LinkedHashMap(fresh)
        out.putAll(stored)
        val attr = stored["attrXP"]
        out["attrXP"] =
            if (attr is JsonObject)
                JsonObject((fresh.getValue("attrXP") as JsonObject).members + attr.members)
            else fresh.getValue("attrXP")
        return out
    }

    /** A default quest as `defaultQuests` holds it, keys in the page's order. */
    private fun quest(q: ContentQuest): JsonObject =
        JsonObject(
            linkedMapOf<String, JsonValue>(
                    "id" to JsonString(q.id),
                    "name" to JsonString(q.name),
                    "desc" to JsonString(q.desc),
                    "cadence" to JsonString(q.cadence),
                    "xp" to JsonNumber(q.xp.toDouble()),
                    "kind" to JsonString(q.kind),
                )
                .apply {
                    q.attribute?.let { put("attribute", JsonString(it)) }
                    q.day?.let { put("day", JsonString(it)) }
                    q.stage?.let { put("stage", JsonNumber(it.toDouble())) }
                }
        )

    private val ZERO = JsonNumber(0.0)
}
