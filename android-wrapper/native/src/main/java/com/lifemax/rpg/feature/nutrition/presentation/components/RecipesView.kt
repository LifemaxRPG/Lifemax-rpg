package com.lifemax.rpg.feature.nutrition.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lifemax.rpg.core.designsystem.LocalLifeMaxNutritionColors
import com.lifemax.rpg.feature.nutrition.domain.model.RecipeItem
import com.lifemax.rpg.feature.nutrition.domain.model.RecipesProjection

/**
 * Pixel-accurate 1:1 Jetpack Compose reproduction of `#view-recipes`
 * (`lifemax_modular_v73/src/views/recipes.html` and `renderRecipes()` in `30-food.js`).
 *
 * Includes:
 * - Header card with `+ New` custom recipe toggle
 * - Expandable custom recipe creator card (`#newRecipeWrap`)
 * - Tag filter pills (`All`, `High Protein`, `Quick`, `Low Carb`, `Vegetarian`, `Budget`)
 * - Search filter input (`#recSearch`)
 * - Recipe cards with macro badges, ingredient list, instructions, `Log to Today` CTA, and custom
 *   delete
 */
@Composable
fun RecipesView(
    recipesProjection: RecipesProjection,
    searchQuery: String,
    selectedTag: String,
    isNewRecipeCreatorOpen: Boolean,
    onSearchQueryChange: (String) -> Unit,
    onSelectTag: (String) -> Unit,
    onToggleNewRecipeCreator: () -> Unit,
    onCreateCustomRecipe:
        (
            name: String,
            tags: List<String>,
            cal: Int,
            pro: Int,
            carb: Int,
            fat: Int,
            ingredients: List<String>,
            instructions: String,
        ) -> Unit,
    onLogRecipeToToday: (String) -> Unit,
    onDeleteCustomRecipe: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalLifeMaxNutritionColors.current

    val filteredRecipes =
        remember(recipesProjection.recipes, searchQuery, selectedTag) {
            recipesProjection.recipes.filter { rec ->
                val matchesTag =
                    selectedTag.equals("All", ignoreCase = true) ||
                        rec.tags.any { it.equals(selectedTag, ignoreCase = true) }
                val q = searchQuery.trim().lowercase()
                val matchesQuery =
                    q.isEmpty() ||
                        rec.name.lowercase().contains(q) ||
                        rec.ingredients.any { it.lowercase().contains(q) }
                matchesTag && matchesQuery
            }
        }

    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // Header + Search + Tags Card
        Column(
            modifier =
                Modifier.fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(colors.cardGradient)
                    .border(1.dp, colors.borderSubtle, RoundedCornerShape(20.dp))
                    .padding(15.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "🍲 RECIPES",
                    color = colors.textPrimary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Black,
                )

                Box(
                    modifier =
                        Modifier.clip(RoundedCornerShape(10.dp))
                            .background(colors.accent.copy(alpha = 0.16f))
                            .border(
                                1.dp,
                                colors.accent.copy(alpha = 0.4f),
                                RoundedCornerShape(10.dp),
                            )
                            .clickable(onClick = onToggleNewRecipeCreator)
                            .padding(horizontal = 11.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = if (isNewRecipeCreatorOpen) "Close" else "+ New",
                        color = colors.accent,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.ExtraBold,
                    )
                }
            }

            // Tag filter pills (#recFilterRow)
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                for (tag in recipesProjection.availableTags) {
                    val active = selectedTag.equals(tag, ignoreCase = true)
                    Box(
                        modifier =
                            Modifier.clip(CircleShape)
                                .background(
                                    if (active) colors.accent.copy(alpha = 0.18f)
                                    else colors.surfaceSunken
                                )
                                .border(
                                    1.dp,
                                    if (active) colors.accent.copy(alpha = 0.45f)
                                    else colors.borderSubtle,
                                    CircleShape,
                                )
                                .clickable { onSelectTag(tag) }
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = tag,
                            color = if (active) colors.accent else colors.textMuted,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.ExtraBold,
                        )
                    }
                }
            }

            // Search box (#recSearch)
            OutlinedTextField(
                value = searchQuery,
                onValueChange = onSearchQueryChange,
                placeholder = {
                    Text(
                        "Search recipes or ingredients...",
                        fontSize = 12.sp,
                        color = colors.textSubtle,
                    )
                },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        // Expandable New Recipe Creator (#newRecipeWrap)
        if (isNewRecipeCreatorOpen) {
            NewRecipeCreatorCard(
                onSave = { name, tags, cal, pro, carb, fat, ings, inst ->
                    onCreateCustomRecipe(name, tags, cal, pro, carb, fat, ings, inst)
                    onToggleNewRecipeCreator()
                }
            )
        }

        // Recipe List (#recList)
        for (recipe in filteredRecipes) {
            RecipeCardItem(
                recipe = recipe,
                onLogToToday = { onLogRecipeToToday(recipe.id) },
                onDelete = { onDeleteCustomRecipe(recipe.id) },
            )
        }
    }
}

@Composable
private fun NewRecipeCreatorCard(
    onSave: (String, List<String>, Int, Int, Int, Int, List<String>, String) -> Unit
) {
    val colors = LocalLifeMaxNutritionColors.current
    var name by remember { mutableStateOf("") }
    var tagsText by remember { mutableStateOf("High Protein, Quick") }
    var calText by remember { mutableStateOf("480") }
    var proText by remember { mutableStateOf("42") }
    var carbText by remember { mutableStateOf("45") }
    var fatText by remember { mutableStateOf("12") }
    var ingsText by remember { mutableStateOf("150g chicken breast\n150g cooked rice") }
    var instText by remember { mutableStateOf("Pan-sear chicken and serve over steamed rice.") }

    Column(
        modifier =
            Modifier.fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(colors.cardGradient)
                .border(1.dp, colors.accent.copy(alpha = 0.35f), RoundedCornerShape(20.dp))
                .padding(15.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            "NEW CUSTOM RECIPE",
            color = colors.accent,
            fontSize = 11.sp,
            fontWeight = FontWeight.ExtraBold,
        )
        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text("Recipe Name") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = tagsText,
            onValueChange = { tagsText = it },
            label = { Text("Tags (comma separated)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = calText,
                onValueChange = { calText = it },
                label = { Text("Calories") },
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
            OutlinedTextField(
                value = proText,
                onValueChange = { proText = it },
                label = { Text("Protein (g)") },
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = carbText,
                onValueChange = { carbText = it },
                label = { Text("Carbs (g)") },
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
            OutlinedTextField(
                value = fatText,
                onValueChange = { fatText = it },
                label = { Text("Fat (g)") },
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
        }
        OutlinedTextField(
            value = ingsText,
            onValueChange = { ingsText = it },
            label = { Text("Ingredients (one per line)") },
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = instText,
            onValueChange = { instText = it },
            label = { Text("Instructions") },
            modifier = Modifier.fillMaxWidth(),
        )
        Box(
            modifier =
                Modifier.fillMaxWidth()
                    .height(42.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(colors.primaryCtaGradient)
                    .border(1.dp, colors.accent.copy(alpha = 0.45f), RoundedCornerShape(12.dp))
                    .clickable {
                        if (name.isNotBlank()) {
                            onSave(
                                name.trim(),
                                tagsText.split(",").map { it.trim() }.filter { it.isNotEmpty() },
                                calText.toIntOrNull() ?: 0,
                                proText.toIntOrNull() ?: 0,
                                carbText.toIntOrNull() ?: 0,
                                fatText.toIntOrNull() ?: 0,
                                ingsText.lines().map { it.trim() }.filter { it.isNotEmpty() },
                                instText.trim(),
                            )
                        }
                    },
            contentAlignment = Alignment.Center,
        ) {
            Text(
                "Save Recipe",
                color = colors.textPrimary,
                fontSize = 13.sp,
                fontWeight = FontWeight.Black,
            )
        }
    }
}

@Composable
private fun RecipeCardItem(recipe: RecipeItem, onLogToToday: () -> Unit, onDelete: () -> Unit) {
    val colors = LocalLifeMaxNutritionColors.current

    Column(
        modifier =
            Modifier.fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(colors.cardGradient)
                .border(1.dp, colors.borderSubtle, RoundedCornerShape(18.dp))
                .padding(15.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = recipe.name,
                    color = colors.textPrimary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.ExtraBold,
                )
                Text(
                    text =
                        "${recipe.cal} kcal · P:${recipe.pro}g · C:${recipe.carb}g · F:${recipe.fat}g",
                    color = colors.accent,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                for (tag in recipe.tags.take(2)) {
                    Box(
                        modifier =
                            Modifier.clip(CircleShape)
                                .background(colors.surfaceSunken)
                                .padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        Text(
                            tag,
                            color = colors.textSubtle,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }
        }

        if (recipe.ingredients.isNotEmpty()) {
            Text(
                text = "Ingredients: ${recipe.ingredients.joinToString(", ")}",
                color = colors.textMuted,
                fontSize = 12.sp,
            )
        }

        if (recipe.instructions.isNotBlank()) {
            Text(text = recipe.instructions, color = colors.textSubtle, fontSize = 12.sp)
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(
                modifier =
                    Modifier.weight(1f)
                        .height(36.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(colors.accent.copy(alpha = 0.15f))
                        .border(1.dp, colors.accent.copy(alpha = 0.38f), RoundedCornerShape(10.dp))
                        .clickable(onClick = onLogToToday),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "+ Log to Today",
                    color = colors.accent,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.ExtraBold,
                )
            }

            if (recipe.isCustom) {
                Box(
                    modifier =
                        Modifier.height(36.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(colors.statusBad.copy(alpha = 0.14f))
                            .border(
                                1.dp,
                                colors.statusBad.copy(alpha = 0.35f),
                                RoundedCornerShape(10.dp),
                            )
                            .clickable(onClick = onDelete)
                            .padding(horizontal = 12.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "Delete",
                        color = colors.statusBad,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.ExtraBold,
                    )
                }
            }
        }
    }
}
