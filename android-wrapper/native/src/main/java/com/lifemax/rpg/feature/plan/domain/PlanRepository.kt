package com.lifemax.rpg.feature.plan.domain

import com.lifemax.rpg.core.designsystem.feedback.XpAward
import kotlinx.coroutines.flow.StateFlow

interface PlanRepository {
    val state: StateFlow<PlanProjection?>

    /** The offer the page makes while there is no plan; null while there is one. */
    val offers: StateFlow<PlanOfferProjection?>
}

/** One set's box: a day, an exercise of that day and the set's index. */
data class SetRef(val date: String, val item: String, val index: Int)

/** What the screen asks the page to do. The page answers with a [PlanReply] naming [requestId]. */
fun interface PlanCommands {
    /** Tick or clear one set. The set ends as asked, so sending it again changes nothing. */
    fun toggle(requestId: String, set: SetRef, checked: Boolean)
}

/** An attribute's share of a [Celebration]: the shared celebration's type. */
typealias XpGain = com.lifemax.rpg.core.designsystem.feedback.XpGain

/**
 * An award the page held back for the screen to draw (celebrateXP): the shared celebration's type.
 */
typealias Celebration = XpAward

/**
 * The page's answer to a toggle. [changed] is false when the set was already as asked and nothing
 * was saved. When [ok] is false nothing was saved and [message] says why; the projection sent with
 * the answer shows the boxes as the page holds them (a tick whose save failed stays in the page's
 * memory, as after its own checkbox, until a later save stores it).
 */
data class PlanReply(
    val requestId: String,
    val ok: Boolean,
    val changed: Boolean,
    val message: String,
    val celebrations: List<Celebration>,
    /** The command it answers (toggle, value, log, leave, signUp or runner). */
    val command: String = "toggle",
)

/** Actions the page runs with its own handler, after the screen has stepped aside. */
sealed interface PlanPageAction {
    data class Guided(val date: String) : PlanPageAction

    data class EditValue(val date: String, val item: String) : PlanPageAction

    data class EditLog(val date: String, val item: String) : PlanPageAction

    data object Leave : PlanPageAction
}

/**
 * A write the screen asks of the page beyond ticking a box (plan.fields.v1, plan.leave.v1,
 * plan.offers.v1, plan.runner.v1): [command] and [target] name the operation, [args] go to the page
 * with it. The page runs its own function and answers with a [PlanReply].
 */
data class PlanWrite(val command: String, val target: String, val args: Map<String, Any?>)

fun interface PlanWrites {
    fun send(requestId: String, operation: String, write: PlanWrite)
}
