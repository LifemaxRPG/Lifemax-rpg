package com.lifemax.rpg.core.quests

import com.lifemax.rpg.core.economy.jsRound
import com.lifemax.rpg.core.save.JsonArray
import com.lifemax.rpg.core.save.JsonNull
import com.lifemax.rpg.core.save.JsonObject
import com.lifemax.rpg.core.save.JsonValue
import java.math.BigDecimal
import java.math.RoundingMode

/**
 * The quest board's lists, order and summary (QUESTS_SPEC section 6: `renderQuests` and
 * `qosUpdateSummary`) and the 3D table's ledger (QST-59, QST-60), computed from the save as the
 * page holds it. It reproduces the page's rules, their accidents included: a nutrition epic shows
 * in the Nutrition card and among the epics, a quest with another cadence shows nowhere [QD11], and
 * the available XP ignores every cap and perk [QD12]. Nothing here draws or writes; the Daily Hand
 * cards above the lists, the badges at the foot of the ledger (their catalogue is not in the kit)
 * and the page's `dhAutoVerify` are out.
 *
 * Where the page would throw (a `null` in `quests`, no `questDone`, no character level) the result
 * is [HandStep.Threw] with the save untouched: a board draw changes nothing.
 */
class QuestBoard(tuning: BoardTuning = BoardTuning.PAGE) {
    private val rules = QuestRules(tuning)

    /**
     * `renderQuests()`'s data. [level] is `characterLevel().level`, or null when that call would
     * throw (it is read only for epics).
     */
    fun project(save: JsonObject, clock: BoardClock, level: Int?): HandStep<BoardView> =
        guard(save) {
            val quests = questList(save)
            val daily = quests.filter { isDaily(it, clock) }
            val weekly =
                quests.filter { Js.isText(Js.prop(it, "cadence"), "weekly") && !isNutrition(it) }
            val nut =
                quests.filter {
                    isNutrition(it) &&
                        (!Js.isText(Js.prop(it, "cadence"), "daily") || rules.questToday(it, clock))
                }
            val dailyRows =
                daily.filter { !Js.truthy(Js.prop(it, "hand")) }.map { row(save, it, clock) }
            val weeklyRows = weekly.map { row(save, it, clock) }
            val nutRows = nut.map { row(save, it, clock) }
            val epicRows = epics(quests).map { epicRow(save, it, clock, level) }
            BoardView(
                daily =
                    BoardList(
                        dailyRows,
                        if (dailyRows.isEmpty() && daily.isEmpty()) EMPTY_DAILY else null,
                    ),
                weekly = BoardList(weeklyRows, if (weekly.isEmpty()) EMPTY_WEEKLY else null),
                nutrition = nutRows,
                epics = BoardList(epicRows, if (epicRows.isEmpty()) EMPTY_EPICS else null),
                summary = summary(save, daily, weekly, nut, clock),
            )
        }

    /**
     * The ledger's quest sections in the page's order (`renderLedger`): weekly missions, your own
     * quests (daily, not nutrition, not Hand cards, today's), nutrition when there is any, epic
     * milestones. The badges section is out.
     */
    fun ledger(save: JsonObject, clock: BoardClock, level: Int?): HandStep<LedgerView> =
        guard(save) {
            val quests = questList(save)
            val own =
                quests.filter {
                    Js.isText(Js.prop(it, "cadence"), "daily") &&
                        !isNutrition(it) &&
                        !Js.truthy(Js.prop(it, "hand")) &&
                        rules.questToday(it, clock)
                }
            val weekly =
                quests.filter { Js.isText(Js.prop(it, "cadence"), "weekly") && !isNutrition(it) }
            val nut =
                quests.filter {
                    isNutrition(it) &&
                        (!Js.isText(Js.prop(it, "cadence"), "daily") || rules.questToday(it, clock))
                }
            val epics = epics(quests)
            fun section(title: String, list: List<JsonValue>, empty: String) =
                LedgerSection(
                    title,
                    list.map { ledgerRow(save, it, clock, level) },
                    if (list.isEmpty()) empty else null,
                )
            val sections =
                listOfNotNull(
                    section("WEEKLY MISSIONS", weekly, "None this week."),
                    section("YOUR OWN QUESTS", own, "Forge one with New Quest."),
                    if (nut.isEmpty()) null else section("NUTRITION", nut, ""),
                    section("EPIC MILESTONES", epics, "Your first epic awaits."),
                )
            LedgerView(sections)
        }

    private fun <T> guard(save: JsonObject, body: () -> T): HandStep<T> =
        try {
            HandStep.Done(save, body())
        } catch (thrown: PageThrew) {
            HandStep.Threw(save, thrown.message.orEmpty())
        }

    /** `state.quests.filter(...)` needs an array. */
    private fun questList(save: JsonObject): List<JsonValue> {
        val quests = save["quests"]
        return (quests as? JsonArray)?.items
            ?: throw PageThrew(
                if (quests == null || quests == JsonNull)
                    "Cannot read properties of ${Js.string(quests)} (reading 'filter')"
                else "state.quests.filter is not a function"
            )
    }

    private fun isNutrition(q: JsonValue): Boolean = Js.isText(Js.prop(q, "kind"), "nutrition")

    private fun isDaily(q: JsonValue, clock: BoardClock): Boolean =
        Js.isText(Js.prop(q, "cadence"), "daily") && !isNutrition(q) && rules.questToday(q, clock)

    /**
     * `quests.filter(q => q.cadence === 'epic').sort((a, b) => (a.stage || 0) - (b.stage || 0))`.
     */
    private fun epics(quests: List<JsonValue>): List<JsonValue> =
        quests
            .filter { Js.isText(Js.prop(it, "cadence"), "epic") }
            .sortedWith { a, b -> sign(stageNumber(a) - stageNumber(b)) }

    private fun stageNumber(q: JsonValue): Double =
        Js.prop(q, "stage").let { if (Js.truthy(it)) Js.number(it) else 0.0 }

    /** A comparator result: NaN counts as 0, as `Array.prototype.sort` reads it. */
    private fun sign(difference: Double): Int =
        if (difference < 0) -1 else if (difference > 0) 1 else 0

    /** `esc(s)` before escaping: `String(s ?? '')`. */
    private fun text(value: JsonValue?): String =
        if (value == null || value == JsonNull) "" else Js.string(value)

    private fun rewardText(q: JsonValue): String = "+${Js.numberText(rules.listedXp(q))} XP"

    private fun row(save: JsonObject, q: JsonValue, clock: BoardClock): QuestRow {
        val done = rules.isQuestDone(save, q, clock)
        val desc = Js.prop(q, "desc")
        return QuestRow(
            id = Js.string(Js.prop(q, "id")),
            name = text(Js.prop(q, "name")),
            desc = if (Js.truthy(desc)) text(desc) else null,
            reward = rewardText(q),
            done = done,
        )
    }

    private fun epicRow(save: JsonObject, q: JsonValue, clock: BoardClock, level: Int?): EpicRow {
        val locked = rules.questLocked(q, level)
        val done = rules.isQuestDone(save, q, clock)
        val desc = Js.prop(q, "desc")
        val sub =
            if (locked) "Unlock at level ${rules.stageLvl(Js.prop(q, "stage"))}"
            else if (Js.truthy(desc)) text(desc) else "One-time milestone"
        return EpicRow(
            id = Js.string(Js.prop(q, "id")),
            name = text(Js.prop(q, "name")),
            sub = sub,
            reward = rewardText(q),
            locked = locked,
            done = done,
        )
    }

    private fun ledgerRow(
        save: JsonObject,
        q: JsonValue,
        clock: BoardClock,
        level: Int?,
    ): LedgerRow {
        val done = rules.isQuestDone(save, q, clock)
        val locked = rules.questLocked(q, level)
        val desc = Js.prop(q, "desc")
        val sub =
            if (locked) "Unlocks at level ${rules.stageLvl(Js.prop(q, "stage"))}"
            else if (Js.truthy(desc)) text(desc) else ""
        return LedgerRow(
            id = text(Js.prop(q, "id")),
            name = text(Js.prop(q, "name")),
            sub = sub,
            reward = "+${Js.numberText(rules.listedXp(q))}",
            done = done,
            locked = locked,
        )
    }

    private fun summary(
        save: JsonObject,
        daily: List<JsonValue>,
        weekly: List<JsonValue>,
        nut: List<JsonValue>,
        clock: BoardClock,
    ): BoardSummary {
        val dDone = daily.count { rules.isQuestDone(save, it, clock) }
        var available = 0.0
        for (q in daily + weekly + nut) if (!rules.isQuestDone(save, q, clock))
            available += rules.listedXp(q)
        val percent = if (daily.isEmpty()) 0.0 else jsRound(dDone.toDouble() / daily.size * 100)
        val percentText = Js.numberText(percent)
        return BoardSummary(
            progress = "$dDone / ${daily.size}",
            percent = "$percentText%",
            dailyTotal = daily.size,
            weeklyTotal = weekly.size,
            availableXp = localeNumber(available),
            dailyCount = "${daily.size} quests",
            weeklyCount = "${weekly.size} quests",
            nutritionCount = "${nut.size} quests",
            motivation =
                if (percent >= 100 && daily.isNotEmpty()) "Today’s missions cleared. Well played."
                else if (dDone > 0) "Keep that momentum going." else "Your next quest is waiting.",
        )
    }

    companion object {
        /**
         * `n.toLocaleString()` in the page's locale (en-US): thousands separated by commas, at most
         * three decimals, halves rounded away from zero on the shortest decimal text of the double.
         */
        internal fun localeNumber(value: Double): String {
            if (value.isNaN()) return "NaN"
            if (value.isInfinite()) return if (value > 0) "∞" else "-∞"
            val exact =
                BigDecimal(Js.numberText(value)).setScale(MAX_FRACTION, RoundingMode.HALF_UP)
            val plain = exact.abs().stripTrailingZeros().toPlainString()
            val (whole, fraction) = plain.substringBefore('.') to plain.substringAfter('.', "")
            val grouped = whole.reversed().chunked(GROUP).joinToString(",").reversed()
            val negative = exact.signum() < 0
            return (if (negative) "-" else "") +
                grouped +
                if (fraction.isEmpty()) "" else ".$fraction"
        }

        private const val MAX_FRACTION = 3
        private const val GROUP = 3

        /** The page's line for an empty Daily list. */
        const val EMPTY_DAILY = "No daily missions yet. Forge one of your own."

        /** The page's line for an empty Weekly list. */
        const val EMPTY_WEEKLY = "No weekly missions yet. Build your next objective."

        /** The page's line for an empty epic list. */
        const val EMPTY_EPICS = "Your first epic milestone awaits."
    }
}

/** The ledger's quest sections, top to bottom. */
data class LedgerView(val sections: List<LedgerSection>)

/** One ledger section: [empty] is the line shown when it has no rows. */
data class LedgerSection(val title: String, val rows: List<LedgerRow>, val empty: String?)

/**
 * A ledger row (`lgRow`): the toggle shows a check when done, a lock when locked and nothing
 * otherwise, and is disabled while locked; [reward] has no `XP` (the board's has).
 */
data class LedgerRow(
    val id: String,
    val name: String,
    val sub: String,
    val reward: String,
    val done: Boolean,
    val locked: Boolean,
) {
    /** The toggle's glyph. */
    val mark: String
        get() = if (done) "✓" else if (locked) "🔒" else ""
}
