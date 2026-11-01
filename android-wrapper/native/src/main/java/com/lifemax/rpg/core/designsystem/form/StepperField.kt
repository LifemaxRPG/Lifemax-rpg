package com.lifemax.rpg.core.designsystem.form

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * A number field with a stepper (the page's input type=number with its min and step): its [label]
 * above, then − , the number (typed digits count once they make a number within the limits) and +.
 * − and + move by [step] within [min]..[max]; each is a 48 dp button TalkBack names after the
 * field.
 */
@Composable
fun StepperField(
    label: String,
    value: Int,
    onChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    min: Int? = null,
    max: Int? = null,
    step: Int = 1,
) {
    val look = pickerFieldLook()
    val lo = min ?: Int.MIN_VALUE
    val hi = max ?: Int.MAX_VALUE
    // what is typed stays as typed until it is a number again; a value from outside replaces it
    var typed by remember { mutableStateOf(value.toString()) }
    var shown by remember { mutableStateOf(value) }
    if (value != shown) {
        shown = value
        if (typed.toIntOrNull() != value) typed = value.toString()
    }
    val shape = RoundedCornerShape(11.dp)
    Column(modifier.alpha(if (enabled) 1f else .45f)) {
        PickerLabel(label, look)
        Row(
            Modifier.padding(top = 5.dp).fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            StepperButton("−", "$label, $step $FEWER", enabled && value > lo, look) {
                onChange((value - step).coerceIn(lo, hi))
            }
            BasicTextField(
                typed,
                { t ->
                    val digits = t.filter(Char::isDigit).take(MAX_DIGITS)
                    typed = digits
                    digits.toIntOrNull()?.takeIf { it in lo..hi }?.let(onChange)
                },
                Modifier.weight(1f)
                    .heightIn(min = 48.dp)
                    .clip(shape)
                    .background(look.fill, shape)
                    .border(1.dp, look.border, shape)
                    .padding(horizontal = 12.dp, vertical = 13.dp)
                    .semantics { contentDescription = label },
                enabled = enabled,
                textStyle =
                    LocalTextStyle.current.copy(
                        color = look.ink,
                        fontSize = 15.2.sp,
                        textAlign = TextAlign.Center,
                    ),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                cursorBrush = SolidColor(look.accent),
            )
            StepperButton("+", "$label, $step $MORE", enabled && value < hi, look) {
                onChange((value + step).coerceIn(lo, hi))
            }
        }
    }
}

@Composable
private fun StepperButton(
    sign: String,
    description: String,
    enabled: Boolean,
    look: PickerFieldLook,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(11.dp)
    Box(
        Modifier.size(48.dp)
            .clip(shape)
            .background(look.button, shape)
            .border(1.dp, look.border, shape)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            sign,
            Modifier.clearAndSetSemantics {}.alpha(if (enabled) 1f else .4f),
            style = LocalTextStyle.current,
            color = look.ink,
            fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

private const val MAX_DIGITS = 5
private const val FEWER = "fewer"
private const val MORE = "more"
