package com.lifemax.rpg.feature.lifedirector.presentation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.lifemax.rpg.feature.lifedirector.domain.DirectorGoals
import com.lifemax.rpg.feature.lifedirector.domain.DirectorQuests
import com.lifemax.rpg.feature.lifedirector.domain.DirectorSettings
import com.lifemax.rpg.feature.lifedirector.domain.DirectorWeek
import com.lifemax.rpg.feature.lifedirector.domain.Tap
import com.lifemax.rpg.feature.lifedirector.presentation.components.ButtonLook
import com.lifemax.rpg.feature.lifedirector.presentation.components.DirectorButton
import com.lifemax.rpg.feature.lifedirector.presentation.components.Slice
import com.lifemax.rpg.feature.lifedirector.presentation.guide.GuideAnchors
import com.lifemax.rpg.feature.lifedirector.presentation.guide.guideAnchor
import com.lifemax.rpg.feature.lifedirector.presentation.sections.ChoiceCard
import com.lifemax.rpg.feature.lifedirector.presentation.sections.DataCard
import com.lifemax.rpg.feature.lifedirector.presentation.sections.DiagnosticsCard
import com.lifemax.rpg.feature.lifedirector.presentation.sections.GoalCardView
import com.lifemax.rpg.feature.lifedirector.presentation.sections.GoalsEmpty
import com.lifemax.rpg.feature.lifedirector.presentation.sections.GoalsExplain
import com.lifemax.rpg.feature.lifedirector.presentation.sections.GuideCard
import com.lifemax.rpg.feature.lifedirector.presentation.sections.IntensityCard
import com.lifemax.rpg.feature.lifedirector.presentation.sections.MilestoneRowView
import com.lifemax.rpg.feature.lifedirector.presentation.sections.MilestonesHeading
import com.lifemax.rpg.feature.lifedirector.presentation.sections.MissesCard
import com.lifemax.rpg.feature.lifedirector.presentation.sections.QuestBoardCard
import com.lifemax.rpg.feature.lifedirector.presentation.sections.QuestRowView
import com.lifemax.rpg.feature.lifedirector.presentation.sections.QuestsEmpty
import com.lifemax.rpg.feature.lifedirector.presentation.sections.RestDaysCard
import com.lifemax.rpg.feature.lifedirector.presentation.sections.WeekFitCard
import com.lifemax.rpg.feature.lifedirector.presentation.sections.WeekLegend
import com.lifemax.rpg.feature.lifedirector.presentation.sections.WeekRow
import com.lifemax.rpg.feature.lifedirector.presentation.sections.WeekStats
import com.lifemax.rpg.feature.lifedirector.presentation.sections.WeekTicks
import com.lifemax.rpg.feature.lifedirector.presentation.sections.XpYieldCard

/*
 * The bodies of Life Director's sections below the frame, each in the order its page renderer draws it, one panel slice per list
 * item. [body] is the section-change slide every body item takes.
 */

/** Week below the frame (renderWeek's order); a day row opens that day on Today. */
internal fun LazyListScope.weekBody(
    week: DirectorWeek,
    ui: DirectorUi,
    actions: DirectorActions,
    anchors: GuideAnchors,
    body: Modifier,
) {
    item("week-stats") {
        Panel(Slice.MIDDLE) { Box(body.guideAnchor(anchors, "week.stats")) { WeekStats(week) } }
    }
    // Week's guide points at the bars: the ticks and the first rows, as the page's spotlight takes
    // its .ld-week
    item("week-ticks") {
        Panel(Slice.MIDDLE) {
            Box(body.guideAnchor(anchors, "week.rows")) { WeekTicks(week.ticks) }
        }
    }
    items(week.days, key = { "week-day-" + it.index }) { day ->
        Panel(Slice.MIDDLE) {
            Box(body.padding(vertical = 2.dp).guideAnchor(anchors, "week.rows#" + day.index)) {
                WeekRow(day, ui.pendingSection != null) {
                    actions.tap(Tap("gotoDay", it.toString()))
                }
            }
        }
    }
    item("week-legend") { Panel(Slice.MIDDLE) { Box(body) { WeekLegend(week) } } }
    week.yield?.let { y ->
        item("week-yield") {
            Panel(Slice.MIDDLE) { Box(body.guideAnchor(anchors, "week.yield")) { XpYieldCard(y) } }
        }
    }
    item("week-fit") { Panel(Slice.BOTTOM) { Box(body) { WeekFitCard(week.fit) } } }
}

/** Quests below the frame (renderQuests' order). */
internal fun LazyListScope.questsBody(q: DirectorQuests, actions: DirectorActions, body: Modifier) {
    val miles = q.milestones
    val hasRows = q.rows.isNotEmpty() || q.passive.isNotEmpty()
    val headLast = !hasRows && miles == null && q.empty.isEmpty()
    item("quests-head") {
        Panel(if (headLast) Slice.BOTTOM else Slice.MIDDLE) {
            Box(body) { QuestBoardCard(q, actions.tap) }
        }
    }
    if (!hasRows && q.empty.isNotEmpty())
        item("quests-empty") {
            Panel(if (miles == null) Slice.BOTTOM else Slice.MIDDLE) {
                Box(body) { QuestsEmpty(q.empty) }
            }
        }
    val all = q.rows + q.passive
    itemsIndexed(all, key = { i, _ -> "quest-$i" }) { i, row ->
        Panel(if (miles == null && i == all.lastIndex) Slice.BOTTOM else Slice.MIDDLE) {
            Box(body) { QuestRowView(row) }
        }
    }
    if (miles != null) {
        item("milestones") { Panel(Slice.MIDDLE) { Box(body) { MilestonesHeading(miles) } } }
        itemsIndexed(miles.rows, key = { i, _ -> "milestone-$i" }) { i, m ->
            Panel(if (i == miles.rows.lastIndex) Slice.BOTTOM else Slice.MIDDLE) {
                Box(body) { MilestoneRowView(m.title, m.desc, m.earned, m.status) }
            }
        }
        if (miles.rows.isEmpty()) item("milestones-end") { Panel(Slice.BOTTOM) {} }
    }
}

/**
 * Goals below the frame (renderGoals' order): the explanation, a card per goal or the empty words,
 * then Add a goal (the page's sheet).
 */
internal fun LazyListScope.goalsBody(
    g: DirectorGoals,
    ui: DirectorUi,
    actions: DirectorActions,
    body: Modifier,
) {
    val pending = ui.pendingChange != null
    item("goals-explain") { Panel(Slice.MIDDLE) { Box(body) { GoalsExplain(g.explain) } } }
    g.empty?.let { e ->
        item("goals-empty") { Panel(Slice.MIDDLE) { Box(body) { GoalsEmpty(e) } } }
    }
    items(g.cards, key = { "goal-" + it.id }) { card ->
        Panel(Slice.MIDDLE) { Box(body) { GoalCardView(card, g, pending, actions.goals) } }
    }
    item("goals-add") {
        Panel(Slice.BOTTOM) {
            Box(body.padding(top = 4.dp)) {
                DirectorButton(
                    g.add,
                    ButtonLook.PRIMARY,
                    { actions.tap(Tap("addGoal")) },
                    Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

/** Settings below the frame (renderSettings' order). */
internal fun LazyListScope.settingsBody(
    s: DirectorSettings,
    ui: DirectorUi,
    actions: DirectorActions,
    body: Modifier,
) {
    val pending = ui.pendingChange != null
    val a = actions.settings
    item("settings-intensity") {
        Panel(Slice.MIDDLE) { Box(body) { IntensityCard(s, pending, a) } }
    }
    item("settings-rest") { Panel(Slice.MIDDLE) { Box(body) { RestDaysCard(s, pending, a) } } }
    item("settings-horizon") {
        Panel(Slice.MIDDLE) {
            Box(body) { ChoiceCard(s.horizon, "planningHorizonDays", pending, a) }
        }
    }
    item("settings-misses") { Panel(Slice.MIDDLE) { Box(body) { MissesCard(s, pending, a) } } }
    item("settings-guide") { Panel(Slice.MIDDLE) { Box(body) { GuideCard(s, pending, a) } } }
    item("settings-data") { Panel(Slice.MIDDLE) { Box(body) { DataCard(s, pending, a) } } }
    item("settings-diagnostics") {
        Panel(Slice.BOTTOM) { Box(body) { DiagnosticsCard(s, pending, a) } }
    }
}
