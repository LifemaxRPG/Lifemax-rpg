package com.lifemax.rpg.feature.nutrition.dev

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.tooling.preview.Preview
import com.lifemax.rpg.feature.nutrition.data.DeterministicNutritionFixtures
import com.lifemax.rpg.feature.nutrition.data.MockNutritionRepository
import com.lifemax.rpg.feature.nutrition.domain.model.MealsSubPane
import com.lifemax.rpg.feature.nutrition.domain.model.NutritionThemeMode
import com.lifemax.rpg.feature.nutrition.domain.model.NutritionTopTab
import com.lifemax.rpg.feature.nutrition.presentation.NutritionDestinationScreen

/**
 * Standalone development/preview host Activity (`NutritionPreviewActivity`) for inspecting the
 * native Nutrition & Recipes feature in isolation without modifying `MainActivity`.
 */
class NutritionPreviewActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val mockRepo = remember { MockNutritionRepository() }
            NutritionDestinationScreen(repository = mockRepo)
        }
    }
}

@Preview(
    name = "1. Meals Log Pane - Dark (Default)",
    showBackground = true,
    widthDp = 412,
    heightDp = 915,
)
@Composable
fun PreviewNutritionLogDark() {
    val repo = remember {
        MockNutritionRepository(
            DeterministicNutritionFixtures.createPopulatedProjection(
                themeMode = NutritionThemeMode.DARK,
                activeTopTab = NutritionTopTab.MEALS,
                activeMealsPane = MealsSubPane.LOG,
            )
        )
    }
    NutritionDestinationScreen(repository = repo)
}

@Preview(
    name = "2. Smart Diet Plan Pane - Dark",
    showBackground = true,
    widthDp = 412,
    heightDp = 915,
)
@Composable
fun PreviewSmartDietPlanDark() {
    val repo = remember {
        MockNutritionRepository(
            DeterministicNutritionFixtures.createPopulatedProjection(
                themeMode = NutritionThemeMode.DARK,
                activeTopTab = NutritionTopTab.MEALS,
                activeMealsPane = MealsSubPane.PLAN,
            )
        )
    }
    NutritionDestinationScreen(repository = repo)
}

@Preview(
    name = "3. Smart Diet Shopping Pane - Dark",
    showBackground = true,
    widthDp = 412,
    heightDp = 915,
)
@Composable
fun PreviewSmartDietShoppingDark() {
    val repo = remember {
        MockNutritionRepository(
            DeterministicNutritionFixtures.createPopulatedProjection(
                themeMode = NutritionThemeMode.DARK,
                activeTopTab = NutritionTopTab.MEALS,
                activeMealsPane = MealsSubPane.SHOPPING,
            )
        )
    }
    NutritionDestinationScreen(repository = repo)
}

@Preview(name = "4. Recipes View - Dark", showBackground = true, widthDp = 412, heightDp = 915)
@Composable
fun PreviewRecipesDark() {
    val repo = remember {
        MockNutritionRepository(
            DeterministicNutritionFixtures.createPopulatedProjection(
                themeMode = NutritionThemeMode.DARK,
                activeTopTab = NutritionTopTab.RECIPES,
            )
        )
    }
    NutritionDestinationScreen(repository = repo)
}

@Preview(
    name = "5. Meals Log Pane - Light Theme",
    showBackground = true,
    widthDp = 412,
    heightDp = 915,
)
@Composable
fun PreviewNutritionLogLight() {
    val repo = remember {
        MockNutritionRepository(
            DeterministicNutritionFixtures.createPopulatedProjection(
                themeMode = NutritionThemeMode.LIGHT,
                activeTopTab = NutritionTopTab.MEALS,
                activeMealsPane = MealsSubPane.LOG,
            )
        )
    }
    NutritionDestinationScreen(repository = repo)
}
