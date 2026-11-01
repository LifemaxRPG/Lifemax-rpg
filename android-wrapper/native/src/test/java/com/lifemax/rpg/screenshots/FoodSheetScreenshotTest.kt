package com.lifemax.rpg.screenshots

import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.lifemax.rpg.feature.food.presentation.NativeNutritionScreen
import org.json.JSONObject
import org.junit.Rule
import org.junit.Test

/**
 * The add-food sheet's views over Food, from projections the web app itself produced
 * (tools/gen_native_fixtures.js).
 */
class FoodSheetScreenshotTest {
    @get:Rule
    val paparazzi =
        Paparazzi(
            deviceConfig = DeviceConfig.PIXEL_5.copy(screenHeight = 2400),
            maxPercentDifference = 0.001,
        )

    private fun snap(name: String, light: Boolean = false) =
        paparazzi.snapshot {
            NativeNutritionScreen(
                data =
                    JSONObject(javaClass.getResource("/food-sheet-$name.json")!!.readText())
                        .put("light", light),
                topInsetDp = 64f,
                bottomInsetDp = 72f,
                dispatch = { _, _ -> },
            )
        }

    @Test fun sheetSearchDark() = snap("search")

    @Test fun sheetResultsDark() = snap("results")

    @Test fun sheetPortionDark() = snap("portion")

    @Test fun sheetQuickDark() = snap("quick")

    @Test fun sheetNoticeDark() = snap("notice")

    @Test fun sheetResultsLight() = snap("results", light = true)

    @Test fun sheetPortionLight() = snap("portion", light = true)
}
