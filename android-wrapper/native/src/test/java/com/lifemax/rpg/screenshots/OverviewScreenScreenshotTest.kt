package com.lifemax.rpg.screenshots

import androidx.compose.foundation.lazy.rememberLazyListState
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.android.resources.Density
import com.lifemax.rpg.feature.overview.data.WebOverviewRepository
import com.lifemax.rpg.feature.overview.presentation.OverviewActions
import com.lifemax.rpg.feature.overview.presentation.OverviewScreen
import org.json.JSONObject
import org.junit.Rule
import org.junit.Test

/**
 * The whole native Overview, every tile in the page's order, from the page's own projections, on a
 * phone as wide as the owner's (411 dp) and tall enough to hold the whole list, so the image lies
 * beside the page rendered at 412 CSS px: the established save and the extras (a hidden tile,
 * reordered, as tests/overview-classic.test.js sets it up), dark and light; then a cat with its
 * portrait and the glance's day dial, and a celebration the page handed over, mid-flight.
 */
class OverviewScreenScreenshotTest {
    @get:Rule
    val paparazzi =
        Paparazzi(
            deviceConfig = DeviceConfig.PIXEL_5.copy(screenHeight = TALL, density = Density(420)),
            useDeviceResolution = true,
            maxPercentDifference = 0.001,
        )

    private fun fixture(name: String) =
        JSONObject(javaClass.getResource("/fixtures/$name.json")!!.readText())

    private fun screen(
        name: String,
        light: Boolean = false,
        tile: String = "",
        celebrationAt: Float = 0f,
        edit: (JSONObject) -> Unit = {},
    ) {
        val j =
            fixture("overview-cards-$name")
                .put("light", light)
                .put("tokens", ScreenshotPalette.tokens(light))
        edit(j)
        val cards = WebOverviewRepository().apply { accept(j, true) }.state.value!!
        val index =
            if (tile.isEmpty()) 0
            else 1 + (if (cards.tip != null) 1 else 0) + cards.shown.indexOf(tile)
        paparazzi.snapshot {
            OverviewScreen(
                cards,
                OverviewActions(),
                list = rememberLazyListState(index),
                celebrationAt = celebrationAt,
            )
        }
    }

    @Test fun establishedDark() = screen("established")

    @Test fun establishedLight() = screen("established", light = true)

    @Test fun extrasDark() = screen("extras")

    @Test fun extrasLight() = screen("extras", light = true)

    /** A cat with its portrait, health on with apps, and today's dial in the glance. */
    @Test
    fun petAndDayGlance() =
        screen("pet") { j ->
            val tiles = j.getJSONObject("tiles")
            tiles.put("plan", fixture("overview-glance-day"))
            val health = fixture("overview-cards-health")
            tiles.put("health", health.getJSONObject("tiles").getJSONObject("health"))
            tiles.put("apps", health.getJSONObject("tiles").getJSONObject("apps"))
            for (k in health.getJSONObject("images").keys()) j.getJSONObject("images")
                .put(k, health.getJSONObject("images").getString(k))
        }

    /** The page's celebration (fxSink) flying from the last tap to the logo, over the cards. */
    @Test fun celebrationFlight() = screen("celebration", celebrationAt = 0.6f)

    private companion object {
        /** Tall enough for every tile of the established save at 420 dpi. */
        const val TALL = 9000
    }
}
