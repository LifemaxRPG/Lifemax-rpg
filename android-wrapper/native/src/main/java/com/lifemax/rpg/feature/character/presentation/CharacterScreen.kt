package com.lifemax.rpg.feature.character.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.*
import androidx.compose.ui.unit.dp
import com.lifemax.rpg.core.designsystem.LifeMaxTheme
import com.lifemax.rpg.feature.character.domain.*
import com.lifemax.rpg.feature.character.presentation.components.*
import kotlin.math.*

@Composable
fun CharacterRoute(
    vm: CharacterViewModel,
    topInsetDp: Float = 64f,
    bottomInsetDp: Float = 72f,
    onAction: (String, String) -> Unit,
) {
    val data by vm.projection.collectAsState()
    val ui by vm.selection.collectAsState()
    data?.let {
        CharacterScreen(
            it,
            ui,
            vm::select,
            vm::compare,
            vm::sort,
            vm::skills,
            topInsetDp,
            bottomInsetDp,
            onAction,
        )
    }
}

@Composable
fun CharacterScreen(
    p: CharacterProjection,
    ui: CharacterSelection,
    onSelect: (String) -> Unit,
    onCompare: (Boolean) -> Unit,
    onSort: (String) -> Unit,
    onSkills: () -> Unit,
    topInsetDp: Float = 64f,
    bottomInsetDp: Float = 72f,
    onAction: (String, String) -> Unit,
) {
    val selected =
        p.attributes.find { it.id == ui.selected }
            ?: p.attributes.maxBy { it.level }.takeIf { it.level > 1 }
            ?: p.attributes.firstOrNull { it.focus }
            ?: p.attributes.first()
    LifeMaxTheme(p.tokens, p.light) {
        Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            Spacer(Modifier.height(topInsetDp.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outline)
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
            HorizontalDivider(color = MaterialTheme.colorScheme.outline)
            Spacer(Modifier.height(bottomInsetDp.dp))
        }
    }
}
