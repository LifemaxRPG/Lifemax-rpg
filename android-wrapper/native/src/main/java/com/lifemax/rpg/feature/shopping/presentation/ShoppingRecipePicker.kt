package com.lifemax.rpg.feature.shopping.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Recipes from the recipe book that can be added to this week's list. A picked recipe adds its
 * ingredient lines, scaled by the servings wanted, and joins identical ingredients. Closed by
 * default so the list stays the first thing seen.
 */
@Composable
internal fun ShoppingRecipePicker(state: ShoppingState, colors: ShopColors) {
    if (state.recipes.isEmpty()) return
    var open by remember { mutableStateOf(false) }
    val pickedCount = state.recipes.count { state.pickedServings(it.id) != null }
    ShopCard(colors) {
        ShopButton(
            text =
                if (open) "Hide recipes"
                else "Add recipes to this list" + if (pickedCount > 0) " ($pickedCount)" else "",
            colors = colors,
            modifier = Modifier.fillMaxWidth(),
        ) {
            open = !open
        }
        if (open) {
            state.recipes.forEach { recipe ->
                val servings = state.pickedServings(recipe.id)
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        recipe.name,
                        color = colors.fg,
                        fontSize = 14.sp,
                        fontWeight = if (servings != null) FontWeight.Bold else FontWeight.Medium,
                        modifier = Modifier.weight(1f).padding(end = 8.dp),
                    )
                    if (servings == null) {
                        ShopButton("Add", colors, description = "Add ${recipe.name} to the list") {
                            state.pickRecipe(recipe.id, recipe.servings.toDouble())
                        }
                    } else {
                        ShopStepper(servings.toInt(), colors, "${recipe.name} servings", 0, 100) {
                            state.pickRecipe(recipe.id, it.toDouble())
                        }
                    }
                }
            }
        }
    }
}
