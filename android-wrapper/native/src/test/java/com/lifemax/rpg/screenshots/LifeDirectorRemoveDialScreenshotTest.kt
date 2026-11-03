package com.lifemax.rpg.screenshots

import androidx.compose.runtime.CompositionLocalProvider
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.android.resources.Density
import com.lifemax.rpg.feature.lifedirector.DirectorRecorder
import com.lifemax.rpg.feature.lifedirector.data.WebLifeDirectorRepository
import com.lifemax.rpg.feature.lifedirector.domain.DragPoint
import com.lifemax.rpg.feature.lifedirector.domain.DragSource
import com.lifemax.rpg.feature.lifedirector.presentation.DirectorActions
import com.lifemax.rpg.feature.lifedirector.presentation.DirectorUi
import com.lifemax.rpg.feature.lifedirector.presentation.LifeDirectorScreen
import com.lifemax.rpg.feature.lifedirector.presentation.components.LocalWallClock
import com.lifemax.rpg.feature.lifedirector.presentation.drag.DragRemoveController
import kotlinx.coroutines.flow.MutableStateFlow
import org.json.JSONObject
import org.junit.Rule
import org.junit.Test

/**
 * A session lifted off the day from its segment on the dial (director.remove.v1), from the
 * projection the web app produced for a screen that offers removal (director-remove.json):
 * Resistance Training's segment left as a ghost where it was, its card under the finger, and the
 * trash idle and armed, in the dark and light themes and with larger text. Reduced-motion looks
 * (nothing slides, grows or springs), as in the running order's images.
 */
class LifeDirectorRemoveDialScreenshotTest {
    @get:Rule
    val paparazzi =
        Paparazzi(deviceConfig = phone(), useDeviceResolution = true, maxPercentDifference = 0.001)

    private fun phone(fontScale: Float = 1f) =
        DeviceConfig.PIXEL_5.copy(
            screenHeight = 3600,
            density = Density(420),
            fontScale = fontScale,
        )

    private fun repository(light: Boolean) =
        WebLifeDirectorRepository().apply {
            accept(
                JSONObject(javaClass.getResource("/fixtures/director-remove.json")!!.readText())
                    .put("light", light)
                    .put("tokens", ScreenshotPalette.tokens(light)),
                true,
            )
        }

    /** Resistance Training lifted from the dial under a finger at 540 px across and [y] down. */
    private fun lifted(
        name: String,
        light: Boolean = false,
        armed: Boolean = false,
        fontScale: Float = 1f,
        y: Float = 1500f,
    ) {
        paparazzi.unsafeUpdateConfig(phone(fontScale))
        val repo = repository(light)
        val p = repo.state.value!!
        val drag =
            DragRemoveController(
                DirectorRecorder(),
                repo.state,
                MutableStateFlow(DirectorUi()),
                { true },
                {},
            )
        drag.layout(1080f, 3600f, 64f * DENSITY, 72f * DENSITY, DENSITY)
        val row = p.today!!.ahead.first { it.title == "Resistance Training" }
        drag.lift(row, DragPoint(540f, y), drag.dialGrab(), DragSource.DIAL)
        if (armed) drag.move(drag.target.value!!.centre)
        paparazzi.snapshot(name) {
            CompositionLocalProvider(LocalWallClock provides { p.today!!.now * 1000 }) {
                LifeDirectorScreen(
                    p,
                    DirectorUi(),
                    DirectorActions(),
                    animate = false,
                    removal = drag,
                )
            }
        }
    }

    @Test fun dialLiftedDark() = lifted("dark")

    @Test fun dialLiftedLight() = lifted("light", light = true)

    @Test fun dialArmedDark() = lifted("dark", armed = true, y = 3250f)

    @Test fun dialArmedLight() = lifted("light", light = true, armed = true, y = 3250f)

    /** Larger text: the card, the trash and its label grow with it; the dial keeps its size. */
    @Test fun dialLargeText() = lifted("large", armed = true, fontScale = 1.3f, y = 3150f)

    private companion object {
        const val DENSITY = 2.625f
    }
}
