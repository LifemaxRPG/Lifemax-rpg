package com.lifemax.rpg.screenshots

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.android.resources.Density
import com.lifemax.rpg.feature.lifedirector.data.WebLifeDirectorRepository
import com.lifemax.rpg.feature.lifedirector.domain.LifeDirectorProjection
import com.lifemax.rpg.feature.lifedirector.presentation.DirectorActions
import com.lifemax.rpg.feature.lifedirector.presentation.DirectorUi
import com.lifemax.rpg.feature.lifedirector.presentation.LifeDirectorScreen
import com.lifemax.rpg.feature.lifedirector.presentation.SleepDraft
import com.lifemax.rpg.feature.lifedirector.presentation.TimeDrafts
import org.json.JSONObject
import org.junit.Rule
import org.junit.Test

/**
 * My time and the first-run card (phase 4B), from the page's own projections
 * (tools/gen_director_time_fixtures.js), on the same 411 dp phone as LifeDirectorScreenshotTest:
 * each image one screenful, the screen scrolled down a screen at a time.
 */
class LifeDirectorTimeScreenshotTest {
    @get:Rule
    val paparazzi =
        Paparazzi(
            deviceConfig = DeviceConfig.PIXEL_5.copy(screenHeight = 1080, density = Density(420)),
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

    private fun snap(p: LifeDirectorProjection, ui: DirectorUi, prefix: String, screens: Int) {
        for (k in 0 until screens) paparazzi.snapshot("$prefix$k") {
            Scrolled(SCREEN * k) { LifeDirectorScreen(p, ui, DirectorActions(), animate = false) }
        }
    }

    /**
     * During set-up: the steps, the sleep card with the weekend's own times, a night shift behind,
     * Build my week.
     */
    @Test fun firstTime() = snap(projection("time-first"), DirectorUi(), "w", 5)

    /** Once set up: no steps, one weekday commitment, three goals, Re-plan now. */
    @Test fun setUp() = snap(projection("time"), DirectorUi(), "w", 5)

    /**
     * The sleep handles moved before Save sleep (bedtime 12:30 AM, wake 8:15 AM) and the weekend
     * switched on: the dial, its centre and the fields follow in the page's words.
     */
    @Test
    fun sleepDraft() =
        snap(
            projection("time"),
            DirectorUi(
                timeDrafts =
                    TimeDrafts(SleepDraft(30, 495, true, 60, 540), mapOf("windDownMinutes" to 45))
            ),
            "w",
            3,
        )

    /** Today for a new player: the first-run card with its demo dial. */
    @Test fun firstRun() = snap(projection("firstrun"), DirectorUi(), "w", 2)

    private companion object {
        val SCREEN = 400.dp
    }
}

/** [content] laid out on a tall screen, seen through this one from [top]. */
@Composable
private fun Scrolled(top: Dp, content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize().clipToBounds()) {
        Box(
            Modifier.wrapContentHeight(Alignment.Top, unbounded = true)
                .requiredHeight(3600.dp)
                .offset(y = -top)
        ) {
            content()
        }
    }
}
