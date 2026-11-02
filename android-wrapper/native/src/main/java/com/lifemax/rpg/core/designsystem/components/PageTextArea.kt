package com.lifemax.rpg.core.designsystem.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lifemax.rpg.core.designsystem.gesture.noTabSwipe

/**
 * The colours of a [PageTextArea]: the typed [ink], the [placeholder] shown while it is empty, the
 * [rule] of the notebook lines, the [well] behind them, the [edge] around the box and the [cursor]
 * (the selection and its handles take the cursor's colour).
 */
@Stable
class PageTextAreaLook(
    val ink: Color,
    val placeholder: Color,
    val rule: Color,
    val well: Color,
    val edge: Color,
    val cursor: Color,
)

/**
 * The page's ruled notebook field (.j-reflect textarea): a box at least [minHeight] tall that grows
 * with the text, whose lines are drawn from the text's own layout, so they follow it at any font
 * size: one rule under every line, the first at the line's bottom edge, and more below the text
 * down to the box's foot. Typing stops at [maxLength].
 *
 * The caller owns the [value] (a TextFieldValue, so the keyboard's composing text, the selection
 * and its handles stay the field's own); [onFocusChange] hears the field gaining or losing the
 * focus. The cursor's line is brought into view whenever it moves, the focus arrives, or [revealOn]
 * changes (the caller passes the keyboard's state, since the keyboard shrinks the room the field
 * has). [focusRequester] lets a caller focus it. Never a tab swipe: a finger on the field selects
 * text and moves the cursor.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PageTextArea(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    placeholder: String,
    look: PageTextAreaLook,
    modifier: Modifier = Modifier,
    maxLength: Int = Int.MAX_VALUE,
    minHeight: Dp = 124.dp,
    fontSize: TextUnit = 14.5.sp,
    lineHeight: TextUnit = 28.sp,
    revealOn: Any? = null,
    description: String? = null,
    focusRequester: FocusRequester? = null,
    onFocusChange: (Boolean) -> Unit = {},
) {
    val shape = RoundedCornerShape(14.dp)
    var layout by remember { mutableStateOf<TextLayoutResult?>(null) }
    var focused by remember { mutableStateOf(false) }
    val bring = remember { BringIntoViewRequester() }
    val padX = 15.dp
    val padY = 11.dp
    val handles = TextSelectionColors(look.cursor, look.cursor.copy(alpha = 0.3f))
    if (focused) {
        val cursor = value.selection.end.coerceIn(0, value.text.length)
        val density = androidx.compose.ui.platform.LocalDensity.current
        LaunchedEffect(cursor, layout?.lineCount, revealOn) {
            val l = layout ?: return@LaunchedEffect
            if (l.layoutInput.text.length < cursor) return@LaunchedEffect
            val rect = l.getCursorRect(cursor)
            bring.bringIntoView(
                rect.translate(with(density) { padX.toPx() }, with(density) { padY.toPx() })
            )
        }
    }
    CompositionLocalProvider(LocalTextSelectionColors provides handles) {
        BasicTextField(
            value,
            { typed ->
                onValueChange(
                    if (typed.text.length <= maxLength) typed
                    else
                        typed.copy(
                            text = typed.text.take(maxLength),
                            selection = TextRange(maxLength),
                        )
                )
            },
            modifier
                .fillMaxWidth()
                .heightIn(min = minHeight)
                .clip(shape)
                .background(look.well)
                .drawBehind {
                    // repeating-linear-gradient(transparent 0 27px, rule 27px 28px): one rule a
                    // line apart, from the first line's foot to the box's. The pitch is the text's
                    // own, so the lines keep to it when the font is scaled.
                    val l = layout
                    val step =
                        if (l != null && l.lineCount > 0) l.getLineBottom(0) - l.getLineTop(0)
                        else lineHeight.toPx()
                    val b = 1.dp.toPx()
                    if (step > b) {
                        var y = step
                        while (y < size.height - b) {
                            drawRect(look.rule, Offset(b, y), Size(size.width - 2 * b, b))
                            y += step
                        }
                    }
                }
                .border(1.dp, look.edge, shape)
                .noTabSwipe()
                .bringIntoViewRequester(bring)
                .then(
                    if (focusRequester != null) Modifier.focusRequester(focusRequester)
                    else Modifier
                )
                .onFocusChanged {
                    focused = it.isFocused
                    onFocusChange(it.isFocused)
                }
                .semantics { description?.let { d -> contentDescription = d } },
            textStyle = TextStyle(color = look.ink, fontSize = fontSize, lineHeight = lineHeight),
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            cursorBrush = SolidColor(look.cursor),
            onTextLayout = { layout = it },
            decorationBox = { inner ->
                Box(Modifier.padding(horizontal = padX, vertical = padY)) {
                    if (value.text.isEmpty())
                        Text(
                            placeholder,
                            color = look.placeholder,
                            fontSize = fontSize,
                            lineHeight = lineHeight,
                        )
                    inner()
                }
            },
        )
    }
}
