package com.lifemax.rpg.core.lifedirector

import com.lifemax.rpg.core.save.JsonArray
import com.lifemax.rpg.core.save.JsonNumber
import com.lifemax.rpg.core.save.JsonObject
import com.lifemax.rpg.core.save.JsonRead
import com.lifemax.rpg.core.save.JsonReader
import com.lifemax.rpg.core.save.JsonString
import com.lifemax.rpg.core.save.JsonValue

/**
 * LDD-6: an activity template, a goal's recurring session kind. [members] are exactly the members
 * the page's template object has (an archetype's as the page's table writes them, a custom one as
 * LDD-7 builds it), so a member the page leaves out is absent here too; the getters read the ones
 * the demand layer uses, as stored.
 */
class ActivityTemplate(val members: Map<String, JsonValue>) {
    operator fun get(key: String): JsonValue? = members[key]

    /** The template's key within its goal (`strength`, `vocab`), as text. */
    val key: String
        get() = PageValues.text(members["key"])

    /** The activity tag (LDD-1), as stored. */
    val tag: JsonValue?
        get() = members["tag"]

    /** `goalId:key`, set by LDD-6 and LDD-7. */
    val templateId: String
        get() = PageValues.text(members["templateId"])

    /** A copy with [extra] members set over these. */
    fun with(vararg extra: Pair<String, JsonValue>): ActivityTemplate =
        ActivityTemplate(LinkedHashMap(members).apply { putAll(extra) })
}

/** LDD-8: a goal archetype: its [title], the title [keywords] LDD-5 looks for, its [templates]. */
class Archetype(
    val title: String,
    val keywords: List<String>,
    val templates: List<ActivityTemplate>,
)

/** LDD-3: the default demand of a tag root: physical and cognitive intensity and minutes. */
data class TagProfile(val physical: Double, val cognitive: Double, val minutes: Double)

/**
 * The engine's demand tables (`engine.js` `ARCHETYPES`, `TAG_PROFILE`, `QUEST_WEIGHT`,
 * `LOAD_DRAIN`), read from `lifedirector/demand-tables.json`, which the page's recorder writes
 * (`lifemax_modular_v73/tools/gen_ld_demand_golden.js`) and its web test keeps equal to the page:
 * the numbers are never retyped. The reader is strict: a missing or unknown member, or a value of
 * the wrong kind, refuses the file, and [packaged] then throws on first use.
 */
object DemandTables {
    /** The file's place among the app's resources. */
    const val RESOURCE = "lifedirector/demand-tables.json"

    /** The tables as read from [text]. */
    class Tables(
        val archetypes: Map<String, Archetype>,
        val tagProfiles: Map<String, TagProfile>,
        val questWeights: Map<String, Double>,
        val loadDrain: Map<Int, Pair<Double, Double>>,
    )

    /** The tables packaged with the app. */
    val packaged: Tables by lazy {
        val bytes =
            DemandTables::class.java.getResourceAsStream("/$RESOURCE")?.use { it.readBytes() }
        read(String(requireNotNull(bytes) { "$RESOURCE is missing" }, Charsets.UTF_8))
    }

    /** The archetypes, in the page's order. */
    val archetypes: Map<String, Archetype>
        get() = packaged.archetypes

    private val TOP = setOf("about", "archetypes", "tagProfile", "questWeight", "loadDrain")
    private val ARCHETYPE = setOf("title", "keywords", "templates")
    private val PROFILE = setOf("IP", "IC", "d")
    private val TEMPLATE =
        setOf(
            "key",
            "title",
            "tag",
            "d",
            "f",
            "IP",
            "IC",
            "spacingMinH",
            "spacingMinHNovice",
            "spacingIdealH",
            "avoidBeforeSleepMin",
            "phases",
            "volumeDriver",
            "preferRestDay",
            "maxPerBwc",
            "requiresPrior",
        )
    private val TEMPLATE_REQUIRED = setOf("key", "title", "tag", "d", "f", "IP", "IC")

    /** Reads the tables' text; throws [IllegalStateException] naming the first fault. */
    fun read(text: String): Tables {
        val parsed = JsonReader.read(text)
        check(parsed is JsonRead.Parsed) { "$RESOURCE: not JSON" }
        val top = fields(parsed.value, "", TOP, TOP)
        val archetypes = linkedMapOf<String, Archetype>()
        for ((id, value) in objectOf(top["archetypes"], "archetypes").members) {
            val a = fields(value, id, ARCHETYPE, ARCHETYPE)
            val templates =
                arrayOf(a["templates"], "$id.templates").mapIndexed { i, t ->
                    val m = fields(t, "$id.templates[$i]", TEMPLATE, TEMPLATE_REQUIRED)
                    check(m["key"] is JsonString && m["tag"] is JsonString) { "$id[$i]: key, tag" }
                    ActivityTemplate(m)
                }
            archetypes[id] =
                Archetype(
                    textOf(a["title"], "$id.title"),
                    arrayOf(a["keywords"], "$id.keywords").map { textOf(it, "$id.keywords") },
                    templates,
                )
        }
        val profiles =
            objectOf(top["tagProfile"], "tagProfile").members.mapValues { (root, v) ->
                val p = fields(v, root, PROFILE, PROFILE)
                TagProfile(numberOf(p["IP"], root), numberOf(p["IC"], root), numberOf(p["d"], root))
            }
        check("general" in profiles) { "tagProfile: no general" }
        val weights =
            objectOf(top["questWeight"], "questWeight").members.mapValues { (k, v) ->
                numberOf(v, k)
            }
        val drain =
            objectOf(top["loadDrain"], "loadDrain").members.entries.associate { (k, v) ->
                val pair = arrayOf(v, "loadDrain.$k").map { numberOf(it, k) }
                check(pair.size == 2) { "loadDrain.$k: two numbers" }
                requireNotNull(k.toIntOrNull()) { "loadDrain.$k" } to (pair[0] to pair[1])
            }
        return Tables(archetypes, profiles, weights, drain)
    }

    private fun fields(
        value: JsonValue?,
        path: String,
        known: Set<String>,
        required: Set<String>,
    ): Map<String, JsonValue> {
        val members = objectOf(value, path).members
        check(known.containsAll(members.keys)) { "$path: unknown ${members.keys - known}" }
        check(members.keys.containsAll(required)) { "$path: missing ${required - members.keys}" }
        return members
    }

    private fun objectOf(value: JsonValue?, path: String): JsonObject =
        value as? JsonObject ?: error("$path: not an object")

    private fun arrayOf(value: JsonValue?, path: String): List<JsonValue> =
        (value as? JsonArray)?.items ?: error("$path: not a list")

    private fun textOf(value: JsonValue?, path: String): String =
        (value as? JsonString)?.value ?: error("$path: not text")

    private fun numberOf(value: JsonValue?, path: String): Double =
        (value as? JsonNumber)?.value ?: error("$path: not a number")
}
