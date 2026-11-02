package com.lifemax.rpg.screenshots

import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.lifemax.rpg.feature.food.presentation.NativeNutritionScreen
import org.json.JSONObject
import org.junit.Rule
import org.junit.Test

/**
 * The Smart Diet swap sheet over Food's Plan pane, and Recipes' "Log this recipe" as the add-food
 * sheet's portion view, from projections the web app itself produced
 * (tools/gen_food_swap_fixtures.js).
 */
class FoodSwapScreenshotTest {
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
                    JSONObject(javaClass.getResource("/$name.json")!!.readText())
                        .put("light", light),
                topInsetDp = 64f,
                bottomInsetDp = 72f,
                dispatch = { _, _ -> },
            )
        }

    @Test fun swapOpenDark() = snap("food-swap-open")

    @Test fun swapOpenLight() = snap("food-swap-open", light = true)

    @Test fun swapWithWayBackDark() = snap("food-swap-reset")

    @Test fun swapNothingToOfferDark() = snap("food-swap-none")

    @Test fun swapMessageWithUndoDark() = snap("food-swap-notice")

    @Test fun recipePortionDark() = snap("food-recipe-seed")

    @Test fun recipePortionLight() = snap("food-recipe-seed", light = true)
}

/** The swap sheet at a larger system font, as the other Food sheets are checked. */
class FoodSwapLargeTextScreenshotTest {
    @get:Rule
    val paparazzi =
        Paparazzi(
            deviceConfig = DeviceConfig.PIXEL_5.copy(screenHeight = 2400, fontScale = 1.3f),
            maxPercentDifference = 0.001,
        )

    @Test
    fun swapLargeText() =
        paparazzi.snapshot {
            NativeNutritionScreen(
                data = JSONObject(javaClass.getResource("/food-swap-open.json")!!.readText()),
                topInsetDp = 64f,
                bottomInsetDp = 72f,
                dispatch = { _, _ -> },
            )
        }
}
