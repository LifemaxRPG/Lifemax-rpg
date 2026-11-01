package com.lifemax.rpg.screenshots

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.android.resources.Density
import com.lifemax.rpg.feature.lifedirector.data.WebLifeDirectorRepository
import com.lifemax.rpg.feature.lifedirector.data.directorReply
import com.lifemax.rpg.feature.lifedirector.presentation.DirectorActions
import com.lifemax.rpg.feature.lifedirector.presentation.DirectorCelebration
import com.lifemax.rpg.feature.lifedirector.presentation.DirectorCelebrationLayer
import com.lifemax.rpg.feature.lifedirector.presentation.DirectorUi
import com.lifemax.rpg.feature.lifedirector.presentation.LifeDirectorScreen
import com.lifemax.rpg.feature.lifedirector.presentation.components.LocalWallClock
import org.json.JSONObject
import org.junit.Rule
import org.junit.Test

/**
 * Life Director's Today with the XP celebration of a session marked done from the screen (phase 4A,
 * director.celebrations.v1), frozen at moments of its run: the page's own reply
 * (director-replies-celebrate.json) over the page's own Today, the burst where the finger came
 * down, the orbs on their way to the top bar's logo (the projection's celebrationTarget), and the
 * reduced-motion burst. On the same 411 dp phone as the other Life Director images.
 */
class LifeDirectorCelebrationScreenshotTest {
    @get:Rule
    val paparazzi =
        Paparazzi(
            deviceConfig = DeviceConfig.PIXEL_5.copy(screenHeight = 1080, density = Density(420)),
            useDeviceResolution = true,
            maxPercentDifference = 0.001,
        )

    private val projection =
        WebLifeDirectorRepository()
            .apply {
                accept(
                    JSONObject(javaClass.getResource("/fixtures/director-today.json")!!.readText())
                        .put("tokens", ScreenshotPalette.tokens(false)),
                    true,
                )
            }
            .state
            .value!!

    private val awards =
        directorReply(
                JSONObject(
                        javaClass
                            .getResource("/fixtures/director-replies-celebrate.json")!!
                            .readText()
                    )
                    .getJSONObject("done")
            )
            .celebrations

    private fun frame(seconds: Float, reduced: Boolean = false) =
        paparazzi.snapshot {
            CompositionLocalProvider(
                LocalWallClock provides { (projection.today?.now ?: 0) * 1000 }
            ) {
                Box(Modifier.fillMaxSize()) {
                    LifeDirectorScreen(projection, DirectorUi(), DirectorActions(), animate = false)
                    DirectorCelebrationLayer(
                        DirectorCelebration(4, awards),
                        Offset(820f, 900f), // where the finger came down
                        projection.celebrationTarget,
                        reduced,
                        onFinished = {},
                        startAt = seconds,
                    )
                }
            }
        }

    @Test fun burst() = frame(0.25f)

    @Test fun flight() = frame(0.6f)

    @Test fun reducedMotion() = frame(0.4f, reduced = true)
}
