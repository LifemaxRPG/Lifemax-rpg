package com.lifemax.rpg.feature.character.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lifemax.rpg.feature.character.domain.CharacterProjection

@Composable
internal fun CharacterHeader(p: CharacterProjection, onAction: (String, String) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CharacterAvatar(p, Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)), 18)
        Text(
            p.name,
            Modifier.weight(1f),
            fontWeight = FontWeight.Bold,
            fontSize = 15.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Button(
            onClick = { onAction("navigate", "log") },
            shape = RoundedCornerShape(9.dp),
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
        ) {
            Text("＋ Log activity", fontWeight = FontWeight.Bold, fontSize = 14.sp)
        }
        Box(
            Modifier.size(40.dp)
                .clip(RoundedCornerShape(9.dp))
                .background(p.tokenColor("panel2"))
                .border(1.dp, p.tokenColor("line"), RoundedCornerShape(9.dp))
                .clickable(role = Role.Button) { onAction("navigate", "settings") }
                .semantics { contentDescription = "Settings" },
            contentAlignment = Alignment.Center,
        ) {
            CharacterPixelIcon("gear", p.tokenColor("text"), Modifier.size(18.dp))
        }
    }
}

private fun navIcon(id: String) =
    when (id) {
        "dashboard",
        "overview" -> "grid"
        "character" -> "person"
        "pet",
        "cat" -> "paw"
        "log" -> "plus"
        "quests" -> "flag"
        "plan",
        "workout" -> "list"
        "library" -> "book"
        "food" -> "heart"
        "money" -> "coin"
        "journal" -> "book"
        "director",
        "lifeDirector",
        "life-director" -> "dial"
        "settings" -> "gear"
        else -> "list"
    }

@Composable
internal fun CharacterNavigation(p: CharacterProjection, onAction: (String, String) -> Unit) {
    LazyRow(
        Modifier.fillMaxWidth().background(p.tokenColor("bg")),
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(p.tabs, key = { it.first }) { (id, label) ->
            val active = id == "character"
            val color = if (active) p.tokenColor("accent") else p.tokenColor("muted")
            Column(
                Modifier.clip(RoundedCornerShape(14.dp))
                    .background(if (active) color.copy(alpha = .16f) else Color.Transparent)
                    .clickable(role = Role.Tab) { if (!active) onAction("navigate", id) }
                    .semantics { selected = active }
                    .padding(horizontal = 7.dp, vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                CharacterPixelIcon(navIcon(id), color, Modifier.size(18.dp))
                Text(
                    label,
                    Modifier.padding(top = 4.dp),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = color,
                )
            }
        }
    }
}
