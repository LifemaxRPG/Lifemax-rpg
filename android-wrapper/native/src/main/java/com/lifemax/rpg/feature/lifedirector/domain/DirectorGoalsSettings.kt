package com.lifemax.rpg.feature.lifedirector.domain

/**
 * One choice of a segmented control or a priority: the value sent back and the page's word for it.
 */
data class Choice(val value: String, val label: String)

/** A goal's share of the week: 0..1 with the page's own percentages, and its sessions line. */
data class GoalShare(
    val target: Float,
    val targetText: String,
    val scheduled: Float,
    val scheduledText: String,
    val line: String,
)

data class GoalClarify(val text: String, val options: List<Choice>)

data class GoalCard(
    val id: String,
    val title: String,
    val sub: String,
    val paused: Boolean,
    val priority: Int,
    val share: GoalShare?,
    val warning: String?,
    val clarify: GoalClarify?,
    val pause: String,
    val remove: String,
)

data class TitledText(val title: String, val text: String)

/**
 * The page's Goals (phase 3B): the explanation, a card per goal, the priorities a goal may take,
 * and the page's words.
 */
data class DirectorGoals(
    val explain: TitledText,
    val priorities: List<Choice>,
    val cards: List<GoalCard>,
    val empty: TitledText?,
    val add: String,
    val removeConfirm: String,
)

/**
 * A slider: its bounds and step, its value, and the page's label for every stop (so the label
 * follows the finger).
 */
data class SettingRange(
    val key: String,
    val label: String,
    val hint: String,
    val min: Int,
    val max: Int,
    val step: Int,
    val value: Int,
    val stops: List<String>,
) {
    val stopCount: Int
        get() = (max - min) / step + 1

    fun labelAt(v: Int): String =
        stops.getOrElse(((v - min) / step).coerceIn(0, stops.lastIndex)) { "" }
}

data class ChoiceSetting(val title: String, val value: String, val options: List<Choice>)

data class RestDay(val day: Int, val label: String, val on: Boolean)

data class SettingsData(
    val title: String,
    val text: String,
    val storageWarning: String,
    val storageUnavailable: Boolean,
    val copy: String,
    val restore: String,
    val reset: String,
    val resetConfirm: String,
    val restoreTitle: String,
    val restoreText: String,
    val restoreButton: String,
)

/** The page's Settings (phase 3B). */
data class DirectorSettings(
    val intensity: ChoiceSetting,
    val intensityNote: String,
    val ranges: List<SettingRange>,
    val restDaysTitle: String,
    val restDaysNote: String,
    val restDays: List<RestDay>,
    val horizon: ChoiceSetting,
    val misses: ChoiceSetting,
    val missesText: List<GuideSpan>,
    val grace: SettingRange,
    val guide: TitledText,
    val guideButton: String,
    val data: SettingsData,
    val diagnosticsTitle: String,
    val diagnostics: String,
    val labLabel: String,
    val labOn: Boolean,
)
