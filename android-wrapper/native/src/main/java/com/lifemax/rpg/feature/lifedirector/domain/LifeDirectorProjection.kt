package com.lifemax.rpg.feature.lifedirector.domain

/** A block's colour family, as Life Director classes its time (the web's ld-c-* classes). */
enum class BlockKind {
    SLEEP,
    WORK,
    COMMUTE,
    MAINT,
    BLOCK,
    TASK,
    MEAL,
    REST,
    FREE,
}

/** Where a session stands; [NONE] for a block that is not a session. */
enum class SessionState {
    NONE,
    PLAN,
    DONE,
    PART,
    OFF,
}

/**
 * How an arc is drawn: an outer band of fixed time, or a session, meal, appointment or downtime.
 */
enum class ArcStyle {
    BAND,
    SESSION,
    MEAL,
    APPT,
    THIN,
}

enum class StatusDot {
    OK,
    WARN,
    OFF,
}

enum class ChipKind {
    QUEST,
    GOAL,
    XP,
    PINNED,
}

/** GO: on now or a timer running; NEXT: later today; REST: nothing more today. */
enum class UpNextKind {
    GO,
    NEXT,
    REST,
}

/**
 * A tap the page answers with the tapped web element's own handler: its act and the id it needs.
 */
data class Tap(val act: String, val id: String = "")

data class DirectorHeader(val dot: StatusDot, val line: String)

data class DirectorClock(val time: String, val day: String)

data class DirectorTab(val id: String, val label: String, val icon: String)

data class BannerAction(val label: String, val tap: Tap, val ghost: Boolean, val small: Boolean)

data class DirectorBanner(
    val bad: Boolean,
    val text: String,
    val actions: List<BannerAction>,
    val dismiss: Boolean,
)

/** Life Director's latest message; shown once per [id]. */
data class DirectorNotice(val id: Long, val text: String)

data class StripDay(val label: String, val date: Int, val dots: List<SessionState>)

/** The dial's radii in its 360-unit square (the web's DIAL). */
data class DialRadii(
    val centre: Float,
    val outer: Float,
    val outerWidth: Float,
    val inner: Float,
    val innerWidth: Float,
    val tickIn: Float,
    val tickOut: Float,
    val hourWords: Float,
)

data class HourWord(val hour: Int, val word: String)

/**
 * One block on the dial. Angles are degrees clockwise from midnight at the top, after the web's
 * gaps. [tap] is what the page runs; [tip] is what fixed time says when tapped.
 */
data class DialArc(
    val outer: Boolean,
    val style: ArcStyle,
    val kind: BlockKind,
    val a0: Float,
    val a1: Float,
    val start: Long,
    val end: Long,
    val title: String,
    val label: String,
    val state: SessionState,
    val next: Boolean,
    val icon: String,
    val tap: Tap?,
    val tip: String,
) {
    val tappable
        get() = tap != null || tip.isNotEmpty()
}

/** Words written along an arc, already chosen by the web; [flip] runs them anticlockwise. */
data class RingWord(
    val radius: Float,
    val a0: Float,
    val a1: Float,
    val kind: BlockKind,
    val inner: Boolean,
    val text: String,
    val flip: Boolean,
)

/** How much of the current block is gone ([frac]), drawn as the ring around the centre. */
data class CurrentRing(val radius: Float, val frac: Float, val kind: BlockKind, val warn: Boolean)

data class DirectorDial(
    val start: Long,
    val end: Long,
    val radii: DialRadii,
    val hourWords: List<HourWord>,
    val arcs: List<DialArc>,
    val words: List<RingWord>,
    val hand: Float?,
    val ring: CurrentRing?,
)

/** Time left, as the centre shows it; [less] is "under a minute". */
data class Countdown(val hours: Int, val minutes: Int, val less: Boolean)

/** A running focus timer, in epoch milliseconds as the page keeps it. */
data class FocusTimer(val startedAt: Long, val pausedMs: Long, val paused: Long?, val tap: Tap?) {
    fun elapsedMs(nowMs: Long) = ((paused ?: nowMs) - startedAt - pausedMs).coerceAtLeast(0)
}

data class CentreNext(val label: String, val title: String, val live: Boolean, val tap: Tap?)

data class CentreButton(val label: String, val tap: Tap, val go: Boolean)

/**
 * The middle of the dial. [kickKind] colours the kick's dot; [kickLive] is the running timer's
 * pulsing dot. [date] is the larger title of a day that is not today.
 */
data class DialCentre(
    val kick: String,
    val kickKind: BlockKind?,
    val kickLive: Boolean,
    val warn: Boolean,
    val title: String,
    val date: Boolean,
    val countdown: Countdown?,
    val timer: FocusTimer?,
    val sub: String,
    val next: CentreNext?,
    val button: CentreButton?,
)

data class MixShare(val kind: BlockKind, val label: String, val minutes: Int, val text: String)

data class Chip(val kind: ChipKind, val text: String)

/** A button of the up-next card: a tap for the page, or another [day] of the strip. */
data class UpNextAction(
    val label: String,
    val tap: Tap?,
    val day: Int?,
    val primary: Boolean,
    val ghost: Boolean,
)

data class UpNext(
    val kind: UpNextKind,
    val cue: String,
    val title: String,
    val timing: String,
    val timer: FocusTimer?,
    val chips: List<Chip>,
    val actions: List<UpNextAction>,
)

/** A row of the running order; [key] is stable and unique within its list. */
data class OrderRow(
    val key: String,
    val start: Long,
    val end: Long,
    val time: String,
    val length: String,
    val kind: BlockKind,
    val title: String,
    val icon: String,
    val state: SessionState,
    val stateLabel: String,
    val now: Boolean,
    val past: Boolean,
    val sub: String,
    val chips: List<Chip>,
    val tap: Tap?,
)

data class EarlierToday(val summary: String, val rows: List<OrderRow>)

data class DirectorToday(
    val now: Long,
    val dayIndex: Int,
    val isToday: Boolean,
    val strip: List<StripDay>,
    val dial: DirectorDial,
    val centre: DialCentre,
    val mix: List<MixShare>,
    val upNext: UpNext?,
    val reminders: List<String>,
    val sessions: Int,
    val ahead: List<OrderRow>,
    val earlier: EarlierToday?,
)

/** What the page projects for the Today screen: Life Director's frame and the day on the strip. */
data class LifeDirectorProjection(
    val session: String,
    val sequence: Long,
    val header: DirectorHeader,
    val clock: DirectorClock,
    val tabs: List<DirectorTab>,
    val tab: String,
    val banner: DirectorBanner?,
    val notice: DirectorNotice?,
    val today: DirectorToday?,
    val tokens: Map<String, Long>,
    val light: Boolean,
)
