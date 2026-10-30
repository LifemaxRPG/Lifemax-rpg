package com.lifemax.rpg.feature.plan.domain

import kotlinx.coroutines.flow.StateFlow

interface PlanRepository {
    val state: StateFlow<PlanProjection?>
}

/** One set's box: a day, an exercise of that day and the set's index. */
data class SetRef(val date: String, val item: String, val index: Int)

/** What the screen asks the page to do. The page answers with a [PlanReply] naming [requestId]. */
fun interface PlanCommands {
    /** Tick or clear one set. The set ends as asked, so sending it again changes nothing. */
    fun toggle(requestId: String, set: SetRef, checked: Boolean)
}

data class XpGain(val attribute: String, val short: String, val xp: Int, val color: Long)

/** An award the page held back for the screen to draw (celebrateXP). */
data class Celebration(val xp: Int, val gains: List<XpGain>)

/**
 * The page's answer to a toggle. [changed] is false when the set was already as asked and nothing
 * was saved; when [ok] is false nothing changed and [message] says why.
 */
data class PlanReply(
    val requestId: String,
    val ok: Boolean,
    val changed: Boolean,
    val message: String,
    val celebrations: List<Celebration>,
)

/** Actions the page runs with its own handler, after the screen has stepped aside. */
sealed interface PlanPageAction {
    data class Guided(val date: String) : PlanPageAction

    data class EditValue(val date: String, val item: String) : PlanPageAction

    data class EditLog(val date: String, val item: String) : PlanPageAction

    data object Leave : PlanPageAction
}
