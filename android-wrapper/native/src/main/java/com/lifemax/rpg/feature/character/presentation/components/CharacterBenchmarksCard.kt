package com.lifemax.rpg.feature.character.presentation.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lifemax.rpg.feature.character.domain.*
import kotlin.math.*

@Composable
internal fun CharacterBenchmarksCard(p: CharacterProjection, onAction: (String, String) -> Unit) {
    CharacterCard {
        Text("Real-world benchmarks", fontSize = 17.sp, fontWeight = FontWeight.Bold)
        Text(
            "Optional metrics you choose to track.",
            fontSize = 12.sp,
            color = p.tokenColor("muted"),
        )
        p.benchmarks.forEach { b ->
            Text(b.name, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 12.dp))
            Text(b.value, color = p.tokenColor("muted"))
        }
        if (p.benchmarks.isEmpty())
            Text(
                "No benchmarks yet.",
                color = p.tokenColor("muted"),
                modifier = Modifier.padding(top = 12.dp),
            )
        TextButton(onClick = { onAction("benchmarks", "") }) { Text("Add or edit in Classic ›") }
    }
}
