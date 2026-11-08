package com.lifemax.rpg.core.adapters

import com.lifemax.rpg.core.economy.EconomyClock
import com.lifemax.rpg.core.save.JsonArray
import com.lifemax.rpg.core.save.JsonBool
import com.lifemax.rpg.core.save.JsonNull
import com.lifemax.rpg.core.save.JsonNumber
import com.lifemax.rpg.core.save.JsonObject
import com.lifemax.rpg.core.save.JsonString
import com.lifemax.rpg.core.save.JsonValue

/**
 * The day keys the page derives from a stored value (`activityDate(a)` is `new
 * Date(a.date).toLocaleDateString('en-CA')`), for a value read straight from the save.
 */
object PageDays {
    /**
     * The local day key of a record's `date` value. A missing value is `new Date(undefined)`, an
     * invalid date; `null` is the epoch; a number is an instant in milliseconds; a text is parsed
     * as the economy does (TIME-4). Anything else (a flag, a list, an object) is not a date this
     * area writes and reads as invalid, where the page would parse its text: no stored record has
     * such a date.
     */
    fun dayOf(date: JsonValue?, clock: EconomyClock): String =
        when (date) {
            null -> EconomyClock.INVALID_DAY
            JsonNull -> clock.localDate(0)
            is JsonString -> clock.dayOf(date.value)
            is JsonNumber ->
                if (date.value.isFinite()) clock.localDate(date.value.toLong())
                else EconomyClock.INVALID_DAY
            is JsonBool,
            is JsonArray,
            is JsonObject -> EconomyClock.INVALID_DAY
        }
}
