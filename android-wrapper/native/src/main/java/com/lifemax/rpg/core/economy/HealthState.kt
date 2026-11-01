package com.lifemax.rpg.core.economy

/** The health sync's settings and memory in the save (`state.health`, section 11.3). */
data class HealthState(
    val on: Boolean = false,
    val exercise: Boolean = false,
    val steps: Boolean = false,
    val sleep: Boolean = false,
    val weight: Boolean = false,
    /** Health Connect record keys already handled, with the instant that may prune them. */
    val seen: Map<String, Double> = emptyMap(),
    val since: Double = Double.NaN,
    val last: Double = Double.NaN,
    val message: String = "",
    val sdk: String = "",
    val granted: List<String> = emptyList(),
    val source: String = "",
    /** Today's step count so far, stored only (HEALTH-4). */
    val today: HealthConnect.StepDay? = null,
    val weightTime: Double? = null,
    val weightHistory: List<WeightPoint>? = null,
)

/** A weight reading kept in the history (HEALTH-6). */
data class WeightPoint(val time: Double, val pounds: Double)
