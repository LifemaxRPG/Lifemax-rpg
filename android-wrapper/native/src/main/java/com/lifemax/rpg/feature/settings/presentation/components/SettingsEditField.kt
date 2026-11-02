package com.lifemax.rpg.feature.settings.presentation.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lifemax.rpg.core.designsystem.LocalPageColors
import com.lifemax.rpg.core.designsystem.PageColors
import com.lifemax.rpg.core.designsystem.form.ClockText
import com.lifemax.rpg.core.designsystem.form.TimePick
import com.lifemax.rpg.core.designsystem.form.decimalOnly
import com.lifemax.rpg.core.designsystem.form.rememberPickerState
import com.lifemax.rpg.feature.settings.domain.FieldInput
import com.lifemax.rpg.feature.settings.domain.SettingsControl
import com.lifemax.rpg.feature.settings.presentation.SettingsUi

/**
 * How the page draws a field where it stands: its fill, corner radius and the padding inside its 1
 * dp edge (the edge itself is drawn outside the padding, as CSS does). At the right of a row a time
 * is LMA_IN (37 dp tall) and a number the XP rules' box (78 dp wide, 9 dp corners); under a form
 * label it is the form's full-width input. Shared by the field handed to the page and the one the
 * screen edits, so the two look alike.
 */
internal class FieldLook(
    val fill: Color,
    val radius: Dp,
    val horizontal: Dp,
    val vertical: Dp,
    /** The XP rules' narrow number box. */
    val rule: Boolean,
    val form: Boolean,
)

internal fun fieldLook(input: FieldInput, place: Place, c: PageColors): FieldLook {
    val ink = SettingsInk(c)
    val rule = place == Place.Row && input == FieldInput.Number
    val form = place == Place.Form
    val (fill, radius) =
        when {
            form -> ink.formField to 8.dp
            rule -> ink.ruleField to 9.dp
            else -> ink.inField to 8.dp
        }
    val (horizontal, vertical) =
        when {
            form -> 12.dp to 10.dp
            rule -> 8.dp to 8.dp
            else -> 8.dp to 6.dp
        }
    return FieldLook(fill, radius, horizontal, vertical, rule, form)
}

/**
 * A text, number or time field the screen edits (the page said so: settings.fields.v1). It is drawn
 * as the field handed to the page is. A text or number is typed into in place, the number keyboard
 * for numbers (with a point when the page's step is fractional) and typing stops at the page's
 * maxlength; a time opens the kit's clock picker on the phone's clock style and OK sends the
 * minutes. What is shown is what the owner typed, kept after a refusal, until the page stores it.
 */
@Composable
internal fun EditFieldBox(
    control: SettingsControl.Field,
    ui: SettingsUi,
    taps: ControlTaps,
    place: Place,
    modifier: Modifier,
) {
    val c = LocalPageColors.current
    val look = fieldLook(control.input, place, c)
    val text = ui.text(control)
    val box =
        modifier
            .then(if (look.rule) Modifier.width(78.dp) else Modifier)
            .alpha(if (control.enabled) 1f else .45f)
            .inputSurface(c, look.fill, look.radius)
    val inset =
        Modifier.padding(horizontal = look.horizontal + 1.dp, vertical = look.vertical + 1.dp)
    if (control.input == FieldInput.Time) TimeBox(control, text, taps, box, inset, look)
    else TypedBox(control, text, taps, box, inset, look)
}

@Composable
private fun TimeBox(
    control: SettingsControl.Field,
    text: String,
    taps: ControlTaps,
    box: Modifier,
    inset: Modifier,
    look: FieldLook,
) {
    val c = LocalPageColors.current
    val pickers = rememberPickerState()
    val shown = clock(text)
    val minutes = minutesOf(text)
    Box(
        box.clickable(control.enabled && minutes != null, "Change", Role.Button) {
                minutes?.let {
                    pickers.show(
                        TimePick(control.label, it, ClockText.is12h(shown)) { m ->
                            taps.time(control, m)
                        }
                    )
                }
            }
            .semantics {
                contentDescription = control.label
                stateDescription = shown
            }
            .then(inset)
    ) {
        Text(
            shown,
            Modifier.clearAndSetSemantics {},
            color = c.text,
            fontSize = 14.sp,
            lineHeight = if (look.form) 21.sp else 23.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            softWrap = false,
        )
    }
}

@Composable
private fun TypedBox(
    control: SettingsControl.Field,
    text: String,
    taps: ControlTaps,
    box: Modifier,
    inset: Modifier,
    look: FieldLook,
) {
    val c = LocalPageColors.current
    val edit = control.edit
    val number = control.input == FieldInput.Number
    // a fractional step (the weight's 0.1) takes a point; the other numbers are whole
    val decimal = number && edit?.step?.let { it % 1.0 != 0.0 } == true
    Box(box.then(inset).semantics { contentDescription = control.label }) {
        BasicTextField(
            text,
            { typed ->
                val kept =
                    when {
                        decimal -> decimalOnly(typed)
                        number -> typed.filter(Char::isDigit)
                        else -> typed
                    }
                taps.type(control, edit?.maxLength?.let { kept.take(it) } ?: kept)
            },
            Modifier.fillMaxWidth(),
            enabled = control.enabled,
            textStyle = TextStyle(color = c.text, fontSize = 14.sp, lineHeight = 21.sp),
            keyboardOptions =
                KeyboardOptions(
                    capitalization =
                        if (number) KeyboardCapitalization.None
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
            cursorBrush = SolidColor(c.accent),
            decorationBox = { inner ->
                if (text.isEmpty())
                    Text(
                        control.placeholder,
                        color = Placeholder,
                        fontSize = 14.sp,
                        lineHeight = 21.sp,
                        maxLines = 1,
                        softWrap = false,
                    )
                inner()
            },
        )
    }
}

/** "HH:mm" as minutes after midnight, or null for a value that is not a time. */
internal fun minutesOf(value: String): Int? {
    val m = Regex("^([01]\\d|2[0-3]):([0-5]\\d)$").matchEntire(value) ?: return null
    return m.groupValues[1].toInt() * 60 + m.groupValues[2].toInt()
}
