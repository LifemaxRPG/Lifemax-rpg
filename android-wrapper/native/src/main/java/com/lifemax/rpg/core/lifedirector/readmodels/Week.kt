package com.lifemax.rpg.core.lifedirector.readmodels

import com.lifemax.rpg.core.economy.LifeDirector
import com.lifemax.rpg.core.economy.XpShare
import com.lifemax.rpg.core.lifedirector.CalendarMath
import com.lifemax.rpg.core.lifedirector.PageTypeError
import com.lifemax.rpg.core.lifedirector.PageValues
import com.lifemax.rpg.core.lifedirector.PageValues.member
import com.lifemax.rpg.core.lifedirector.PageValues.number
import com.lifemax.rpg.core.lifedirector.PageValues.numberText
import com.lifemax.rpg.core.lifedirector.PageValues.text
import com.lifemax.rpg.core.lifedirector.PageValues.truthy
import com.lifemax.rpg.core.lifedirector.PlanDay
import com.lifemax.rpg.core.lifedirector.WallClock
import com.lifemax.rpg.core.save.JsonArray
import com.lifemax.rpg.core.save.JsonNull
import com.lifemax.rpg.core.save.JsonValue
import kotlin.math.floor

/** LifeMax's XP preview of a session (`LifeMaxHost.xpFor`'s answer, the members read here). */
data class XpPreview(val xp: Double, val primary: String, val split: List<XpShare>)

/**
 * RM-14: LifeMax's XP preview, `Host.xpFor(type, minutes, 1, dateKey)` (quality 1, not
 * committed). Null when LifeMax is not connected or the preview fails, which the page treats
 * alike. The page's host computes it from the main save (LD-2); native gives it
 * `core/economy LifeDirector.xpFor` on the native save, and the golden replay the recorded answers.
 */
fun interface SessionXpPreview {
    fun preview(type: String, minutes: Double, dateKey: String): XpPreview?
}

/** One day row of the week (RM-15): its day and blocks (`dayBlocks`), and what the row says. */
data class WeekRow(
    val index: Int,
    val day: PlanDay,
    val blocks: List<DayBlock>,
    val sessions: Int,
    val done: Int,
    val minutes: Double,
    val name: String,
    val aria: String,
)

/** RM-16: the XP an attribute gets from the week's sessions, still planned and done. */
data class AttributeXp(val attr: String, val plan: Double, val done: Double) {
    val total: Double
        get() = plan + done
}

/** One of the week's three figures (RM-17): the value and its label. */
data class WeekStat(val value: String, val label: String)

/** RM-15 to RM-17 `weekModel()`: everything Week shows, decided once (logic.js:1318-1345). */
data class WeekModel(
    val rows: List<WeekRow>,
    val stats: List<WeekStat>,
    val attributes: List<AttributeXp>,
    val maxAttribute: Double,
    val fitText: String,
)

/** RM-14 to RM-18, RM-20. */
internal object Week {
    private const val MINUTE = 60.0
    private const val HOUR = 60.0
    private const val PERCENT = 100.0

    /**
     * RM-14 `xpOf(inst, minutes)`: the preview for [session]'s activity type (LD-1
     * `typeForTag`), [minutes] when truthy else its planned length in whole minutes, on its local
     * date in the plan's zone. A date the zone cannot give (an invalid start) is a failed preview.
     */
    fun xpOf(m: DirectorReadModels, session: StoredSession, minutes: JsonValue?): XpPreview? {
        val type = LifeDirector.typeForTag(text(session.tag))
        val length =
            if (truthy(minutes)) number(minutes)
            else PageValues.round((session.endEpoch - session.startEpoch) / MINUTE)
        val key =
            try {
                CalendarMath.key(WallClock.localParts(session.startEpoch, m.zone()).date)
            } catch (e: RuntimeException) {
                return null
            }
        return m.xp.preview(type, length, key)
    }

    /** RM-15 to RM-17. */
    fun model(m: DirectorReadModels): WeekModel {
        val s = m.requireSchedule()
        val words = m.words
        val days = m.horizonDays()
        var nSes = 0
        var nDone = 0
        var minTot = 0.0
        var xpTot = 0.0
        val byAttr = LinkedHashMap<String, DoubleArray>()
        val rows =
            days.mapIndexed { k, d ->
                val blocks = DayBlocks.of(m, s, d)
                val insts = blocks.mapNotNull { it.session }
                val done = insts.count { it.sessionState == "done" }
                var mins = 0.0
                for (i in insts) mins += PageValues.round((i.endEpoch - i.startEpoch) / MINUTE)
                nSes += insts.size
                nDone += done
                minTot += mins
                for (i in insts) {
                    if (!i.isPlanned && i.sessionState != "done") continue
                    val x = xpOf(m, i, i.actualMinutes) ?: continue
                    xpTot += x.xp
                    for (sp in x.split) {
                        val a = byAttr.getOrPut(sp.attr) { DoubleArray(2) }
                        a[if (i.isPlanned) 0 else 1] += sp.xp
                    }
                }
                val name = if (k == 0) words.phrase("today") else words.dayNames[d.date.weekday]
                val label =
                    if (k == 0) words.phrase("today")
                    else words.dayNames[d.date.weekday] + words.phrase("ariaGap") + d.date.day
                WeekRow(
                    k,
                    d,
                    blocks,
                    insts.size,
                    done,
                    mins,
                    name,
                    label +
                        words.phrase("ariaColon") +
                        insts.size +
                        words.phrase("ariaSessions") +
                        mins(mins, words),
                )
            }
        val capacity =
            member(s, "capacity") as? JsonArray ?: throw PageTypeError("capacity.reduce")
        var free = 0.0
        var rest = 0.0
        for (c in capacity.items) {
            if (c == JsonNull) throw PageTypeError("Cannot read properties of null (reading 'freeMin')")
            free += number(member(c, "freeMin"))
            rest += number(member(c, "recoveryMin"))
        }
        val q = member(member(s, "validation") ?: throw PageTypeError("validation"), "qualityMetrics")
        val coverage = member(q ?: throw PageTypeError("qualityMetrics"), "coverageRequired")
        val attrs =
            byAttr.entries
                .map { AttributeXp(it.key, it.value[0], it.value[1]) }
                .sortedWith { a, b -> sign(b.total - a.total) }
        val maxA = attrs.fold(1.0) { acc, a -> maxOf(acc, a.total) }
        val stats =
            listOf(
                WeekStat(
                    nSes.toString(),
                    words.phrase("statSessions") +
                        (if (nDone != 0)
                            words.phrase("statComma") + nDone + words.phrase("statDone")
                        else ""),
                ),
                WeekStat(mins(minTot, words), words.phrase("statPlanned")),
                WeekStat(
                    if (truthy(xpTot)) words.phrase("statAbout") + numberText(xpTot)
                    else words.phrase("statNone"),
                    words.phrase("statXp"),
                ),
            )
        val fit =
            mins(rest, words) +
                words.phrase("fitDowntime") +
                mins(free, words) +
                words.phrase("fitFree") +
                numberText(PageValues.round(number(coverage) * PERCENT)) +
                words.phrase("fitCoverage")
        return WeekModel(rows, stats, attrs, maxA, fit)
    }

    /**
     * RM-20 `mins(m)`: whole hours and the minutes left (`2h 15m`, `3h`), or minutes under an
     * hour (`45m`), numbers as JavaScript writes them.
     */
    fun mins(m: Double, words: ReadModelWords): String {
        val h = words.phrase("hours")
        val unit = words.phrase("minutes")
        if (!(m >= HOUR)) return numberText(m) + unit
        val rest = m % HOUR
        return numberText(floor(m / HOUR)) +
            h +
            (if (truthy(rest)) words.phrase("gap") + numberText(rest) + unit else "")
    }

    private fun sign(d: Double): Int = if (d > 0) 1 else if (d < 0) -1 else 0
}
