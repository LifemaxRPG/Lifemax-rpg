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
internal fun CharacterCard(content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(
                1.dp,
                MaterialTheme.colorScheme.outline.copy(alpha = .3f),
                RoundedCornerShape(20.dp),
            )
            .padding(16.dp),
        content = content,
    )
}

@Composable
internal fun CharacterProgress(pct: Float, color: Color) {
    Box(
        Modifier.fillMaxWidth()
            .padding(top = 8.dp)
            .height(5.dp)
            .clip(CircleShape)
            .background(color.copy(alpha = .12f))
    ) {
        Box(Modifier.fillMaxWidth((pct / 100).coerceIn(0f, 1f)).fillMaxHeight().background(color))
    }
}
