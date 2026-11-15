package com.lifemax.rpg.feature.habits.presentation

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * The developer's clock for the Habits street, in memory only (every launch starts on the real
 * time; nothing is saved or sent to the page): the hour the street is drawn at instead of the
 * clock's, or null for the real time. Shown only while the page's developer mode is on
 * ([com.lifemax.rpg.feature.habits.domain.HabitsProjection.dev]).
 */
object HabitsDev {
    var previewHour: Float? by mutableStateOf(null)

    /** The chips: a name and the hour it sets (null: the real time). */
    val CHOICES: List<Pair<String, Float?>> =
        listOf("Real" to null, "Day" to 13f, "Dusk" to 18.6f, "Night" to 23f)
}
