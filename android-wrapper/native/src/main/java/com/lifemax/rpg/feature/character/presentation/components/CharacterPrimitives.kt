package com.lifemax.rpg.feature.character.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.*
import androidx.compose.ui.unit.dp
import com.lifemax.rpg.feature.character.domain.*
import kotlin.math.*

internal fun CharacterProjection.tokenColor(key: String) =
    Color((tokens[key] ?: 0xffeef1f6).toInt())

@Composable
internal fun CharacterCard(
    modifier: Modifier = Modifier,
    padding: PaddingValues = PaddingValues(16.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(
                1.dp,
                MaterialTheme.colorScheme.outline.copy(alpha = .3f),
                RoundedCornerShape(14.dp),
            )
            .padding(padding),
        content = content,
    )
}

@Composable
internal fun CharacterProgress(pct: Float, color: Color) {
    Box(
        Modifier.fillMaxWidth()
            .padding(top = 8.dp)
            .height(8.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.background)
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = .5f), CircleShape)
    ) {
        Box(
            Modifier.fillMaxWidth((pct / 100).coerceIn(0f, 1f))
                .fillMaxHeight()
                .background(Brush.horizontalGradient(listOf(color.copy(alpha = .62f), color)))
        )
    }
}
