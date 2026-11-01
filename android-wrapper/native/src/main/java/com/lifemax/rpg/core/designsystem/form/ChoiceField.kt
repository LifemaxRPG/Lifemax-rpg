package com.lifemax.rpg.core.designsystem.form

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lifemax.rpg.core.designsystem.components.tapTarget

/**
 * One choice of several, under its label. Up to [CHOICE_SEGMENTS] options are the page's segmented
 * control (.ld-seg): a dark track holding every option, the picked one washed blue; more than that
 * are the page's select, a box showing the picked option that opens a menu of them all (plan §3.7),
 * as is a choice asked to be a [menu] because it has no room for segments. [error] is the page's
 * words when it refused the field.
 */
@Composable
fun ChoiceField(
    label: String,
    options: List<FormOption>,
    value: String,
    onChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    error: String? = null,
    menu: Boolean = options.size > CHOICE_SEGMENTS,
) {
    val look = LocalFormKitLook.current
    Column(modifier.alpha(if (enabled) 1f else look.disabledAlpha)) {
        FormKitLabel(label)
        if (!menu) Segments(label, options, value, onChange, enabled, error)
        else Menu(label, options, value, onChange, enabled, error)
    }
}

/** The page's .ld-seg: a 3 dp padded track, 12 dp round, its buttons 38 dp tall and 9 dp round. */
@Composable
private fun Segments(
    label: String,
    options: List<FormOption>,
    value: String,
    onChange: (String) -> Unit,
    enabled: Boolean,
    error: String?,
) {
    val look = LocalFormKitLook.current
    val track = RoundedCornerShape(12.dp)
    val button = RoundedCornerShape(9.dp)
    Row(
        Modifier.padding(top = 5.dp)
            .fillMaxWidth()
            .background(look.well, track)
            .border(1.dp, if (error != null) look.error else look.segEdge, track)
            .padding(3.dp)
            .selectableGroup()
            .semantics {
                contentDescription = label
                if (error != null) error(error)
            },
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        options.forEach { o ->
            val on = o.value == value
            Box(
                Modifier.weight(1f)
                    .heightIn(min = 42.dp)
                    .clip(button)
                    .then(
                        if (on)
                            Modifier.background(look.segOn, button)
                                .border(1.dp, look.segOnEdge, button)
                        else Modifier
                    )
                    .selectable(on, enabled = enabled, role = Role.RadioButton) {
                        if (!on) onChange(o.value)
                    }
                    .padding(horizontal = 6.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    o.label,
                    style = look.text,
                    color = if (on) look.ink else look.ink3,
                    fontSize = 13.4.sp,
                    fontWeight = FontWeight(650),
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/**
 * The page's select: the input's well showing the picked option and a chevron, opening the list.
 */
@Composable
private fun Menu(
    label: String,
    options: List<FormOption>,
    value: String,
    onChange: (String) -> Unit,
    enabled: Boolean,
    error: String?,
) {
    val look = LocalFormKitLook.current
    val shape = RoundedCornerShape(FormKitSize.fieldRadius)
    var open by remember { mutableStateOf(false) }
    val picked = options.firstOrNull { it.value == value }?.label.orEmpty()
    Box(Modifier.padding(top = 5.dp)) {
        Row(
            Modifier.fillMaxWidth()
                .heightIn(min = FormKitSize.fieldHeight)
                .clip(shape)
                .background(look.well, shape)
                .border(1.dp, if (error != null) look.error else look.edge, shape)
                .then(
                    if (enabled) Modifier.tapTarget(Role.DropdownList) { open = true } else Modifier
                )
                .semantics {
                    contentDescription = label
                    stateDescription = picked
                    if (error != null) error(error)
                }
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                picked,
                Modifier.weight(1f),
                style = look.text,
                color = look.ink,
                fontSize = 15.2.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text("▾", style = look.text, color = look.ink3, fontSize = 13.sp)
        }
        DropdownMenu(
            expanded = open,
            onDismissRequest = { open = false },
            shape = shape,
            containerColor = look.well,
            border = BorderStroke(1.dp, look.edge),
        ) {
            options.forEach { o ->
                DropdownMenuItem(
                    text = {
                        Text(
                            o.label,
                            style = look.text,
                            color = if (o.value == value) look.ink else look.ink2,
                            fontWeight = if (o.value == value) FontWeight.SemiBold else null,
                        )
                    },
                    onClick = {
                        open = false
                        if (o.value != value) onChange(o.value)
                    },
                )
            }
        }
    }
}

/** The most options drawn as segments; more become a menu (plan §3.7). */
internal const val CHOICE_SEGMENTS = 4
