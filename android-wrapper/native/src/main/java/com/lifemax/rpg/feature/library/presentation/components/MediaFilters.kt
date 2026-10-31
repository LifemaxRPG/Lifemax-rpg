package com.lifemax.rpg.feature.library.presentation.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lifemax.rpg.feature.library.domain.FilterControl
import com.lifemax.rpg.feature.library.domain.MediaFilter

/**
 * The top of the list's card: the search box, then the Show and Media type selects, which keep the
 * page's least widths and spread apart on one line, or wrap as the page's do. [visible] is false
 * while the screen is hidden, which closes a select's menu.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun MediaFilters(
    filter: MediaFilter,
    searchLabel: String,
    placeholder: String,
    status: FilterControl,
    type: FilterControl,
    visible: Boolean,
    onSearch: (String) -> Unit,
    onStatus: (String) -> Unit,
    onType: (String) -> Unit,
) {
    val c = LocalLibraryColors.current
    Column(
        Modifier.cardPiece(c, first = true)
            .padding(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 14.dp)
    ) {
        SearchBox(filter.search, searchLabel, placeholder, onSearch)
        FlowRow(
            Modifier.padding(top = 10.dp).fillMaxWidth(),
            horizontalArrangement = SpaceBetweenWithGap(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Field(status.label) { Select(status, filter.status, 155.dp, visible, onStatus) }
            Field(type.label) { Select(type, filter.type, 135.dp, visible, onType) }
        }
    }
}

/** The page's .field: a label over its control, which names itself for TalkBack. */
@Composable
private fun Field(label: String, control: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        FieldLabel(label, Modifier.clearAndSetSemantics {})
        control()
    }
}

@Composable
private fun FieldLabel(label: String, modifier: Modifier = Modifier) {
    Text(
        label,
        modifier,
        color = LocalLibraryColors.current.muted,
        fontSize = 12.sp,
        fontWeight = FontWeight(550),
        lineHeight = 18.sp,
    )
}

/**
 * The search field: its label over its box, both part of the text field, so that TalkBack reads the
 * label as the field's name, as a browser reads the page's label, and a tap on it focuses the box.
 * Focused, the box takes the page's accent border and its 3 dp ring; the keyboard's search key puts
 * the keyboard away, since the list follows every key.
 */
@Composable
private fun SearchBox(
    text: String,
    label: String,
    placeholder: String,
    onChange: (String) -> Unit,
) {
    val c = LocalLibraryColors.current
    val focus = LocalFocusManager.current
    var focused by remember { mutableStateOf(false) }
    val style = LocalTextStyle.current.copy(color = c.text, fontSize = 14.sp, lineHeight = 21.sp)
    BasicTextField(
        text,
        onChange,
        Modifier.fillMaxWidth().onFocusChanged { focused = it.isFocused },
        textStyle = style,
        singleLine = true,
        cursorBrush = SolidColor(c.text),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { focus.clearFocus() }),
        decorationBox = { field ->
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                FieldLabel(label)
                FieldFrame(
                    if (focused) c.accent else c.line,
                    Modifier.fillMaxWidth()
                        .then(if (focused) Modifier.focusRing(c.accentDim) else Modifier),
                ) {
                    Box {
                        if (text.isEmpty())
                            Text(
                                placeholder,
                                style = style.copy(color = PlaceholderGrey),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        field()
                    }
                }
            }
        },
    )
}

/**
 * A select: as wide as its longest option (and at least [least]), as the browser sizes the page's,
 * showing the chosen option and opening a menu of all of them.
 */
@Composable
private fun Select(
    control: FilterControl,
    chosen: String,
    least: Dp,
    visible: Boolean,
    onChoose: (String) -> Unit,
) {
    val c = LocalLibraryColors.current
    var expanded by remember { mutableStateOf(false) }
    LaunchedEffect(visible) { if (!visible) expanded = false }
    val style = LocalTextStyle.current.copy(color = c.text, fontSize = 14.sp, lineHeight = 19.sp)
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val longest =
        remember(control.options, style, density) {
            with(density) {
                control.options.maxOf { measurer.measure(it.label, style).size.width }.toDp()
            }
        }
    val label = control.label(chosen)
    Box {
        FieldFrame(
            c.line,
            Modifier.widthIn(min = least)
                .width(longest + SelectChrome)
                .clip(FieldShape)
                .clickable(role = Role.DropdownList) { expanded = true }
                .semantics { contentDescription = "${control.label}: $label" },
            PaddingValues(start = 17.dp, top = 11.dp, end = 5.dp, bottom = 11.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    label,
                    Modifier.weight(1f),
                    style = style,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Chevron(c.text)
            }
        }
        DropdownMenu(
            expanded,
            onDismissRequest = { expanded = false },
            shape = RoundedCornerShape(10.dp),
            containerColor = c.panel,
            border = BorderStroke(1.dp, c.line),
        ) {
            control.options.forEach { option ->
                val on = option.id == chosen
                DropdownMenuItem(
                    text = {
                        Text(
                            option.label,
                            color = if (on) c.accentStrong else c.text,
                            fontSize = 14.sp,
                            fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal,
                        )
                    },
                    onClick = {
                        expanded = false
                        onChoose(option.id)
                    },
                    modifier = Modifier.semantics { selected = on },
                )
            }
        }
    }
}

/** The browser's select arrow: a chevron about 9.5 × 6 dp. */
@Composable
private fun Chevron(color: Color) {
    Spacer(
        Modifier.size(9.5.dp, 6.dp).drawWithCache {
            val stroke = Stroke(2.dp.toPx())
            val inset = stroke.width / 2
            val path =
                Path().apply {
                    moveTo(inset, inset)
                    // the stroke's mitred corner reaches √2 half widths below its point
                    lineTo(size.width / 2, size.height - inset * 1.414f)
                    lineTo(size.width - inset, inset)
                }
            onDrawBehind { drawPath(path, color, style = stroke) }
        }
    )
}

/**
 * The page's field box: its background and a 1 dp [border], with the page's padding inside it
 * (Compose draws the border over the padding, so [padding] includes the border's width).
 */
@Composable
private fun FieldFrame(
    border: Color,
    modifier: Modifier = Modifier,
    padding: PaddingValues = PaddingValues(horizontal = 13.dp, vertical = 11.dp),
    content: @Composable () -> Unit,
) {
    val c = LocalLibraryColors.current
    Box(
        modifier
            .clip(FieldShape)
            .background(c.field)
            .border(1.dp, border, FieldShape)
            .padding(padding)
    ) {
        content()
    }
}

private val FieldShape = RoundedCornerShape(8.dp)

/** The page's focus ring: box-shadow 0 0 0 3px, around the field's 8 dp corners. */
private fun Modifier.focusRing(color: Color): Modifier = drawWithCache {
    val ring = 3.dp.toPx()
    val stroke = Stroke(ring)
    val corner = CornerRadius(8.dp.toPx() + ring / 2)
    onDrawBehind {
        drawRoundRect(
            color,
            Offset(-ring / 2, -ring / 2),
            Size(size.width + ring, size.height + ring),
            corner,
            style = stroke,
        )
    }
}

/**
 * What the browser adds to a select's longest option: the border and padding, its indent before the
 * text, and the arrow with the space around it.
 */
private val SelectChrome = 47.dp

/**
 * CSS justify-content: space-between with a gap: a line breaks as if its items were [gap] apart.
 */
private class SpaceBetweenWithGap(gap: Dp) : Arrangement.Horizontal {
    override val spacing = gap

    override fun Density.arrange(
        totalSize: Int,
        sizes: IntArray,
        layoutDirection: LayoutDirection,
        outPositions: IntArray,
    ) = with(Arrangement.SpaceBetween) { arrange(totalSize, sizes, layoutDirection, outPositions) }
}
