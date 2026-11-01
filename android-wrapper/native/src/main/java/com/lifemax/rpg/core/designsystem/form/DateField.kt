package com.lifemax.rpg.core.designsystem.form

import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/** A "YYYY-MM-DD" date: as the phone writes a date ("Oct 28, 2026"), and to and from the picker. */
object DateText {
    fun format(value: String): String =
        runCatching {
                LocalDate.parse(value).format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))
            }
            .getOrDefault(value)

    /** The picker counts a day from its UTC midnight. */
    internal fun millis(value: String): Long? =
        runCatching {
                LocalDate.parse(value).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
            }
            .getOrNull()

    internal fun ymd(millis: Long): String =
        Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate().toString()
}

/**
 * A date field (the page's input type=date): its [label] above, the date in a box; a tap opens a
 * calendar and OK sends the day as "YYYY-MM-DD" to [onChange]. [text] replaces the phone's own
 * words for the date when given.
 */
@Composable
fun DateField(
    label: String,
    value: String,
    onChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    text: String? = null,
) {
    val pickers = rememberPickerState()
    PickerFieldBox(label, text ?: DateText.format(value), modifier, enabled) {
        pickers.show(DatePick(label, value, onChange))
    }
}

/** Material's calendar in the screen's colours; every day picked goes to [onChange]. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DateCalendar(r: DatePick, look: PickerFieldLook, onChange: (String) -> Unit) {
    val state = rememberDatePickerState(initialSelectedDateMillis = DateText.millis(r.value))
    LaunchedEffect(state) {
        snapshotFlow { state.selectedDateMillis }
            .collect { ms -> ms?.let { onChange(DateText.ymd(it)) } }
    }
    DatePicker(
        state,
        title = null,
        showModeToggle = false,
        colors =
            DatePickerDefaults.colors(
                containerColor = look.panel,
                titleContentColor = look.label,
                headlineContentColor = look.ink,
                weekdayContentColor = look.muted,
                subheadContentColor = look.muted,
                navigationContentColor = look.ink,
                yearContentColor = look.ink,
                currentYearContentColor = look.accent,
                selectedYearContentColor = look.onAccent,
                selectedYearContainerColor = look.accent,
                dayContentColor = look.ink,
                selectedDayContentColor = look.onAccent,
                selectedDayContainerColor = look.accent,
                todayContentColor = look.accent,
                todayDateBorderColor = look.accent,
                dividerColor = look.border,
            ),
    )
}
