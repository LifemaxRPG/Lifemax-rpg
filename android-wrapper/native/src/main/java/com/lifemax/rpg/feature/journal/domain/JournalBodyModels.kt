// align at merge
//
// The Journal body's models: the day list, the selected day and the selected review, as the page's
// projection library.journal.projection.v1 carries them (plan JOURNAL_AND_LIBRARY_SHEETS.md §3.3).
// Field names follow the plan's JSON exactly; the decoder (another file) fills them. Every string
// is worded by the page; colours are ARGB as NativeTabKit.color writes them.
package com.lifemax.rpg.feature.journal.domain

/** An item of the tile strip (`list.items`), newest first. */
sealed interface JournalListItem {
    /**
     * A day tile. [bar] is the bar's percent against the biggest listed day; [xp] is as the page
     * prints it; [pen] when the day has a reflection; [color] is the day's archetype colour;
     * [spoken] is what TalkBack reads ("Today, 177 XP").
     */
    data class Day(
        val key: String,
        val weekday: String,
        val date: String,
        val bar: Int,
        val xp: String,
        val pen: Boolean,
        val color: Long,
        val today: Boolean,
        val spoken: String,
    ) : JournalListItem

    /** The pill between two tiles three or more quiet days apart ("4 quiet days"). */
    data class Quiet(val text: String) : JournalListItem

    /** A weekly or monthly review tile: [badge] "W" or "M", [title] "Week 43", [sub] its kind. */
    data class Review(val key: String, val badge: String, val title: String, val sub: String) :
        JournalListItem
}

/** `list`: the memoised items, and [rev], what they were built from. */
data class JournalList(val rev: String, val items: List<JournalListItem>)

/** The empty Journal (`empty`), drawn in place of the entry. */
data class JournalEmpty(val icon: String, val title: String, val body: String)

/** The selected day or review (`entry`). */
sealed interface JournalEntry {
    val key: String
}

/** The day head: the big number, the weekday and month, and the XP total with its label. */
data class DayHead(
    val number: String,
    val weekday: String,
    val month: String,
    val xp: String,
    val xpLabel: String,
)

/** The care note, word for word from the page's JC_CARE. */
data class JournalCare(val lead: String, val text: String)

/** A welcome chip: `pet`, `fresh`, `line` or `director`; [done] once claimed. */
data class WelcomeChip(val id: String, val label: String, val done: Boolean)

/** The welcome-back card after three or more quiet days. */
data class JournalWelcome(val head: String, val body: String, val chips: List<WelcomeChip>)

/** The insight card: the archetype's [label] and [color], its sentence, and a pattern line. */
data class JournalInsight(
    val label: String,
    val color: Long,
    val text: String,
    val pattern: String?,
)

/** The reflection's question: [kicker] "Reflect", the question, its id, [swap] the ↻'s name. */
data class ReflectPrompt(val kicker: String, val text: String, val id: String, val swap: String)

/** The four counter lines the page chooses from (jcCounterText). */
data class ReflectCounter(
    val awarded: String,
    val ready: String,
    val short: String,
    val old: String,
)

/**
 * The reflection box. With no [prompt] the box has a [label] ("Reflection (optional)"). [meter] is
 * the meter's percent; [min] the characters that pay, [max] the field's limit.
 */
data class JournalReflect(
    val prompt: ReflectPrompt?,
    val label: String?,
    val text: String,
    val placeholder: String,
    val max: Int,
    val min: Int,
    val canPay: Boolean,
    val awarded: Boolean,
    val meter: Double,
    val counter: ReflectCounter,
)

/** An activity's attribute pill. */
data class TimelineAttr(val text: String, val color: Long)

/** A workout-plan note: the exercise, and the weight and note when written (empty when not). */
data class PlanNote(val label: String, val weight: String, val note: String)

/**
 * A timeline item: [time] as the page prints it, [color] the attribute's, [icon] a resolved pixel
 * name, [xp] already signed ("+30"), [plan] the plan session's notes.
 */
data class TimelineItem(
    val time: String,
    val color: Long,
    val icon: String,
    val name: String,
    val attr: TimelineAttr?,
    val minutes: String?,
    val skill: String?,
    val xp: String,
    val notes: String?,
    val plan: List<PlanNote>?,
)

/** A day with nothing logged: the page's ✎ box with its title and sentence. */
data class JournalBlank(val icon: String, val title: String, val body: String)

/** A day's page (`entry.kind == "day"`). */
data class DayEntry(
    override val key: String,
    val head: DayHead,
    val meta: List<String>,
    val care: JournalCare?,
    val welcome: JournalWelcome?,
    val insight: JournalInsight?,
    val reflect: JournalReflect,
    val timeline: List<TimelineItem>,
    /** "Workout notes" (its card's title) and the notes of a day with no plan session logged. */
    val workoutNotes: WorkoutNotes?,
    val blank: JournalBlank?,
) : JournalEntry

/** The workout notes card: its [title] and the notes. */
data class WorkoutNotes(val title: String, val notes: List<PlanNote>)

/** A review's head: [badge] "W43" or "OCT", the headline and the date range. */
data class ReviewHead(val badge: String, val title: String, val range: String)

/** A weekly bar: the weekday letter, the XP ("" for none), its height in percent. */
data class ReviewBar(
    val label: String,
    val value: String,
    val height: Int,
    val color: Long,
    val today: Boolean,
)

/** A month's heat cell: the day number, colour and opacity, [off] when nothing was logged. */
data class HeatCell(
    val day: String,
    val color: Long,
    val alpha: Double,
    val off: Boolean,
    val label: String,
)

/** A review's chart: a bar a day for a week, a heat grid for a month. */
sealed interface ReviewChart {
    data class Bars(val bars: List<ReviewBar>) : ReviewChart

    /** [lead] empty cells before the 1st, under [weekdays] (Monday first). */
    data class Heat(val weekdays: List<String>, val lead: Int, val cells: List<HeatCell>) :
        ReviewChart
}

data class ReviewGridItem(val label: String, val value: String)

/** The coach's synthesis card: [badge] "Coach". */
data class ReviewCoach(val badge: String, val text: String)

/**
 * A weekly or monthly review (`entry.kind == "review"`). [counter] is the page's line for the
 * answer box, chosen by [paid].
 */
data class ReviewEntry(
    override val key: String,
    val head: ReviewHead,
    val chart: ReviewChart,
    val grid: List<ReviewGridItem>,
    val coach: ReviewCoach,
    val question: String,
    val answer: String,
    val placeholder: String,
    val max: Int,
    val min: Int,
    val paid: Boolean,
    val counter: String,
) : JournalEntry
