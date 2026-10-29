package com.lifemax.rpg.feature.character.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lifemax.rpg.feature.character.domain.*
import com.lifemax.rpg.feature.character.presentation.CharacterSelection
import kotlin.math.*

@Composable
internal fun CharacterBuildCard(
    p: CharacterProjection,
    ui: CharacterSelection,
    selected: AttributeProjection,
    onSelect: (String) -> Unit,
    onCompare: (Boolean) -> Unit,
    onAction: (String, String) -> Unit,
) {
    CharacterCard {
        Text("Build shape", fontSize = 17.sp, fontWeight = FontWeight.Bold)
        Row {
            FilterChip(!ui.compare, { onCompare(false) }, { Text("Now") })
            Spacer(Modifier.width(8.dp))
            FilterChip(ui.compare, { onCompare(true) }, { Text("vs last week") })
        }
        Text(p.archetype, color = p.tokenColor("accent"), fontSize = 12.sp)
        NativeRadar(p.attributes, p.cap, ui.compare, selected.id, onSelect)
        Text(selected.name, fontWeight = FontWeight.Bold)
        Text(
            "LV ${selected.level} · ${selected.into.toLong()} / ${selected.needed.toLong()} XP",
            color = p.tokenColor("muted"),
            fontSize = 12.sp,
        )
        CharacterProgress(selected.pct, Color(selected.color.toInt()))
        Text(
            "THIS WEEK  +${selected.week.toLong()} XP   ·   LAST TRAINED  ${selected.last}",
            fontSize = 10.sp,
            color = p.tokenColor("muted"),
            modifier = Modifier.padding(top = 10.dp),
        )
        Text(selected.status, color = p.tokenColor("muted"), fontSize = 12.sp)
        Row(
            Modifier.fillMaxWidth().height(56.dp).padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            val maximum = selected.days.maxOrNull()?.coerceAtLeast(1f) ?: 1f
            selected.days.forEach { value ->
                Box(
                    Modifier.weight(1f).fillMaxHeight(),
                    contentAlignment = Alignment.BottomCenter,
                ) {
                    Box(
                        Modifier.fillMaxWidth()
                            .fillMaxHeight((value / maximum).coerceAtLeast(.08f))
                            .clip(RoundedCornerShape(3.dp))
                            .background(
                                Color(selected.color.toInt())
                                    .copy(alpha = if (value > 0) .8f else .22f)
                            )
                    )
                }
            }
        }
        TextButton(onClick = { onAction("attribute", selected.id) }) {
            Text("Log ${selected.short} activity ›")
        }
    }
}
