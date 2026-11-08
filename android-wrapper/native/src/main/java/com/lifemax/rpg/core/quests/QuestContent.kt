package com.lifemax.rpg.core.quests

/**
 * The quest content the page keeps as data (`QUEST_LIBRARY`, `DH_CLASS_ATTR`, `defaultQuests`,
 * `CLASS_CONTENT[class].quests`, `stageLvl`), read from `quest-content.json`, which
 * `lifemax_modular_v73/tools/gen_quest_content.js` writes from the real page. Nothing here is a
 * rule; it is what the Daily Hand draw and the class-pack rules read.
 */
data class QuestContent(
    /** The file's shape version; the decoder accepts exactly [SCHEMA_VERSION]. */
    val schemaVersion: Int,
    /** SHA-256 (hex) of the content, checked when the file is read. */
    val contentHash: String,
    /** The attribute ids in the page's `ATTRS` order (the tie order of the weakest attribute). */
    val attributes: List<String>,
    val hand: HandContent,
    /** `freshState().quests`: what a new save starts with. */
    val defaultQuests: List<ContentQuest>,
    /** `stageLvl(0..5)`: the character level a quest stage unlocks at, indexed by stage. */
    val stageLevels: List<Int>,
    /** Every class the player can pick, ready or not. */
    val classes: List<ClassInfo>,
    /** `CLASS_CONTENT[class].quests`, in the page's order, for each class that has content. */
    val classQuests: Map<String, List<ContentQuest>>,
) {
    /** The templates of one attribute, in library order. */
    fun templatesOf(attribute: String): List<HandTemplate> =
        hand.templates.filter { it.attr == attribute }

    /** A template by its id (`t1`..), or null. */
    fun template(id: String): HandTemplate? = hand.templates.firstOrNull { it.id == id }

    companion object {
        /** The only file shape this build reads. */
        const val SCHEMA_VERSION = 1
    }
}

/** The Daily Hand's constants and library (`DH_*`, `QUEST_LIBRARY`). */
data class HandContent(
    /** `DH_FREE_REROLLS`: rerolls a day that cost nothing. */
    val freeRerolls: Int,
    /** `DH_REROLL_COST`: Sparks for each reroll after the free ones. */
    val rerollCost: Int,
    /** `DH_CLASS_ATTR`: the attributes the Class card draws from, by class id. */
    val classAttrs: Map<String, List<String>>,
    /** `QUEST_LIBRARY`, in the page's order (the order of the draw's pool). */
    val templates: List<HandTemplate>,
)

/**
 * One Daily Hand template as `dhMakeQuest` reads it. [verify] is the check text (`self`, `none`,
 * `act:type:minutes`, `any:minutes`, `water:percent`, `pet:feed|pet|play`, `plan`).
 */
data class HandTemplate(
    val id: String,
    val attr: String,
    val tier: Int,
    val name: String,
    val desc: String,
    val verify: String,
    val xp: Int,
)

/**
 * A quest the page ships: a default quest or a class-pack quest. [attribute], [day] and [stage] are
 * null when the page's object has no such key (the default quests have no attribute; a class quest
 * has a day or a stage, not both).
 */
data class ContentQuest(
    val id: String,
    val name: String,
    val desc: String,
    val cadence: String,
    val xp: Int,
    val kind: String,
    val attribute: String?,
    val day: String?,
    val stage: Int?,
)

/** A class in the picker: [ready] when the page ships its content. */
data class ClassInfo(val id: String, val ready: Boolean)
