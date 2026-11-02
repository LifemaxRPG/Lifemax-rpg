package com.lifemax.rpg.core.designsystem.form

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * The page's text input under its label (.ld-root label > input): a dark well 44 dp tall with an 11
 * dp radius and a faint edge, which turns blue with a soft blue ring while focused, as the page's
 * :focus does. Typing stops at [maxLength] as the page's maxlength does; [placeholder] shows while
 * it is empty. [number] asks for the number keyboard and keeps digits only (the page's type=number
 * boxes with a placeholder, such as the goal's target). [error] is the page's words when it refused
 * the field: the edge turns red and TalkBack reads them. [decimal] asks for the number keyboard
 * with a point and keeps digits and one point (Food's label values, such as 12.5 g of protein).
 */
@Composable
fun FormTextField(
    label: String,
    value: String,
    onChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    placeholder: String? = null,
    maxLength: Int? = null,
    number: Boolean = false,
    error: String? = null,
    decimal: Boolean = false,
) {
    val look = LocalFormKitLook.current
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val shape = RoundedCornerShape(FormKitSize.fieldRadius)
    Column(modifier.alpha(if (enabled) 1f else look.disabledAlpha)) {
        FormKitLabel(label)
        BasicTextField(
            value,
            { typed ->
                val kept =
                    when {
                        decimal -> decimalOnly(typed)
                        number -> typed.filter(Char::isDigit)
                        else -> typed
                    }
                onChange(maxLength?.let { kept.take(it) } ?: kept)
            },
            Modifier.padding(top = 5.dp)
                .fillMaxWidth()
                .heightIn(min = FormKitSize.fieldHeight)
                .drawBehind {
                    if (focused && enabled) {
                        val ring = 3.dp.toPx()
                        drawRoundRect(
                            look.focusRing,
                            Offset(-ring, -ring),
                            Size(size.width + 2 * ring, size.height + 2 * ring),
                            CornerRadius(FormKitSize.fieldRadius.toPx() + ring),
                        )
                    }
                }
                .background(look.well, shape)
                .border(
                    1.dp,
                    when {
                        error != null -> look.error
                        focused && enabled -> look.focus
                        else -> look.edge
                    },
                    shape,
                )
                .semantics {
                    contentDescription = label
                    if (error != null) error(error)
                },
            enabled = enabled,
            textStyle = look.text.copy(color = look.ink, fontSize = 15.2.sp),
            keyboardOptions =
                KeyboardOptions(
                    capitalization =
                        if (number || decimal) KeyboardCapitalization.None
                        else KeyboardCapitalization.Sentences,
                    keyboardType =
                        when {
                            decimal -> KeyboardType.Decimal
                            number -> KeyboardType.Number
                            else -> KeyboardType.Text
                        },
                    imeAction = ImeAction.Done,
                ),
            singleLine = true,
            interactionSource = interaction,
            cursorBrush = SolidColor(look.cursor),
            decorationBox = { inner ->
                Box(
                    Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    if (value.isEmpty() && placeholder != null)
                        Text(
                            placeholder,
                            style = look.text,
                            color = look.ink3,
                            fontSize = 15.2.sp,
                            maxLines = 1,
                        )
                    inner()
                }
            },
        )
    }
}

/** What is typed, kept to digits and one point (the page's type=number inputmode=decimal boxes). */
internal fun decimalOnly(typed: String): String {
    val digits = typed.filter { it.isDigit() || it == '.' }
    val point = digits.indexOf('.')
    return if (point < 0) digits
    else digits.substring(0, point + 1) + digits.substring(point + 1).replace(".", "")
}
