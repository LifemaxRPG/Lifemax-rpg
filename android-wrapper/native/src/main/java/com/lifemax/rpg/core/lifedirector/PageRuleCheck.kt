package com.lifemax.rpg.core.lifedirector

import com.lifemax.rpg.core.economy.jsNumber
import com.lifemax.rpg.core.lifedirector.PageValues.member
import com.lifemax.rpg.core.lifedirector.PageValues.number
import com.lifemax.rpg.core.lifedirector.PageValues.text
import com.lifemax.rpg.core.lifedirector.PageValues.truthy
import com.lifemax.rpg.core.save.JsonArray
import com.lifemax.rpg.core.save.JsonNumber
import com.lifemax.rpg.core.save.JsonString
import com.lifemax.rpg.core.save.JsonValue

/**
 * LDA-9: the page's own answer to "does this stored rule apply on this date" (`logic.js` `ruleOn`),
 * which the dial handles and the Money tab's shifts use instead of the engine's [AvailabilityRule]
 * (plan defect D3). It reads the stored rule as it is, without [AvailabilityRule.of], and so
 * differs from the engine where the stored rule is unusual: a `dayOfWeek` without `days`, a cycle
 * length that is fractional or text, an anchor that is not text ([LD06]).
 */
object PageRuleCheck {
    private const val DAYS_IN_WEEK = 7

    /** Whether the stored rule [rule] applies on [date], by the page's copy of the question. */
    fun appliesOn(rule: JsonValue?, date: CalendarDate): Boolean {
        if (!PageValues.present(rule)) throw PageTypeError("Cannot read properties of $rule")
        val key = JsonString(CalendarMath.key(date))
        val from = member(rule, "validFrom")
        val to = member(rule, "validTo")
        if (truthy(from) && PageValues.less(key, from)) return false
        if (truthy(to) && PageValues.less(to, key)) return false
        val rotation = member(rule, "rotation")
        val cycle = member(rotation, "cycleLength")
        if (truthy(rotation) && truthy(member(rotation, "anchorDate")) && number(cycle) > 0) {
            val pieces = text(member(rotation, "anchorDate")).split('-')
            fun piece(i: Int) = pieces.getOrNull(i)?.let(::jsNumber) ?: Double.NaN
            val n = CalendarMath.daysBetween(DateFields(piece(0), piece(1), piece(2)), date.fields)
            val c = number(cycle)
            val index = number(PageValues.plus(n % c, cycle)) % c
            val active =
                when (val a = member(rotation, "activeDays")) {
                    is JsonArray -> a.items.map(::number)
                    else ->
                        if (truthy(a)) throw PageTypeError("activeDays.map is not a function")
                        else emptyList()
                }
            return PageValues.includes(active, index)
        }
        val days = member(rule, "days") as? JsonArray ?: return date.weekday in 0 until DAYS_IN_WEEK
        return days.items.any { it is JsonNumber && it.value == date.weekday.toDouble() }
    }
}
