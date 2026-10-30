package com.lifemax.rpg.feature.character.presentation.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lifemax.rpg.core.designsystem.components.ProgressBar
import com.lifemax.rpg.core.designsystem.components.SurfaceCard
import com.lifemax.rpg.core.designsystem.components.barFade
import com.lifemax.rpg.feature.character.domain.*
import com.lifemax.rpg.feature.character.presentation.CharacterSelection
import kotlin.math.*

@Composable
internal fun CharacterSkillsCard(
    p: CharacterProjection,
    ui: CharacterSelection,
    onSkills: () -> Unit,
) {
    SurfaceCard {
        Text("Skill progression", fontSize = 17.sp, fontWeight = FontWeight.Bold)
        Text(
            "Practice level is logged effort only. Gaps in training show up over time.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 12.sp,
            modifier = Modifier.padding(top = 6.dp, bottom = 8.dp),
        )
        if (p.skills.isEmpty())
            Text(
                "Skills appear here as you practice them.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 20.dp),
            )
        (if (ui.allSkills) p.skills else p.skills.take(6)).forEach { s ->
            Text(
                s.name + " · LV " + s.level,
                modifier = Modifier.padding(top = 12.dp),
                fontWeight = FontWeight.SemiBold,
            )
            ProgressBar(
                s.pct / 100,
                barFade(MaterialTheme.colorScheme.primary),
                Modifier.padding(top = 8.dp),
            )
            Text(
                "${s.xp.toLong()} XP",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 11.sp,
            )
        }
        if (p.skills.size > 6)
            TextButton(onSkills) {
                Text(if (ui.allSkills) "Show fewer" else "Show all ${p.skills.size}")
            }
    }
}
