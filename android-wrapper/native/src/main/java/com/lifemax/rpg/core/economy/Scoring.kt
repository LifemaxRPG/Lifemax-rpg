package com.lifemax.rpg.core.economy

import kotlin.math.max
import kotlin.math.min

/**
 * DUR-1: 30 minutes earn the base, 120 minutes and more three times it, under 5 minutes nothing.
 */
fun durationFactor(minutes: Double): Double {
    val d = minutes.orZero
    return when {
        d < Eco.MIN_MINUTES -> 0.0
        d <= 60 -> d / 30
        else -> min(3.0, 2 + (d - 60) / 60)
    }
}

/**
 * CAP-2: [xp] cut to what a day holding [dayTotal] still takes; above 180 the overflow is halved.
 */
fun cap(xp: Double, dayTotal: Double): Double {
    if (xp <= 0 || dayTotal >= Eco.HARD_CAP) return 0.0
    var out = xp
    if (dayTotal + xp > Eco.SOFT_CAP) {
        val over = dayTotal + xp - max(Eco.SOFT_CAP, dayTotal)
        out = xp - over + jsRound(over * 0.5)
    }
    return max(0.0, min(out, Eco.HARD_CAP - dayTotal))
}

/** SPLIT-1: 70% to the primary, the rest to a different secondary. */
fun splitXp(xp: Double, primary: String, secondary: String?): List<XpShare> {
    if (secondary.isNullOrEmpty() || secondary == primary) return listOf(XpShare(primary, xp))
    val first = jsRound(xp * 0.7)
    return listOf(XpShare(primary, first), XpShare(secondary, xp - first))
}

/** An award and the reasons it was reduced or refused, joined with `' · '` (AWARD-1). */
data class Award(val xp: Double, val message: String)

/** REST-1: [xp] with the rested bonus the pool would add. Writes nothing. */
fun EconomySave.restedPeek(xp: Double): Double {
    val pool = restedXp.orZero
    return if (pool > 0 && xp > 0) xp + min(pool, jsRound(xp * Eco.RESTED_BONUS)) else xp
}

/** REST-2: the same, spending the bonus from the pool. A missing or empty pool is left as it is. */
fun EconomySave.restedTake(xp: Double): Pair<Double, EconomySave> {
    val pool = restedXp.orZero
    if (pool <= 0 || xp <= 0) return xp to this
    val bonus = min(pool, jsRound(xp * Eco.RESTED_BONUS))
    return xp + bonus to copy(restedXp = pool - bonus)
}

/** STAR-1: [xp] raised 5% per ascension star of [attr], the factor formed first. */
fun EconomySave.starXp(attr: String, xp: Double): Double =
    jsRound(xp * (1 + 0.05 * starCount(attr)))

internal fun EconomySave.starCount(attr: String): Double = attrStars[attr].orNaN.orZero

/**
 * Applies stars to each share (STAR-1); [reasons] gains `Intelligence ★ +2` for every share they
 * raised (LOGAWARD-1 step 5). Stars come after the cap, so a day can end above 240 [D06].
 */
internal fun EconomySave.starred(
    shares: List<XpShare>,
    reasons: MutableList<String>? = null,
): List<XpShare> =
    shares.map { share ->
        val raised = starXp(share.attr, share.xp)
        if (raised > share.xp && reasons != null) {
            val stars = "★".repeat(starCount(share.attr).toInt())
            reasons += "${Attribute.shortName(share.attr)} $stars +${(raised - share.xp).text()}"
        }
        share.copy(xp = raised)
    }

/**
 * AWARD-1, the pipeline every scored award goes through: the per-type limit, the active-time limit
 * for [manual] logs, Scholar's Flow, rested XP and the daily cap. A [peek] returns what the commit
 * would pay and spends nothing (AWARD-4).
 */
fun EconomySave.award(
    type: String,
    total: Double,
    minutes: Double,
    ctx: EconomyContext,
    day: String? = null,
    manual: Boolean = false,
    peek: Boolean = false,
): Outcome<Award> {
    val onDay = day?.takeIf { it.isNotEmpty() } ?: ctx.today
    fun refuse(message: String) = result(Award(0.0, message))
    if (!(total > 0)) return refuse("Add at least 5 minutes to earn XP")
    val records = activities.onDay(onDay, ctx.clock)
    val factor =
        when (records.count { it.type == type }) {
            0,
            1 -> 1.0
            2 -> 0.5
            else -> return refuse("Daily XP limit reached for this activity type")
        }
    if (manual) {
        val minutesToday =
            records
                .filter { it.type != "quest" && it.source != Activity.HEALTH }
                .sumOf { it.duration.orZero }
        if (minutesToday + minutes.orZero > Eco.MAX_MANUAL_MINUTES)
            return refuse("Daily active-time limit (6 hours) reached")
    }
    val reasons = mutableListOf<String>()
    if (factor < 1) reasons += "Last of this type today: half XP"
    var xp = max(1.0, jsRound(total * factor))
    if ((type == "reading" || type == "language") && minutes.orZero >= 45 && hasPerk("scholar")) {
        val scholarly = jsRound(xp * 1.1)
        if (scholarly > xp) reasons += "Scholar’s Flow +${(scholarly - xp).text()}"
        xp = scholarly
    }
    val rested = restedPeek(xp)
    val dayTotal = records.dayXp(onDay, ctx.clock)
    val paid = cap(rested, dayTotal)
    if (rested > xp) reasons += "Rested +${(rested - xp).text()}"
    // D05: a paying award spends the whole bonus computed from x, even when the cap cut it.
    val after = if (paid > 0 && !peek) restedTake(xp).second else this
    if (paid > 0 && paid < rested)
        reasons +=
            if (dayTotal + paid < Eco.HARD_CAP) "XP above 180 a day is halved"
            else "Reaches the daily XP cap"
    return if (paid > 0) after.result(Award(paid, reasons.joinToString(" · ")))
    else after.result(Award(0.0, "Daily XP cap reached"))
}
