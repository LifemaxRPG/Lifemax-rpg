package com.lifemax.rpg.feature.character.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.lifemax.rpg.core.designsystem.LifeMaxTheme
import com.lifemax.rpg.feature.character.domain.*
import com.lifemax.rpg.feature.character.presentation.components.*
import kotlin.math.*

@Composable
fun CharacterRoute(vm: CharacterViewModel, onAction: (String, String) -> Unit) {
    val data by vm.projection.collectAsState()
    val ui by vm.selection.collectAsState()
    data?.let { CharacterScreen(it, ui, vm::select, vm::compare, vm::sort, vm::skills, onAction) }
}

@Composable
fun CharacterScreen(
    p: CharacterProjection,
    ui: CharacterSelection,
    onSelect: (String) -> Unit,
    onCompare: (Boolean) -> Unit,
    onSort: (String) -> Unit,
    onSkills: () -> Unit,
    onAction: (String, String) -> Unit,
) {
    val selected =
        p.attributes.find { it.id == ui.selected }
            ?: p.attributes.maxBy { it.level }.takeIf { it.level > 1 }
            ?: p.attributes.firstOrNull { it.focus }
            ?: p.attributes.first()
    LifeMaxTheme(p.tokens, p.light) {
        Column(Modifier.fillMaxSize().background(p.tokenColor("bg"))) {
            Row(
                Modifier.fillMaxWidth().padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(p.name, Modifier.weight(1f), fontWeight = FontWeight.Bold, maxLines = 1)
                TextButton(onClick = { onAction("navigate", "log") }) { Text("＋ Log activity") }
                TextButton(onClick = { onAction("navigate", "settings") }) { Text("⚙") }
            }
            HorizontalDivider(color = p.tokenColor("line"))
            LazyColumn(
                Modifier.weight(1f),
                contentPadding = PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                item { CharacterHeroCard(p, onAction) }
                item { CharacterBuildCard(p, ui, selected, onSelect, onCompare, onAction) }
                item { CharacterAttributesCard(p, ui, onSort, onAction) }
                item { CharacterSkillsCard(p, ui, onSkills) }
                item { CharacterBenchmarksCard(p, onAction) }
            }
            HorizontalDivider(color = p.tokenColor("line"))
            LazyRow(
                Modifier.fillMaxWidth().background(p.tokenColor("panel")),
                contentPadding = PaddingValues(6.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                items(p.tabs) { (id, label) ->
                    TextButton(onClick = { if (id != "character") onAction("navigate", id) }) {
                        Text(
                            label,
                            color =
                                if (id == "character") p.tokenColor("accent")
                                else p.tokenColor("muted"),
                            fontWeight =
                                if (id == "character") FontWeight.Bold else FontWeight.Normal,
                        )
                    }
                }
            }
        }
    }
}
