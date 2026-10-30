package com.lifemax.rpg.screenshots

import androidx.activity.OnBackPressedDispatcher
import androidx.activity.OnBackPressedDispatcherOwner
import androidx.compose.runtime.CompositionLocalProvider
import androidx.lifecycle.compose.LocalLifecycleOwner
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
            val lifecycle = LocalLifecycleOwner.current.lifecycle
            val owner =
                object : OnBackPressedDispatcherOwner {
                    override val lifecycle = lifecycle
                    override val onBackPressedDispatcher = OnBackPressedDispatcher()
                }
            CompositionLocalProvider(
                androidx.activity.compose.LocalOnBackPressedDispatcherOwner provides owner
            ) {
                NativeNutritionScreen(
                    data =
                        JSONObject(javaClass.getResource("/food-$pane.json")!!.readText())
                            .put("light", light),
                    topInsetDp = 64f,
                    bottomInsetDp = 72f,
                    active = true,
                    dispatch = { _, _ -> },
                )
            }
        }

    @Test fun foodLogDark() = snap("log", false)

    @Test fun foodPlanDark() = snap("plan", false)

    @Test fun foodShopDark() = snap("shop", false)

    @Test fun foodPlanLight() = snap("plan", true)
}
