package com.lifemax.rpg.feature.lifedirector.presentation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.ui.Modifier
import com.lifemax.rpg.feature.lifedirector.domain.DirectorTime
import com.lifemax.rpg.feature.lifedirector.domain.FirstRun
import com.lifemax.rpg.feature.lifedirector.domain.GuideTag
import com.lifemax.rpg.feature.lifedirector.domain.Sections
import com.lifemax.rpg.feature.lifedirector.domain.Tap
import com.lifemax.rpg.feature.lifedirector.presentation.components.Slice
import com.lifemax.rpg.feature.lifedirector.presentation.guide.GuideAnchors
import com.lifemax.rpg.feature.lifedirector.presentation.guide.guideAnchor
import com.lifemax.rpg.feature.lifedirector.presentation.sections.BuildWeekCard
import com.lifemax.rpg.feature.lifedirector.presentation.sections.CommitmentsCard
import com.lifemax.rpg.feature.lifedirector.presentation.sections.FirstRunCard
import com.lifemax.rpg.feature.lifedirector.presentation.sections.ReplanRow
import com.lifemax.rpg.feature.lifedirector.presentation.sections.RoutinesCard
import com.lifemax.rpg.feature.lifedirector.presentation.sections.SetupSteps
import com.lifemax.rpg.feature.lifedirector.presentation.sections.SleepCard
import com.lifemax.rpg.feature.lifedirector.presentation.sections.TimeGoalsCard
import com.lifemax.rpg.feature.lifedirector.presentation.sections.TimeLede

/*
 * My time and the first-run card below the frame (phase 4B), one panel slice per list item, in renderSetup's order. Set-up's
 * guide points at the parts by these anchor names: time.steps, time.sleep (the mini dial), time.commitments,
 * time.routines, time.goals and time.build (Build my week, or Re-plan now).
 */

/** The list items of My time, in order, each with the guide anchor it carries. */
private fun timeItems(t: DirectorTime): List<String> = buildList {
    if (t.first && t.steps.isNotEmpty()) add("time.steps")
    add("time.lede")
    add("time.sleep")
    add("time.commitments")
    add("time.routines")
    add("time.goals")
    add("time.build")
}

/**
 * The list index of the item that holds guide anchor [anchor] (the frame is item 0), or -1 when My
 * time has no such part; the dial's anchor is in the sleep card's item.
 */
fun timeItemIndex(t: DirectorTime, anchor: String): Int {
    val i = timeItems(t).indexOf(anchor)
    return if (i < 0) -1 else i + 1
}

/** My time below the frame. [h12] is the page's clock style for a time picked on the screen. */
internal fun LazyListScope.timeBody(
    t: DirectorTime,
    ui: DirectorUi,
    actions: DirectorActions,
    anchors: GuideAnchors,
    body: Modifier,
    h12: Boolean,
    reducedMotion: Boolean,
    tags: List<GuideTag>,
) {
    val pending = ui.pendingChange != null
    val a = actions.time
    fun Modifier.anchor(name: String) = guideAnchor(anchors, name)
    if ("time.steps" in timeItems(t))
        item("time-steps") {
            Panel(Slice.MIDDLE) { SetupSteps(t.steps, body.anchor("time.steps")) }
        }
    item("time-lede") { Panel(Slice.MIDDLE) { Box(body) { TimeLede(t.lede) } } }
    item("time-sleep") {
        Panel(Slice.MIDDLE) {
            Box(body) {
                SleepCard(
                    t.sleep,
                    ui.timeDrafts.sleep ?: SleepDraft.of(t.sleep),
                    h12,
                    pending,
                    reducedMotion,
                    a,
                    Modifier.anchor("time.sleep"),
                    tags,
                )
            }
        }
    }
    item("time-commitments") {
        Panel(Slice.MIDDLE) {
            Box(body.anchor("time.commitments")) { CommitmentsCard(t.commitments, pending, a) }
        }
    }
    item("time-routines") {
        Panel(Slice.MIDDLE) {
            Box(body.anchor("time.routines")) {
                RoutinesCard(
                    t.routines,
                    { row -> ui.timeDrafts.routines[row.key] ?: row.value },
                    pending,
                    a,
                )
            }
        }
    }
    item("time-goals") {
        Panel(Slice.MIDDLE) {
            Box(body.anchor("time.goals")) {
                TimeGoalsCard(t.goals, pending, a) { actions.tap(Tap("tab", Sections.GOALS)) }
            }
        }
    }
    item("time-build") {
        Panel(Slice.BOTTOM) {
            val build = t.build
            val replan = t.replan
            if (build != null) BuildWeekCard(build, pending, a, body.anchor("time.build"))
            else if (replan != null) ReplanRow(replan, pending, a, body.anchor("time.build"))
        }
    }
}

/** The first-run card below the frame, on Today or Week while there is no plan. */
internal fun LazyListScope.firstRunBody(f: FirstRun, actions: DirectorActions, body: Modifier) {
    item("first-run") {
        Panel(Slice.BOTTOM) {
            Box(body) { FirstRunCard(f, { actions.tap(Tap("tab", f.section)) }) }
        }
    }
}
