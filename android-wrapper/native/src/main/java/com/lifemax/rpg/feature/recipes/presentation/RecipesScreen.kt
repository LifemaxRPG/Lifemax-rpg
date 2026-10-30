package com.lifemax.rpg.feature.recipes.presentation

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lifemax.rpg.core.designsystem.LifeMaxTheme
import com.lifemax.rpg.core.designsystem.components.PaneSwitch
import com.lifemax.rpg.feature.recipes.domain.RecipesPageAction
import com.lifemax.rpg.feature.recipes.domain.RecipesProjection
import com.lifemax.rpg.feature.recipes.presentation.components.LocalRecipesColors
import com.lifemax.rpg.feature.recipes.presentation.components.RecipeCard
import com.lifemax.rpg.feature.recipes.presentation.components.RecipesButton
import com.lifemax.rpg.feature.recipes.presentation.components.RecipesColors
import com.lifemax.rpg.feature.recipes.presentation.components.RecipesEyebrow
import com.lifemax.rpg.feature.recipes.presentation.components.RecipesFilterCard

@Composable
fun RecipesRoute(
    vm: RecipesViewModel,
    visible: Boolean,
    topInsetDp: Float,
    bottomInsetDp: Float,
    onPage: (RecipesPageAction) -> Unit,
) {
    val projection by vm.projection.collectAsState()
    val ui by vm.ui.collectAsState()
    val p = projection ?: return
    val context = LocalContext.current
    val actions = remember(vm, context, onPage) { RecipesActions(vm, context::openLink, onPage) }
    val list = rememberLazyListState()
    // the page set the search (the diet card asked for a recipe) or reloaded: start at the top
    LaunchedEffect(p.session, p.filterRevision) { list.scrollToItem(0) }
    RecipesScreen(p, ui, actions, topInsetDp, bottomInsetDp, list, visible)
}

/** Everything the screen can ask for, so the screen itself holds no state. */
class RecipesActions(
    val search: (String) -> Unit = {},
    val choose: (String) -> Unit = {},
    val toggle: (String) -> Unit = {},
    val select: (IngredientRef) -> Unit = {},
    val openLink: (String) -> Unit = {},
    val page: (RecipesPageAction) -> Unit = {},
) {
    constructor(
        vm: RecipesViewModel,
        openLink: (String) -> Unit,
        page: (RecipesPageAction) -> Unit,
    ) : this(vm::search, vm::choose, vm::toggle, vm::select, openLink, page)
}

/**
 * The Recipes view as the page lays it out: the Meals/Recipes switch, the heading with "Log a
 * meal", the search and category, and a card per recipe the search shows, or the page's word that
 * none does.
 */
@Composable
fun RecipesScreen(
    p: RecipesProjection,
    ui: RecipesUi,
    actions: RecipesActions,
    topInsetDp: Float = 64f,
    bottomInsetDp: Float = 72f,
    list: LazyListState = rememberLazyListState(),
    visible: Boolean = true,
) {
    RecipesTheme(p.tokens, p.light) {
        LazyColumn(
            Modifier.fillMaxSize().background(LocalRecipesColors.current.bg).imePadding(),
            state = list,
            contentPadding =
                PaddingValues(
                    start = 12.dp,
                    end = 12.dp,
                    top = topInsetDp.dp + 16.dp,
                    bottom = bottomInsetDp.dp + 12.dp,
                ),
        ) {
            item("switch") {
                PaneSwitch(
                    listOf("Meals", "Recipes"),
                    "Recipes",
                    p.light,
                    { actions.page(RecipesPageAction.Meals) },
                    Modifier.padding(bottom = 14.dp),
                )
            }
            item("head") { RecipesHead { actions.page(RecipesPageAction.Meals) } }
            item("filter") {
                RecipesFilterCard(
                    ui.filter,
                    p.categories,
                    visible,
                    actions.search,
                    actions.choose,
                    Modifier.padding(bottom = 14.dp),
                )
            }
            if (ui.hits.isEmpty()) item("empty") { EmptyResult() }
            items(ui.hits, key = { it.id }, contentType = { "recipe" }) { recipe ->
                RecipeCard(
                    recipe,
                    open = recipe.id in ui.expanded,
                    selected = ui.selected?.takeIf { it.recipeId == recipe.id }?.index,
                    onToggle = { actions.toggle(recipe.id) },
                    onSelect = { actions.select(IngredientRef(recipe.id, it)) },
                    onOpenLink = actions.openLink,
                    onLog = { actions.page(RecipesPageAction.Log(recipe.id)) },
                    modifier = Modifier.padding(bottom = 12.dp),
                )
            }
        }
    }
}

/** The page's palette and type for the Recipes view: its theme tokens, CSS line spacing. */
@Composable
private fun RecipesTheme(
    tokens: Map<String, Long>,
    light: Boolean,
    content: @Composable () -> Unit,
) {
    val colors = remember(tokens, light) { RecipesColors(tokens, light) }
    LifeMaxTheme(tokens, light) {
        CompositionLocalProvider(
            LocalRecipesColors provides colors,
            LocalTextStyle provides
                LocalTextStyle.current.copy(letterSpacing = 0.sp, lineHeightStyle = CssLines),
            content = content,
        )
    }
}

@Composable
private fun RecipesHead(onLogMeal: () -> Unit) {
    val c = LocalRecipesColors.current
    Column(Modifier.padding(bottom = 18.dp)) {
        RecipesEyebrow("FUEL YOUR BUILD")
        Text(
            "Recipes",
            Modifier.padding(top = 6.dp).semantics { heading() },
            color = c.text,
            fontSize = 21.sp,
            fontWeight = FontWeight(650),
            letterSpacing = (-0.63).sp,
            lineHeight = 24.15.sp,
        )
        Text(
            "Recipes from the Fit to Win Athlete Cookbook. Nutrition values are shown as printed " +
                "in the cookbook; recipe variations may change them. Class-plan recipes are " +
                "marked (est.) and use estimated nutrition.",
            Modifier.padding(top = 6.dp),
            color = c.muted,
            fontSize = 13.5.sp,
            lineHeight = 20.25.sp,
        )
        RecipesButton("＋ Log a meal", "Log a meal", onLogMeal, Modifier.padding(top = 14.dp))
    }
}

@Composable
private fun EmptyResult() {
    Text(
        "No recipes match that search.",
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 36.dp),
        color = LocalRecipesColors.current.muted,
        fontSize = 14.sp,
        lineHeight = 21.sp,
        textAlign = TextAlign.Center,
    )
}

/** Hands a product link to the browser or the shop's app; the decoder lets only https through. */
private fun Context.openLink(url: String) {
    try {
        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    } catch (e: ActivityNotFoundException) {
        // nothing on the phone opens web links: the page's own link would open nothing either
    }
}

/** Line heights spread above and below the text, as CSS does. */
private val CssLines = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None)
