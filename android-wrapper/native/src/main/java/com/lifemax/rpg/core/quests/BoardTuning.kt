package com.lifemax.rpg.core.quests

/**
 * The numbers and names the quest board's rules are built on, in one place so a test can change one
 * and see the golden cases fail ([PAGE] is what the page has). Nothing here is a rule by itself.
 */
data class BoardTuning(
    /** `stageLvl`'s table: the character level each quest stage unlocks at, by stage. */
    val stageLevels: List<Int>,
    /** The least a custom quest pays (`Math.max(1, ...)`). */
    val minXp: Double,
    /** The most a custom quest pays (`Math.min(500, ...)`). */
    val maxXp: Double,
    /** What a custom quest's XP field becomes when it is empty, zero or not a number. */
    val defaultXp: Double,
    /** `getDay()` order: the names `questToday` compares a quest's `day` with. */
    val weekdays: List<String>,
) {
    companion object {
        /** The page's own values (QST-5, QST-11, QST-15). */
        val PAGE =
            BoardTuning(
                stageLevels = listOf(0, 1, 3, 5, 8, 12),
                minXp = 1.0,
                maxXp = 500.0,
                defaultXp = 25.0,
                weekdays = listOf("sun", "mon", "tue", "wed", "thu", "fri", "sat"),
            )
    }
}
