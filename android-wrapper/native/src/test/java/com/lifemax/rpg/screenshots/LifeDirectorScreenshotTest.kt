package com.lifemax.rpg.screenshots

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.android.resources.Density
import com.lifemax.rpg.feature.lifedirector.data.WebLifeDirectorRepository
import com.lifemax.rpg.feature.lifedirector.presentation.DirectorActions
import com.lifemax.rpg.feature.lifedirector.presentation.DirectorUi
import com.lifemax.rpg.feature.lifedirector.presentation.LifeDirectorScreen
import com.lifemax.rpg.feature.lifedirector.presentation.components.DirectorPalette
import com.lifemax.rpg.feature.lifedirector.presentation.dial.ChronoDial
import org.json.JSONObject
import org.junit.Rule
import org.junit.Test

/**
 * The native Life Director Today from projections the web app produced (LifeDirector.native over
 * the lived-in day of tests/fixtures/director-day.js), on a 411 dp wide phone like the owner's,
 * where the dial is about 360 dp as on the web. Whole screens are scaled down to fit the reference
 * images, so the dial also has one of its own at full size.
 */
class LifeDirectorScreenshotTest {
    @get:Rule val paparazzi = Paparazzi(deviceConfig = phone(screenHeight = 6000))

    private fun phone(screenHeight: Int) =
        DeviceConfig.PIXEL_5.copy(screenHeight = screenHeight, density = Density(420))

    private fun projection(fixture: String, light: Boolean) =
        WebLifeDirectorRepository()
            .apply {
                accept(
                    JSONObject(javaClass.getResource("/fixtures/$fixture.json")!!.readText())
                        .put("light", light)
                        .put("tokens", ScreenshotPalette.tokens(light)),
                    true,
                )
            }
            .state
            .value!!

    private fun snap(fixture: String, light: Boolean, ui: DirectorUi = DirectorUi()) {
        val p = projection(fixture, light)
        paparazzi.snapshot { LifeDirectorScreen(p, ui, DirectorActions(), animate = false) }
    }

    @Test fun todayDark() = snap("director-today", false)

    @Test fun tomorrowDark() = snap("director-tomorrow", false)

    /** With the earlier blocks open, on a taller screen so the whole day fits. */
    @Test
    fun todayLight() {
        paparazzi.unsafeUpdateConfig(phone(screenHeight = 6400))
        snap("director-today", true, DirectorUi(earlierOpen = true))
    }

    @Test
    fun todayDial() {
        paparazzi.unsafeUpdateConfig(phone(screenHeight = 1080))
        val today = projection("director-today", false).today!!
        paparazzi.snapshot {
            Box(Modifier.fillMaxSize().background(DirectorPalette.bg0)) {
                ChronoDial(today.dial, today.centre, animate = false, onArc = {}, onTap = {})
            }
        }
    }
}
