package com.lifemax.rpg.feature.lifedirector.presentation

import com.lifemax.rpg.feature.lifedirector.domain.DialArc
import com.lifemax.rpg.feature.lifedirector.domain.SectionFlick
import com.lifemax.rpg.feature.lifedirector.domain.Sections
import com.lifemax.rpg.feature.lifedirector.domain.Tap
import com.lifemax.rpg.feature.lifedirector.domain.UpNextAction
import com.lifemax.rpg.feature.lifedirector.presentation.dial.HandleActions
import com.lifemax.rpg.feature.lifedirector.presentation.sections.GoalActions
import com.lifemax.rpg.feature.lifedirector.presentation.sections.SettingsActions
import com.lifemax.rpg.feature.lifedirector.presentation.sheets.MoveDisruptActions
import com.lifemax.rpg.feature.lifedirector.presentation.sheets.SessionActions
import com.lifemax.rpg.feature.lifedirector.presentation.sheets.TimerMealActions

/** Everything the screen can ask for, so the screen itself holds no state. */
class DirectorActions(
    val tap: (Tap) -> Unit = {},
    val day: (Int) -> Unit = {},
    val tip: (String) -> Unit = {},
    val earlier: () -> Unit = {},
    val handles: HandleActions = HandleActions(),
    val keep: () -> Unit = {},
    val change: () -> Unit = {},
    val touch: () -> Unit = {},
    val guideNext: () -> Unit = {},
    val guideBack: () -> Unit = {},
    val guideSkip: () -> Unit = {},
    val flick: (Int) -> Unit = {},
    val goals: GoalActions = GoalActions(),
    val settings: SettingsActions = SettingsActions(),
    val confirmChange: () -> Unit = {},
    val cancelChange: () -> Unit = {},
    val restoreDraft: (String) -> Unit = {},
    val submitRestore: () -> Unit = {},
    val closeRestore: () -> Unit = {},
    val session: SessionActions = SessionActions(),
    val timerMeal: TimerMealActions = TimerMealActions(),
    val moves: MoveDisruptActions = MoveDisruptActions(),
) {
    constructor(
        vm: LifeDirectorViewModel,
        emit: (action: String, id: String) -> Unit,
    ) : this(
        // a section or a day the screen draws is a command, and so is a session's sheet, its timer
        // and Done, the timer's and a meal's sheets, and Finish and log, where the page runs them
        // for the screen (phase 4A); any other tap the page answers (it closes the screen)
        tap = { t ->
            when {
                t.act == "inst" && vm.sheetsAvailable -> vm.openSession(t.id)
                t.act == "focusStart" && vm.sheetsAvailable -> vm.startTimer(t.id)
                t.act == "doneQuick" && vm.sheetsAvailable -> vm.doneQuick(t.id)
                t.act == "focusOpen" && vm.sheetsAvailable -> vm.timerMeal.openTimer(t.id)
                t.act == "focusFinish" && vm.sheetsAvailable -> vm.timerMeal.finishRunning()
                t.act == "meal" && vm.sheetsAvailable -> vm.timerMeal.openMeal(t.id)
                // Something came up, Re-plan and the banner's answers too (phase 4A)
                t.act == "disrupt" && vm.sheetsAvailable -> vm.moves.openDisrupt()
                t.act == "replan" && vm.sheetsAvailable -> vm.moves.replan()
                t.act == "regroup" && vm.sheetsAvailable -> vm.moves.regroup(t.id)
                t.act == "dismissBanner" && vm.sheetsAvailable -> vm.moves.dismissBanner()
                t.act == "tour" && vm.projection.value?.guide != null -> vm.startGuide()
                t.act == "tab" && vm.draws(t.id) -> vm.showSection(t.id)
                t.act == "gotoDay" && vm.draws(Sections.TODAY) ->
                    t.id.toIntOrNull()?.let(vm::gotoDay)
                vm.handOver() -> emit(t.act, t.id)
            }
        },
        day = vm::showDay,
        tip = vm::tip,
        earlier = vm::toggleEarlier,
        handles = HandleActions(vm::hold, vm::move, vm::release, vm::step),
        keep = vm::keep,
        change = vm::change,
        touch = vm::touch,
        guideNext = vm::guideNext,
        guideBack = vm::guideBack,
        guideSkip = vm::guideSkip,
        goals =
            GoalActions(
                priority = { id, v -> vm.change("goalPriority", mapOf("id" to id, "value" to v)) },
                pause = { id, paused ->
                    vm.change("goalPause", mapOf("id" to id, "paused" to paused))
                },
                remove = vm::askRemoveGoal,
                clarify = { id, arch -> vm.change("clarify", mapOf("id" to id, "arch" to arch)) },
            ),
        settings =
            SettingsActions(
                choose = { key, v -> vm.change("pref", mapOf("key" to key, "value" to v)) },
                range = { key, v -> vm.change("prefRange", mapOf("key" to key, "value" to v)) },
                restDay = { day, on -> vm.change("restDay", mapOf("day" to day, "on" to on)) },
                replayGuides = { vm.change("tourReset") },
                copy = { vm.change("exportBackup") },
                restore = vm::openRestore,
                reset = vm::askReset,
                lab = { on -> vm.change("labToggle", mapOf("on" to on)) },
            ),
        confirmChange = vm::confirmChange,
        cancelChange = vm::cancelChange,
        restoreDraft = vm::restoreDraft,
        submitRestore = vm::submitRestore,
        closeRestore = vm::closeRestore,
        session =
            SessionActions(
                close = vm::closeSheet,
                minutes = vm::sessionMinutes,
                step = vm::sessionStep,
                primary = {
                    vm.sessionPrimary()?.let { if (vm.handOverFromSheet()) emit(it.act, it.id) }
                },
                complete = vm::sessionComplete,
                row = { b ->
                    vm.sessionRow(b)?.let { if (vm.handOverFromSheet()) emit(it.act, it.id) }
                },
            ),
        timerMeal =
            TimerMealActions(
                close = vm::closeSheet,
                pause = vm.timerMeal::pauseOrResume,
                finish = vm.timerMeal::finish,
                stop = vm.timerMeal::stop,
                openMeal = {
                    vm.timerMeal.mealTap()?.let { if (vm.handOverFromSheet()) emit(it.act, it.id) }
                },
            ),
        moves = MoveDisruptActions(vm::closeSheet, vm.moves::moveTo, vm.moves::answer),
        flick = { dir ->
            val p = vm.projection.value
            val to =
                p?.let {
                    SectionFlick.neighbour(
                        it.tabs.map { t -> t.id },
                        vm.ui.value.pendingSection ?: it.tab,
                        dir,
                    )
                }
            if (to != null) {
                if (vm.draws(to)) vm.showSection(to) else if (vm.handOver()) emit("tab", to)
            }
        },
    )

    /** An arc runs its tap on the page; fixed time only says what it is. */
    fun arc(arc: DialArc) {
        when {
            arc.tap != null -> tap(arc.tap)
            arc.tip.isNotEmpty() -> tip(arc.tip)
        }
    }

    /** An up-next button runs its tap on the page or shows another day of the strip. */
    fun upNext(action: UpNextAction) {
        when {
            action.tap != null -> tap(action.tap)
            action.day != null -> day(action.day)
        }
    }
}
