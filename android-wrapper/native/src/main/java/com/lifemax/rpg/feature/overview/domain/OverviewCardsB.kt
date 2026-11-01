package com.lifemax.rpg.feature.overview.domain

// align at merge: these are the Cards B tiles of `overview.cards.v1` (plan OVERVIEW_BODY.md §3.3),
// written beside ov-native-a's full domain (OverviewCards.kt), which decodes them. Field names
// follow §3.3; where §3.3 is silent the name is the page's data function's own and says so below.
// Every string is the page's wording; colours are ARGB, already resolved by the page.

/** `tiles.attrs`: one gem per attribute and the dot legend under them (ovAttrsData). */
data class AttrsTile(
    val title: String,
    val link: String,
    val gems: List<AttrGem>,
    val legend: List<GemLegend>,
)

/**
 * One attribute: its level words ("LV 6"), this week's XP words ("+41" or "quiet") and the fill of
 * its level bar in percent. [focus] draws the gold star.
 */
data class AttrGem(
    val id: String,
    val short: String,
    val icon: String,
    val color: Long,
    val focus: Boolean,
    val dot: GemDot,
    val level: String,
    val week: String,
    val pct: Float,
)

/**
 * The gem's status dot: [kind] is today, ok, soon or decay (the page's `s-` classes), [text] the
 * page's words for it (its `title`).
 */
data class GemDot(val kind: String, val text: String)

/** A legend entry: a dot of [kind] and its word. */
data class GemLegend(val kind: String, val label: String)

/** `tiles.week`: the week's XP stacked by attribute against the pace line (ovWeekData). */
data class WeekTile(
    val title: String,
    val total: String,
    val delta: WeekDelta?,
    val pace: WeekPace,
    val bars: List<WeekBar>,
    val top: List<WeekTop>,
    /** The words under an empty week (the page's `empty`), shown when [top] is empty. */
    val empty: String?,
    /** The day the page has picked (`OV_SEL`), 0 to 6; the screen keeps its own after a tap. */
    val selected: Int,
)

/** "▲ 13% vs last week": [up] colours it good, otherwise bad. */
data class WeekDelta(val up: Boolean, val text: String)

/**
 * The dashed pace line. [value] is the page's label for it ("pace 60"; the page's data calls it
 * `label`, §3.3 `value`), [frac] its height as a fraction of the chart's top.
 */
data class WeekPace(val value: String, val frac: Float)

/**
 * One day: [label] is its letter or "Today", [a11y] the page's aria-label, [segs] its XP per
 * attribute from the bottom up, each a fraction of the chart's top, and [tip] the line the chart
 * shows under it while it is picked.
 */
data class WeekBar(
    val date: String,
    val label: String,
    val today: Boolean,
    val xp: Int,
    val segs: List<WeekSeg>,
    val a11y: String,
    val tip: WeekTip,
)

data class WeekSeg(val color: Long, val frac: Float)

/** "**Friday** · 59 XP — Physique 41, Discipline 18". */
data class WeekTip(val label: String, val rest: String)

/**
 * One of the week's top three attributes, as a pill. (§3.3 names only `top`; the page's fields.)
 */
data class WeekTop(val color: Long, val text: String)

/** `tiles.health` (ovHealthData); null outside the Android app, so never drawn there. */
sealed interface HealthTile {
    val title: String

    /** Health sync is off: the invitation and its button, which opens Settings. */
    data class Off(override val title: String, val text: String, val button: String) : HealthTile

    /**
     * Today's steps against the goal ([pct], 0 to 100), the last seven days as mini-bars, and the
     * latest workouts, sleep and weight as [chips].
     */
    data class On(
        override val title: String,
        val meta: String,
        val steps: String,
        val unit: String,
        val sub: String,
        val pct: Int,
        val days: List<HealthDay>,
        val chips: List<CardChip>,
        val more: String,
    ) : HealthTile
}

/** A mini-bar: [frac] of the tallest day, [now] for today, [a11y] its step count (the `title`). */
data class HealthDay(val label: String, val frac: Float, val now: Boolean, val a11y: String)

/** An `.ovh-chip`: a small caption over a value. Rewards uses the same shape. */
data class CardChip(val label: String, val value: String)

/** `tiles.apps` (ovAppsData); null outside the Android app. */
data class AppsTile(
    val title: String,
    val link: String,
    val items: List<AppItem>,
    val add: String,
    /** The words under an empty row; empty while there are apps. */
    val hint: String,
)

/**
 * A shortcut. [image] is the key of its icon in the projection's `images`, or null until the
 * launcher has sent it. [a11y] is the page's aria-label ("Open Maps"; §3.3 is silent, the page's
 * own field).
 */
data class AppItem(val pkg: String, val label: String, val image: String?, val a11y: String)

/**
 * `tiles.cat` (petPeekData): the cat card as a door to the live room. [eyebrow] is the page's
 * caption (COMPANION, IN MEMORY, A VISITOR); [hearts] 0 to 5 while alive; [text] the line under a
 * visitor's name. §3.3 lists alive and gone; the page also has the stray kitten (`invite`), drawn
 * here too, and the eyebrow and text are the page's own fields.
 */
data class CatTile(
    val mode: CatMode,
    val eyebrow: String,
    val name: String,
    val hearts: Int = 0,
    val text: String = "",
)

enum class CatMode {
    Alive,
    Gone,
    Invite,
}
