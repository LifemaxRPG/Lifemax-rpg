package com.lifemax.rpg.screenshots

import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.lifemax.rpg.app.shell.swipe.SwipeColors
import com.lifemax.rpg.app.shell.swipe.SwipeTab
import com.lifemax.rpg.app.shell.swipe.SwipeVeilContent
import com.lifemax.rpg.app.shell.swipe.TabKind
import org.junit.Rule
import org.junit.Test

/**
 * The tab swipe's veil: what slides in for a native tab not opened yet (its icon and label on the
 * page's background), and the bare filler behind a wall, in both palettes and at the largest font.
 */
class SwipeVeilScreenshotTest {
    // Renders are exact; the default tolerance (0.1 %) would pass a lost label.
    @get:Rule
    val paparazzi = Paparazzi(deviceConfig = DeviceConfig.PIXEL_5, maxPercentDifference = 0.001)

    private val food = SwipeTab("food", "meals", "Food", "heart", TabKind.Native)
    private val plan = SwipeTab("plan", "plan", "Workout plan", "dumbbell", TabKind.Native)

    private fun colors(light: Boolean): SwipeColors {
        val tokens = ScreenshotPalette.tokenMap(light)
        fun argb(name: String) = tokens.getValue(name).toInt()
        return SwipeColors(argb("bg"), argb("text"), argb("muted"))
    }

    @Test fun veilDark() = paparazzi.snapshot { SwipeVeilContent(food, colors(false)) }

    @Test fun veilLight() = paparazzi.snapshot { SwipeVeilContent(food, colors(true)) }

    @Test
    fun veilLargeText() {
        paparazzi.unsafeUpdateConfig(DeviceConfig.PIXEL_5.copy(fontScale = 2f))
        paparazzi.snapshot { SwipeVeilContent(plan, colors(false)) }
    }

    @Test fun veilFiller() = paparazzi.snapshot { SwipeVeilContent(null, colors(false)) }
}
