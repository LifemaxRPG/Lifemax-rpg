package com.lifemax.rpg.feature.character.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import com.lifemax.rpg.core.designsystem.components.PixelIcon
import com.lifemax.rpg.core.designsystem.components.ProgressBar
import com.lifemax.rpg.core.designsystem.components.SurfaceCard
import com.lifemax.rpg.core.designsystem.components.barFade
import com.lifemax.rpg.core.designsystem.components.tokenColor
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
    SurfaceCard {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Build shape", fontSize = 17.sp, fontWeight = FontWeight.Bold)
            Row(
                Modifier.clip(RoundedCornerShape(50))
                    .background(MaterialTheme.colorScheme.background)
                    .border(
                        1.dp,
                        MaterialTheme.colorScheme.outline.copy(alpha = .5f),
                        RoundedCornerShape(50),
                    )
                    .padding(3.dp)
            ) {
                for ((compare, label) in listOf(false to "Now", true to "vs last week")) {
                    Text(
                        label,
                        Modifier.clip(RoundedCornerShape(50))
                            .background(
                                if (ui.compare == compare) tokenColor(p.tokens, "panel2")
                                else Color.Transparent
                            )
                            .clickable { onCompare(compare) }
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        color =
                            if (ui.compare == compare) MaterialTheme.colorScheme.onSurface
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        Text(
            p.archetype,
            Modifier.padding(top = 10.dp)
                .clip(RoundedCornerShape(50))
                .background(Color(0xffc58bff).copy(alpha = .14f))
                .border(1.dp, Color(0xffc58bff).copy(alpha = .38f), RoundedCornerShape(50))
                .padding(horizontal = 10.dp, vertical = 5.dp),
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
        )
        NativeRadar(p.attributes, p.cap, ui.compare, selected.id, onSelect)
        if (ui.compare)
            Text(
                "Dashed outline: last week",
                Modifier.fillMaxWidth().padding(bottom = 6.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 11.5.sp,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
        HorizontalDivider(
            Modifier.padding(top = 10.dp, bottom = 15.dp),
            color = MaterialTheme.colorScheme.outline.copy(alpha = .5f),
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                Modifier.size(46.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color(selected.color.toInt()).copy(alpha = .16f))
                    .border(
                        1.dp,
                        Color(selected.color.toInt()).copy(alpha = .45f),
                        RoundedCornerShape(14.dp),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                PixelIcon(
                    attributeIcon(selected.id),
                    Color(selected.color.toInt()),
                    Modifier.size(22.dp),
                )
            }
            Column(Modifier.weight(1f)) {
                Text(selected.name, fontWeight = FontWeight.ExtraBold, fontSize = 15.5.sp)
                Text(
                    "${selected.into.toLong()} / ${selected.needed.toLong()} XP to next · ${selected.total.toLong()} total",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 11.sp,
                )
            }
            Text(
                "LV ${selected.level}",
                fontSize = 21.sp,
                fontWeight = FontWeight.Black,
                color = Color(selected.color.toInt()),
            )
        }
        ProgressBar(
            selected.pct / 100,
            barFade(Color(selected.color.toInt())),
            Modifier.padding(top = 8.dp),
        )
        Row(
            Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            for ((label, value) in
                listOf(
                    "THIS WEEK" to
                        if (selected.week > 0) "+${selected.week.toLong()} XP" else "None",
                    "LAST TRAINED" to selected.last,
                )) {
                Column(
                    Modifier.weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(tokenColor(p.tokens, "panel2"))
                        .border(
                            1.dp,
                            MaterialTheme.colorScheme.outline.copy(alpha = .5f),
                            RoundedCornerShape(12.dp),
                        )
                        .padding(horizontal = 10.dp, vertical = 9.dp)
                ) {
                    Text(
                        label,
                        fontSize = 8.5.sp,
                        letterSpacing = 1.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        value,
                        Modifier.padding(top = 4.dp),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color =
                            if (label == "THIS WEEK" && selected.week > 0)
                                tokenColor(p.tokens, "good")
                            else MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }
        Text(selected.status, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
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
        Button(
            onClick = { onAction("attribute", selected.id) },
            modifier = Modifier.fillMaxWidth().padding(top = 14.dp),
            shape = RoundedCornerShape(10.dp),
        ) {
            Text("Log ${selected.short} activity ›")
        }
    }
}
