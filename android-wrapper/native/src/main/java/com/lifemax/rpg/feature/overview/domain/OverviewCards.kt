package com.lifemax.rpg.feature.overview.domain

import com.lifemax.rpg.core.designsystem.feedback.HapticScale
import com.lifemax.rpg.core.designsystem.feedback.HeldCelebration
import com.lifemax.rpg.core.designsystem.feedback.PagePoint
import com.lifemax.rpg.feature.lifedirector.domain.DirectorGlance
import com.lifemax.rpg.feature.pet.art.PageCoatSource

/*
 * The whole projection `overview.cards.v1` (plan OVERVIEW_BODY §3.3): what the page's Overview
 * shows, card by card, as the page's data functions decide it (ovHeadData, ovMoves, ovHeroData, …
 * in 17/18-runtime.js, 39-health.js, 41-ov-apps.js, 44-rewards.js). Every word is the page's;
 * colours are ARGB, the page having resolved its var(--x) tokens. Native code decides nothing here:
 * it draws these.
 *
 * Field names follow §3.3 where it names them and the page's data functions where it does not
 * (hero's ring, week's pace label, luck's colour); WebOverviewRepository is where each is read.
 */

/** The Overview's projection: the head, the tip, the tiles in the page's order, and the rest. */
data class OverviewCards(
    val session: String,
    val sequence: Long,
    val head: OverviewHead,
    val tip: OverviewTip?,
    val layout: OverviewLayout,
    val tileInfo: List<TileInfo>,
    /** The editor's words (OV_EDIT_WORDS): phase 2's native editor draws them. */
    val editor: EditorWords,
    val tiles: OverviewTiles,
    /**
     * Data URLs by the page's key ("i" + FNV-1a of the URL in hex), for the avatar, the app icons
     * and the cat's portrait.
     */
    val images: Map<String, String>,
    val notice: OverviewNotice?,
    /**
     * The XP celebrations the page handed over (fxSink), each held 2 s; PageCelebrations plays
     * them.
     */
    val celebrations: List<HeldCelebration>,
    /** Where the orbs fly: the logo in the shared bar, in the page's CSS px. */
    val celebrationTarget: PagePoint,
    val reducedMotion: Boolean,
    /** The owner's haptic strength (HAP_LEVELS, HAP_MAX); null with haptics off. */
    val haptics: HapticScale?,
    /** Rises when the screen opens on a page arrival (phase 3); 0 until the page sends it. */
    val arrival: Long,
    val tokens: Map<String, Long>,
    val light: Boolean,
) {
    /**
     * The tiles to draw, in the page's order (`layout`, never the JSON's): hidden ones, ids this
     * build does not know and tiles with nothing to draw (null on the page: no Life Director, not
     * the Android app, no cat) are left out, as the page leaves them out.
     */
    val shown: List<String>
        get() =
            layout.order.filter {
                it in OverviewTileIds.ALL && it !in layout.hidden && tiles.has(it)
            }

    /** The cat's portrait, its key looked up in [images]; null when the page sent none. */
    val catPortrait: CatPortrait?
        get() = tiles.cat?.portrait?.let { key -> images[key]?.let { CatPortrait(key, it) } }
}

/** The tile ids the page knows (OV_TILES with the three tiles later segments add). */
object OverviewTileIds {
    const val PLAN = "plan"
    const val NEXT = "next"
    const val HERO = "hero"
    const val REWARDS = "rewards"
    const val APPS = "apps"
    const val HEALTH = "health"
    const val ATTRS = "attrs"
    const val WEEK = "week"
    const val CAT = "cat"
    const val LUCK = "luck"
    const val RECENT = "recent"
    val ALL = setOf(PLAN, NEXT, HERO, REWARDS, APPS, HEALTH, ATTRS, WEEK, CAT, LUCK, RECENT)
}

/** The head: today's date and greeting, the Edit button's words, and the class for its crest. */
data class OverviewHead(
    val date: String,
    val hello: String,
    /** "" with no class chosen: no crest. */
    val classId: String,
    val editLabel: String,
    val editA11y: String,
)

/** FEATURE_TIPS.dashboard while it shows (tutorial done, not dismissed). */
data class OverviewTip(
    val key: String,
    val icon: String,
    val title: String,
    val body: String,
    val dismiss: String,
)

/** ovLayout(): every known tile in order, and the hidden ones. */
data class OverviewLayout(val order: List<String>, val hidden: List<String>)

/** One tile as the editor names it (OV_TILES, ovTileNote); phase 2's rows draw these. */
data class TileInfo(
    val id: String,
    val name: String,
    val sub: String,
    val note: String,
    val icon: String,
    val color: Long,
)

/** Each card's model; null where the page draws nothing for it. */
data class OverviewTiles(
    /** Life Director's glance (LifeDirector.native.glance), drawn by its DirectorGlance. */
    val plan: DirectorGlance?,
    val next: NextMoves,
    val hero: HeroCard,
    val rewards: RewardsCard,
    val apps: AppsCard?,
    val health: HealthCard?,
    val attrs: AttrsCard,
    val week: WeekCard,
    val cat: CatCard?,
    val luck: LuckCard,
    val recent: RecentCard,
) {
    fun has(id: String): Boolean =
        when (id) {
            OverviewTileIds.PLAN -> plan != null
            OverviewTileIds.APPS -> apps != null
            OverviewTileIds.HEALTH -> health != null
            OverviewTileIds.CAT -> cat != null
            else -> true
        }
}

/* ---- Next moves */

/** Where a move leads: a page view, an attribute's sheet, or Daily Luck. */
sealed interface MoveGo {
    data class View(val view: String) : MoveGo

    data class Attr(val id: String) : MoveGo

    data object Luck : MoveGo
}

enum class MoveWarn {
    NONE,
    /** due soon or hungry: the gold border */
    WARN,
    /** fading or starving: the red border */
    BAD,
}

/**
 * One move (.ov-move): its [cta] pill ("" for the chevron, ✓ when [done]); [pill] is the quests'
 * gold one.
 */
data class NextMove(
    val title: String,
    val sub: String,
    val icon: String,
    val color: Long,
    val warn: MoveWarn,
    val done: Boolean,
    val cta: String,
    val pill: Boolean,
    val go: MoveGo,
)

data class NextMoves(val title: String, val note: String, val moves: List<NextMove>)

/* ---- Level & today */

/** "▲ 12%" in green, or "▼ 15%" in red. */
data class Delta(val up: Boolean, val text: String)

/** One attribute's arc of today's ring, in pathLength-100 units as the page's SVG draws it. */
data class HeroArc(val color: Long, val offset: Float, val length: Float)

/**
 * The ring's centre: "TODAY", today's XP (gold when [over] the pace), "of 60 XP · +20%"; [r] and
 * [stroke] are the page's SVG circle (62 and 11 in its 148 box).
 */
data class HeroRing(
    val label: String,
    val today: Int,
    val over: Boolean,
    val of: String,
    val arcs: List<HeroArc>,
    val r: Int = 62,
    val stroke: Int = 11,
)

data class HeroXp(val text: String, val next: String, val pct: Float)

data class StreakDot(val on: Boolean, val now: Boolean)

data class HeroStreak(val days: Int, val label: String, val dots: List<StreakDot>)

data class HeroFoot(val label: String, val value: String, val delta: Delta?)

/**
 * The hero (.ov-hero): the ring, the avatar ([avatar] an image key, or the [initial]), the level
 * badge, title and class, the XP bar, the streak and its seven days, and the foot. The whole card
 * is a button to Character; TalkBack reads its words, as on the page (which gives it no label).
 */
data class HeroCard(
    val ring: HeroRing,
    val avatar: String?,
    val initial: String,
    val level: Int,
    val title: String,
    val className: String,
    val xp: HeroXp,
    val streak: HeroStreak,
    val foot: List<HeroFoot>,
)

/* ---- Rewards, apps, health */

/** A label and value chip (.ovh-chip), shared by Rewards and Activity today. */
data class CardChip(val label: String, val value: String)

data class RewardsCard(val title: String, val link: String, val chips: List<CardChip>)

/** An app shortcut; [image] is its icon's key, null until Android has sent it. */
data class AppItem(val pkg: String, val label: String, val a11y: String, val image: String?)

data class AppsCard(
    val title: String,
    val link: String,
    val items: List<AppItem>,
    val add: String,
    /** "" once there are apps. */
    val hint: String,
)

/** Activity today: off until health sync is on, then today's steps and the week. */
sealed interface HealthCard {
    val title: String

    data class Off(override val title: String, val text: String, val button: String) : HealthCard

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
    ) : HealthCard
}

/** A day's steps bar: [frac] of the tallest; [a11y] its count. */
data class HealthDay(val label: String, val frac: Float, val now: Boolean, val a11y: String)

/* ---- Attributes, week */

/** chDecay's kinds (14-runtime.js) and trained today; the page colours today, ok, soon, decay. */
enum class DotKind {
    TODAY,
    OK,
    SOON,
    DECAY,
    /** never trained */
    NONE,
    /** decay switched off */
    OFF,
    /** nothing left to lose */
    ZERO,
}

data class GemDot(val kind: DotKind, val text: String)

/** One attribute (.ov-gem): its level, this week's XP ("+41" or "quiet") and its bar. */
data class Gem(
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

data class LegendItem(val kind: DotKind, val label: String)

data class AttrsCard(
    val title: String,
    val link: String,
    val gems: List<Gem>,
    val legend: List<LegendItem>,
)

/** "pace 60", at [frac] of the chart's height. */
data class WeekPace(val label: String, val frac: Float)

data class WeekSeg(val color: Long, val frac: Float)

/** The line under the chart for a day: its name, then its XP and where it went. */
data class WeekTip(val label: String, val rest: String)

data class WeekBar(
    val date: String,
    val label: String,
    val today: Boolean,
    val xp: Int,
    val segs: List<WeekSeg>,
    val a11y: String,
    val tip: WeekTip,
)

data class WeekTop(val color: Long, val text: String)

/** This week (.ov-chart): seven days stacked by attribute, the pace line, the top three. */
data class WeekCard(
    val title: String,
    val total: String,
    val delta: Delta?,
    val pace: WeekPace,
    val bars: List<WeekBar>,
    val top: List<WeekTop>,
    val empty: String,
    /** OV_SEL: the day the page has picked (today, 6, unless picked on the page). */
    val selected: Int,
)

/* ---- Cat, luck, recent */

enum class CatMode {
    ALIVE,
    GONE,
}

/**
 * The cat card (petPeekData): "Miso" (or "Miso & Pip") and its [hearts] (0 to 5, null for a cat in
 * memory), or "IN MEMORY". [portrait] is the cat sitting as the page's room draws it (petPortrait),
 * an image key in the projection's images; null while its art loads, and for a cat in memory. The
 * page sends no card for the stray kitten (no cat yet).
 */
data class CatCard(
    val mode: CatMode,
    val eyebrow: String,
    val name: String,
    val hearts: Int?,
    val portrait: String?,
    val live: LiveCat?,
)

/**
 * What native needs to draw the first cat itself (`overview.cat.live.v1`, the owner's "Native cats
 * (beta)" switch): the coat key, the growth stage (-1 grown, 0 to 5 a kitten) and whether it is the
 * hungry, weak cat. The page sends it only with the switch on. Native draws the four coats it ships
 * ([LiveCat.LEGACY_COATS]) at once, and a genetic `dna_...` coat (which the page sends only to a
 * screen with `overview.cat.coat.v1`) once the page has handed over its sheets; until then, and for
 * any other key, the still portrait stays.
 */
data class LiveCat(val coat: String, val stage: Int, val weak: Boolean) {
    /** True when native can draw this coat: shipped, or a genetic coat the page can compose. */
    val drawable: Boolean
        get() = coat in LEGACY_COATS || PageCoatSource.isDnaKey(coat)

    companion object {
        val LEGACY_COATS = setOf("ginger", "gray", "black", "tuxedo")
        const val ADULT = -1
        const val LAST_STAGE = 5
    }
}

/** Daily luck: the score (monospace) in its tier's [color] once rolled. */
data class LuckCard(
    val eyebrow: String,
    val rolled: Boolean,
    val score: String,
    val color: Long?,
    val text: String,
    val button: String,
)

data class RecentItem(
    val name: String,
    val icon: String,
    val color: Long,
    val meta: String,
    val xp: String,
)

data class RecentCard(
    val title: String,
    val link: String,
    val items: List<RecentItem>,
    val emptyIcon: String,
    val emptyText: String,
)

/* ---- What the page hands over besides the cards */

/** The page's toast while it shows. */
data class OverviewNotice(val id: Long, val text: String)
