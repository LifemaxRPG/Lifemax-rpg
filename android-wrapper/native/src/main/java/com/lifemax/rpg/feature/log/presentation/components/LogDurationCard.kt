package com.lifemax.rpg.feature.log.presentation.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lifemax.rpg.core.designsystem.components.ChoicePill
import com.lifemax.rpg.core.designsystem.components.RoundButton
import com.lifemax.rpg.core.designsystem.components.SectionLabel
import com.lifemax.rpg.core.designsystem.components.SurfaceCard

/** Minutes, with a five-minute stepper and the common lengths one tap away. */
@Composable
internal fun LogDurationCard(minutes: Int, onChange: (Int) -> Unit) {
    SurfaceCard {
        SectionLabel("Duration")
        Row(
            Modifier.padding(top = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RoundButton("−", "Five minutes less", { onChange(minutes - STEP) })
            Row(
                Modifier.weight(1f),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.Bottom,
            ) {
                Text(
                    "$minutes",
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 40.sp,
                    fontWeight = FontWeight.Black,
                )
                Text(
                    " min",
                    Modifier.padding(bottom = 7.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 16.sp,
                )
            }
            RoundButton("+", "Five minutes more", { onChange(minutes + STEP) })
        }
        Row(Modifier.padding(top = 14.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            QUICK.forEach { m ->
                ChoicePill(
                    "$m",
                    m == minutes,
                    { onChange(m) },
                    Modifier.weight(1f),
                    centered = true,
                )
            }
        }
    }
}

private const val STEP = 5
private val QUICK = listOf(15, 30, 45, 60, 90)
