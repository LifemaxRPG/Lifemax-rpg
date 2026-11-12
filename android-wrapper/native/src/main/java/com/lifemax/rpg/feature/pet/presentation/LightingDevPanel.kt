package com.lifemax.rpg.feature.pet.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Slider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

/**
 * DEVELOPER MODE ONLY (docs/specs/CAT_LIGHTING_PLAN.md WP-L3 and WP-L8): the switches of cat
 * lighting under the Cat tab's buttons, in memory, reset on every launch ([LiveLight]).
 * - "Cat lighting: off / on" turns the cats' light on (off by default: the room as it was).
 * - The hour slider previews the room at any time of day, in 15 minute steps; "Play" runs an hour a
 *   second; "Now" goes back to the clock.
 * - "Weather" cycles clear, the seeded weather of the day, overcast and rain.
 */
@Composable
internal fun LightingDevPanel(modifier: Modifier = Modifier) {
    val preview = LiveLight.previewHour
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PetSecondaryButton(
                "Cat lighting: ${if (LiveLight.enabled) "on" else "off"}",
                "Developer: turn the cats' light on or off",
                { LiveLight.enabled = !LiveLight.enabled },
                Modifier.weight(1f),
                small = true,
            )
            PetSecondaryButton(
                "Weather: ${LiveLight.weatherPick.label}",
                "Developer: cycle the weather outside",
                {
                    val all = WeatherPick.entries
                    LiveLight.weatherPick = all[(LiveLight.weatherPick.ordinal + 1) % all.size]
                },
                Modifier.weight(1f),
                small = true,
            )
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PetSecondaryButton(
                if (preview == null) "Hour: clock" else "Hour ${LiveLight.hourLabel(preview)}",
                "Developer: back to the clock's hour",
                {
                    LiveLight.playing = false
                    LiveLight.previewHour = null
                },
                Modifier.weight(1f),
                small = true,
            )
            PetSecondaryButton(
                if (LiveLight.playing) "Pause" else "Play day",
                "Developer: run the preview an hour a second",
                {
                    LiveLight.playing = !LiveLight.playing
                    if (LiveLight.previewHour == null) LiveLight.previewHour = 0.0
                },
                Modifier.weight(1f),
                small = true,
            )
        }
        Slider(
            value = (preview ?: 12.0).toFloat(),
            onValueChange = {
                LiveLight.previewHour = Math.round(it * STEPS_PER_HOUR) / STEPS_PER_HOUR.toDouble()
            },
            valueRange = 0f..HOURS,
            steps = (HOURS * STEPS_PER_HOUR).toInt() - 1,
            modifier = Modifier.fillMaxWidth(),
        )
        PetNote("Preview hour, 0 to 24 (developer only, not saved)", size = 11.sp)
    }
    LaunchedEffect(LiveLight.playing) {
        while (LiveLight.playing) {
            delay(TICK_MS)
            val h = (LiveLight.previewHour ?: 0.0) + TICK_MS / MS_PER_HOUR_PLAYED
            LiveLight.previewHour = if (h >= HOURS) h - HOURS else h
        }
    }
}

private const val HOURS = 24f
private const val STEPS_PER_HOUR = 4
private const val TICK_MS = 50L

/** Played, one hour of the day passes in this many ms. */
private const val MS_PER_HOUR_PLAYED = 1000.0
