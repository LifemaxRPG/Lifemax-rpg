package com.lifemax.rpg.core.lifedirector

import com.lifemax.rpg.core.lifedirector.PageValues.clamp
import com.lifemax.rpg.core.lifedirector.PageValues.number
import com.lifemax.rpg.core.lifedirector.PageValues.round
import com.lifemax.rpg.core.lifedirector.PageValues.truthy
import com.lifemax.rpg.core.save.JsonArray
import com.lifemax.rpg.core.save.JsonBool
import com.lifemax.rpg.core.save.JsonNumber
import com.lifemax.rpg.core.save.JsonObject
import com.lifemax.rpg.core.save.JsonString
import com.lifemax.rpg.core.save.JsonValue

/**
 * LDP-1, LDP-2: the player's planning preferences, every member the stored preferences hold over
 * the defaults. Only five numbers and two choices are checked ([PlanPreferences.of]); every other
 * member is kept exactly as stored, and the getters below read it as the engine does where it uses
 * it (LDS-7).
 */
class PlanPreferences private constructor(val members: Map<String, JsonValue>) {
    /** A member as stored (absent: null). */
    operator fun get(key: String): JsonValue? = members[key]

    /** LDP-2: the checked shortest sleep, in minutes (300 to 720). */
    val minSleepMinutes: Double
        get() = number(members[MIN_SLEEP])

    /** LDP-2: the checked number of plan days (1 to 28). */
    val planningHorizonDays: JsonValue?
        get() = members[HORIZON]

    /** Whether meals are placed (any truthy value). */
    val mealsEnabled: Boolean
        get() = truthy(members["mealsEnabled"])

    companion object {
        private const val MAX_GOAL = "maxDailyGoalMinutes"
        private const val MIN_RECOVERY = "minDailyRecoveryMinutes"
        private const val MAX_HIGH_LOAD = "maxConsecutiveHighLoadMinutes"
        private const val MIN_SLEEP = "minSleepMinutes"
        private const val HORIZON = "planningHorizonDays"
        private const val INTENSITY = "intensityLevel"
        private const val CONSEQUENCES = "consequenceMode"
        private const val HARDCORE = "HARDCORE"
        private const val HARDCORE_FLOOR = 240.0
        private const val HARDCORE_CEILING = 480.0
        private const val GOAL_CEILING = 300.0
        private const val DEFAULT_HORIZON = 7.0
        private const val FEWEST_DAYS = 1.0
        private const val MOST_DAYS = 28.0
        private val INTENSITIES = setOf("LIGHT", "BALANCED", "AGGRESSIVE", HARDCORE)
        private val MODES = setOf("LENIENT", "MODERATE", "HARDCORE_RPG")

        private fun n(value: Double) = JsonNumber(value)

        private fun numbers(vararg values: Double) = JsonArray(values.map(::n))

        /** LDP-1 `defaultPreferences`, in the page's order. */
        val DEFAULTS: Map<String, JsonValue> =
            linkedMapOf(
                INTENSITY to JsonString("BALANCED"),
                MAX_GOAL to n(180.0),
                MIN_RECOVERY to n(90.0),
                MAX_HIGH_LOAD to n(90.0),
                MIN_SLEEP to n(450.0),
                CONSEQUENCES to JsonString("LENIENT"),
                "preferredRestDays" to JsonArray.EMPTY,
                HORIZON to n(DEFAULT_HORIZON),
                "missGraceMinutes" to n(60.0),
                "maxHighIntensityPerDay" to n(3.0),
                "morningRoutineMinutes" to n(30.0),
                "windDownMinutes" to n(30.0),
                "mealMinutes" to n(30.0),
                "mealOffsetsMinutes" to numbers(60.0, 330.0, 630.0),
                "mealsEnabled" to JsonBool.TRUE,
                "defaultBedtimeMin" to n(1380.0),
                "avoidWindows" to JsonArray.EMPTY,
                "maxFreeMovesPerTask" to n(3.0),
                "leadMinutes" to n(15.0),
                "partialSatisfiedRatio" to n(0.75),
            )

        /** LDP-1: the defaults alone. */
        fun defaults(): PlanPreferences = PlanPreferences(DEFAULTS)

        /** The checked ranges of LDP-2: member to (low, high). */
        private val RANGES =
            listOf(
                MAX_GOAL to (30.0 to 480.0),
                MIN_RECOVERY to (30.0 to 360.0),
                MAX_HIGH_LOAD to (30.0 to 240.0),
                MIN_SLEEP to (300.0 to 720.0),
            )

        /**
         * LDP-2 `normalizePreferences`: the defaults, then every own member of [stored] over them
         * (text gives its characters by index and an array its items, as `Object.assign` copies
         * them; other values give nothing), then the checks: each of [RANGES] read as a number, 0
         * or NaN taking the default, clamped; the horizon rounded (7 for 0 or NaN) into 1..28; an
         * unknown intensity or consequence mode replaced by the default; and LDP-3, the ceiling on
         * daily goal minutes.
         */
        fun of(stored: JsonValue?): PlanPreferences {
            val o = LinkedHashMap<String, JsonValue>(DEFAULTS)
            if (truthy(stored)) o.putAll(ownMembers(stored!!))
            for ((key, range) in RANGES) {
                val value = number(o[key]).takeUnless { it == 0.0 || it.isNaN() }
                o[key] = n(clamp(value ?: number(DEFAULTS[key]), range.first, range.second))
            }
            val days = number(o[HORIZON]).takeUnless { it == 0.0 || it.isNaN() } ?: DEFAULT_HORIZON
            o[HORIZON] = n(clamp(round(days), FEWEST_DAYS, MOST_DAYS))
            if ((o[INTENSITY] as? JsonString)?.value !in INTENSITIES)
                o[INTENSITY] = DEFAULTS.getValue(INTENSITY)
            if ((o[CONSEQUENCES] as? JsonString)?.value !in MODES)
                o[CONSEQUENCES] = DEFAULTS.getValue(CONSEQUENCES)
            val hardcore = (o[INTENSITY] as JsonString).value == HARDCORE
            var goal = number(o[MAX_GOAL])
            if (hardcore) goal = minOf(HARDCORE_CEILING, maxOf(goal, HARDCORE_FLOOR))
            o[MAX_GOAL] = n(minOf(goal, if (hardcore) HARDCORE_CEILING else GOAL_CEILING))
            return PlanPreferences(o)
        }

        /** What `Object.assign` copies from [value]. */
        private fun ownMembers(value: JsonValue): Map<String, JsonValue> =
            when (value) {
                is JsonObject -> value.members
                is JsonArray -> value.items.withIndex().associate { (i, v) -> i.toString() to v }
                is JsonString ->
                    value.value.withIndex().associate { (i, c) ->
                        i.toString() to JsonString(c.toString())
                    }
                else -> emptyMap()
            }
    }
}
