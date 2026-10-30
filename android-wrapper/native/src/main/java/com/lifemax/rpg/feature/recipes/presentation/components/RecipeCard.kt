package com.lifemax.rpg.feature.recipes.presentation.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.collapse
import androidx.compose.ui.semantics.expand
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lifemax.rpg.core.designsystem.components.tapTarget
import com.lifemax.rpg.feature.recipes.domain.Recipe

/**
 * A recipe as the page's card draws it: category, name, yield and times, calories, the three
 * macros, its ingredients and instructions folded under a disclosure ([open]), "Log this recipe"
 * and the footnote. [selected] is the index of the selected ingredient when it is this recipe's.
 */
@Composable
internal fun RecipeCard(
    recipe: Recipe,
    open: Boolean,
    selected: Int?,
    onToggle: () -> Unit,
    onSelect: (Int) -> Unit,
    onOpenLink: (String) -> Unit,
    onLog: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = LocalRecipesColors.current
    RecipesCard(modifier) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Column(Modifier.weight(1f)) {
                RecipesEyebrow(recipe.category)
                Text(
                    recipe.name,
                    Modifier.padding(top = 4.dp, bottom = 6.dp).semantics { heading() },
                    color = c.textDim,
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight(650),
                    letterSpacing = (-0.135).sp,
                    lineHeight = 20.25.sp,
                )
                Text(recipe.meta, color = c.muted, fontSize = 12.sp, lineHeight = 18.sp)
            }
            RecipesTag(recipe.kcal)
        }
        Row(Modifier.padding(top = 14.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Macro("Protein", recipe.protein, Modifier.weight(1f))
            Macro("Carbs", recipe.carbs, Modifier.weight(1f))
            Macro("Fat", recipe.fat, Modifier.weight(1f))
        }
        Disclosure(open, onToggle)
        if (open) RecipeDetails(recipe, selected, onSelect, onOpenLink)
        RecipesButton(
            "＋ Log this recipe",
            "Log ${recipe.name}",
            onLog,
            Modifier.fillMaxWidth().padding(top = 10.dp),
        )
        Text(
            recipe.footnote,
            Modifier.padding(top = 8.dp),
            color = c.muted,
            fontSize = 11.sp,
            lineHeight = 16.5.sp,
        )
    }
}

@Composable
private fun Macro(label: String, value: String, modifier: Modifier) {
    val c = LocalRecipesColors.current
    RecipesCard(modifier.semantics(mergeDescendants = true) {}, padding = 10.dp) {
        Text(
            label,
            color = c.muted,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            lineHeight = 18.sp,
        )
        Text(
            value,
            Modifier.padding(top = 8.dp, bottom = 4.dp),
            color = c.text,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = (-0.54).sp,
            lineHeight = 27.sp,
        )
    }
}

/**
 * The page's details summary with its triangle. Its 48 dp target takes in the 14 dp the page leaves
 * above it, so the words sit where the page has them.
 */
@Composable
private fun Disclosure(open: Boolean, onToggle: () -> Unit) {
    val c = LocalRecipesColors.current
    Row(
        Modifier.fillMaxWidth()
            .semantics {
                stateDescription = if (open) "Expanded" else "Collapsed"
                if (open)
                    collapse {
                        onToggle()
                        true
                    }
                else
                    expand {
                        onToggle()
                        true
                    }
            }
            .tapTarget(Role.Button, onClick = onToggle)
            .padding(top = 18.dp, bottom = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(7.dp, 8.dp).rotate(if (open) 90f else 0f).drawWithCache {
                val marker =
                    Path().apply {
                        lineTo(size.width, size.height / 2)
                        lineTo(0f, size.height)
                        close()
                    }
                onDrawBehind { drawPath(marker, c.text) }
            }
        )
        Text(
            "Ingredients & instructions",
            color = c.text,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            lineHeight = 19.5.sp,
        )
    }
}
