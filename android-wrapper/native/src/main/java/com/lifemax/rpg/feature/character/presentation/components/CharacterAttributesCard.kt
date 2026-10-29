package com.lifemax.rpg.feature.character.presentation.components

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
    CharacterCard {
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
                            .border(1.dp, p.tokenColor("line"), RoundedCornerShape(12.dp))
                            .clickable { onAction("attribute", a.id) }
                            .padding(12.dp)
                    ) {
                        Text(
                            "LV ${a.level}",
                            color = Color(a.color.toInt()),
                            fontWeight = FontWeight.Bold,
                        )
                        Text(a.short + if (a.focus) " ★" else "", fontWeight = FontWeight.Bold)
                        CharacterProgress(a.pct, Color(a.color.toInt()))
                        Text(
                            "${a.into.toLong()} / ${a.needed.toLong()}",
                            fontSize = 10.sp,
                            color = p.tokenColor("muted"),
                        )
                        Text(
                            "+${a.week.toLong()} wk",
                            fontSize = 10.sp,
                            color = p.tokenColor("good"),
                        )
                    }
                }
            }
        }
        Text(
            "★ marks your class focus. Customize how you earn XP in Settings.",
            fontSize = 11.sp,
            color = p.tokenColor("muted"),
            modifier = Modifier.padding(top = 12.dp),
        )
    }
}
