package com.lifemax.rpg.screenshots

import androidx.compose.runtime.CompositionLocalProvider
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.android.resources.Density
import com.lifemax.rpg.feature.lifedirector.data.WebLifeDirectorRepository
import com.lifemax.rpg.feature.lifedirector.domain.LifeDirectorProjection
import com.lifemax.rpg.feature.lifedirector.presentation.DirectorActions
import com.lifemax.rpg.feature.lifedirector.presentation.DirectorUi
import com.lifemax.rpg.feature.lifedirector.presentation.GuideRun
import com.lifemax.rpg.feature.lifedirector.presentation.LifeDirectorScreen
import com.lifemax.rpg.feature.lifedirector.presentation.components.LocalWallClock
import org.json.JSONObject
import org.junit.Rule
import org.junit.Test

/**
 * Today's guide drawn natively (phase 4A), from the projection the page gives a screen that draws
 * it (director-guide-today.json): the screen dimmed around the part each step explains, the dial in
 * the step's mode (layers dimmed, the page's name tags pinned on, the handles at rest as the pulse
 * starts), and the card in the page's words. A whole phone screen (411 by 891 dp), so the dial and
 * the card both show without scrolling.
 */
class LifeDirectorGuideScreenshotTest {
    @get:Rule
    val paparazzi =
        Paparazzi(
            deviceConfig = DeviceConfig.PIXEL_5.copy(screenHeight = 2340, density = Density(420)),
            useDeviceResolution = true,
            maxPercentDifference = 0.001,
        )

    private fun projection(): LifeDirectorProjection =
        WebLifeDirectorRepository()
            .apply {
                accept(
                    JSONObject(
                            javaClass
                                .getResource("/fixtures/director-guide-today.json")!!
                                .readText()
                        )
                        .put("light", false)
                        .put("tokens", ScreenshotPalette.tokens(false)),
                    true,
                )
            }
            .state
            .value!!

    private fun snap(step: Int, name: String) {
        val p = projection()
        paparazzi.snapshot(name) {
            CompositionLocalProvider(LocalWallClock provides { (p.today?.now ?: 0) * 1000 }) {
                LifeDirectorScreen(
                    p,
                    DirectorUi(guide = GuideRun("today", step)),
                    DirectorActions(),
                    animate = false,
                )
            }
        }
    }

    /** "This is your day as a clock": the round spotlight, Now and Already passed pinned on. */
    @Test fun clock() = snap(0, "clock")

    /** The outer ring: the inner ring, the middle and the handles dim; Travel, Routine, Blocked. */
    @Test fun outer() = snap(1, "outer")

    /** The inner ring: the outer ring, the words, the middle and the handles dim; Meal, Session. */
    @Test fun inner() = snap(2, "inner")

    /** The handles: the rings and the middle dim, each handle named. */
    @Test fun handles() = snap(3, "handles")

    /** The middle: lit through a round spotlight, the rings and handles dimmed. */
    @Test fun centre() = snap(4, "centre")

    /** Up next, with the page's bold Start and Done; the dial back as it is. */
    @Test fun upNext() = snap(5, "upNext")
}
