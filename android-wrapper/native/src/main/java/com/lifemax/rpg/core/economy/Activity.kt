package com.lifemax.rpg.core.economy

import kotlin.math.max

/** One attribute's part of a record's XP. */
data class XpShare(val attr: String, val xp: Double)

/**
 * One record of the activity ledger (section 2.2). Optional fields are null when a record does not
 * carry them; numbers are doubles because a save can hold anything (NUM-1, NUM-3).
 */
data class Activity(
    val id: String,
    val type: String,
    val name: String? = null,
    val icon: String? = null,
    val duration: Double = 0.0,
    val quality: Double? = null,
    val skill: String? = null,
    val primary: String? = null,
    val secondary: String? = null,
    val xp: Double = 0.0,
    val date: String? = null,
    val notes: String? = null,
    val split: List<XpShare> = emptyList(),
    val source: String? = null,
    val sparked: Boolean = false,
    val epic: Boolean? = null,
    val cadence: String? = null,
    val opId: String? = null,
    val ldEventId: String? = null,
    val ldBonus: Double? = null,
    val healthKey: String? = null,
    val actId: String? = null,
    val link: String? = null,
    val result: String? = null,
    val kind: String? = null,
    val planDate: String? = null,
) {
    /** PASSIVE-1: steps, sleep and coach records pay XP but are not training. */
    val isPassive: Boolean
        get() =
            type == "steps" ||
                type == "coach" ||
                type == "sleep" ||
                (source == HEALTH && (skill == "Walking" || skill == "Sleep"))

    /** DERIVE-1: the record's split, or its whole XP on the primary when it has none. */
    val shares: List<XpShare>
        get() = split.ifEmpty { listOf(XpShare(primary.orEmpty(), xp.orZero)) }

    companion object {
        const val HEALTH = "health"
    }
}

/** The ledger in insertion order, and the questions every rule asks of it. */
fun List<Activity>.onDay(day: String, clock: EconomyClock): List<Activity> = filter {
    clock.dayOf(it.date) == day
}

/**
 * CAP-1: the XP a day already holds; epic quests do not count, negative values are not clamped
 * [D33].
 */
fun List<Activity>.dayXp(day: String, clock: EconomyClock): Double =
    onDay(day, clock).sumOf { if (it.epic == true) 0.0 else it.xp.orZero }

/** DERIVE-1: attribute XP rebuilt from the ledger, less decay and ascension, in attribute order. */
fun List<Activity>.attributeXp(
    decayTotal: Map<String, Double>,
    ascended: Map<String, Double>,
): Map<String, Double> {
    val sums = Attribute.ids.associateWithTo(linkedMapOf()) { 0.0 }
    for (activity in this) {
        for (share in activity.shares) {
            sums.computeIfPresent(share.attr) { _, sum -> sum + max(0.0, share.xp.orZero) }
        }
    }
    return sums.mapValues { (id, sum) ->
        max(0.0, sum - max(0.0, decayTotal[id].orNaN.orZero) - max(0.0, ascended[id].orNaN.orZero))
    }
}

/**
 * DERIVE-2: skill XP rebuilt from the ledger, keyed by the trimmed skill, in order of first
 * appearance.
 */
fun List<Activity>.skillXp(): Map<String, Double> {
    val sums = linkedMapOf<String, Double>()
    for (activity in this) {
        val skill = activity.skill.orEmpty().trim()
        if (skill.isNotEmpty()) sums[skill] = (sums[skill] ?: 0.0) + max(0.0, activity.xp.orZero)
    }
    return sums
}

internal val Double?.orNaN: Double
    get() = this ?: Double.NaN
