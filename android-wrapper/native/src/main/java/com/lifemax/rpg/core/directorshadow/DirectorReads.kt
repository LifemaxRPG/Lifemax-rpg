package com.lifemax.rpg.core.directorshadow

import com.lifemax.rpg.core.economy.XpShare
import com.lifemax.rpg.core.lifedirector.EngineRefusal
import com.lifemax.rpg.core.lifedirector.PageRangeError
import com.lifemax.rpg.core.lifedirector.PageTypeError
import com.lifemax.rpg.core.lifedirector.PlanDay
import com.lifemax.rpg.core.lifedirector.readmodels.DirectorReadModels
import com.lifemax.rpg.core.lifedirector.readmodels.ReadModelJson
import com.lifemax.rpg.core.lifedirector.readmodels.ReadModelWords
import com.lifemax.rpg.core.lifedirector.readmodels.SessionXpPreview
import com.lifemax.rpg.core.lifedirector.readmodels.XpPreview
import com.lifemax.rpg.core.save.JsonArray
import com.lifemax.rpg.core.save.JsonNull
import com.lifemax.rpg.core.save.JsonNumber
import com.lifemax.rpg.core.save.JsonObject
import com.lifemax.rpg.core.save.JsonString
import com.lifemax.rpg.core.save.JsonValue

/**
 * Native's read models in the shape the page's half sends them (SH-12, the read-model recorder's
 * shape, LIFE_DIRECTOR_READMODELS_SPEC section 7): `days` (`horizonDays`), `unplanned` (the first
 * day's availability), `blocks` (each day's `dayBlocks`), `timeline`, `now`, `week` (`weekModel`
 * without the rows' day and blocks), `native` (`nativeWeek()`, the projection's `week`) and `xp`
 * (every XP preview asked, in order). A section the page's code throws in is `{threw: name}`.
 */
object DirectorReads {
    val SECTIONS = listOf("days", "unplanned", "blocks", "timeline", "now", "week", "native", "xp")

    /** The page's error name for a native throw that stands for one of the page's (SH-13). */
    fun errorName(e: Throwable): String? =
        when (e) {
            is PageTypeError -> "TypeError"
            is PageRangeError -> "RangeError"
            is IllegalArgumentException -> "RangeError"
            is EngineRefusal -> "Error"
            else -> null
        }

    /** [f]'s answer, or `{threw: name}` for a throw that stands for the page's; anything else is native's own error. */
    inline fun section(f: () -> JsonValue): JsonValue =
        try {
            f()
        } catch (e: RuntimeException) {
            val name = errorName(e) ?: throw e
            JsonObject(mapOf("threw" to JsonString(name)))
        }

    fun compute(state: JsonValue, facts: ReadFacts, words: ReadModelWords): JsonObject {
        val xp = RecordedXp(facts.xp)
        val m = DirectorReadModels(state, facts.nowMillis, facts.deviceZone, xp, words)
        val out = LinkedHashMap<String, JsonValue>()
        var days: List<PlanDay>? = null
        out["days"] = section { ReadModelJson.days(m.horizonDays().also { days = it }) }
        out["unplanned"] = section {
            ReadModelJson.segments(m.unplannedSegments(days?.first() ?: throw PageTypeError("days")))
        }
        out["blocks"] = section {
            JsonArray((days ?: throw PageTypeError("days")).map { ReadModelJson.blocks(m.dayBlocks(it)) })
        }
        out["timeline"] = section { ReadModelJson.blocks(m.liveTimeline()) }
        out["now"] = section { ReadModelJson.now(m.nowState()) }
        out["week"] = section { ReadModelJson.weekModel(m.weekModel()) }
        out["native"] = section { ReadModelJson.week(m.weekProjection()) }
        out["xp"] = JsonArray(xp.calls)
        return JsonObject(out)
    }

    /**
     * LifeMax's XP previews as the page's host answered them while its read models ran (SH-14): in
     * order, by their arguments; a question the page did not ask is answered as a failed preview,
     * and every question is kept so the list is compared too.
     */
    class RecordedXp(private val recorded: List<JsonValue>) : SessionXpPreview {
        val calls = ArrayList<JsonValue>()

        override fun preview(type: String, minutes: Double, dateKey: String): XpPreview? {
            val args = JsonArray(listOf(JsonString(type), JsonNumber(minutes), JsonNumber(1.0), JsonString(dateKey)))
            val r = recorded.getOrNull(calls.size) as? JsonObject
            val out = if (r != null && r.members["args"] == args) r.members["out"] ?: JsonNull else JsonNull
            calls += JsonObject(linkedMapOf("args" to args, "out" to out))
            val o = out as? JsonObject ?: return null
            val xp = (o.members["xp"] as? JsonNumber)?.value ?: return null
            val primary = (o.members["primary"] as? JsonString)?.value ?: return null
            val split = (o.members["split"] as? JsonArray)?.items?.map {
                val s = it as? JsonObject ?: return null
                XpShare((s.members["attr"] as? JsonString)?.value ?: return null, (s.members["xp"] as? JsonNumber)?.value ?: return null)
            } ?: return null
            return XpPreview(xp, primary, split)
        }
    }
}
