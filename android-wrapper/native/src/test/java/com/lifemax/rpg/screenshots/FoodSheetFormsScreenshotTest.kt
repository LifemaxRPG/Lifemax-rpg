package com.lifemax.rpg.screenshots

import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.lifemax.rpg.feature.food.presentation.NativeNutritionScreen
import org.json.JSONObject
import org.junit.Rule
import org.junit.Test

/**
 * The add-food sheet's phase 2 views over Food (create food, the daily targets, an entry being
 * edited, the Undo snackbar after Remove), dark and light, from projections the web app itself
 * produced (tools/gen_native_fixtures.js).
 */
class FoodSheetFormsScreenshotTest {
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

    @Test fun createDark() = snap("create")

    @Test fun createLight() = snap("create", light = true)

    @Test fun goalDark() = snap("goal")

    @Test fun goalLight() = snap("goal", light = true)

    @Test fun editDark() = snap("edit")

    @Test fun editLight() = snap("edit", light = true)

    @Test fun undoSnackDark() = snap("answer")
}

/** The add-food sheet's views at large text (the system's font scale at 1.3). */
class FoodSheetLargeTextScreenshotTest {
    @get:Rule
    val paparazzi =
        Paparazzi(
            deviceConfig = DeviceConfig.PIXEL_5.copy(screenHeight = 2400, fontScale = 1.3f),
            maxPercentDifference = 0.001,
        )

    private fun snap(name: String) =
        paparazzi.snapshot {
            NativeNutritionScreen(
                data = JSONObject(javaClass.getResource("/food-sheet-$name.json")!!.readText()),
                topInsetDp = 64f,
                bottomInsetDp = 72f,
                dispatch = { _, _ -> },
            )
        }

    @Test fun searchLargeText() = snap("results")

    @Test fun portionLargeText() = snap("portion")

    @Test fun quickLargeText() = snap("quick")

    @Test fun createLargeText() = snap("create")

    @Test fun goalLargeText() = snap("goal")

    @Test fun editLargeText() = snap("edit")
}
