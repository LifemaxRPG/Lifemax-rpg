package com.lifemax.rpg.feature.recipes.presentation.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lifemax.rpg.core.designsystem.LocalPageColors
import com.lifemax.rpg.core.designsystem.PageColors
import com.lifemax.rpg.core.designsystem.components.PageCard
import com.lifemax.rpg.feature.recipes.domain.RecipeCategory
import com.lifemax.rpg.feature.recipes.domain.RecipeFilter

/**
 * The search field and the category menu. The menu closes when the screen is hidden ([visible]), as
 * a hidden screen shows nothing over the page (A24).
 */
@Composable
internal fun RecipesFilterCard(
    filter: RecipeFilter,
    categories: List<RecipeCategory>,
    visible: Boolean,
    onSearch: (String) -> Unit,
    onChoose: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    PageCard(modifier) {
        FieldLabel("Find a recipe")
        SearchField(filter.query, onSearch)
        FieldLabel("Category", Modifier.padding(top = 10.dp))
        CategoryMenu(filter.category, categories, visible, onChoose)
    }
}

/** A field's caption. TalkBack hears it from the field itself. */
@Composable
private fun FieldLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        modifier.padding(bottom = 6.dp).clearAndSetSemantics {},
        color = LocalPageColors.current.muted,
        fontSize = 12.sp,
        fontWeight = FontWeight(550),
        lineHeight = 18.sp,
    )
}

/** The search box. The keyboard's search key puts the keyboard away; the list follows each key. */
@Composable
private fun SearchField(query: String, onSearch: (String) -> Unit) {
    val c = LocalPageColors.current
    val focus = LocalFocusManager.current
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    BasicTextField(
        query,
        onSearch,
        Modifier.fillMaxWidth().semantics { contentDescription = "Find a recipe" },
        textStyle =
            LocalTextStyle.current.copy(color = c.text, fontSize = 14.sp, lineHeight = 21.sp),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { focus.clearFocus() }),
        singleLine = true,
        interactionSource = interaction,
        cursorBrush = SolidColor(c.accent),
    ) { field ->
        Box(Modifier.field(c, focused).padding(horizontal = 13.dp, vertical = 11.dp)) {
            if (query.isEmpty())
                Text(
                    "Search recipes or ingredients…",
                    Modifier.clearAndSetSemantics {},
                    color = Placeholder,
                    fontSize = 14.sp,
                    lineHeight = 21.sp,
                    overflow = TextOverflow.Ellipsis,
                    maxLines = 1,
                )
            field()
        }
    }
}

/** The category select: the chosen category, and a menu of the page's categories. */
@Composable
private fun CategoryMenu(
    selected: String,
    categories: List<RecipeCategory>,
    visible: Boolean,
    onChoose: (String) -> Unit,
) {
    val c = LocalPageColors.current
    var open by remember(visible) { mutableStateOf(false) }
    var width by remember { mutableIntStateOf(0) }
    val label = categories.firstOrNull { it.value == selected }?.label ?: selected
    Box(Modifier.onSizeChanged { width = it.width }) {
        Row(
            Modifier.fillMaxWidth()
                .field(c, open)
                .semantics {
                    contentDescription = "Category"
                    stateDescription = label
                }
                .clickable(role = Role.DropdownList, onClickLabel = "Choose a category") {
                    open = true
                }
                .padding(start = 13.dp, end = 3.dp, top = 11.dp, bottom = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(label, Modifier.weight(1f), color = c.text, fontSize = 14.sp, lineHeight = 19.sp)
            Chevron()
        }
        DropdownMenu(
            open,
            { open = false },
            Modifier.width(with(LocalDensity.current) { width.toDp() }).selectableGroup(),
            shape = FieldShape,
            containerColor = c.panel,
            tonalElevation = 0.dp,
            border = BorderStroke(1.dp, c.line),
        ) {
            categories.forEach { category ->
                val on = category.value == selected
                DropdownMenuItem(
                    text = {
                        Text(
                            category.label,
                            color = if (on) c.accentStrong else c.text,
                            fontSize = 14.sp,
                            fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal,
                        )
                    },
                    onClick = {
                        open = false
                        onChoose(category.value)
                    },
                    modifier =
                        Modifier.semantics { this.selected = on }
                            .then(if (on) Modifier.background(c.accentDim) else Modifier),
                )
            }
        }
    }
}

/** The select's arrow, as the browser draws it: near the right edge, within the padding. */
@Composable
private fun Chevron() {
    val color = LocalPageColors.current.text
    Box(
        Modifier.size(10.dp, 6.dp).drawWithCache {
            val width = 2.dp.toPx()
            val stroke = Stroke(width, cap = StrokeCap.Round, join = StrokeJoin.Round)
            val arrow =
                Path().apply {
                    moveTo(width / 2, width / 2)
                    lineTo(size.width / 2, size.height - width / 2)
                    lineTo(size.width - width / 2, width / 2)
                }
            onDrawBehind { drawPath(arrow, color, style = stroke) }
        }
    )
}

/**
 * A field's box: white in the light theme, its border, and the accent border with a soft ring while
 * it is [active]. Compose draws the border over the box's edge, so the padding inside it also holds
 * the page's border.
 */
private fun Modifier.field(c: PageColors, active: Boolean) =
    then(if (active) Modifier.outline(c.accentDim, 3.dp, 8.dp) else Modifier)
        .clip(FieldShape)
        .background(c.field)
        .border(1.dp, if (active) c.accent else c.line, FieldShape)

private val FieldShape = RoundedCornerShape(8.dp)
