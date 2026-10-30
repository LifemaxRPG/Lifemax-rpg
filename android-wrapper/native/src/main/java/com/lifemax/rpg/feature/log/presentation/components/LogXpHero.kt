package com.lifemax.rpg.feature.log.presentation.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lifemax.rpg.core.designsystem.components.PixelIcon
import com.lifemax.rpg.core.designsystem.components.ProgressBar
import com.lifemax.rpg.core.designsystem.components.SectionLabel
import com.lifemax.rpg.core.designsystem.components.SurfaceCard
import com.lifemax.rpg.feature.log.domain.LogPreview
import com.lifemax.rpg.feature.log.domain.LogProjection

/**
 * The award this draft would earn, as the page computes it: the total, a bar, and where the XP
 * goes.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun LogXpHero(p: LogProjection, preview: LogPreview) {
    val lead = p.attribute(preview.split.firstOrNull()?.attribute ?: "")
    val leadColor = lead?.let { Color(it.color.toInt()) } ?: MaterialTheme.colorScheme.primary
    val total by animateIntAsState(preview.total, tween(420), label = "xp")
    val fraction by
        animateFloatAsState(
            (preview.total / BAR_FULL_XP).coerceIn(0f, 1f),
            tween(420),
            label = "bar",
        )
    SurfaceCard(
        Modifier.semantics { contentDescription = "This activity earns ${preview.total} XP" }
    ) {
        SectionLabel("XP preview")
        Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.Bottom) {
            Text(
                "+$total",
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 52.sp,
                fontWeight = FontWeight.Black,
                lineHeight = 52.sp,
            )
            Text(
                "XP",
                Modifier.padding(start = 8.dp, bottom = 8.dp),
                color = leadColor,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
            )
        }
        ProgressBar(
            fraction,
            Brush.horizontalGradient(listOf(leadColor.copy(alpha = .55f), leadColor)),
            Modifier.padding(top = 12.dp),
            height = 10.dp,
            outline = MaterialTheme.colorScheme.outline.copy(alpha = .45f),
        )
        Spacer(Modifier.height(14.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            preview.split.forEach { share ->
                val a = p.attribute(share.attribute) ?: return@forEach
                val color = Color(a.color.toInt())
                Fact {
                    PixelIcon(a.icon, color, Modifier.size(14.dp))
                    Text(
                        a.short,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 13.sp,
                    )
                    Text(
                        "+${share.xp}",
                        color = color,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
            Fact {
                Text(
                    "Duration",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 13.sp,
                )
                Text(
                    "%.2f×".format(preview.durationFactor),
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
            Fact {
                Text("Skill", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                Text(
                    preview.skill,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
        if (!preview.enough)
            Text(
                "Log at least 5 minutes to earn XP",
                Modifier.padding(top = 12.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 13.sp,
            )
    }
}

@Composable
private fun Fact(content: @Composable () -> Unit) {
    Row(
        Modifier.clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        content()
    }
}

/** The web preview's bar reads full at 120 XP. */
private const val BAR_FULL_XP = 120f
