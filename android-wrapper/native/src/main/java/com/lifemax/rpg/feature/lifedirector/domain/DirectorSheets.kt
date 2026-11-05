package com.lifemax.rpg.feature.lifedirector.domain

/** The sheets the screen can ask the page for (phase 4A) with the `sheet` command. */
object SheetKinds {
    const val SESSION = "session"
    const val TIMER = "timer"
    const val MOVE = "move"
}

/**
 * The model of the sheet the screen asked for, as the page projects it (`sheet`): only while the
 * screen has one open, and for the [kind] and [id] it asked for.
 */
sealed interface SheetModel {
    val kind: String
    val id: String
}

/** What the sheet showed has left the plan: the screen closes it and says so. */
data class GoneSheet(override val kind: String, override val id: String) : SheetModel

/** Which of the page's three bodies a session sheet has: doable now, later, or already past. */
enum class SessionMode {
    NOW,
    LATER,
    PAST,
}

/** The minutes field: its label and value, and the limits the page keeps (1..480). */
data class SheetMinutes(val label: String, val value: Int, val min: Int, val max: Int)

/**
 * The main button: a Today [command] ("timer") with its [act] ("start"), or another [sheet] to open
 * ("timer").
 */
data class SheetPrimary(
    val label: String,
    val command: String? = null,
    val act: String? = null,
    val sheet: String? = null,
)

/** Mark done ([command] "done") or Log it for today ("logToday"), with its small print. */
data class SheetComplete(val label: String, val command: String, val smallPrint: String)

/**
 * A small button of the sheet's row: a [command] (pin with [on], skip, openPlan with [key]) or a
 * [sheet] to open (move).
 */
data class SheetRowButton(
    val label: String,
    val command: String? = null,
    val sheet: String? = null,
    val on: Boolean? = null,
    val key: String? = null,
)

/** One reason the session sits where it does, and its bar's share of the 24-point scale. */
data class WhyFactor(val name: String, val points: String, val frac: Float)

/**
 * "Why this time?": the engine's summary, its factors and on how many days it could have fitted.
 */
data class SheetWhy(
    val title: String,
    val summary: String,
    val factors: List<WhyFactor>,
    val feasible: String,
)

/**
 * The page's session sheet (instSheet), branch for branch: the header ([state] colours its rail),
 * the chips, then the [mode]'s body (now: [primary], [minutes], [complete] and [row]; later: the
 * [note], [minutes], [complete] and [row]; past: [past]), "Why this time?" and the [orphan]
 * warning.
 */
data class SessionSheetModel(
    override val id: String,
    val state: SessionState,
    val icon: String,
    val title: String,
    val `when`: String,
    val chips: List<Chip>,
    val mode: SessionMode,
    val note: List<GuideSpan>,
    val minutes: SheetMinutes?,
    val primary: SheetPrimary?,
    val complete: SheetComplete?,
    val row: List<SheetRowButton>,
    val past: String,
    val why: SheetWhy,
    val orphan: String,
    val description: String = "",
) : SheetModel {
    override val kind
        get() = SheetKinds.SESSION
}

/** The sheet the screen has open: what it asked the page for. */
data class OpenSheet(val kind: String, val id: String)
