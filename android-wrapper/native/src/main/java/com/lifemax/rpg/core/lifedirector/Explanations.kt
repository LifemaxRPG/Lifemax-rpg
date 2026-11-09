package com.lifemax.rpg.core.lifedirector

import com.lifemax.rpg.core.lifedirector.PageValues.member
import com.lifemax.rpg.core.lifedirector.PageValues.numberText
import com.lifemax.rpg.core.lifedirector.PageValues.round
import com.lifemax.rpg.core.lifedirector.PageValues.text
import com.lifemax.rpg.core.lifedirector.PageValues.truthy
import com.lifemax.rpg.core.lifedirector.ScheduleJson.bool
import com.lifemax.rpg.core.lifedirector.ScheduleJson.num
import com.lifemax.rpg.core.lifedirector.ScheduleJson.obj
import com.lifemax.rpg.core.lifedirector.ScheduleJson.str
import com.lifemax.rpg.core.save.JsonArray
import com.lifemax.rpg.core.save.JsonNull
import com.lifemax.rpg.core.save.JsonNumber
import com.lifemax.rpg.core.save.JsonValue

/** LDO-2 `explainInstance` (`engine.js:1440-1466`): why a session is where it is. */
object Explanations {
    private const val TENTHS = 10.0
    private const val HUNDREDTHS = 100.0
    private const val PERCENT = 100.0
    private const val SCARCE_DAYS = 2
    private const val SLOT_MINUTES = 15.0
    private const val LONG_SPACING_HOURS = 24.0
    private const val TOP_FACTORS = 4
    private const val ACTIVE_QUEST = "ACTIVE_QUEST"

    private val TIER_TEXT =
        mapOf(
            "CRITICAL_DEADLINE" to "its quest deadline is less than a day away",
            ACTIVE_QUEST to "it advances an active quest",
            "SIDE_QUEST" to "it is an optional side quest placed in spare capacity",
            "WEEKLY_MINIMUM" to "it keeps the goal at its weekly minimum",
            "WEEKLY_TARGET" to "it moves the goal toward its weekly target",
            "STRETCH_VOLUME" to "it is optional extra volume your intensity setting allows",
            "RESIDUAL" to "it finishes the rest of a partially completed session",
            "REPLACEMENT" to "it replaces a session that was missed or displaced",
        )
    private val PHASE_TEXT =
        mapOf(
            WakePhase.WAKE to "just after waking",
            WakePhase.PEAK to "your primary energy peak",
            WakePhase.DIP to "your mid-cycle dip",
            WakePhase.SECONDARY to "your secondary peak",
            WakePhase.WIND to "your wind-down hours",
        )

    /** `Math.round(v * 10) / 10` and its kin. */
    private fun rounded(v: Double, scale: Double) = round(v * scale) / scale

    /**
     * LDO-2. A kept or pinned session says so (its stored tier, no factors, locked). Otherwise its
     * placement is scored again where it is, on the state as the plan ends (the session itself in
     * it; a task whose urgency base is not a number is prepared again first, without goal
     * information, as the page does): the summary's sentences in the page's order, the four largest
     * factors (each rounded to tenths, largest first, ties in the factors' order), the fairness
     * multiplier to hundredths, the penalties, the placement record's selection numbers ([record],
     * null when it has none), the energies to hundredths and the wake phase.
     */
    fun explain(s: PlanState, x: PlanSession, record: PlacementRecord?): JsonValue {
        val stored = x.stored
        if (stored != null || x.commitment == null) {
            val locked = truthy(member(stored, "isUserLocked"))
            return obj {
                this["tier"] = member(stored, "tier")
                this["summary"] =
                    str(
                        if (locked)
                            "You pinned this time, so Life Director never moves it automatically."
                        else "Already started; kept in place."
                    )
                this["factors"] = JsonArray.EMPTY
                this["locked"] = bool(true)
            }
        }
        val c = x.commitment
        val t = c.task
        if (t.prepared.urgencyBase.isNaN())
            t.prepared =
                TaskPreparation.prepare(
                    TaskPreparation.demandOf(t.task),
                    emptyMap(),
                    s.slots.startEpoch,
                    s.slots.endEpoch,
                    s.now,
                )
        val day = s.cycles.first { it.cycle.id == x.cycleId }
        val slot = s.slots.slotAt(x.startEpoch).toInt()
        val d = PlacementScore.detail(s, t, day, slot, x.durationSlots.toInt())
        val factors =
            d.factors
                .map { it.first to rounded(it.second, TENTHS) }
                .sortedWith { a, b -> PageValues.byDifference(b.second - a.second) }
        val quests = x.questLinks.map { text(member(it, "questTitle")) }
        val reason = t.task.tierReason
        var why = TIER_TEXT[reason] ?: "it serves your goals"
        if (reason == ACTIVE_QUEST && quests.isNotEmpty())
            why = "it advances " + quests.joinToString(" and ") { "“$it”" }
        val goalTitle = t.task.goalTitle
        if (reason.startsWith("WEEKLY") && !goalTitle.isNullOrEmpty())
            why = PageText.replaceFirst(why, "the goal", "“$goalTitle”")
        val parts = ArrayList<String>()
        parts += "Scheduled because $why."
        parts +=
            "This time falls in " +
                PHASE_TEXT.getValue(d.phase) +
                " (estimated energy: body " +
                numberText(round(d.energyP * PERCENT)) +
                "%, mind " +
                numberText(round(d.energyC * PERCENT)) +
                "%)."
        if (record != null && record.count != 0 && record.count <= SCARCE_DAYS)
            parts +=
                "Only ${record.count} day" +
                    (if (record.count == 1) "" else "s") +
                    " could fit it, so it was placed before more flexible tasks."
        if (c.compressed)
            parts +=
                "Shortened to " +
                    numberText(x.durationSlots * SLOT_MINUTES) +
                    " min (from " +
                    numberText(c.optSlots * SLOT_MINUTES) +
                    ") to fit the available time."
        val spaced = s.spacing[x.spacingKey]
        if (
            spaced != null &&
                spaced.size > 1 &&
                PageValues.atLeast(t.task.spacingMinH, JsonNumber(LONG_SPACING_HOURS))
        )
            parts +=
                "Kept at least " +
                    text(t.task.spacingMinH) +
                    "h from similar sessions for recovery."
        if (quests.isNotEmpty() && x.goalLinks.isNotEmpty() && reason != ACTIVE_QUEST)
            parts +=
                "Also counts toward " +
                    quests.joinToString(", ") { "“$it”" } +
                    " — one session, no duplicate scheduling."
        if (record?.move == "SWAP")
            parts += "Placed by local repair after swapping with a more flexible task."
        if (record?.move == "COMPRESSION") parts += "Placed by shortening flexible tasks that day."
        if (d.restDay && t.high)
            parts += "It lands on a preferred rest day because no better slot existed."
        return obj {
            this["tier"] = num(c.tier)
            this["tierReason"] = str(reason)
            this["summary"] = str(parts.joinToString(" "))
            this["factors"] =
                JsonArray(
                    factors.take(TOP_FACTORS).map { (k, v) ->
                        obj {
                            this["factor"] = str(k)
                            this["points"] = num(v)
                        }
                    }
                )
            this["fairness"] = num(rounded(d.fairness, HUNDREDTHS))
            this["penalties"] = obj {
                this["fragmentation"] = num(d.fragmentation)
                this["movement"] = num(d.movement)
                this["compression"] = num(d.compression)
            }
            this["selection"] = record?.let(::selection) ?: JsonNull
            this["energy"] = obj {
                this["physical"] = num(rounded(d.energyP, HUNDREDTHS))
                this["cognitive"] = num(rounded(d.energyC, HUNDREDTHS))
            }
            this["wakePhase"] = str(d.phase.name)
        }
    }

    /**
     * The record's selection numbers: regret to tenths, days (null for 0), key (null for 0 or NaN),
     * phase, move.
     */
    private fun selection(r: PlacementRecord): JsonValue = obj {
        val regret = r.regret.takeIf(PageValues::truthy) ?: 0.0
        this["regret"] = num(rounded(regret, TENTHS))
        this["feasibleDays"] = if (r.count != 0) num(r.count) else JsonNull
        this["key"] = r.key?.takeIf(PageValues::truthy)?.let(::num) ?: JsonNull
        this["phase"] = num(r.phase)
        this["move"] = r.move?.let(::str) ?: JsonNull
    }
}

/** The page's text operations where JavaScript's differ from Kotlin's. */
object PageText {
    /**
     * `text.replace(pattern, replacement)` with a text pattern: the first occurrence only, and the
     * replacement's `$` patterns read as JavaScript reads them (`$$` a dollar, `$&` the match, ``
     * $` `` what precedes it, `$'` what follows; anything else, `$1` included, as written).
     */
    fun replaceFirst(text: String, pattern: String, replacement: String): String {
        val at = text.indexOf(pattern)
        if (at < 0) return text
        val before = text.substring(0, at)
        val after = text.substring(at + pattern.length)
        val out = StringBuilder()
        var i = 0
        while (i < replacement.length) {
            val ch = replacement[i]
            if (ch == '$' && i + 1 < replacement.length) {
                when (replacement[i + 1]) {
                    '$' -> {
                        out.append('$')
                        i += 2
                        continue
                    }
                    '&' -> {
                        out.append(pattern)
                        i += 2
                        continue
                    }
                    '`' -> {
                        out.append(before)
                        i += 2
                        continue
                    }
                    '\'' -> {
                        out.append(after)
                        i += 2
                        continue
                    }
                }
            }
            out.append(ch)
            i++
        }
        return before + out + after
    }
}
