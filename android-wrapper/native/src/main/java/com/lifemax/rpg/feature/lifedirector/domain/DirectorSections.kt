package com.lifemax.rpg.feature.lifedirector.domain

/**
 * The sections a screen may draw besides Today (phase 3A), as the page decides them (nativeWeek and
 * nativeQuests in logic.js). Every word is the page's; nothing here is computed.
 */
object Sections {
    const val TODAY = "today"
    const val WEEK = "week"
    const val QUESTS = "quests"
    const val GOALS = "goals"
    const val SETTINGS = "settings"
    /** My time (phase 4B) */
    const val SETUP = "setup"
    /** not a section: the page's cover of Today and Week while there is no plan (phase 4B) */
    const val FIRST_RUN = "first-run"

    /** What this screen can draw; the page's `covers` is the subset it will actually hand over. */
    val DRAWN = setOf(TODAY, WEEK, QUESTS, GOALS, SETTINGS, SETUP)
}

/** One number of Week's three (sessions, time planned, XP on the table). */
data class WeekStat(val value: String, val label: String)

/**
 * A part of a day's bar, in percent of the day from midnight. [state] is a session's, NONE for
 * other time.
 */
data class WeekSeg(val kind: BlockKind, val state: SessionState, val left: Float, val width: Float)

data class WeekDay(
    val index: Int,
    val label: String,
    val date: Int,
    val today: Boolean,
    /** what TalkBack reads for the row */
    val aria: String,
    val count: String,
    /** "✓2", or empty when none is done */
    val done: String,
    val minutes: String,
    val segs: List<WeekSeg>,
)

data class LegendEntry(val kind: BlockKind, val label: String)

/**
 * An attribute's XP: [done] and [plan] as shares of the longest bar (together at most 1), [total]
 * in the page's words.
 */
data class YieldRow(
    val label: String,
    val color: Long,
    val done: Float,
    val plan: Float,
    val total: String,
)

data class XpYield(val title: String, val note: String, val rows: List<YieldRow>)

data class UnfitGroup(val title: String, val tier: String, val text: String)

data class Unscheduled(val summary: String, val groups: List<UnfitGroup>)

data class WeekFit(
    val title: String,
    val text: String,
    val allFit: String,
    val unscheduled: Unscheduled?,
)

data class DirectorWeek(
    val stats: List<WeekStat>,
    val ticks: List<String>,
    val days: List<WeekDay>,
    val legend: List<LegendEntry>,
    val yield: XpYield?,
    val fit: WeekFit,
)

/** ok: covered or done; pas: tracked without time; no: not placed. */
enum class QuestTone {
    OK,
    PAS,
    NO,
}

/** The Quest Board card; [open] is its button's words, empty when the board is not connected. */
data class QuestHead(val title: String, val text: String, val open: String)

data class QuestRow(
    val title: String,
    val whenText: String,
    val status: String,
    val tone: QuestTone,
    val why: String,
)

data class MilestoneRow(
    val title: String,
    val desc: String,
    val earned: Boolean,
    val status: String,
)

data class Milestones(val summary: String, val note: String, val rows: List<MilestoneRow>)

data class DirectorQuests(
    val head: QuestHead,
    val rows: List<QuestRow>,
    val passive: List<QuestRow>,
    val empty: String,
    val milestones: Milestones?,
)

/** A run of text in a guide; [b] where the page's guide text is bold. */
data class GuideSpan(val t: String, val b: Boolean)

/**
 * One step of a guide: the part of the screen it points at (an anchor name), its title and text,
 * and whether it says "tap anywhere". Phase 4A (Today): how the dial looks on the step ([mode], the
 * page's data-tour: clock, outer, inner, handles, center, or none), the spotlight's shape ([round],
 * [pad] dp around the part) and the dial's name tags.
 */
data class GuideStep(
    val anchor: String,
    val title: String,
    val text: List<GuideSpan>,
    val hint: Boolean,
    val mode: String = "",
    val round: Boolean = false,
    val pad: Float = 8f,
    val tags: List<GuideTag> = emptyList(),
)

data class GuideLabels(
    val skip: String,
    val back: String,
    val next: String,
    val last: String,
    val tap: String,
)

/**
 * A section's guide, as the page's TOURS has it (phase 3A: Week's). [auto]: not seen yet, so it
 * starts by itself.
 */
data class DirectorGuide(
    val key: String,
    val auto: Boolean,
    val steps: List<GuideStep>,
    val labels: GuideLabels,
)
