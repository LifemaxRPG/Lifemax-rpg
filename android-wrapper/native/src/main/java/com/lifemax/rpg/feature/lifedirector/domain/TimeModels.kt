package com.lifemax.rpg.feature.lifedirector.domain

/*
 * My time and the first-run card (phase 4B), as the page decides them (timeModel, nativeTime, FIRST_RUN and nativeFirstRun in
 * logic.js). Every word and every limit is the page's; the screen only draws them and sends the page's own `do` writes.
 */

/** A set-up step's look: done (ticked, green), the current one (blue) or still to come. */
enum class SetupStepState {
    OK,
    CUR,
    TODO,
}

data class SetupStep(val label: String, val state: SetupStepState)

/** The dial face the page draws its small dials with: its radii and its hour words. */
data class DialFaceSpec(val radii: DialRadii, val hourWords: List<HourWord>)

/** A span of the day in minutes after midnight; [to] may pass midnight. */
data class MinuteRange(val from: Int, val to: Int)

/** A button the page may light as the next thing to do ([primary]). */
data class TimeButton(val label: String, val primary: Boolean)

/** The weekend's own sleep: [on] when Friday and Saturday nights differ. */
data class WeekendSleep(val on: Boolean, val bed: Int, val wake: Int)

data class SleepLabels(
    val centre: String,
    val bed: String,
    val wake: String,
    val weekend: String,
    val weekendBed: String,
    val weekendWake: String,
    val bedHandle: String,
    val wakeHandle: String,
    /** the mini dial's name for TalkBack ("Sleep window") */
    val group: String,
)

/**
 * The sleep card: the saved (or default) [bed] and [wake] in minutes after midnight with the page's
 * words for them, the weekend's, and what the mini dial needs to follow the finger in the page's
 * words: its snap [step], the clock words of every stop ([clock]) and the length words of every
 * length ([lengths]), and the work arcs faint behind ([context]).
 */
data class TimeSleep(
    val title: String,
    val note: String,
    val saved: Boolean,
    val bed: Int,
    val wake: Int,
    val bedText: String,
    val wakeText: String,
    val length: String,
    val weekend: WeekendSleep,
    val labels: SleepLabels,
    val save: TimeButton,
    val step: Int,
    val context: List<MinuteRange>,
    val clock: List<String>,
    val lengths: List<String>,
    val face: DialFaceSpec,
)

/** A commitment's dot: work or school, or time that is simply unavailable. */
enum class CommitmentDot {
    WORK,
    BLOCK,
}

data class CommitmentRow(
    val id: String,
    val title: String,
    val dot: CommitmentDot,
    val line: String,
)

data class Commitments(
    val title: String,
    val rows: List<CommitmentRow>,
    val empty: String,
    val add: String,
)

/**
 * A daily routine's stepper: [value] minutes within [min]..[max] by [step], [text] the page's words
 * for it and [stops] the page's words for every stop. The meals row adds whether meals are planned
 * ([on], null on the other rows) and its switch's words ([toggle]).
 */
data class RoutineRow(
    val key: String,
    val label: String,
    val hint: String,
    val min: Int,
    val max: Int,
    val step: Int,
    val value: Int,
    val text: String,
    val stops: List<String>,
    val less: String,
    val more: String,
    val on: Boolean? = null,
    val toggle: String? = null,
) {
    /** The page's words for [v] minutes: the stop it falls on, or [text] off the stops. */
    fun textAt(v: Int): String = if (v == value) text else stops.getOrNull((v - min) / step) ?: text
}

data class Routines(
    val title: String,
    val text: String,
    val rows: List<RoutineRow>,
    val meals: RoutineRow,
    val note: String,
)

/**
 * Goals on My time: a chip per goal, Add a goal, and [manage] (Goals' section) once there is one.
 */
data class TimeGoals(
    val title: String,
    val chips: List<String>,
    val empty: String,
    val add: TimeButton,
    val manage: String?,
)

/** "Build my week", before set-up is finished; [enabled] once sleep is saved. */
data class BuildCard(val title: String, val text: String, val label: String, val enabled: Boolean)

/**
 * My time. [first] while set-up is not finished: then the [steps] show and [build] is set;
 * afterwards [replan] is.
 */
data class DirectorTime(
    val first: Boolean,
    val steps: List<SetupStep>,
    val lede: String,
    val sleep: TimeSleep,
    val commitments: Commitments,
    val routines: Routines,
    val goals: TimeGoals,
    val build: BuildCard?,
    val replan: String?,
)

/** What a first-run demo arc shows: sleep or work on the outer ring, a session on the inner. */
enum class FirstRunKind {
    SLEEP,
    WORK,
    SESSION,
}

/** One demo arc, in minutes after midnight ([to] may pass midnight). */
data class FirstRunArc(val outer: Boolean, val kind: FirstRunKind, val from: Int, val to: Int)

/**
 * The first-run card (Today or Week with no plan yet): the demo dial, its words and its button,
 * which opens [section] (My time).
 */
data class FirstRun(
    val arcs: List<FirstRunArc>,
    val title: String,
    val text: String,
    val button: String,
    val section: String,
    val face: DialFaceSpec,
)
