package com.lifemax.rpg.screenshots

import androidx.compose.runtime.CompositionLocalProvider
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.android.resources.Density
import com.lifemax.rpg.feature.lifedirector.data.WebLifeDirectorRepository
import com.lifemax.rpg.feature.lifedirector.domain.LifeDirectorProjection
import com.lifemax.rpg.feature.lifedirector.domain.OpenSheet
import com.lifemax.rpg.feature.lifedirector.presentation.DirectorActions
import com.lifemax.rpg.feature.lifedirector.presentation.DirectorUi
import com.lifemax.rpg.feature.lifedirector.presentation.LifeDirectorScreen
import com.lifemax.rpg.feature.lifedirector.presentation.components.LocalWallClock
import org.json.JSONObject
import org.junit.Rule
import org.junit.Test

/**
 * Today's timer and meal sheets over the native Today, from the page's own projections
 * (tools/gen_native_fixtures.js), on the 411 dp wide phone of LifeDirectorScreenshotTest made tall
 * enough for each sheet to show whole. The wall clock stands at the projection's own time, so the
 * running timer reads the 12:33 the page would show then, and the paused one the 12:34 it stopped
 * at.
 */
class LifeDirectorTimerMealScreenshotTest {
    @get:Rule
    val paparazzi =
        Paparazzi(
            deviceConfig = DeviceConfig.PIXEL_5.copy(screenHeight = 1900, density = Density(420)),
            useDeviceResolution = true,
            maxPercentDifference = 0.001,
        )

    private fun projection(fixture: String): LifeDirectorProjection =
        WebLifeDirectorRepository()
            .apply {
                accept(
                    JSONObject(
                            javaClass.getResource("/fixtures/director-$fixture.json")!!.readText()
                        )
                        .put("light", false)
                        .put("tokens", ScreenshotPalette.tokens(false)),
                    true,
                )
            }
            .state
            .value!!

    private fun snap(fixture: String, open: OpenSheet) {
        val p = projection(fixture)
        paparazzi.snapshot {
            CompositionLocalProvider(LocalWallClock provides { (p.today?.now ?: 0) * 1000 }) {
                LifeDirectorScreen(p, DirectorUi(sheet = open), DirectorActions(), animate = false)
            }
        }
    }

    /** The running timer, opened from the dial's centre: green ring, Pause. */
    @Test fun timerRunning() = snap("sheet-timer", OpenSheet("timer", ""))

    /** The same timer paused: the ring in amber without its glow, Resume. */
    @Test fun timerPaused() = snap("sheet-timer-paused", OpenSheet("timer", ""))

    /** Dinner inside the dial: recipe, scheduled time, calories, state and Open meal menu. */
    @Test fun meal() = snap("sheet-meal", OpenSheet("meal", "1793210400"))
}
