package com.lifemax.rpg.feature.lifedirector.presentation

import com.lifemax.rpg.feature.lifedirector.domain.LifeDirectorProjection
import com.lifemax.rpg.feature.lifedirector.domain.MEAL_SHEET
import com.lifemax.rpg.feature.lifedirector.domain.MealSheetModel
import com.lifemax.rpg.feature.lifedirector.domain.SheetKinds
import com.lifemax.rpg.feature.lifedirector.domain.Tap
import com.lifemax.rpg.feature.lifedirector.domain.TimerSheetModel
import com.lifemax.rpg.feature.lifedirector.domain.answers
import kotlinx.coroutines.flow.StateFlow

/**
 * Today's timer and meal sheets (phase 4A), on top of [sheets]: which to ask the page for, and the
 * timer's writes. Each write is the page's `today` command `timer` with its act, through the
 * sheets' pending-operation path (operation ids `timer.<act>` per session), so one runs at a time
 * and a lost reply is sent again with the same id. Finish and Stop name the start of the timer they
 * mean, so a repeat after one landed is not taken for a new timer: the page answers "Already
 * recorded." instead of paying twice.
 */
internal class TimerMealController(
    private val sheets: SheetController,
    private val projection: StateFlow<LifeDirectorProjection?>,
    private val ui: StateFlow<DirectorUi>,
    /** a handle, a guide or another sheet holds the screen */
    private val blocked: () -> Boolean,
) {
    /** The timer sheet on show, once the page has sent the running timer's model. */
    val timer: TimerSheetModel?
        get() = shown() as? TimerSheetModel

    /** The meal sheet on show, once the page has sent the meal's model. */
    val meal: MealSheetModel?
        get() = shown() as? MealSheetModel

    private fun shown() =
        ui.value.sheet?.let { open -> projection.value?.sheet?.takeIf { it.answers(open) } }

    private val free: Boolean
        get() = sheets.available && !blocked() && ui.value.sheet == null

    /**
     * The running timer's sheet: of session [id], or of whichever timer runs when [id] is empty (up
     * next's Open timer, the dial's centre).
     */
    fun openTimer(id: String) {
        if (free) sheets.open(SheetKinds.TIMER, id)
    }

    /** A planned meal's sheet: [start] is its block's first minute, as its arc and row name it. */
    fun openMeal(start: String) {
        if (free && start.isNotEmpty()) sheets.open(MEAL_SHEET, start)
    }

    /** Pause or Resume: the sheet stays open and shows the page's next model. */
    fun pauseOrResume() {
        val t = timer ?: return
        act(t.id, t.pauseAct, emptyMap(), fromSheet = false)
    }

    /**
     * Finish and log, as the page's focusFinish: the page logs the minutes run so far from the
     * timer's own start, clears the timer and celebrates; the sheet closes once it has landed.
     */
    fun finish() {
        val t = timer ?: return
        act(t.id, "finish", mapOf("start" to t.startedAt), fromSheet = true)
    }

    /** Reset elapsed time without logging; keep the timer paused and available to resume. */
    fun reset() {
        val t = timer ?: return
        act(t.id, "reset", mapOf("start" to t.startedAt), fromSheet = false)
    }

    /** Stop without logging: the page clears the timer and says nothing was logged. */
    fun stop() {
        val t = timer ?: return
        act(t.id, "cancel", mapOf("start" to t.startedAt), fromSheet = true)
    }

    /**
     * Up next's Finish and log: the running timer, by the start up next shows (the page names no
     * session there, so a repeat after it landed answers as a timer that is no longer running).
     */
    fun finishRunning() {
        val today = projection.value?.today ?: return
        val running = today.upNext?.timer ?: today.centre.timer ?: return
        if (free) sheets.today("timer", "", timerArgs("finish", running.startedAt), false)
    }

    /**
     * The meal sheet's button: the page's meal menu, handed over as mealOpen (the page's own sheet
     * button's act), so one tap opens the menu rather than the page's meal sheet first.
     */
    fun mealTap(): Tap? = meal?.let { Tap("mealOpen", it.id) }

    private fun act(id: String, act: String, extra: Map<String, Any>, fromSheet: Boolean) {
        if (blocked() || !sheets.available) return
        sheets.today("timer", id, mapOf("act" to act) + extra, fromSheet)
    }

    private fun timerArgs(act: String, start: Long) = mapOf("act" to act, "start" to start)
}
