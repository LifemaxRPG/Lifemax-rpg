// replaced at merge by TimeField.kt/DateField.kt/StepperField.kt
package com.lifemax.rpg.core.designsystem.form

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.unit.dp

/*
 * Temporary stand-ins so the form kit compiles before the pickers arrive: another agent builds TimeField,
 * DateField and StepperField in this package with these signatures. This whole file is deleted at that merge.
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
) = StandIn(label, text ?: "%d:%02d".format(value / 60, value % 60), modifier, enabled)

@Composable
fun DateField(
    label: String,
    value: String,
    onChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    text: String? = null,
) = StandIn(label, text ?: value, modifier, enabled)

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
) = StandIn(label, value.toString(), modifier, enabled)

@Composable
private fun StandIn(label: String, shown: String, modifier: Modifier, enabled: Boolean) {
    val look = LocalFormKitLook.current
    val shape = RoundedCornerShape(FormKitSize.fieldRadius)
    Column(modifier.alpha(if (enabled) 1f else look.disabledAlpha)) {
        FormKitLabel(label)
        Box(
            Modifier.padding(top = 5.dp)
                .fillMaxWidth()
                .heightIn(min = FormKitSize.fieldHeight)
                .background(look.well, shape)
                .border(1.dp, look.edge, shape)
                .padding(horizontal = 12.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            Text(shown, style = look.text, color = look.ink)
        }
    }
}
