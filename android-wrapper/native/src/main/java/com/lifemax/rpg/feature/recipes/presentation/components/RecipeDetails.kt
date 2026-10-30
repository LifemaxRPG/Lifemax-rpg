package com.lifemax.rpg.feature.recipes.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lifemax.rpg.feature.recipes.domain.Recipe
import com.lifemax.rpg.feature.recipes.domain.RecipeIngredient

/**
 * An open recipe: its ingredients, the product panel of the [selected] one, its numbered
 * instructions and its note.
 */
@Composable
internal fun RecipeDetails(
    recipe: Recipe,
    selected: Int?,
    onSelect: (Int) -> Unit,
    onOpenLink: (String) -> Unit,
) {
    val c = LocalRecipesColors.current
    // the page's space under the steps, or under the note
    Column(Modifier.padding(bottom = if (recipe.note.isEmpty()) 10.dp else 12.dp)) {
        Heading("Ingredients")
        Column(
            Modifier.padding(start = 20.dp, top = 6.dp, bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            recipe.ingredients.forEachIndexed { i, ingredient ->
                IngredientRow(ingredient, i == selected) { onSelect(i) }
            }
        }
        selected?.let(recipe.ingredients::getOrNull)?.let {
            ProductPanel(it, onOpenLink, Modifier.padding(bottom = 12.dp))
        }
        Heading("Instructions")
        Column(Modifier.padding(top = 6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            recipe.steps.forEachIndexed { i, step ->
                Row(Modifier.semantics(mergeDescendants = true) {}) {
                    Text(
                        "${i + 1}.",
                        Modifier.width(20.dp).padding(end = 3.dp),
                        color = c.textDim,
                        fontSize = 12.5.sp,
                        lineHeight = 18.75.sp,
                        textAlign = TextAlign.End,
                    )
                    Text(step, color = c.textDim, fontSize = 12.5.sp, lineHeight = 18.75.sp)
                }
            }
        }
        if (recipe.note.isNotEmpty())
            Text(
                recipe.note,
                Modifier.padding(top = 12.dp),
                color = c.muted,
                fontSize = 12.sp,
                lineHeight = 18.sp,
            )
    }
}

/** A bold caption, on a line as tall as the page's body text. */
@Composable
private fun Heading(text: String) {
    Text(
        text,
        color = LocalRecipesColors.current.text,
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold,
        lineHeight = 21.sp,
    )
}

/**
 * An ingredient: tapping it selects it, outlined, and shows its product panel. The mark is 🛒 when
 * it has a product link.
 */
@Composable
private fun IngredientRow(ingredient: RecipeIngredient, selected: Boolean, onSelect: () -> Unit) {
    val c = LocalRecipesColors.current
    val linked = ingredient.link.isNotEmpty()
    Row(
        Modifier.fillMaxWidth()
            .then(if (selected) Modifier.outline(c.accent, 2.dp, 8.dp) else Modifier)
            .clip(IngredientShape)
            .background(IngredientFill)
            .selectable(selected, role = Role.Button, onClick = onSelect)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            ingredient.text,
            Modifier.weight(1f),
            color = c.textDim,
            fontSize = 12.5.sp,
            lineHeight = 18.75.sp,
        )
        Text(
            if (linked) "🛒" else "＋",
            Modifier.alpha(0.7f).clearAndSetSemantics {
                if (linked) contentDescription = "Walmart link"
            },
            color = c.textDim,
            fontSize = 12.5.sp,
            lineHeight = 18.75.sp,
        )
    }
}

private val IngredientShape = RoundedCornerShape(8.dp)

/**
 * The selected ingredient's product panel: its Walmart link and a button that opens it, or the
 * page's word that it has none yet.
 */
@Composable
private fun ProductPanel(
    ingredient: RecipeIngredient,
    onOpenLink: (String) -> Unit,
    modifier: Modifier,
) {
    val c = LocalRecipesColors.current
    if (ingredient.link.isEmpty())
        RecipesCard(modifier, padding = 10.dp) {
            Text(
                ingredient.text,
                color = c.text,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                lineHeight = 18.sp,
            )
            Text(
                "No Walmart product link added for this ingredient yet.",
                Modifier.padding(top = 3.dp),
                color = c.muted,
                fontSize = 12.sp,
                lineHeight = 18.sp,
            )
        }
    else
        RecipesCard(modifier, padding = 12.dp) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        ingredient.text,
                        color = c.text,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        lineHeight = 21.sp,
                    )
                    Text(
                        "Walmart product link attached",
                        Modifier.padding(top = 3.dp),
                        color = c.muted,
                        fontSize = 11.sp,
                        lineHeight = 16.5.sp,
                    )
                }
                RecipesButton(
                    "Open in Walmart ↗",
                    "Open in Walmart",
                    { onOpenLink(ingredient.link) },
                )
            }
        }
}
