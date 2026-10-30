package com.lifemax.rpg.feature.character.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
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
internal fun CharacterAttributesCard(
    p: CharacterProjection,
    ui: CharacterSelection,
    onSort: (String) -> Unit,
    onAction: (String, String) -> Unit,
) {
    SurfaceCard {
        Text("Eight core attributes", fontSize = 17.sp, fontWeight = FontWeight.Bold)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            items(
                listOf("level" to "Level", "attention" to "Needs attention", "week" to "This week")
            ) { (mode, label) ->
                FilterChip(ui.sort == mode, { onSort(mode) }, { Text(label) })
            }
        }
        val attrs =
            when (ui.sort) {
                "attention" ->
                    p.attributes.sortedWith(
                        compareBy<AttributeProjection> { it.rank }.thenByDescending { it.total }
                    )
                "week" ->
                    p.attributes.sortedWith(
                        compareByDescending<AttributeProjection> { it.week }
                            .thenByDescending { it.total }
                    )
                else -> p.attributes.sortedByDescending { it.total }
            }
        attrs.chunked(2).forEach { pair ->
            Row(
                Modifier.fillMaxWidth().padding(top = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                pair.forEach { a ->
                    Column(
                        Modifier.weight(1f)
                            .clip(RoundedCornerShape(14.dp))
                            .background(tokenColor(p.tokens, "panel2"))
                            .border(
                                1.dp,
                                MaterialTheme.colorScheme.outline.copy(alpha = .5f),
                                RoundedCornerShape(14.dp),
                            )
                            .drawBehind {
                                drawRect(
                                    Color(a.color.toInt()),
                                    size = Size(3.dp.toPx(), size.height),
                                )
                            }
                            .clickable { onAction("attribute", a.id) }
                            .padding(12.dp)
                    ) {
                        Row(
                            Modifier.fillMaxWidth().padding(bottom = 7.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            PixelIcon(
                                attributeIcon(a.id),
                                Color(a.color.toInt()),
                                Modifier.size(20.dp),
                            )
                            Spacer(Modifier.weight(1f))
                            Text(
                                "LV ${a.level}",
                                color = Color(a.color.toInt()),
                                fontWeight = FontWeight.Black,
                                fontSize = 15.sp,
                            )
                        }
                        Text(
                            a.short + if (a.focus) " ★" else "",
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 14.sp,
                            modifier = Modifier.padding(bottom = 1.dp),
                        )
                        ProgressBar(
                            a.pct / 100,
                            barFade(Color(a.color.toInt())),
                            Modifier.padding(top = 8.dp),
                        )
                        Row(
                            Modifier.fillMaxWidth().padding(top = 7.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(
                                "${a.into.toLong()} / ${a.needed.toLong()}",
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                if (a.week > 0) "+${a.week.toLong()} wk" else "—",
                                fontSize = 10.sp,
                                color =
                                    if (a.week > 0) tokenColor(p.tokens, "good")
                                    else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Text(
                            a.status,
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 7.dp),
                        )
                    }
                }
            }
        }
        Text(
            "★ marks your class focus. Customize how you earn XP in Settings.",
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 12.dp),
        )
    }
}

/** The web's pixel icon for each attribute; the page projects only its colour. */
internal fun attributeIcon(id: String) =
    when (id) {
        "physique" -> "dumbbell"
        "endurance" -> "heart"
        "intelligence" -> "book"
        "business" -> "briefcase"
        "social" -> "chat"
        "discipline" -> "target"
        "adventure" -> "mountain"
        else -> "moon"
    }
