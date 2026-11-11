package com.lifemax.rpg.core.lifedirector.readmodels

import com.lifemax.rpg.core.save.JsonArray
import com.lifemax.rpg.core.save.JsonNumber
import com.lifemax.rpg.core.save.JsonObject
import com.lifemax.rpg.core.save.JsonRead
import com.lifemax.rpg.core.save.JsonReader
import com.lifemax.rpg.core.save.JsonString
import com.lifemax.rpg.core.save.JsonValue

/** One entry of the week's legend (`WEEK_LEGEND`): a block class and its label. */
data class LegendEntry(val kind: String, val label: String)

/**
 * RM-21: the page words and tables the read models use, read from
 * `lifedirector/readmodel-words.json`, which the page's recorder writes from the page itself
 * (`lifemax_modular_v73/tools/gen_ld_readmodels_golden.js`, tables by the controller's constant,
 * inline phrases by the function and position they have in `logic.js`) and its web test keeps equal
 * to the page: no word is retyped here. The reader is strict: a missing or unknown member, or a
 * value of the wrong kind, refuses the file, and [packaged] then throws on first use.
 */
class ReadModelWords(
    /** `DOW`: the short day names, Sunday first. */
    val dayNames: List<String>,
    /** `KIND_CLASS`: a block kind's class (`sleep`, `work`, ...). */
    val kindClass: Map<String, String>,
    val weekLegend: List<LegendEntry>,
    val weekTicks: List<String>,
    val yieldNote: String,
    val allFit: String,
    /** `SOON`, `ENDING`, `LATE` (seconds, RM-13). */
    val soonSeconds: Double,
    val endingSeconds: Double,
    val lateSeconds: Double,
    /** The inline phrases, by the names the export gives them (RM-21). */
    val phrases: Map<String, String>,
    /** `NativeTabKit.color(ATTR_COL[a])` for each attribute, as the page computes it (ARGB). */
    val attributeColours: Map<String, Double>,
    /** The colour of an attribute LifeMax has no colour for (`#10B981`, ARGB). */
    val fallbackColour: Double,
) {
    /** The phrase [key]; a key the export does not have is a programming error. */
    fun phrase(key: String): String = phrases[key] ?: error("readmodel-words.json has no phrase $key")

    companion object {
        /** The file's place among the app's resources. */
        const val RESOURCE = "lifedirector/readmodel-words.json"

        private val PHRASES =
            setOf(
                "freeTime",
                "today",
                "ariaGap",
                "ariaColon",
                "ariaSessions",
                "fallbackColour",
                "statSessions",
                "statComma",
                "statDone",
                "statPlanned",
                "statAbout",
                "statNone",
                "statXp",
                "fitDowntime",
                "fitFree",
                "fitCoverage",
                "doneMark",
                "noMinutes",
                "yieldTitle",
                "fitTitle",
                "times",
                "tier",
                "unfitSession",
                "unfitPlural",
                "unfitTail",
                "hours",
                "gap",
                "minutes",
            )
        private val TOP =
            setOf(
                "about",
                "dayNames",
                "kindClass",
                "weekLegend",
                "weekTicks",
                "yieldNote",
                "allFit",
                "thresholds",
                "phrases",
                "colours",
            )

        /** The words packaged with the app. */
        val packaged: ReadModelWords by lazy {
            val stream =
                ReadModelWords::class.java.classLoader?.getResourceAsStream(RESOURCE)
                    ?: error("$RESOURCE is not packaged")
            read(stream.bufferedReader(Charsets.UTF_8).use { it.readText() })
        }

        /** The words in [text]; throws when the file is not exactly what the export writes. */
        fun read(text: String): ReadModelWords {
            val parsed = JsonReader.read(text)
            check(parsed is JsonRead.Parsed) { "$RESOURCE does not parse" }
            val o = objectOf(parsed.value, TOP)
            val thresholds = objectOf(o["thresholds"], setOf("soon", "ending", "late"))
            val phrases = objectOf(o["phrases"], PHRASES).members.mapValues { textOf(it.value) }
            check(phrases.keys == PHRASES) { "$RESOURCE lacks phrases ${PHRASES - phrases.keys}" }
            val colours = objectOf(o["colours"], setOf("attributes", "fallback"))
            return ReadModelWords(
                dayNames = textsOf(o["dayNames"]).also { check(it.size == DAYS) },
                kindClass = (o["kindClass"] as? JsonObject ?: error("kindClass")).members.mapValues {
                    textOf(it.value)
                },
                weekLegend =
                    listOf(o["weekLegend"]).flatMap { (it as JsonArray).items }.map {
                        val e = objectOf(it, setOf("kind", "label"))
                        LegendEntry(textOf(e["kind"]), textOf(e["label"]))
                    },
                weekTicks = textsOf(o["weekTicks"]),
                yieldNote = textOf(o["yieldNote"]),
                allFit = textOf(o["allFit"]),
                soonSeconds = numberOf(thresholds["soon"]),
                endingSeconds = numberOf(thresholds["ending"]),
                lateSeconds = numberOf(thresholds["late"]),
                phrases = phrases,
                attributeColours =
                    (colours["attributes"] as? JsonObject ?: error("colours")).members.mapValues {
                        numberOf(it.value)
                    },
                fallbackColour = numberOf(colours["fallback"]),
            )
        }

        private const val DAYS = 7

        private fun objectOf(value: JsonValue?, keys: Set<String>): JsonObject {
            val o = value as? JsonObject ?: error("$RESOURCE: an object was expected")
            check(o.members.keys == keys) { "$RESOURCE: members ${o.members.keys}, wanted $keys" }
            return o
        }

        private fun textOf(value: JsonValue?): String =
            (value as? JsonString)?.value ?: error("$RESOURCE: text was expected")

        private fun numberOf(value: JsonValue?): Double =
            (value as? JsonNumber)?.value ?: error("$RESOURCE: a number was expected")

        private fun textsOf(value: JsonValue?): List<String> =
            (value as? JsonArray ?: error("$RESOURCE: a list was expected")).items.map(::textOf)
    }
}
