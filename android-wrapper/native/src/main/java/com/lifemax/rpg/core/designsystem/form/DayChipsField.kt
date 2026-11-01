package com.lifemax.rpg.core.designsystem.form

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * The page's day chips (.ld-dchips): the days in a row of seven equal chips, at most 330 dp wide,
 * each 40 dp tall and 11 dp round. A day off is a faint wash with an edge; a day on is the page's
 * green with dark words. The days and their order are the page's options (its DOW, Sunday first);
 * each chip is a switch named by its label. [error] is the page's words when it refused the field
 * ("Pick at least one day").
 */
@Composable
fun DayChipsField(
    label: String,
    options: List<FormOption>,
    value: Set<Int>,
    onChange: (Set<Int>) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    error: String? = null,
) {
    val look = LocalFormKitLook.current
    val shape = RoundedCornerShape(FormKitSize.fieldRadius)
    Column(modifier.alpha(if (enabled) 1f else look.disabledAlpha)) {
        // .ld-lbl sits 12 px above and 6 px over the chips
        FormKitLabel(label, Modifier.padding(top = 2.dp))
        Row(
            Modifier.padding(top = 6.dp).widthIn(max = 330.dp).fillMaxWidth().semantics {
                contentDescription = label
                if (error != null) error(error)
            },
            horizontalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            options.forEach { o ->
                val day = o.value.toInt()
                val on = day in value
                Box(
                    Modifier.weight(1f)
                        .height(40.dp)
                        .clip(shape)
                        .background(if (on) look.on else look.chipFill, shape)
                        .then(
                            if (on) Modifier
                            else
                                Modifier.border(
                                    1.dp,
                                    if (error != null) look.error else look.edge,
                                    shape,
                                )
                        )
                        .toggleable(on, enabled = enabled, role = Role.Checkbox) {
                            onChange(if (it) value + day else value - day)
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        o.label,
                        style = look.text,
                        color = if (on) look.onInk else look.ink2,
                        fontSize = 13.1.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}
