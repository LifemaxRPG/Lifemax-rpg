package com.lifemax.rpg.screenshots

import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.android.resources.Density
import com.lifemax.rpg.feature.overview.data.WebOverviewRepository
import com.lifemax.rpg.feature.overview.presentation.OverviewActions
import com.lifemax.rpg.feature.overview.presentation.OverviewArrivalRun
import com.lifemax.rpg.feature.overview.presentation.OverviewScreen
import org.json.JSONObject
import org.junit.Rule
import org.junit.Test

/**
 * The page's Overview arrival on the native cards (plan §4 phase 3), stopped at its start, its
 * middle and its end: the rows rising in turn, today's ring sweeping in, the count-up, the week's
 * bars growing, the XP and gem bars filling, and the glance's dial arriving session by session, on
 * the established save with today's dial, at the owner's 411 dp.
 */
class OverviewArrivalScreenshotTest {
    @get:Rule
    val paparazzi =
        Paparazzi(
            deviceConfig = DeviceConfig.PIXEL_5.copy(screenHeight = TALL, density = Density(420)),
            useDeviceResolution = true,
            maxPercentDifference = 0.001,
        )

    private fun fixture(name: String) =
        JSONObject(javaClass.getResource("/fixtures/$name.json")!!.readText())

    private fun frame(ms: Long) {
        val j =
            fixture("overview-cards-established")
                .put("light", false)
                .put("tokens", ScreenshotPalette.tokens(false))
        j.getJSONObject("tiles").put("plan", fixture("overview-glance-day"))
        val cards = WebOverviewRepository().apply { accept(j, true) }.state.value!!
        paparazzi.snapshot {
            OverviewScreen(cards, OverviewActions(), arrival = OverviewArrivalRun.at(ms))
        }
    }

    @Test fun arrivalStart() = frame(0)

    /** The upper rows nearly in place and the lower still rising, the ring part-way round. */
    @Test fun arrivalMiddle() = frame(200)

    @Test fun arrivalEnd() = frame(2_000)

    private companion object {
        /** Tall enough for every tile of the established save and the glance at 420 dpi. */
        const val TALL = 9000
    }
}
