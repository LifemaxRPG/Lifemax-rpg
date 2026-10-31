package com.lifemax.rpg.screenshots

import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.lifemax.rpg.app.shell.swipe.SwipeColors
import com.lifemax.rpg.app.shell.swipe.SwipeState
import com.lifemax.rpg.app.shell.swipe.SwipeVeilContent
import org.json.JSONObject
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

    /** The bar as the page sends it (tools/gen_native_fixtures.js): its labels and icon names. */
    private val bar = run {
        val page = JSONObject(javaClass.getResource("/fixtures/swipe-state.json")!!.readText())
        SwipeState.parse(page.getJSONObject("character"), 1)!!.tabs
    }
    private val food = bar.single { it.key == "food" }
    private val plan = bar.single { it.key == "plan" }

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
