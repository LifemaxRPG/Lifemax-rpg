package com.lifemax.rpg.core.designsystem.form

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.collapse
import androidx.compose.ui.semantics.expand
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.lifemax.rpg.core.designsystem.components.tapTarget

/**
 * Draws a [FormSchema]'s fields in the page's order with the current [values] (a draft, by key),
 * each with its page's words in [errors] under it and its note. Fields of one group share a row as
 * the page's ld-grid2 (two equal columns) and ld-grid3 (1fr 1fr 1.3fr) do, their bottoms lined up;
 * a field a check hides is left out. The fold is the page's details element, closed until opened
 * (or until one of its fields is refused). Every change goes to [onChange] with the field's key;
 * nothing is checked or sent here (FormValidation, the sheet's Save).
 */
@Composable
fun SchemaForm(
    schema: FormSchema,
    values: Map<String, FieldValue>,
    onChange: (key: String, value: FieldValue) -> Unit,
    modifier: Modifier = Modifier,
    errors: Map<String, String> = emptyMap(),
    enabled: Boolean = true,
) {
    val shown = FormValidation.visibleKeys(schema, values)
    // the screen's picker look if it set one, else the kit's own, so the pickers match the form
    val pickers = LocalPickerFieldLook.current ?: LocalFormKitLook.current.pickers()
    CompositionLocalProvider(LocalPickerFieldLook provides pickers) {
        Column(modifier.fillMaxWidth()) {
            Fields(schema.fields, shown, values, onChange, errors, enabled)
            schema.fold?.let { fold ->
                var open by rememberSaveable { mutableStateOf(false) }
                val refused = fold.fields.any { it.key in errors }
                FoldSummary(fold.label, open || refused) { open = !open }
                if (open || refused) Fields(fold.fields, shown, values, onChange, errors, enabled)
            }
        }
    }
}

/** The fields shown, a group's fields side by side. */
@Composable
private fun Fields(
    fields: List<FormField>,
    shown: Set<String>,
    values: Map<String, FieldValue>,
    onChange: (String, FieldValue) -> Unit,
    errors: Map<String, String>,
    enabled: Boolean,
) {
    formRows(fields.filter { it.key in shown }).forEach { row ->
        if (row.size == 1) {
            val f = row[0]
            Column(Modifier.fillMaxWidth()) {
                Cell(f, values, onChange, errors, enabled, Modifier.fillMaxWidth())
                Words(f, errors[f.key], Modifier.fillMaxWidth())
            }
        } else {
            // ld-grid3's last column is 1.3 times as wide
            val weights = row.indices.map { if (row.size == 3 && it == 2) 1.3f else 1f }
            // the boxes line up at the bottom as the page's grid aligns them (align-items:end); the
            // words under them start together below
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.Bottom,
            ) {
                row.forEachIndexed { i, f ->
                    Cell(f, values, onChange, errors, enabled, Modifier.weight(weights[i]))
                }
            }
            if (row.any { it.key in errors || it.note != null })
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    row.forEachIndexed { i, f ->
                        Words(f, errors[f.key], Modifier.weight(weights[i]))
                    }
                }
        }
    }
}

/** The page's words when it refused [field], then the field's note. */
@Composable
private fun Words(field: FormField, error: String?, modifier: Modifier) {
    Column(modifier) {
        error?.let { FormKitError(it) }
        field.note?.let { FormKitNote(it) }
    }
}

/** Consecutive fields of one group make a row; a field with no group is a row alone. */
internal fun formRows(fields: List<FormField>): List<List<FormField>> {
    val out = mutableListOf<MutableList<FormField>>()
    for (f in fields) {
        val last = out.lastOrNull()
        if (f.group != null && last != null && last.first().group == f.group) last += f
        else out += mutableListOf(f)
    }
    return out
}

@Composable
private fun Cell(
    field: FormField,
    values: Map<String, FieldValue>,
    onChange: (String, FieldValue) -> Unit,
    errors: Map<String, String>,
    enabled: Boolean,
    modifier: Modifier,
) {
    val error = errors[field.key]
    // .ld-root label sits 10 px below what is above it; a check row's 48 dp holds its own 12 px
    // margins
    Column(modifier.padding(top = if (field.kind == FieldKind.CHECK) 0.dp else 10.dp)) {
        FieldFor(
            field,
            values[field.key] ?: field.value,
            { onChange(field.key, it) },
            enabled,
            error,
        )
    }
}

@Composable
private fun FieldFor(
    f: FormField,
    v: FieldValue,
    set: (FieldValue) -> Unit,
    enabled: Boolean,
    error: String?,
) {
    when (f.kind) {
        FieldKind.TEXT ->
            FormTextField(
                f.label,
                (v as FieldValue.Text).text,
                { set(FieldValue.Text(it)) },
                enabled = enabled,
                placeholder = f.placeholder,
                maxLength = f.maxLength,
                error = error,
            )
        FieldKind.NUMBER -> {
            val n = (v as FieldValue.Number).number
            if (f.typedNumber)
                FormTextField(
                    f.label,
                    n?.toString().orEmpty(),
                    { set(FieldValue.Number(it.toIntOrNull())) },
                    enabled = enabled,
                    placeholder = f.placeholder,
                    maxLength = f.maxLength ?: NUMBER_DIGITS,
                    number = true,
                    error = error,
                )
            else
                StepperField(
                    label = f.label,
                    value = n ?: f.min ?: 0,
                    onChange = { set(FieldValue.Number(it)) },
                    min = f.min,
                    max = f.max,
                    step = f.step ?: 1,
                    enabled = enabled,
                )
        }
        FieldKind.TIME ->
            TimeField(
                label = f.label,
                value = (v as FieldValue.Time).minutes,
                onChange = { set(FieldValue.Time(it)) },
                enabled = enabled,
            )
        FieldKind.DATE ->
            DateField(
                label = f.label,
                value = (v as FieldValue.Date).date,
                onChange = { set(FieldValue.Date(it)) },
                enabled = enabled,
            )
        FieldKind.CHOICE ->
            ChoiceField(
                f.label,
                f.options,
                (v as FieldValue.Choice).value,
                { set(FieldValue.Choice(it)) },
                enabled = enabled,
                error = error,
                // a choice sharing a row has no room for its segments: it is the page's select
                menu = f.options.size > CHOICE_SEGMENTS || f.group != null,
            )
        FieldKind.CHECK ->
            CheckField(
                f.label,
                (v as FieldValue.Check).on,
                { set(FieldValue.Check(it)) },
                enabled = enabled,
                error = error,
            )
        FieldKind.DAYS ->
            DayChipsField(
                f.label,
                f.options,
                (v as FieldValue.Days).days,
                { set(FieldValue.Days(it)) },
                enabled = enabled,
                error = error,
            )
    }
}

/**
 * The details element's summary (.ld-details summary): its disclosure mark and words, semibold,
 * ink2.
 */
@Composable
private fun FoldSummary(label: String, open: Boolean, onToggle: () -> Unit) {
    val look = LocalFormKitLook.current
    Row(
        Modifier.padding(top = 10.dp)
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .tapTarget(Role.Button, onClick = onToggle)
            .semantics {
                heading()
                // TalkBack says expanded or collapsed from the action offered
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
            },
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(if (open) "▾" else "▸", style = look.text, color = look.ink2)
        Text(label, style = look.text, color = look.ink2, fontWeight = FontWeight(650))
    }
}

/** A typed number's longest entry, so it stays an Int. */
private const val NUMBER_DIGITS = 9
