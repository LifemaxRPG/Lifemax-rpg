package com.lifemax.rpg.core.quests

import com.lifemax.rpg.core.save.JsonObject
import com.lifemax.rpg.core.save.JsonValue

/**
 * What the Daily Hand draw reads outside the save, as plain inputs: the page computes each of them
 * from the device (its locale, its clock, other areas of the save), and so will the native app.
 */
data class HandContext(
    /** `getToday()`: the local day key, `toLocaleDateString('en-CA')` (YYYY-MM-DD). */
    val today: String,
    /**
     * `luckToday()?.score`: today's luck roll, or null when there is none. Null, 0 and NaN all make
     * the draw use [HandOdds.luckFallback] (QST-34). Any other value is used as it is, out of range
     * or fractional.
     */
    val luckScore: Double?,
    /** `ecoRestDay(day)` (REST-DAY-1): the draw asks it for the day it draws for. */
    val isRestDay: (String) -> Boolean,
    /**
     * `activityDate(a)`: the local day key of a record's `date` value (`null` here is a record
     * without one). An unreadable date is `"Invalid Date"`, which sorts after every real day key.
     */
    val dayOf: (JsonValue?) -> String,
)

/**
 * What a Daily Hand call did: the save after it, and either its value or the point where the page
 * would have thrown. A throw keeps every change made before it, as the page's state does.
 */
sealed interface HandStep<out T> {
    /** The whole save after the call (only the Hand's fields can differ from the input). */
    val save: JsonObject

    /**
     * The call finished. [toasts] are the messages it showed, in order; [saveRequests] how many
     * times it asked the page's `save()` (whose work, auto-verify included, is the economy's).
     */
    data class Done<T>(
        override val save: JsonObject,
        val value: T,
        val toasts: List<String> = emptyList(),
        val saveRequests: Int = 0,
    ) : HandStep<T>

    /** The page would have thrown here; [reason] says what, in the page's own words. */
    data class Threw(override val save: JsonObject, val reason: String) : HandStep<Nothing>
}
