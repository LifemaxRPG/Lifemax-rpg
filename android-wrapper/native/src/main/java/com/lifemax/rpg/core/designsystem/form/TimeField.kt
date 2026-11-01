package com.lifemax.rpg.core.designsystem.form

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TimePickerDefaults
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * The page's clock style, read from its own words: a time it wrote with a word in it ("5:00 PM",
 * "오후 5:00") is on a 12-hour clock, digits alone ("17:00") on a 24-hour one. The page decides (its
 * Intl formatter, logic.js fHm); the screen only follows.
 */
object ClockText {
    fun is12h(pageText: String): Boolean = pageText.any(Char::isLetter)

    /** [minutes] after midnight as the page writes a time: "5:00 PM" or "17:00". */
    fun format(minutes: Int, h12: Boolean): String {
        val h = (minutes / 60) % 24
        val m = (minutes % 60).toString().padStart(2, '0')
        if (!h12) return "${h.toString().padStart(2, '0')}:$m"
        val h12h = if (h % 12 == 0) 12 else h % 12
        return "$h12h:$m ${if (h < 12) "AM" else "PM"}"
    }
}

/**
 * A time field (the page's input type=time): its [label] above, the time in a box; a tap opens a
 * clock picker on a 12- or 24-hour clock ([h12]) and OK sends the minutes after midnight to
 * [onChange]. [text] is the page's own words for [value] (shown while the value is the page's); any
 * other value is written in the same style.
 */
@Composable
fun TimeField(
    label: String,
    value: Int,
    onChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    h12: Boolean = true,
    text: String? = null,
) {
    val pickers = rememberPickerState()
    PickerFieldBox(label, text ?: ClockText.format(value, h12), modifier, enabled) {
        pickers.show(TimePick(label, value, h12, onChange))
    }
}

/**
 * A field's label and its tappable box (.ld-root label > input): the box is at least 48 dp tall,
 * TalkBack reads "label, value" and offers [onTap] as "Change".
 */
@Composable
internal fun PickerFieldBox(
    label: String,
    shown: String,
    modifier: Modifier,
    enabled: Boolean,
    onTap: () -> Unit,
) {
    val look = pickerFieldLook()
    val shape = RoundedCornerShape(11.dp)
    Column(modifier.alpha(if (enabled) 1f else .45f)) {
        PickerLabel(label, look)
        Box(
            Modifier.padding(top = 5.dp)
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .clip(shape)
                .background(look.fill, shape)
                .border(1.dp, look.border, shape)
                .clickable(enabled = enabled, onClickLabel = PICK_CHANGE, role = Role.Button) {
                    onTap()
                }
                .semantics {
                    contentDescription = label
                    stateDescription = shown
                }
                .padding(horizontal = 12.dp, vertical = 9.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            Text(
                shown,
                Modifier.clearAndSetSemantics {},
                style = LocalTextStyle.current,
                color = look.ink,
                fontSize = 15.2.sp,
                maxLines = 1,
            )
        }
    }
}

/** A field's label: .ld-root label (.82rem, 600, the second ink). */
@Composable
internal fun PickerLabel(label: String, look: PickerFieldLook) =
    Text(
        label,
        Modifier.clearAndSetSemantics {},
        style = LocalTextStyle.current,
        color = look.label,
        fontSize = 13.12.sp,
        fontWeight = FontWeight.SemiBold,
    )

/** Material's clock face in the screen's colours; every change goes to [onChange]. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TimeWheel(r: TimePick, look: PickerFieldLook, onChange: (Int) -> Unit) {
    val state =
        rememberTimePickerState(
            initialHour = r.minutes / 60 % 24,
            initialMinute = r.minutes % 60,
            is24Hour = !r.h12,
        )
    LaunchedEffect(state) { snapshotFlow { state.hour * 60 + state.minute }.collect(onChange) }
    TimePicker(
        state,
        colors =
            TimePickerDefaults.colors(
                clockDialColor = look.fill,
                clockDialSelectedContentColor = look.onAccent,
                clockDialUnselectedContentColor = look.ink,
                selectorColor = look.accent,
                containerColor = look.panel,
                periodSelectorBorderColor = look.border,
                periodSelectorSelectedContainerColor = look.accent.copy(alpha = .22f),
                periodSelectorUnselectedContainerColor = look.panel,
                periodSelectorSelectedContentColor = look.ink,
                periodSelectorUnselectedContentColor = look.muted,
                timeSelectorSelectedContainerColor = look.accent.copy(alpha = .22f),
                timeSelectorUnselectedContainerColor = look.fill,
                timeSelectorSelectedContentColor = look.ink,
                timeSelectorUnselectedContentColor = look.ink,
            ),
    )
}

internal const val PICK_CHANGE = "Change"
