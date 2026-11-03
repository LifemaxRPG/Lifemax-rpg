package com.lifemax.rpg.screenshots

import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.lifemax.rpg.feature.food.presentation.NativeNutritionScreen
import com.lifemax.rpg.feature.shopping.ShoppingFixtures
import org.json.JSONObject
import org.junit.Rule
import org.junit.Test

/**
 * The Walmart grocery list on the Food tab's Shopping pane, from the week's rows the web app itself
 * produced (the Food shop fixture), with a few matches chosen: the list with its summary, the
 * unmatched items drawn with their attention border, the Add All button switched off when nothing
 * is matched, and large text. Dark and light.
 */
class ShoppingScreenshotTest {
    @get:Rule
    val paparazzi =
        Paparazzi(
            deviceConfig = DeviceConfig.PIXEL_5.copy(screenHeight = 4200),
            maxPercentDifference = 0.001,
        )

    private fun base(): JSONObject =
        JSONObject(javaClass.getResource("/food-shop.json")!!.readText())

    /**
     * The page's shopping projection built over the fixture's real rows; [matched] get products.
     */
    private fun shopping(matched: Int, recipes: Boolean = false): JSONObject {
        val shop = base().getJSONObject("shop").getJSONArray("items")
        val rows = (0 until minOf(shop.length(), 9)).map { shop.getJSONObject(it) }
        val items =
            rows.map {
                ShoppingFixtures.item(
                    id = it.getString("id"),
                    name = it.getString("name"),
                    aisle = it.getString("aisleName"),
                    grams = it.getDouble("grams"),
                    packGrams = 500.0,
                    unit = it.getString("unit"),
                    home = it.getBoolean("home"),
                )
            }
        val prefs =
            items
                .filter { !it.getBoolean("home") }
                .take(matched)
                .mapIndexed { i, it ->
                    ShoppingFixtures.pref(
                        "sd:" + it.getString("id"),
                        (145051970L + i * 20000000L).toString(),
                        if (i == 0) "Great Value" else "",
                        if (i == 0) "42 oz" else "",
                    )
                }
        val book =
            if (recipes) {
                listOf(
                    ShoppingFixtures.recipe("r1", "Overnight oats", 1, "1/2 cup rolled oats (40g)"),
                    ShoppingFixtures.recipe("r2", "Chicken and rice bowl", 2, "1 lb chicken breast"),
                )
            } else {
                emptyList()
            }
        return ShoppingFixtures.projection(items = items, prefs = prefs, recipes = book)
    }

    private fun snap(shopping: JSONObject, light: Boolean) =
        paparazzi.snapshot {
            NativeNutritionScreen(
                data = base().put("light", light).put("shopping", shopping),
                topInsetDp = 64f,
                bottomInsetDp = 72f,
                dispatch = { _, _ -> },
            )
        }

    @Test fun shoppingListDark() = snap(shopping(matched = 4), light = false)

    @Test fun shoppingListLight() = snap(shopping(matched = 4), light = true)

    @Test fun shoppingUnmatchedHighlightLight() = snap(shopping(matched = 1), light = true)

    @Test
    fun shoppingNothingMatchedDark() = snap(shopping(matched = 0, recipes = true), light = false)
}

/** The same list at 1.6 times the text size. */
class ShoppingLargeTextScreenshotTest {
    @get:Rule
    val paparazzi =
        Paparazzi(
            deviceConfig = DeviceConfig.PIXEL_5.copy(screenHeight = 6500, fontScale = 1.6f),
            maxPercentDifference = 0.001,
        )

    @Test
    fun shoppingLargeTextDark() {
        val data = JSONObject(javaClass.getResource("/food-shop.json")!!.readText())
        val shop = data.getJSONObject("shop").getJSONArray("items")
        val items =
            (0 until 5).map {
                val row = shop.getJSONObject(it)
                ShoppingFixtures.item(
                    id = row.getString("id"),
                    name = row.getString("name"),
                    aisle = row.getString("aisleName"),
                    grams = row.getDouble("grams"),
                    unit = row.getString("unit"),
                    home = row.getBoolean("home"),
                )
            }
        val prefs =
            listOf(
                ShoppingFixtures.pref("sd:" + items[1].getString("id"), "165233284", "Great Value")
            )
        val shopping = ShoppingFixtures.projection(items = items, prefs = prefs)
        paparazzi.snapshot {
            NativeNutritionScreen(
                data = data.put("light", false).put("shopping", shopping),
                topInsetDp = 64f,
                bottomInsetDp = 72f,
                dispatch = { _, _ -> },
            )
        }
    }
}
