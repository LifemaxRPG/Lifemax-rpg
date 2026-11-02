package com.lifemax.rpg.feature.food.presentation.sheet

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lifemax.rpg.feature.food.domain.SheetSlot
import com.lifemax.rpg.feature.food.presentation.BlueAccent
import com.lifemax.rpg.feature.food.presentation.FoodContext
import com.lifemax.rpg.feature.food.presentation.slotColor

/**
 * A text box in the sheet's look: [hint] shows while it is empty; a [label] (the page's label of
 * its form) shows above it, small and upper case as the page's .fv2-form label is, and names it.
 */
@Composable
internal fun SheetTextField(
    c: FoodContext,
    value: String,
    onChange: (String) -> Unit,
    hint: String,
    modifier: Modifier = Modifier,
    numeric: Boolean = false,
    focus: FocusRequester? = null,
    label: String? = null,
    small: String? = null,
) {
    if (label == null) {
        SheetBox(c, value, onChange, hint, hint, modifier, numeric, focus)
        return
    }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            if (small == null) label.uppercase() else "${label.uppercase()} $small",
            color = c.muted,
            fontSize = 11.sp,
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = 1.1.sp,
            modifier = Modifier.clearAndSetSemantics {},
        )
        val name = if (small == null) label else "$label $small"
        SheetBox(c, value, onChange, hint, name, Modifier.fillMaxWidth(), numeric, focus)
    }
}

@Composable
private fun SheetBox(
    c: FoodContext,
    value: String,
    onChange: (String) -> Unit,
    hint: String,
    name: String,
    modifier: Modifier,
    numeric: Boolean,
    focus: FocusRequester?,
) {
    val shape = RoundedCornerShape(14.dp)
    val style = TextStyle(color = c.fg, fontSize = 16.sp, fontWeight = FontWeight.Medium)
    BasicTextField(
        value = value,
        onValueChange = onChange,
        singleLine = true,
        textStyle = style,
        cursorBrush = SolidColor(BlueAccent),
        keyboardOptions =
            KeyboardOptions(
                keyboardType = if (numeric) KeyboardType.Decimal else KeyboardType.Text
            ),
        modifier =
            modifier
                .let { if (focus != null) it.focusRequester(focus) else it }
                .clip(shape)
                .background(c.sunken)
                .border(1.dp, c.panelBorder, shape)
                .semantics { contentDescription = name },
        decorationBox = { inner ->
            Box(Modifier.heightIn(min = 48.dp).padding(horizontal = 14.dp), Alignment.CenterStart) {
                if (value.isEmpty()) Text(hint, color = c.subtle, style = style)
                inner()
            }
        },
    )
}

/**
 * A rounded button that is either selected or not (a tab, a serving, a meal), which TalkBack reads
 * as a choice; with [choice] false it is a plain button (a multiple, a preset), never "selected".
 * [fill] stretches it across the room it is given.
 */
@Composable
internal fun SheetChip(
    c: FoodContext,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    sub: String? = null,
    tint: Color = BlueAccent,
    choice: Boolean = true,
    fill: Boolean = false,
) {
    val shape = RoundedCornerShape(12.dp)
    Column(
        Modifier.let { if (fill) it.fillMaxWidth() else it }
            .heightIn(min = 48.dp)
            .clip(shape)
            .background(if (selected) tint.copy(alpha = 0.18f) else c.sunken)
            .border(1.dp, if (selected) tint else c.panelBorder, shape)
            .semantics {
                if (choice) {
                    role = Role.RadioButton
                    this.selected = selected
                } else role = Role.Button
            }
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            label,
            color = if (selected) c.fg else c.muted,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
        )
        if (sub != null) Text(sub, color = c.subtle, fontSize = 11.sp)
    }
}

/** The four meals as pills, each in its own colour. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun SheetSlotPills(
    c: FoodContext,
    slots: List<SheetSlot>,
    chosen: String,
    onPick: (String) -> Unit,
) {
    FlowRow(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        slots.forEach { s ->
            SheetChip(c, s.label, s.id == chosen, { onPick(s.id) }, tint = slotColor(s.id))
        }
    }
}

/**
 * A view of the sheet: its [body], which scrolls, over its [foot] (the page's .fv2-foot: the main
 * button and its neighbours), which stays in sight above the bottom bar or the keyboard however
 * long the body is.
 */
@Composable
internal fun SheetScaffold(
    modifier: Modifier,
    foot: @Composable () -> Unit,
    body: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(
            Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            content = body,
        )
        foot()
    }
}

/** The sheet's one main button. */
@Composable
internal fun SheetPrimaryButton(label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(16.dp)
    Box(
        modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .clip(shape)
            .background(BlueAccent)
            .semantics { role = Role.Button }
            .clickable(onClick = onClick)
            .padding(PaddingValues(horizontal = 16.dp, vertical = 12.dp)),
        Alignment.Center,
    ) {
        Text(label, color = Color(0xFF0B1020), fontSize = 16.sp, fontWeight = FontWeight.Bold)
    }
}

/** A quiet button for the sheet's side actions; [ink] is the page's --bad for Remove. */
@Composable
internal fun RowScopeGhost(
    c: FoodContext,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier,
    ink: Color = c.muted,
) {
    val shape = RoundedCornerShape(14.dp)
    Box(
        modifier
            .heightIn(min = 48.dp)
            .clip(shape)
            .border(1.dp, c.panelBorder, shape)
            .semantics { role = Role.Button }
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp),
        Alignment.Center,
    ) {
        Text(label, color = ink, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
    }
}

/** The page's --bad (.fv2-foot button.danger), dark or light. */
internal fun badColor(c: FoodContext): Color = if (c.light) Color(0xFFDC2626) else Color(0xFFF07178)

/** Two ghost buttons side by side. */
@Composable
internal fun GhostRow(
    c: FoodContext,
    first: Pair<String, () -> Unit>,
    second: Pair<String, () -> Unit>,
) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        RowScopeGhost(c, first.first, first.second, Modifier.weight(1f))
        RowScopeGhost(c, second.first, second.second, Modifier.weight(1f))
    }
}
