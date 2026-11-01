package com.lifemax.rpg.screenshots

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.android.ide.common.rendering.api.SessionParams
import com.android.resources.Density
import com.lifemax.rpg.feature.lifedirector.data.WebLifeDirectorRepository
import com.lifemax.rpg.feature.lifedirector.presentation.components.DirectorPalette
import com.lifemax.rpg.feature.lifedirector.presentation.time.MiniDial
import com.lifemax.rpg.feature.lifedirector.presentation.time.MiniDialWords
import com.lifemax.rpg.feature.lifedirector.presentation.time.MiniSleepDial
import com.lifemax.rpg.feature.lifedirector.presentation.time.MinuteSpan
import org.json.JSONObject
import org.junit.Rule
import org.junit.Test

/**
 * My time's mini sleep dial (4B) on a 411 dp phone: the main dial's face from the lived-in day's
 * projection, a weekday shift faint behind, and two sleep windows, one across midnight. The words
 * per stop are the page's formats (fHm in en-US, mins) written out here until the page projects
 * time.sleep.
 */
class MiniSleepDialScreenshotTest {
    @get:Rule
    val paparazzi =
        Paparazzi(
            deviceConfig = DeviceConfig.PIXEL_5.copy(density = Density(420)),
            renderingMode = SessionParams.RenderingMode.SHRINK,
            useDeviceResolution = true,
            maxPercentDifference = 0.001,
        )

    private val dial =
        WebLifeDirectorRepository()
            .apply {
                accept(
                    JSONObject(javaClass.getResource("/fixtures/director-today.json")!!.readText())
                        .put("light", false)
                        .put("tokens", ScreenshotPalette.tokens(false)),
                    true,
                )
            }
            .state
            .value!!
            .today!!
            .dial

    private fun model(bed: Int, wake: Int) =
        MiniDial(
            bed = bed,
            wake = wake,
            bedText = CLOCK[bed / 15],
            wakeText = CLOCK[wake / 15],
            length = LENGTHS[((wake - bed).mod(1440)) / 15],
            step = 15,
            context = listOf(MinuteSpan(540, 1020)),
            clock = CLOCK,
            lengths = LENGTHS,
            words = MiniDialWords("Sleep", "Bedtime", "Wake up", "Sleep window"),
        )

    private fun snap(name: String, bed: Int, wake: Int) =
        paparazzi.snapshot(name) {
            Box(Modifier.width(411.dp).background(DirectorPalette.sheetTop).padding(16.dp)) {
                MiniSleepDial(model(bed, wake), bed, wake, dial.radii, dial.hourWords, { _, _ -> })
            }
        }

    /** 11:00 PM to 7:00 AM: the arc crosses midnight. */
    @Test fun acrossMidnight() = snap("mini-across", 1380, 420)

    /** 1:30 AM to 9:45 AM, after the night shift that ends at 1 AM would allow. */
    @Test fun afterMidnight() = snap("mini-after", 90, 585)
}

/** fHm for every quarter hour, en-US, as the page writes it. */
private val CLOCK =
    (0 until 96).map { k ->
        val h = k / 4
        "${if (h % 12 == 0) 12 else h % 12}:${"%02d".format(k % 4 * 15)} ${if (h < 12) "AM" else "PM"}"
    }

/** The page's mins for every quarter-hour length, the first entry the whole day. */
private val LENGTHS =
    (0 until 96).map { k ->
        val m = if (k == 0) 1440 else k * 15
        if (m >= 60) "${m / 60}h" + (if (m % 60 != 0) " ${m % 60}m" else "") else "${m}m"
    }
