package com.lifemax.rpg.core.quests

/**
 * What `renderQuests()` puts on the quest board, as data (QUESTS_SPEC section 6): the rows of each
 * list in the page's order, the empty-state lines and the summary strings. Rules only; markup,
 * icons' drawing and the Daily Hand cards above the lists are out.
 */
data class BoardView(
    /** The Daily list: quests that are not Hand cards (the cards have their own table). */
    val daily: BoardList<QuestRow>,
    val weekly: BoardList<QuestRow>,
    /** The Nutrition card: hidden when it has no rows. */
    val nutrition: List<QuestRow>,
    /** The epic milestones, by stage. */
    val epics: BoardList<EpicRow>,
    val summary: BoardSummary,
)

/** One list: its rows, and the line shown when the list is empty (null: no line). */
data class BoardList<out T>(val rows: List<T>, val empty: String?)

/**
 * A quest row (`questRow`). [desc] is the small line, null when the quest has no description;
 * [reward] is the text `+<xp> XP`; a note field is offered while the quest is undone.
 */
data class QuestRow(
    val id: String,
    val name: String,
    val desc: String?,
    val reward: String,
    val done: Boolean,
) {
    /** The private-note field (`qnote-<id>`) is shown while the quest is not done. */
    val noteField: Boolean
        get() = !done
}

/**
 * An epic row. A locked epic shows a lock, the unlock level and no checkbox, so the page shows
 * neither its id nor its done state; [done] and [id] are still what the save holds.
 */
data class EpicRow(
    val id: String,
    val name: String,
    val sub: String,
    val reward: String,
    val locked: Boolean,
    val done: Boolean,
) {
    /** The icon name: `lock`, `trophy` (done) or `flag`. */
    val icon: String
        get() = if (locked) "lock" else if (done) "trophy" else "flag"
}

/** `qosUpdateSummary`: every string the summary writes (QST-29, QST-30). */
data class BoardSummary(
    /** `dDone / daily.length` as `n / m`. */
    val progress: String,
    /** The rounded percent with `%`; also the width of the bar. */
    val percent: String,
    val dailyTotal: Int,
    val weeklyTotal: Int,
    /** Listed XP of every undone quest, `toLocaleString()` in the page's locale (en-US). */
    val availableXp: String,
    val dailyCount: String,
    val weeklyCount: String,
    val nutritionCount: String,
    val motivation: String,
)
