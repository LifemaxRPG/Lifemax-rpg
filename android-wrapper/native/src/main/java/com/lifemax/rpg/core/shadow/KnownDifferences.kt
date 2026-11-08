package com.lifemax.rpg.core.shadow

import com.lifemax.rpg.core.economy.SavePipeline

/**
 * One accepted difference (ECONOMY_SHADOW_PLAN 5.2): page state outside the economy that a port may
 * ignore. [kinds] null means any kind; [path] matches a [Difference.pattern] whole; with [onlyNew]
 * it explains a key only when the before-state did not have it (a screen's redraw put it in).
 * [cites] names the rule or defect it rests on, [ruling] who accepted it.
 */
data class KnownDifference(
    val id: String,
    val kinds: Set<String>?,
    val path: Regex,
    val reason: String,
    val cites: String,
    val ruling: String,
    val onlyNew: Boolean = false,
) {
    /** Whether this entry explains [difference] in an action of [kind] over [before]. */
    fun explains(kind: String, difference: Difference, before: Map<String, *>): Boolean =
        (kinds == null || kind in kinds) &&
            path.matches(difference.pattern) &&
            (!onlyNew || difference.pattern.substringBefore('.') !in before)
}

/**
 * The explicit allowlist. Every entry is pre-approved by ECONOMY_SPEC section 21 (fields a port may
 * ignore or compare loosely); anything else needs an entry written in a pull request and signed off
 * by the owner (decision O-6), and a difference no entry explains is unexplained. An id is `A-` and
 * two digits; an entry about a defect cites its `D` or `QD` id.
 */
object KnownDifferences {
    /** The id of [TOASTS]: a toast list that differs only by a repeated "not saved". */
    const val DOUBLED_UNSAVED = "A-04"

    val ALL: List<KnownDifference> =
        listOf(
            KnownDifference(
                "A-01",
                null,
                Regex("journalCoach\\.(days\\.\\*(\\..*)?|pats(\\..*)?)"),
                "the journal's redraw adds its own day and pattern memory; not a coach award",
                "COACH-1",
                "spec 21",
            ),
            KnownDifference(
                "A-02",
                null,
                Regex("plan\\.checks\\.\\*\\.s(\\..*|\\[.*)?"),
                "plan ticks, specified but outside the economy",
                "PLAN-1",
                "spec 21",
            ),
            KnownDifference(
                "A-03",
                setOf("money.record", "money.remove", "save.other"),
                Regex("money"),
                "the money screen's paycheck settings, dated by the day and put in by its redraw",
                "D26",
                "spec 21",
                onlyNew = true,
            ),
            KnownDifference(
                "A-05",
                null,
                Regex("foodLog|mealsMeta|dayReflections"),
                "the redraw inside a save puts back the food log, the meal settings and the " +
                    "journal's reflections, empty, when the save has none (outside the economy, " +
                    "as gen_economy_golden.js's header says)",
                "SAVE-1",
                "proposed in WP6b, awaiting the owner's sign-off (O-6)",
                onlyNew = true,
            ),
            KnownDifference(
                "A-06",
                null,
                Regex("saves\\.journal"),
                "the journal coach's redraw inside a save (or a take-back) writes A-01's day memory " +
                    "with a plain write of its own (jcWrite), which the page sends apart as " +
                    "env.journal; native does not draw the journal, so it makes no such write",
                "SAVE-1 COACH-1",
                "proposed in WP6c, awaiting the owner's sign-off (O-6)",
            ),
        )

    /**
     * A-04 (spec 21, SAVE-4): a failed save says "not saved" and its take-back says it again, so
     * the page's toasts may repeat it right after itself; such a repeat counts once.
     */
    val TOASTS =
        KnownDifference(
            DOUBLED_UNSAVED,
            null,
            Regex("toasts(\\[.*)?"),
            "a failed save's message said twice, by the save and by its take-back",
            "SAVE-4",
            "spec 21",
        )

    /** Every entry, [TOASTS] included, for the report and the test that none is dead. */
    val ENTRIES: List<KnownDifference> = ALL + TOASTS

    /** The entry that explains [d] in an action of [kind] over [before], or null. */
    fun explaining(kind: String, d: Difference, before: Map<String, *>): KnownDifference? =
        ALL.firstOrNull { it.explains(kind, d, before) }

    /** The toasts with a "not saved" message repeated right after itself counted once. */
    fun singleUnsaved(toasts: List<String>): List<String> =
        toasts.filterIndexed { i, t -> i == 0 || t != toasts[i - 1] || t !in UNSAVED }

    private val UNSAVED = setOf(SavePipeline.SAVE_FULL, SavePipeline.SAVE_READ_ONLY)
}
