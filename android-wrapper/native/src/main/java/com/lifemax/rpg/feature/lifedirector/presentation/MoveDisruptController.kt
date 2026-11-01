package com.lifemax.rpg.feature.lifedirector.presentation

import com.lifemax.rpg.feature.lifedirector.domain.DISRUPT_SHEET
import com.lifemax.rpg.feature.lifedirector.domain.DisruptOption
import com.lifemax.rpg.feature.lifedirector.domain.DisruptSection
import com.lifemax.rpg.feature.lifedirector.domain.DisruptSheetModel
import com.lifemax.rpg.feature.lifedirector.domain.LifeDirectorProjection
import com.lifemax.rpg.feature.lifedirector.domain.MoveSheetModel
import com.lifemax.rpg.feature.lifedirector.domain.MoveTime
import com.lifemax.rpg.feature.lifedirector.domain.SheetKinds
import kotlinx.coroutines.flow.StateFlow

/**
 * Move, "Something came up" and the banner's answers (phase 4A), on the screen: the two sheets are
 * asked of the page like the session sheet, and every answer is a Today write through
 * [SheetController], one at a time with its operation id. The page decides everything: which times
 * fit, what each fold offers, and whether a choice is still one it offers.
 *
 * Operation ids: a move per session, a disrupt per kind and the regroup pause each keep theirs
 * until a terminal reply, so a reply lost while the screen hid is answered "Already added" (or
 * recorded) on the retry instead of adding twice; the next tap after one is a new operation.
 */
internal class MoveDisruptController(
    private val sheets: SheetController,
    private val projection: StateFlow<LifeDirectorProjection?>,
    private val ui: StateFlow<DirectorUi>,
    /** a handle, a guide or another sheet holds the screen */
    private val blocked: () -> Boolean,
) {
    /** The Move sheet on show, once the page has sent its model. */
    val move: MoveSheetModel?
        get() = shown() as? MoveSheetModel

    /** The "Something came up" sheet on show, once the page has sent its model. */
    val disrupt: DisruptSheetModel?
        get() = shown() as? DisruptSheetModel

    private fun shown() =
        ui.value.sheet?.let { open ->
            projection.value?.sheet?.takeIf { it.kind == open.kind && it.id == open.id }
        }

    /** Whether a Today tap may run here now (the page answers it, the screen is free). */
    private val free: Boolean
        get() = sheets.available && !blocked() && ui.value.pendingToday == null

    /**
     * Move, from the session sheet: the session's open times replace its sheet, as the page's do.
     */
    fun openMove(id: String) {
        if (!sheets.available || blocked() || id.isEmpty()) return
        sheets.open(SheetKinds.MOVE, id)
    }

    /** An open time: the session moves there and is pinned; the sheet closes once it has landed. */
    fun moveTo(time: MoveTime) {
        val m = move ?: return
        if (!free) return
        sheets.send("moveTo", "moveTo" to m.id, mapOf("id" to m.id, "start" to time.start), true)
    }

    /** "Something came up" from Today's foot. */
    fun openDisrupt() {
        if (!free || ui.value.sheet != null) return
        sheets.open(DISRUPT_SHEET, "")
    }

    /**
     * A fold's button ([option] null, with the form's [values]) or one of its choices. A disrupt
     * closes the sheet only when it changed the plan, as the page's does; an energy check-in closes
     * it once it has landed (the page closes its sheet first).
     */
    fun answer(section: DisruptSection, values: Map<String, Any>, option: DisruptOption?) {
        val d = disrupt ?: return
        if (!free || section !in d.sections) return
        val disrupting = section.command == DISRUPT_SHEET
        sheets.send(
            section.command,
            section.command to if (disrupting) section.key else "",
            section.payload(values, option),
            fromSheet = true,
            keepOnRepeat = disrupting,
        )
    }

    /** Re-plan from now (Today's foot, a bad banner): a re-plan twice is the same plan. */
    fun replan() {
        if (free && ui.value.sheet == null) sheets.send("replan", "replan" to "", emptyMap(), false)
    }

    /**
     * One of the regroup question's answers (pause, light, go); the pause keeps its id on retry.
     */
    fun regroup(v: String) {
        if (!free || ui.value.sheet != null || v.isEmpty()) return
        sheets.send("regroup", "regroup" to v, mapOf("v" to v), false)
    }

    /** The banner's ×: the page drops the banner only if it is still the one the screen showed. */
    fun dismissBanner() {
        val b = projection.value?.banner ?: return
        if (free && ui.value.sheet == null)
            sheets.send("dismissBanner", "dismissBanner" to "", mapOf("text" to b.text), false)
    }
}
