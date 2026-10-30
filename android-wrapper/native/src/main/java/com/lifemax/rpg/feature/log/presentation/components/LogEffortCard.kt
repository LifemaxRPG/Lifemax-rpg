package com.lifemax.rpg.feature.log.presentation.components

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.lifemax.rpg.core.designsystem.components.SectionLabel
import com.lifemax.rpg.core.designsystem.components.SegmentedChoice
import com.lifemax.rpg.core.designsystem.components.SurfaceCard
import com.lifemax.rpg.feature.log.domain.LogQuality

/** How hard it was: the page's effort levels and their multipliers. */
@Composable
internal fun LogEffortCard(
    qualities: List<LogQuality>,
    selected: String,
    onSelect: (String) -> Unit,
) {
    SurfaceCard {
        SectionLabel("Effort")
        SegmentedChoice(
            qualities.map { Triple(it.value, it.label, it.hint) },
            selected,
            onSelect,
            Modifier.padding(top = 10.dp),
        )
    }
}
