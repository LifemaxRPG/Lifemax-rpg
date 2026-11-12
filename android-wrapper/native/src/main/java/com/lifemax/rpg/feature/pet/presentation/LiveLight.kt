package com.lifemax.rpg.feature.pet.presentation

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.lifemax.rpg.feature.pet.art.CatLightKey
import com.lifemax.rpg.feature.pet.light.Weather
import java.lang.ref.WeakReference
import java.util.TimeZone

/**
 * The developer's switches for cat lighting (docs/specs/CAT_LIGHTING_PLAN.md WP-L3, WP-L8), for the
 * whole app and in memory only: nothing is saved, logged or sent to the page, and every launch
 * starts with lighting OFF, the clock's own hour and clear weather. They are shown only in the
 * page's developer mode, under the Cat tab's buttons ([LightingDevPanel]).
 *
 * With [enabled] off (the default) every cat is drawn in [CatLightKey.NEUTRAL]: the very pixels
 * drawn before lighting existed. On, lighting also needs the performance guard at its best depth
 * level ([active]): a phone that cannot keep up sheds the cats' light first, with the depth.
 */
internal object LiveLight {
    /** The developer switch: cats take the room's light. Off at every launch. */
    var enabled by mutableStateOf(false)

    /** The hour the room is drawn at instead of the clock's (the preview slider), or null. */
    var previewHour: Double? by mutableStateOf(null)

    /** Whether the preview hour runs on by itself (an hour a second). */
    var playing by mutableStateOf(false)

    /** The weather the developer picked; [WeatherPick.CLEAR] by default. */
    var weatherPick by mutableStateOf(WeatherPick.CLEAR)

    /** The weather outside now: the pick, or the seeded weather of the local day. */
    fun weather(): Weather =
        when (weatherPick) {
            WeatherPick.CLEAR -> Weather.CLEAR
            WeatherPick.OVERCAST -> Weather.OVERCAST
            WeatherPick.RAIN -> Weather.RAIN
            WeatherPick.SEEDED -> Weather.seeded(localDay())
        }

    /** Whether cats take light now: the switch, and the guard at its best level. */
    fun active(): Boolean = enabled && LiveDepth.atBest

    @Volatile private var latest: WeakReference<CatLighting>? = null

    /** The stage drawing now reports its lighting here, for [readout]. */
    fun report(lighting: CatLighting) {
        if (latest?.get() !== lighting) latest = WeakReference(lighting)
    }

    /** One line for the developer readout. */
    fun readout(): String {
        val hour = previewHour?.let { "preview ${hourLabel(it)}" } ?: "clock"
        val stage = latest?.get()?.readout() ?: "no room"
        val bake = LitSheets.lastBakeNanos / NANOS_PER_US / US_PER_MS
        return "Light ${if (enabled) "on" else "off"} (${weatherPick.label}, $hour); $stage; " +
            "rows made ${LitSheets.bakes}, last ${"%.2f".format(bake)} ms"
    }

    /** "6:45" for 6.75. */
    fun hourLabel(hour: Double): String {
        val minutes = Math.round(hour * MINUTES_PER_HOUR).toInt()
        return "${minutes / MINUTES_PER_HOUR}:${"%02d".format(minutes % MINUTES_PER_HOUR)}"
    }

    private fun localDay(): Long {
        val now = System.currentTimeMillis()
        return Math.floorDiv(now + TimeZone.getDefault().getOffset(now), MILLIS_PER_DAY)
    }

    private const val NANOS_PER_US = 1000.0
    private const val US_PER_MS = 1000.0
    private const val MINUTES_PER_HOUR = 60
    private const val MILLIS_PER_DAY = 86_400_000L
}

/** The weather picker's choices. */
internal enum class WeatherPick(val label: String) {
    CLEAR("clear"),
    SEEDED("weather of the day"),
    OVERCAST("overcast"),
    RAIN("rain"),
}
