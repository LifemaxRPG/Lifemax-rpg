package com.lifemax.rpg.screenshots

import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.lifemax.rpg.feature.food.presentation.NativeNutritionScreen
import org.json.JSONObject
import org.junit.Rule
import org.junit.Test

/**
 * The three Food panes as the phone draws them, from projections the web app itself produced
 * (tools/gen_native_fixtures.js).
 */
class FoodScreenshotTest {
    @get:Rule
    val paparazzi = Paparazzi(deviceConfig = DeviceConfig.PIXEL_5.copy(screenHeight = 3000))

    private fun snap(pane: String, light: Boolean) =
        paparazzi.snapshot {
            NativeNutritionScreen(
                data =
                    JSONObject(javaClass.getResource("/food-$pane.json")!!.readText())
                        .put("light", light),
                topInsetDp = 64f,
                bottomInsetDp = 72f,
                dispatch = { _, _ -> },
            )
        }

    @Test fun foodLogDark() = snap("log", false)

    @Test fun foodPlanDark() = snap("plan", false)

    @Test fun foodShopDark() = snap("shop", false)

    @Test fun foodPlanLight() = snap("plan", true)
}
