package com.lifemax.rpg.feature.plan.domain

/** How the page's calendar classes a day (dayCls). */
enum class DayStatus {
    DONE,
    MISSED,
    TODAY,
    REST,
    UPCOMING,
}

/** The guided-workout card, worded as the page words it. */
data class PlanLauncher(
    val title: String,
    val detail: String,
    val button: String,
    val enabled: Boolean,
)

/** The weight or minutes field of an exercise, and what was entered last time. */
data class PlanValue(val text: String, val placeholder: String, val last: String)

data class PlanLog(val text: String, val previous: String)

/** One exercise of a day: a box per set, maybe a value field, and a session log. */
data class PlanItem(
    val key: String,
    val title: String,
    val detail: String,
    val optional: Boolean,
    val sets: List<Boolean>,
    val value: PlanValue?,
    val log: PlanLog,
)

data class PlanDay(
    val date: String,
    val weekday: String,
    val dayOfMonth: String,
    val kind: String,
    val kindLabel: String,
    /** The strength session's letter; empty for other kinds, which show [icon]. */
    val glyph: String,
    val icon: String,
    val status: DayStatus,
    val today: Boolean,
    val title: String,
    val description: String,
    val tag: String,
    val note: String,
    /** A day still to come: its boxes and fields cannot be used yet. */
    val locked: Boolean,
    val items: List<PlanItem>,
    val guided: PlanLauncher,
) {
    val rest: Boolean
        get() = kind == "rest"
}

data class PlanWeek(
    val number: String,
    val label: String,
    val range: String,
    val meta: String,
    val done: Int,
    val total: Int,
    val complete: Boolean,
    val current: Boolean,
    /** Monday first; null where the plan has no day (before it starts). */
    val days: List<PlanDay?>,
)

data class PlanKeyTag(val icon: String, val label: String)

/** A line of the General rules, with its bold lead (empty when it has none). */
data class PlanRule(val lead: String, val text: String)

/** A point of the page in CSS pixels from its top left, which are dp on the phone. */
data class PagePoint(val x: Float, val y: Float)

data class PlanProjection(
    val session: String,
    val sequence: Long,
    val program: String,
    val status: String,
    val summary: String,
    val sessionsDone: Int,
    val sessionsTotal: Int,
    val weeks: List<PlanWeek>,
    /** The page's selected day; [selectedRevision] rises whenever the page changes it. */
    val selectedDate: String,
    val selectedRevision: Long,
    /** A paused guided session, which takes the launcher's place on every day. */
    val guidedSession: PlanLauncher?,
    val key: List<PlanKeyTag>,
    val rules: List<PlanRule>,
    val progression: List<PlanRule>,
    val reducedMotion: Boolean,
    /** Where the page's XP orbs fly: the top bar's logo, which the shared bar draws there too. */
    val celebrationTarget: PagePoint,
    val tokens: Map<String, Long>,
    val light: Boolean,
) {
    private val days = weeks.flatMap { it.days }.filterNotNull().associateBy { it.date }

    fun day(date: String): PlanDay? = days[date]

    /** The decoder accepts only a projection whose selected day is a day of the plan. */
    val selectedDay: PlanDay
        get() = days.getValue(selectedDate)

    fun weekOf(day: PlanDay): Int = weeks.indexOfFirst { day in it.days }
}
