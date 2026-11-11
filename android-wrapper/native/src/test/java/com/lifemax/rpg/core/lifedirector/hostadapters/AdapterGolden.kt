package com.lifemax.rpg.core.lifedirector.hostadapters

import com.lifemax.rpg.core.economy.IdSource
import com.lifemax.rpg.core.quests.Js
import com.lifemax.rpg.core.quests.QuestContentLoader
import com.lifemax.rpg.core.quests.QuestContentRead
import com.lifemax.rpg.core.save.JsonArray
import com.lifemax.rpg.core.save.JsonNull
import com.lifemax.rpg.core.save.JsonObject
import com.lifemax.rpg.core.save.JsonRead
import com.lifemax.rpg.core.save.JsonReader
import com.lifemax.rpg.core.save.JsonString
import com.lifemax.rpg.core.save.JsonValue
import com.lifemax.rpg.core.save.PageReads
import java.io.File
import java.util.zip.GZIPInputStream

/**
 * The host adapter golden cases, `ld-adapters-golden.json.gz` (recorded from the page in Chromium;
 * format in `tools/gen_ld_adapters_golden.js`), read with the save codec's reader one line at a
 * time, and the recorded answers of the page's plan and Smart Diet code given to the adapter
 * through its ports.
 */
object AdapterGolden {
    const val FILE = "ld-adapters-golden.json.gz"

    /** The save fields the recorder compares (its FIELDS and EXTRA). */
    val FIELDS: List<String> =
        listOf(
            "activities",
            "attrXP",
            "skillXP",
            "restedXP",
            "sparks",
            "perk",
            "perksOwned",
            "attrStars",
            "ascended",
            "decayTotal",
            "decayApplied",
            "decayLog",
            "decay",
            "legacyCharXPBonus",
            "xpSchemaV2",
            "customRules",
            "quests",
            "questDone",
            "restDays",
            "restTokens",
            "restBoughtAt",
            "streakShields",
            "shieldTick",
            "shieldMiss",
            "shieldDays",
            "shieldMilestone",
            "shieldBoughtAt",
            "themesOwned",
            "theme",
            "toysBought",
            "nutriDone",
            "careDay",
            "mealsMeta",
            "foodLog",
            "money",
            "actXP",
            "plan",
            "health",
            "devMode",
            "created",
            "name",
            "dhMig",
            "dailyDraw",
        )

    /** One recorded case. */
    class Case(
        val id: String,
        val family: String,
        val rule: String,
        val input: JsonObject,
        val output: JsonObject,
    ) {
        val calls: List<JsonObject>
            get() = (input["calls"] as JsonArray).items.map { it as JsonObject }

        val returns: List<JsonValue>
            get() = (output["returns"] as JsonArray).items

        /**
         * The save before the calls: the fresh save, the case's fields over it, its absent ones
         * removed.
         */
        fun storedBefore(base: JsonObject): JsonObject {
            val m = LinkedHashMap(base.members)
            (input["state"] as JsonObject).members.forEach { (k, v) -> m[k] = v }
            (input["absent"] as? JsonArray)?.items?.forEach { m.remove(text(it)) }
            return JsonObject(m)
        }

        /** The save after the calls, as the page left it. */
        fun storedAfter(before: JsonObject): JsonObject {
            val m = LinkedHashMap(before.members)
            (output["state"] as JsonObject).members.forEach { (k, v) -> m[k] = v }
            (output["deleted"] as? JsonArray)?.items?.forEach { m.remove(text(it)) }
            return JsonObject(m)
        }

        val toasts: List<String>
            get() = (output["toasts"] as? JsonArray)?.items?.map(::text).orEmpty()

        fun outside(kind: String): JsonObject =
            ((output["outside"] as? JsonObject)?.get(kind) as? JsonObject) ?: JsonObject.EMPTY
    }

    private fun file(): File {
        val start = File(System.getProperty("user.dir") ?: ".").absoluteFile
        return generateSequence(start) { it.parentFile }
            .map { File(it, "lifemax_modular_v73/tests/fixtures/$FILE") }
            .firstOrNull { it.isFile } ?: error("$FILE not found above $start")
    }

    private fun parse(line: String): JsonObject {
        val read = JsonReader.read(line)
        check(read is JsonRead.Parsed) { "a line of $FILE does not parse: ${line.take(80)}" }
        return read.value as JsonObject
    }

    /** The fresh save every case's state is written against. */
    val base: JsonObject by lazy {
        val first =
            GZIPInputStream(file().inputStream()).bufferedReader(Charsets.UTF_8).use {
                it.readLine()
            }
        val header = parse(first.removeSuffix(",\"cases\":[") + "}")["header"] as JsonObject
        header["base"] as JsonObject
    }

    /** Every case, in the file's order. */
    val cases: List<Case> by lazy {
        val out = ArrayList<Case>()
        GZIPInputStream(file().inputStream()).bufferedReader(Charsets.UTF_8).useLines { lines ->
            for (line in lines) {
                if (!line.startsWith("{\"id\"")) continue
                val c = parse(line.removeSuffix(","))
                out +=
                    Case(
                        text(c["id"]),
                        text(c["family"]),
                        text(c["rule"]),
                        c["input"] as JsonObject,
                        c["output"] as JsonObject,
                    )
            }
        }
        out
    }

    /** The Daily Hand's templates, as the app packages them. */
    val library by lazy {
        (QuestContentLoader.packaged as QuestContentRead.Loaded).content.hand.templates
    }

    fun text(v: JsonValue?): String = (v as JsonString).value

    /** `String(v)` (undefined for a missing value). */
    fun jsString(v: JsonValue?): String = Js.string(v)

    /** `v || ''` as text, null when falsy. */
    fun truthyText(v: JsonValue?): String? = if (PageReads.truthy(v)) Js.string(v) else null

    fun jsNumber(v: JsonValue?): Double = Js.number(v)

    /** The ids the page mints, counted per case. */
    class CountedIds : IdSource {
        private var n = 0

        override fun uuid() = "00000000-0000-4000-8000-%012d".format(++n)

        override fun token() = "000d"
    }

    /**
     * The page's plan days as the recorder saw them; a key the page was never asked is a failure of
     * the case (the adapter asked something the page does not).
     */
    class RecordedPlanDays(private val days: JsonObject, private val done: JsonObject) : PlanDays {
        val unknown = ArrayList<String>()

        override fun day(key: String): PlanDay? {
            if (key !in days) {
                unknown += "planDay($key)"
                return null
            }
            return planDay(days[key])
        }

        override fun isDone(key: String): Boolean {
            if (key !in done) {
                unknown += "dayDone($key)"
                return false
            }
            return done[key] == com.lifemax.rpg.core.save.JsonBool.TRUE
        }
    }

    /** A plan day the page returned, as the port gives it. */
    fun planDay(v: JsonValue?): PlanDay? {
        val d = v as? JsonObject ?: return null
        fun opt(key: String) = (d[key] as? JsonString)?.value
        val items =
            (d["items"] as? JsonArray)?.items?.map { item ->
                when (item) {
                    is JsonString -> item.value
                    is JsonObject -> truthyText(item["name"]) ?: truthyText(item["title"]) ?: ""
                    else -> error("a plan item the port cannot give")
                }
            }
        val hint =
            listOf(d["min"], d["minutes"]).firstOrNull { PageReads.truthy(it) }?.let(::jsNumber)
        return PlanDay(
            kind = truthyText(d["kind"]),
            label = opt("label"),
            primary = opt("primary"),
            minutesHint = hint,
            items = items,
            description = opt("description"),
        )
    }

    /** Smart Diet's days as the recorder saw them. */
    class RecordedMealDays(private val days: JsonObject) : MealDays {
        val unknown = ArrayList<String>()

        override fun day(key: String): MealDay? {
            if (key !in days) {
                unknown += "sdDay($key)"
                return null
            }
            val d = days[key] as? JsonObject ?: return null
            return MealDay(
                (d["items"] as JsonArray).items.map {
                    val o = it as JsonObject
                    MealItem(
                        (o["slot"] as? JsonString)?.value,
                        (o["name"] as? JsonString)?.value,
                        jsNumber(o["kcal"]),
                        o["logged"] == com.lifemax.rpg.core.save.JsonBool.TRUE,
                        o["skipped"] == com.lifemax.rpg.core.save.JsonBool.TRUE,
                    )
                }
            )
        }
    }

    /** `{threw: name}`. */
    fun threw(name: String): JsonObject = JsonObject(linkedMapOf("threw" to JsonString(name)))

    val NULL: JsonValue = JsonNull
}
