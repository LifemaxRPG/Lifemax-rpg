package com.lifemax.rpg.feature.log.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lifemax.rpg.core.designsystem.components.PixelIcon
import com.lifemax.rpg.core.designsystem.components.SectionLabel
import com.lifemax.rpg.feature.log.domain.LogPreset
import com.lifemax.rpg.feature.log.domain.LogProjection

/** The activity presets, three to a row, each in the colour of the attribute it trains. */
@Composable
internal fun LogPresetGrid(p: LogProjection, selected: String, onChoose: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SectionLabel("Activity")
        p.presets.chunked(COLUMNS).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { preset ->
                    PresetTile(p, preset, preset.id == selected, Modifier.weight(1f)) {
                        onChoose(preset.id)
                    }
                }
                repeat(COLUMNS - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun PresetTile(
    p: LogProjection,
    preset: LogPreset,
    on: Boolean,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    val color =
        p.attribute(preset.primary)?.let { Color(it.color.toInt()) }
            ?: MaterialTheme.colorScheme.primary
    val shape = RoundedCornerShape(14.dp)
    Column(
        modifier
            .heightIn(min = 92.dp)
            .clip(shape)
            .background(if (on) color.copy(alpha = .16f) else MaterialTheme.colorScheme.surface)
            .border(
                if (on) 1.5.dp else 1.dp,
                if (on) color else MaterialTheme.colorScheme.outline.copy(alpha = .3f),
                shape,
            )
            .semantics { this.selected = on }
            .clickable(role = Role.RadioButton, onClick = onClick)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        PixelIcon(preset.icon, color, Modifier.size(22.dp))
        Text(
            shortName(preset.name),
            Modifier.fillMaxWidth(),
            color =
                if (on) MaterialTheme.colorScheme.onSurface
                else MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 13.sp,
            lineHeight = 16.sp,
            fontWeight = if (on) FontWeight.Bold else FontWeight.Medium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * "Focused reading / study" reads as "Focused reading" on a tile; the full name is in the preview's
 * skill line.
 */
internal fun shortName(name: String) = name.substringBefore(" / ").trim()

private const val COLUMNS = 3
