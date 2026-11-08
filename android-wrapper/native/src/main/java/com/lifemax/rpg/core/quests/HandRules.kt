package com.lifemax.rpg.core.quests

/**
 * The tier odds the page keeps in `dhPick`'s code (QST-38), not in its content: luck moves weight
 * from tier 1 to tier 3, and the Maintenance card damps tier 3. A data class so the tests can
 * change one number and see the golden cases fail; the app uses [PAGE].
 */
data class HandOdds(
    /** `dhLuckScore()`'s answer when there is no luck roll today (or its score is falsy). */
    val luckFallback: Double = 5500.0,
    /** The lowest luck score; `(score - luckFloor) / luckSpan` is the luck used, not clamped. */
    val luckFloor: Double = 1000.0,
    val luckSpan: Double = 8999.0,
    /** Tier 1 weighs `tier1 - luck * tier1Luck`. */
    val tier1: Double = 1.2,
    val tier1Luck: Double = 0.4,
    /** Tier 2 always weighs this. */
    val tier2: Double = 1.0,
    /** Tier 3 weighs `tier3 + luck * tier3Luck`. */
    val tier3: Double = 0.5,
    val tier3Luck: Double = 1.0,
    /** A tier-3 template drawn for the Maintenance card weighs this much of its weight. */
    val maintenanceTier3: Double = 0.3,
) {
    /** The luck the weights use, from the score the draw was given. */
    fun luck(score: Double): Double = (score - luckFloor) / luckSpan

    /** A template's weight, in the page's order of operations. */
    fun weight(tier: Int, luck: Double, slot: HandSlot?): Double {
        val base =
            when (tier) {
                1 -> tier1 - luck * tier1Luck
                2 -> tier2
                else -> tier3 + luck * tier3Luck
            }
        return if (slot == HandSlot.MAINTENANCE && tier == TOP_TIER) base * maintenanceTier3
        else base
    }

    companion object {
        /** The page's numbers. */
        val PAGE = HandOdds()

        /**
         * The top tier: a rest day leaves it out of every pool (REST-DAY-1), and Maintenance damps
         * it.
         */
        const val TOP_TIER = 3
    }
}

/**
 * The three cards, in the page's slot order (`['maintenance', 'class', 'wildcard']`): Maintenance
 * draws from the weakest attribute, Class from the class's attributes (any when the class has
 * none), Wildcard from any.
 */
enum class HandSlot(val pageName: String) {
    MAINTENANCE("maintenance"),
    CLASS("class"),
    WILDCARD("wildcard");

    companion object {
        /** The slot at [index], or null outside 0 to 2 (the page then filters by no attribute). */
        fun at(index: Int): HandSlot? = entries.getOrNull(index)

        /** Cards a day holds. */
        const val COUNT = 3
    }
}

/** Ids and texts the Daily Hand writes. */
object HandIds {
    /** Every card's quest id starts with this and the day: `dh_<date>_`. */
    fun dayPrefix(date: String): String = "dh_${date}_"

    /** `'dh_' + date + '_' + slot + '_' + nonce` (QST-40). */
    fun card(date: String, slot: Int, nonceText: String): String =
        "${dayPrefix(date)}${slot}_$nonceText"

    /** `date + '|' + luck + '|' + nonce + '|' + slot`, the text `dhDraw` seeds from (QST-39). */
    fun seedText(date: String, luckText: String, nonceText: String, slot: Int): String =
        "$date|$luckText|$nonceText|$slot"

    const val MESSAGE_FINISHED = "Finished cards cannot be rerolled"
    const val MESSAGE_NO_SPARKS = "Not enough Sparks"
    const val MESSAGE_FREE = "Free reroll used"

    fun messagePaid(cost: Int): String = "Rerolled for $cost Sparks"
}

/**
 * The one-time `dhMig` migration (QST-42): `/^(q[1-3]|cc_.*)$/` on the quest id, a daily, and not
 * nutrition or hydration. The pattern is spelled out rather than compiled, because a Java regex
 * differs from JavaScript's here: JavaScript's `.` matches U+0085 and `$` never matches before a
 * final line break.
 */
object HandMigration {
    /** The starter dailies the Hand replaced (`q4`..`q6` are not among them [QD01]). */
    val STARTER_DAILIES = setOf("q1", "q2", "q3")

    /** Class-pack quest ids start with this. */
    const val CLASS_PREFIX = "cc_"

    /** Kinds that stay even when daily. */
    val KEPT_KINDS = setOf("nutrition", "hydration")

    /** The characters JavaScript's `.` does not match. */
    private const val LINE_TERMINATORS = "\n\r\u2028\u2029"

    /** Whether the id text (`String(q.id)`) matches the page's pattern. */
    fun matchesPattern(id: String): Boolean =
        id in STARTER_DAILIES ||
            (id.startsWith(CLASS_PREFIX) &&
                id.substring(CLASS_PREFIX.length).none { it in LINE_TERMINATORS })
}
