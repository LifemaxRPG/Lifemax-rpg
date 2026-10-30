package com.lifemax.rpg.feature.plan.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lifemax.rpg.core.designsystem.components.PixelIcon
import com.lifemax.rpg.feature.plan.domain.PlanLauncher

/**
 * The guided-workout card above the plan (gwrLauncher). [resume] is the look of a session in
 * progress, which takes the card's place on every day.
 */
@Composable
internal fun PlanLauncherCard(launcher: PlanLauncher, resume: Boolean, onOpen: () -> Unit) {
    val c = LocalPlanColors.current
    val shape = RoundedCornerShape(14.dp)
    Row(
        Modifier.fillMaxWidth()
            .clip(shape)
            .background(if (resume) c.launcherResumeFill else c.panel)
            .border(1.dp, if (resume) c.launcherResumeBorder else c.line, shape)
            .padding(start = 16.dp, top = 14.dp, end = 14.dp, bottom = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)).background(c.accentDim),
            contentAlignment = Alignment.Center,
        ) {
            PixelIcon("dumbbell", c.accentStrong, Modifier.size(20.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(
                launcher.title,
                color = c.text,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                lineHeight = 20.sp,
            )
            Text(
                launcher.detail,
                Modifier.padding(top = 2.dp),
                color = c.muted,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                lineHeight = 17.55.sp,
            )
        }
        Box(
            Modifier.defaultMinSize(84.dp, 44.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(if (launcher.enabled) c.accent else c.panel3)
                .clickable(
                    enabled = launcher.enabled,
                    role = Role.Button,
                    onClickLabel = "Open the guided workout",
                    onClick = onOpen,
                )
                .padding(horizontal = 18.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                launcher.button,
                color = if (launcher.enabled) c.onAccent else c.muted,
                fontSize = 15.sp,
                fontWeight = FontWeight(750),
                lineHeight = 15.sp,
            )
        }
    }
}
