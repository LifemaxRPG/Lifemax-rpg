package com.lifemax.rpg.screenshots

import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.lifemax.rpg.feature.log.data.WebLogRepository
import com.lifemax.rpg.feature.log.presentation.LogActions
import com.lifemax.rpg.feature.log.presentation.LogScreen
import org.json.JSONObject
import org.junit.Rule
import org.junit.Test

/**
 * The native Log screen from projections the web app produced (50-native-log.js run against the
 * built page).
 */
class LogScreenshotTest {
    @get:Rule
    val paparazzi = Paparazzi(deviceConfig = DeviceConfig.PIXEL_5.copy(screenHeight = 5700))

    private fun snap(fixture: String, light: Boolean) {
        val p =
            WebLogRepository()
                .apply {
                    accept(
                        JSONObject(javaClass.getResource("/$fixture.json")!!.readText())
                            .put("light", light)
                            .put("tokens", ScreenshotPalette.tokens(light)),
                        true,
                    )
                }
                .state
                .value!!
        paparazzi.snapshot { LogScreen(p, p.draft, LogActions()) }
    }

    @Test fun logDefaultDark() = snap("log-default", false)

    @Test fun logStrengthDark() = snap("log-strength", false)

    @Test fun logStrengthLight() = snap("log-strength", true)
}
