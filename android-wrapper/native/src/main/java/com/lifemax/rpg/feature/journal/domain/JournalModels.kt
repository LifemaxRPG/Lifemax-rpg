package com.lifemax.rpg.feature.journal.domain

/*
 * The Journal's projection, `library.journal.projection.v1` (50-native-journal.js; plan §3.3), as
 * the screen holds it. Every string is the page's, worded and formatted by the page (dates, times
 * and numbers through the WebView's locale); every colour is ARGB as NativeTabKit.color sends it.
 * Only CSS presentation (upper case, letter spacing) is the screen's. The parts that can hold the
 * owner's private words (the hero, the list, the entry) live only in [OpenJournal], which only
 * JournalFrame.Open carries.
 */

/** One side of the Library's Media/Journal switch. */
data class JournalPane(val id: String, val label: String, val current: Boolean)

/** The lock card's words (48-journal-lock.js): its heading, its sentence and its button. */
data class LockCard(val title: String, val body: String, val unlock: String)

/** Whether the owner's Journal lock is on, and its card's words, sent in every frame. */
data class JournalLock(val on: Boolean, val card: LockCard)

/**
 * The page's prompt status (LifeMaxJournalLock.state()): the system prompt is up, and the error
 * line under Unlock, empty when there is none (after a cancel, or before any try).
 */
data class LockStatus(val prompting: Boolean, val error: String) {
    companion object {
        val Idle = LockStatus(prompting = false, error = "")
    }
}

/** The page's toast while it shows; [id] counts the page's toasts (one counter for every kit). */
data class JournalNotice(val id: Long, val text: String)

/**
 * What every frame carries, open or withheld, and what the screen keeps at rest: the page session
 * and sequence, the switch, the lock's words, the palette and the theme. Nothing private.
 */
data class JournalChrome(
    val session: String,
    val sequence: Long,
    val panes: List<JournalPane>,
    val lock: JournalLock,
    val tokens: Map<String, Long>,
    val light: Boolean,
)

/** The page's feature tip for the Journal (FEATURE_TIPS.history); [dismiss] names its ✕. */
data class JournalTip(val icon: String, val title: String, val body: String, val dismiss: String)

// ----- the hero -----

/** A stat of the hero: [value] as the page prints it ("1,055"), its label and its colour. */
data class HeroStat(val value: String, val label: String, val color: Long)

/**
 * A day of the 28-day rhythm. An [off] day (nothing logged) is sent with colour 0 and opacity 0;
 * another has its archetype's [color] at [alpha] (.35 to 1). [label] is the page's title ("Oct 1:
 * 177 XP").
 */
data class RhythmCell(
    val color: Long,
    val alpha: Float,
    val off: Boolean,
    val today: Boolean,
    val label: String,
)

data class HeroRhythm(val title: String, val legend: String, val cells: List<RhythmCell>)

/** The hero's Backup button: its label and what TalkBack says ("Export backup"). */
data class HeroBackup(val label: String, val spoken: String)

data class JournalHero(
    val eyebrow: String,
    val title: String,
    val backup: HeroBackup,
    /** Exactly four: streak, days this month, XP this week, reflections. */
    val stats: List<HeroStat>,
    val rhythm: HeroRhythm,
)

// ----- the tile strip -----

/** An item of the strip, newest first. */
sealed interface JournalListItem {
    /**
     * A day's tile. [bar] is the percent of the biggest listed day (6 to 100); [xp] is printed by
     * the page; [pen] marks a day written about; [spoken] is what TalkBack says ("Today, 177 XP").
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

    /** The pill between two days 3 or more days apart ("4 quiet days"). */
    data class Quiet(val text: String) : JournalListItem

    /** A weekly or monthly review's tile; [key] is "W:2026-W43" or "M:2026-10". */
    data class Review(val key: String, val badge: String, val title: String, val sub: String) :
        JournalListItem
}

/**
 * The strip as the page memoises it. [rev] describes exactly these items: a frame with the same rev
 * carries the same items, so the decoder keeps the list object it already has.
 */
data class JournalList(val rev: String, val items: List<JournalListItem>) {
    /** The keys a tap may select: days and reviews. */
    val keys: Set<String> by lazy {
        items
            .mapNotNull {
                when (it) {
                    is JournalListItem.Day -> it.key
                    is JournalListItem.Review -> it.key
                    is JournalListItem.Quiet -> null
                }
            }
            .toSet()
    }
}

/** What shows in place of the strip and the entry when nothing is logged yet. */
data class JournalEmpty(val icon: String, val title: String, val body: String)

// ----- the selected day or review -----

/** The selected day or review. */
sealed interface JournalEntry {
    val key: String
}

/** A day's head: the big number, the weekday, the month and year, and its XP. */
data class DayHead(
    val number: String,
    val weekday: String,
    val month: String,
    val xp: String,
    val xpLabel: String,
)

/** The care note (JC_CARE), word for word; it replaces the welcome, insight and question. */
data class JournalCare(val lead: String, val text: String)

/** A welcome chip: "pet", "fresh", "line" or "director"; a [done] chip is drawn faded. */
data class WelcomeChip(val id: String, val label: String, val done: Boolean)

data class JournalWelcome(
    /** Which welcome the page chose (1 to 3, by how long the owner was away). */
    val tier: Int,
    val head: String,
    val body: String,
    val chips: List<WelcomeChip>,
)

/** The coach's insight: its archetype's label and colour, its text and the pattern line. */
data class JournalInsight(
    val label: String,
    val color: Long,
    val text: String,
    val pattern: String?,
)

/** The question over the box, with the ↻ swap's spoken name and the prompt id `swap` checks. */
data class ReflectPrompt(val kicker: String, val text: String, val id: String, val swap: String)

/** The four counter lines of a day's box (JC_COUNTER). */
data class ReflectCounter(
    val awarded: String,
    val ready: String,
    val short: String,
    val old: String,
)

/**
 * A day's reflection box. Without a [prompt] the box has a [label] instead ("Reflection
 * (optional)"). [meter] is the meter's percent as the page drew it.
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
    val meter: Float,
    val counter: ReflectCounter,
) {
    /**
     * The counter for [n] characters (trimmed), as jcCounterText chooses: claimed, then too old to
     * pay, then long enough, else the short line, with " (n)" while [typing].
     */
    fun counterFor(n: Int, typing: Boolean): String =
        when {
            awarded -> counter.awarded
            !canPay -> counter.old
            n >= min -> counter.ready
            typing -> "${counter.short} ($n)"
            else -> counter.short
        }

    /** Whether the counter is drawn in its "on" colour (claimed, or long enough). */
    fun counterOn(n: Int): Boolean = awarded || n >= min

    /** The meter for [n] characters, as jcMeter draws it. */
    fun meterFor(n: Int): Float = if (awarded) 100f else minOf(100f, n * 100f / min)
}

/** The attribute pill of a timeline item, in the item's colour. */
data class TimelineAttr(val text: String, val color: Long)

/** A workout plan's note for an exercise: its label, and the weight and note, empty when none. */
data class PlanNote(val label: String, val weight: String, val note: String)

/**
 * An activity on the day's timeline. [icon] is a core PixelIcon name; [xp] is printed ("+30");
 * [plan] holds the plan notes under a plan session (empty otherwise).
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
    val plan: List<PlanNote>,
)

/** The workout-notes card of a day with plan notes but no plan session; the page's `items`. */
data class WorkoutNotes(val title: String, val icon: String, val items: List<PlanNote>)

/** A day with nothing logged: its icon, heading and line. */
data class JournalBlank(val icon: String, val title: String, val text: String)

data class DayEntry(
    override val key: String,
    val head: DayHead,
    val meta: List<String>,
    val care: JournalCare?,
    val welcome: JournalWelcome?,
    val insight: JournalInsight?,
    val reflect: JournalReflect,
    val timeline: List<TimelineItem>,
    val workoutNotes: WorkoutNotes?,
    val blank: JournalBlank?,
) : JournalEntry

/** A review's head: its badge ("W43", "OCT"), headline and date range. */
data class ReviewHead(val badge: String, val title: String, val range: String)

/** A day's bar of a weekly review: [height] in percent; [value] empty on a day with no XP. */
data class ReviewBar(
    val label: String,
    val value: String,
    val height: Int,
    val color: Long,
    val today: Boolean,
)

/** A day's square of a monthly review's heat grid. */
data class HeatCell(
    val day: String,
    val color: Long,
    val alpha: Float,
    val off: Boolean,
    val label: String,
)

sealed interface ReviewChart {
    data class Bars(val bars: List<ReviewBar>) : ReviewChart

    /** [lead] empty squares before the first day, so it falls under its weekday. */
    data class Heat(val weekdays: List<String>, val lead: Int, val cells: List<HeatCell>) :
        ReviewChart
}

data class ReviewGridItem(val label: String, val value: String)

/** The coach's card on a review: its badge ("Coach") and synthesis. */
data class ReviewCoach(val badge: String, val text: String)

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
    /** The page's one line under the answer box, already chosen by [paid]. */
    val counter: String,
) : JournalEntry

/**
 * Everything an open frame adds to the chrome. [motion] is the page's animation setting; [edit] is
 * "page" (phase 1: writing hands over to the page's field) or "native". [selection] is the page's
 * histSel (null only for an empty Journal); [empty] is set exactly when nothing is logged.
 */
data class OpenJournal(
    val motion: Boolean,
    val edit: String,
    val tip: JournalTip?,
    val hero: JournalHero,
    val list: JournalList,
    val selection: String?,
    val empty: JournalEmpty?,
    val entry: JournalEntry?,
)
