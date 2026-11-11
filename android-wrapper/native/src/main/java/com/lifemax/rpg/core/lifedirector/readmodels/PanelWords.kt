package com.lifemax.rpg.core.lifedirector.readmodels

import com.lifemax.rpg.core.save.JsonArray
import com.lifemax.rpg.core.save.JsonBool
import com.lifemax.rpg.core.save.JsonNumber
import com.lifemax.rpg.core.save.JsonObject
import com.lifemax.rpg.core.save.JsonRead
import com.lifemax.rpg.core.save.JsonReader
import com.lifemax.rpg.core.save.JsonString
import com.lifemax.rpg.core.save.JsonValue

/** A value and its label, as the page's option tables pair them (`['LIGHT', 'Light']`). */
data class Choice(val value: JsonValue, val label: String)

/** A stepper or slider the page defines as `[key, lowest, highest, step, label, hint]`. */
data class RangeSpec(
    val key: String,
    val low: Double,
    val high: Double,
    val step: Double,
    val label: String,
    /** Absent in the page's table (`GRACE` has none): null. */
    val hint: String?,
)

/**
 * RM-21: the page words and tables the read models of part b use, read from
 * `lifedirector/readmodel-words-b.json`, which the page's recorder writes from the page itself
 * (`lifemax_modular_v73/tools/gen_ld_readmodels_b_golden.js`: tables by the controller's constant,
 * inline phrases by the function and position they have in `logic.js`) and its web test keeps equal
 * to the page: no word is retyped here. The reader is strict: a missing or unknown member, or a
 * value of the wrong kind, refuses the file, and [packaged] then throws on first use.
 */
class PanelWords
private constructor(
    private val tables: Map<String, JsonValue>,
    /** `LifeDirectorEngine.ENGINE_VERSION`. */
    val engineVersion: String,
    /** The page's `H12` where it was recorded (en-US: true). */
    val h12: Boolean,
    private val phrases: Map<String, Map<String, String>>,
) {
    /** The phrase [key] that the page writes inline in [fn]. */
    fun phrase(fn: String, key: String): String =
        phrases[fn]?.get(key) ?: error("$RESOURCE has no phrase $fn.$key")

    /** The table [name] exactly as the page holds it (for tables passed through whole). */
    fun table(name: String): JsonValue = tables.getValue(name)

    private fun text(name: String) = (table(name) as JsonString).value

    private fun field(name: String, key: String) =
        ((table(name) as JsonObject)[key] as JsonString).value

    private fun pairs(value: JsonValue): List<Choice> =
        (value as JsonArray).items.map {
            val p = (it as JsonArray).items
            Choice(p[0], (p[1] as JsonString).value)
        }

    private fun range(value: JsonValue): RangeSpec {
        val p = (value as JsonArray).items
        fun n(i: Int) = (p[i] as JsonNumber).value
        return RangeSpec(
            (p[0] as JsonString).value,
            n(1),
            n(2),
            n(3),
            (p[4] as JsonString).value,
            (p.getOrNull(5) as? JsonString)?.value,
        )
    }

    /** `PRIO`: the priority names, index 1 to 5. */
    val priorities: List<String> = (table("PRIO") as JsonArray).items.map { (it as JsonString).value }
    val goalsExplain: JsonValue = table("GOALS_EXPLAIN")
    val goalsEmpty: JsonValue = table("GOALS_EMPTY")
    val goalRemoveConfirm: String = text("GOAL_REMOVE_CONFIRM")
    val clarifyText: String = text("CLARIFY_TEXT")
    val questHead: JsonValue = table("QUEST_HEAD")
    val milestoneNote: String = text("MILESTONE_NOTE")
    val kindLabel: Map<String, String> =
        (table("KIND_LABEL") as JsonObject).members.mapValues { (it.value as JsonString).value }
    val routines: List<RangeSpec> = (table("ROUTINES") as JsonArray).items.map(::range)
    val mealRange: RangeSpec = range(table("MEAL_RANGE"))
    val ruleKinds: List<Choice> = pairs(table("RULE_KINDS"))
    val ruleLoads: List<Choice> = pairs(table("RULE_LOADS"))

    /** `NEW_RULE`: the commitment a new sheet starts from, as a stored rule. */
    val newRule: JsonObject = table("NEW_RULE") as JsonObject
    val customTags: List<Choice> = pairs(table("CUSTOM_TAGS"))
    val goalLevels: List<Choice> = pairs(table("GOAL_LEVELS"))
    val intensityOptions: List<Choice> =
        pairs((table("INTENSITY") as JsonObject)["options"]!!)
    val intensityNote: String = field("INTENSITY", "note")
    val prefRanges: List<RangeSpec> = (table("PREF_RANGES") as JsonArray).items.map(::range)
    val grace: RangeSpec = range(table("GRACE"))
    val horizonOptions: List<Choice> = pairs(table("HORIZON"))
    val missOptions: List<Choice> = pairs((table("MISSES") as JsonObject)["options"]!!)
    val missText: String = field("MISSES", "text")

    /** `SETTINGS_TEXT`'s member [key]: text, or (guide, data) an object of texts. */
    fun settingsText(key: String): JsonValue = (table("SETTINGS_TEXT") as JsonObject)[key]!!

    val tooShort: String = field("DRAG_WORDS", "tooShort")
    val dragWords: JsonValue = table("DRAG_WORDS")

    /** `DIAL`: the dial's radii, in the page's order. */
    val dial: JsonValue = table("DIAL")
    val miniSnap: Double = (table("MINI_SNAP") as JsonNumber).value
    val miniGroup: String = text("MINI_GROUP")
    val disruptTitle: String = field("DISRUPT", "title")
    val disruptNote: String = field("DISRUPT", "note")

    /** `TAG_ICON`: a tag's first letters and the icon it is drawn with, first match wins. */
    val tagIcons: List<Pair<String, String>> =
        pairs(table("TAG_ICON")).map { (it.value as JsonString).value to it.label }

    /** `DRAG_KINDS`: the kinds of rule whose ends are handles on the dial. */
    val dragKinds: Set<String> = (table("DRAG_KINDS") as JsonObject).members.keys

    /** `DOW`: the short day names, Sunday first. */
    val dayNames: List<String> = (table("DOW") as JsonArray).items.map { (it as JsonString).value }

    companion object {
        /** The file's place among the app's resources. */
        const val RESOURCE = "lifedirector/readmodel-words-b.json"

        private val TABLES =
            setOf(
                "PRIO",
                "GOALS_EXPLAIN",
                "GOALS_EMPTY",
                "GOAL_REMOVE_CONFIRM",
                "CLARIFY_TEXT",
                "QUEST_HEAD",
                "MILESTONE_NOTE",
                "KIND_LABEL",
                "ROUTINES",
                "MEAL_RANGE",
                "RULE_KINDS",
                "RULE_LOADS",
                "NEW_RULE",
                "CUSTOM_TAGS",
                "GOAL_LEVELS",
                "INTENSITY",
                "PREF_RANGES",
                "GRACE",
                "HORIZON",
                "MISSES",
                "SETTINGS_TEXT",
                "DRAG_WORDS",
                "DIAL",
                "MINI_SNAP",
                "MINI_GROUP",
                "DISRUPT",
                "TAG_ICON",
                "DRAG_KINDS",
                "DOW",
            )
        private val TOP = setOf("about", "tables", "engineVersion", "h12", "phrases")
        private const val PRIORITY_NAMES = 6
        private const val DAYS = 7

        /** The words packaged with the app. */
        val packaged: PanelWords by lazy {
            val stream =
                PanelWords::class.java.classLoader?.getResourceAsStream(RESOURCE)
                    ?: error("$RESOURCE is not packaged")
            read(stream.bufferedReader(Charsets.UTF_8).use { it.readText() })
        }

        /** The words in [text]; throws when the file is not exactly what the export writes. */
        fun read(text: String): PanelWords {
            val parsed = JsonReader.read(text)
            check(parsed is JsonRead.Parsed) { "$RESOURCE does not parse" }
            val o = parsed.value as? JsonObject ?: error("$RESOURCE: not an object")
            check(o.members.keys == TOP) { "$RESOURCE: members ${o.members.keys}" }
            val tables = (o["tables"] as? JsonObject ?: error("$RESOURCE: tables")).members
            check(tables.keys == TABLES) { "$RESOURCE: tables ${tables.keys}" }
            val phrases =
                (o["phrases"] as? JsonObject ?: error("$RESOURCE: phrases")).members.mapValues {
                    (fn, v) ->
                    (v as? JsonObject ?: error("$RESOURCE: phrases of $fn")).members.mapValues {
                        (it.value as? JsonString ?: error("$RESOURCE: $fn.${it.key}")).value
                    }
                }
            check(phrases.keys == PanelPhrases.REQUIRED.keys) {
                "$RESOURCE: phrases of ${phrases.keys}"
            }
            for ((fn, keys) in PanelPhrases.REQUIRED)
                check(phrases.getValue(fn).keys == keys) { "$RESOURCE: phrases of $fn differ" }
            val words =
                PanelWords(
                    tables,
                    (o["engineVersion"] as? JsonString ?: error("engineVersion")).value,
                    (o["h12"] as? JsonBool ?: error("h12")).value,
                    phrases,
                )
            words.check()
            return words
        }
    }

    /** Every typed table is read once here, so a file of the wrong shape is refused at load. */
    private fun check() {
        check(priorities.size == PRIORITY_NAMES) { "$RESOURCE: PRIO" }
        check(dayNames.size == DAYS) { "$RESOURCE: DOW" }
        for (k in listOf("title", "text")) {
            check((goalsExplain as JsonObject)[k] is JsonString)
            check((goalsEmpty as JsonObject)[k] is JsonString)
        }
        check((questHead as JsonObject).members.values.all { it is JsonString })
        check(routines.isNotEmpty() && prefRanges.isNotEmpty())
        check(newRule["title"] is JsonString)
        for (k in listOf("intensity", "restDays", "restNote", "horizon", "misses", "diagnostics", "lab"))
            check(settingsText(k) is JsonString) { "$RESOURCE: SETTINGS_TEXT.$k" }
        for (k in listOf("guide", "data"))
            check((settingsText(k) as JsonObject).members.values.all { it is JsonString })
        check((dial as JsonObject).members.values.all { it is JsonNumber })
        check((dragWords as JsonObject).members.values.all { it is JsonString })
        check(tagIcons.isNotEmpty() && dragKinds.isNotEmpty())
    }
}
