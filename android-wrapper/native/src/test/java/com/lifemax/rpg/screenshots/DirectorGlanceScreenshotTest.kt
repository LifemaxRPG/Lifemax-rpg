package com.lifemax.rpg.screenshots

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.android.resources.Density
import com.lifemax.rpg.feature.lifedirector.data.DirectorGlanceJson
import com.lifemax.rpg.feature.lifedirector.domain.DirectorGlance
import com.lifemax.rpg.feature.lifedirector.presentation.components.LocalWallClock
import com.lifemax.rpg.feature.lifedirector.presentation.dial.DialIntroRun
import com.lifemax.rpg.feature.lifedirector.presentation.glance.DirectorGlance
import org.junit.Rule
import org.junit.Test

/**
 * Life Director's glance as the native Overview draws it, from the page's own
 * LifeDirector.native.glance() on the lived-in day of tests/fixtures/director-day.js
 * (fixtures/director-glance-*.json), on a 411 dp wide phone like the owner's, inset 12 dp as the
 * page's Overview insets its tiles. Each image is at full resolution; the glance stays Life
 * Director's dark panel on the light Overview too. The running timer's clock stands at 12 min 34 s
 * in. The arrival (DialIntro) is stopped at its start, in the middle and at its end.
 */
class DirectorGlanceScreenshotTest {
    @get:Rule
    val paparazzi =
        Paparazzi(
            deviceConfig = DeviceConfig.PIXEL_5.copy(screenHeight = 1280, density = Density(420)),
            useDeviceResolution = true,
            maxPercentDifference = 0.001,
        )

    private fun glance(name: String): DirectorGlance =
        DirectorGlanceJson.decode(
            javaClass.getResource("/fixtures/director-glance-$name.json")!!.readText()
        )

    private fun snap(name: String, light: Boolean = false, introMs: Long? = null) {
        val g = glance(name)
        val now = g.centre?.timer?.let { it.startedAt + 754_000 } ?: 0L
        paparazzi.snapshot {
            CompositionLocalProvider(LocalWallClock provides { now }) {
                Box(
                    Modifier.fillMaxSize()
                        .background(Color(if (light) 0xFFEEF1F6 else 0xFF0C0E12))
                        .padding(horizontal = 12.dp, vertical = 12.dp)
                ) {
                    DirectorGlance(
                        g,
                        visible = false,
                        onTap = {},
                        intro = introMs?.let { DialIntroRun(mutableLongStateOf(it)) },
                    )
                }
            }
        }
    }

    /** In the middle of work, the commute next; the foot with one session done. */
    @Test fun day() = snap("day")

    /** The day on the light Overview: the glance keeps its own dark panel. */
    @Test fun dayLight() = snap("day", light = true)

    /** The evening session starting in two minutes, with Start early. */
    @Test fun sessionNext() = snap("next")

    /** A session's timer running, its clock in the middle. */
    @Test fun timerRunning() = snap("timer")

    /** The same timer paused: the clock stands where it was paused. */
    @Test fun timerPaused() = snap("paused")

    /** Nothing planned: Life Director not set up yet, its words and button. */
    @Test fun nothingPlanned() = snap("setup")

    /** Every session done or past, late in the evening. */
    @Test fun evening() = snap("evening")

    /** The arrival at its start: the face and the middle, the rings not yet in. */
    @Test fun introStart() = snap("day", introMs = 0)

    /** The arrival half way: the outer ring in, the words and sessions coming one after another. */
    @Test fun introMiddle() = snap("day", introMs = 380)

    /** The arrival at its end: as the glance stands. */
    @Test fun introEnd() = snap("day", introMs = 1_000)
}
