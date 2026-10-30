package com.lifemax.rpg.screenshots

import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.android.resources.Density
import com.lifemax.rpg.feature.recipes.data.WebRecipesRepository
import com.lifemax.rpg.feature.recipes.presentation.IngredientRef
import com.lifemax.rpg.feature.recipes.presentation.RecipesActions
import com.lifemax.rpg.feature.recipes.presentation.RecipesScreen
import com.lifemax.rpg.feature.recipes.presentation.RecipesViewModel
import org.json.JSONObject
import org.junit.Rule
import org.junit.Test

/**
 * Recipes from projections the web app produced (tools/gen_native_fixtures.js), on a 411 dp wide
 * phone like the owner's, each image a part of the screen near full resolution. The screen's state
 * comes from its own view model: a search typed, a recipe opened, an ingredient tapped.
 */
class RecipesScreenshotTest {
    @get:Rule val paparazzi = Paparazzi(deviceConfig = phone())

    private fun phone(fontScale: Float = 1f) =
        DeviceConfig.PIXEL_5.copy(
            screenHeight = 1620,
            density = Density(420),
            fontScale = fontScale,
        )

    /** [fixture] after [use], scrolled to the list's [item] and [scrolledDp] further. */
    private fun snap(
        fixture: String,
        light: Boolean = false,
        item: Int = 0,
        scrolledDp: Int = 0,
        use: RecipesViewModel.() -> Unit = {},
    ) {
        val repository =
            WebRecipesRepository().apply {
                accept(
                    JSONObject(
                            javaClass.getResource("/fixtures/recipes-$fixture.json")!!.readText()
                        )
                        .put("light", light)
                        .put("tokens", ScreenshotPalette.tokens(light)),
                    true,
                )
            }
        val vm = RecipesViewModel(repository).apply { onProjection() }.apply(use)
        paparazzi.snapshot {
            RecipesScreen(
                repository.state.value!!,
                vm.ui.value,
                RecipesActions(),
                list =
                    rememberLazyListState(
                        item,
                        with(LocalDensity.current) { scrolledDp.dp.roundToPx() },
                    ),
            )
        }
    }

    @Test fun defaultDark() = snap("scholar")

    @Test fun defaultLight() = snap("scholar", light = true)

    /** The Entrepreneur's two Snack recipes, which only a search or "All recipes" shows. */
    @Test fun searchDark() = snap("entrepreneur", item = FILTER) { search("snack") }

    /** Quick Pesto Chicken open, its chicken breast selected: the Walmart link. */
    @Test
    fun expandedDark() =
        snap("scholar", item = FIRST_RECIPE + 8) {
            toggle("rcp_pesto_chicken")
            select(IngredientRef("rcp_pesto_chicken", 0))
        }

    /** The egg muffins open, an ingredient without a link selected, down to the note. */
    @Test
    fun expandedLight() =
        snap("scholar", light = true, item = FIRST_RECIPE, scrolledDp = 500) {
            toggle("rcp_egg_muffins")
            select(IngredientRef("rcp_egg_muffins", 7))
        }

    @Test fun emptyDark() = snap("scholar") { search("zzz") }

    /** The diet card asked for a recipe: the screen took the page's search over. */
    @Test fun prefillDark() = snap("prefill", item = FILTER)

    /** A Disciple has no class recipes: the cookbook alone, none estimated, to its last. */
    @Test fun discipleDark() = snap("disciple", item = FIRST_RECIPE + 9)

    @Test
    fun largeText() {
        paparazzi.unsafeUpdateConfig(phone(fontScale = 2f))
        snap("scholar", item = FILTER)
    }

    private companion object {
        /** The list's items: the switch, the heading, the filter card, then the recipes. */
        const val FILTER = 2
        const val FIRST_RECIPE = 3
    }
}
