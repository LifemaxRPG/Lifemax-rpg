package com.lifemax.rpg.feature.lifedirector.domain

/** The meal sheet's kind for the `sheet` command; its id is the meal block's first minute. */
const val MEAL_SHEET = "meal"

/**
 * The page's running timer (focusModel) for the timer sheet: the times it runs from, in epoch
 * milliseconds as the page keeps them, and its words. The screen derives the elapsed time from
 * [startedAt], [pausedMs] and [pausedAt] at each tick and never counts on its own, so it shows what
 * the page would show after a pause, a hide or a slow update. [xp] is the page's estimate now (the
 * page bolds the XP), null when the session pays none.
 */
data class TimerSheetModel(
    override val id: String,
    val title: String,
    val label: String,
    val paused: Boolean,
    val startedAt: Long,
    val pausedMs: Long,
    val pausedAt: Long?,
    val plannedMs: Long,
    val planned: String,
    val xp: List<GuideSpan>?,
    val finish: String,
    val pause: String,
    val stop: String,
    val note: String,
) : SheetModel {
    override val kind
        get() = SheetKinds.TIMER

    /** The same times as the dial's centre and up next read them. */
    val timer: FocusTimer
        get() = FocusTimer(startedAt, pausedMs, pausedAt, null)

    /** How much of the planned time has gone at [nowMs], 0..1, as the page's ring fills. */
    fun progress(nowMs: Long): Float =
        (timer.elapsedMs(nowMs).toDouble() / plannedMs).coerceIn(0.0, 1.0).toFloat()

    /** The timer act its second button sends: resume a paused timer, else pause it. */
    val pauseAct: String
        get() = if (paused) "resume" else "pause"
}

/** What a meal chip says: the meal's name, its calories, or whether it is logged. */
enum class MealChipKind {
    MEAL,
    KCAL,
    STATE,
}

data class MealChip(val kind: MealChipKind, val text: String)

/**
 * The page's planned meal (mealModel) for the meal sheet: Smart Diet's recipe when it has one
 * ([title], its calories and state among the [chips]), when it is, and the button that opens the
 * meal menu ([open]). [start] is the meal block's first minute (epoch seconds), its id.
 */
data class MealSheetModel(
    val start: Long,
    val title: String,
    val `when`: String,
    val chips: List<MealChip>,
    val open: String,
) : SheetModel {
    override val kind
        get() = MEAL_SHEET

    override val id
        get() = start.toString()
}

/**
 * Whether this model is the page's answer for the sheet the screen has [open]. A timer sheet opened
 * without an id (from up next or the dial's centre) means whichever timer runs; a sheet the page
 * says is gone without naming it (timer, meal, move) is gone for any id of its kind.
 */
fun SheetModel.answers(open: OpenSheet): Boolean =
    kind == open.kind &&
        (id == open.id ||
            (kind == SheetKinds.TIMER && open.id.isEmpty()) ||
            (this is GoneSheet && id.isEmpty()))
