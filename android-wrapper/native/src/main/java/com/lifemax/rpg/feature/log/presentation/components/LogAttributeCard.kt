package com.lifemax.rpg.feature.log.presentation.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.lifemax.rpg.core.designsystem.components.ChoicePill
import com.lifemax.rpg.core.designsystem.components.PixelIcon
import com.lifemax.rpg.core.designsystem.components.SectionLabel
import com.lifemax.rpg.core.designsystem.components.SurfaceCard
import com.lifemax.rpg.feature.log.domain.LogProjection

/** Which attribute the XP goes to, and optionally a second one that takes 30 % of it. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun LogAttributeCard(
    p: LogProjection,
    primary: String,
    secondary: String,
    onPrimary: (String) -> Unit,
    onSecondary: (String) -> Unit,
) {
    SurfaceCard {
        SectionLabel("Trains")
        FlowRow(
            Modifier.padding(top = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            p.attributes.forEach { a ->
                val color = Color(a.color.toInt())
                ChoicePill(a.short, a.id == primary, { onPrimary(a.id) }, tint = color) {
                    PixelIcon(a.icon, color, Modifier.size(14.dp))
                }
            }
        }
        SectionLabel("Also trains (takes 30 %)", Modifier.padding(top = 18.dp))
        FlowRow(
            Modifier.padding(top = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ChoicePill("None", secondary.isEmpty(), { onSecondary("") })
            p.attributes
                .filter { it.id != primary }
                .forEach { a ->
                    val color = Color(a.color.toInt())
                    ChoicePill(a.short, a.id == secondary, { onSecondary(a.id) }, tint = color) {
                        PixelIcon(a.icon, color, Modifier.size(14.dp))
                    }
                }
        }
    }
}
