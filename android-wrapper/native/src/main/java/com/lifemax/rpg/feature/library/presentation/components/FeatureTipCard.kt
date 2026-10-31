package com.lifemax.rpg.feature.library.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lifemax.rpg.core.designsystem.components.tapTarget
import com.lifemax.rpg.feature.library.domain.FeatureTip

/**
 * The page's feature tip (.feature-tip): its icon, title and words, and a ✕ that dismisses it. The
 * ✕ is 28 dp as on the page; its 48 dp target fits inside the tip's padding, so it lies over the
 * tip's corner without moving anything.
 */
@Composable
internal fun FeatureTipCard(tip: FeatureTip, onDismiss: () -> Unit) {
    val c = LocalLibraryColors.current
    val shape = RoundedCornerShape(10.dp)
    Box(
        Modifier.padding(bottom = 14.dp)
            .fillMaxWidth()
            .then(c.smallShadow(shape))
            .clip(shape)
            .background(c.tipFill)
            .border(1.dp, c.tipBorder, shape)
    ) {
        Row(
            Modifier.padding(horizontal = 17.dp, vertical = 15.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                Modifier.size(36.dp).clip(RoundedCornerShape(10.dp)).background(c.accentDim),
                contentAlignment = Alignment.Center,
            ) {
                Text(tip.icon, color = c.accentStrong, fontSize = 18.sp, lineHeight = 27.sp)
            }
            Column(Modifier.weight(1f)) {
                Text(
                    tip.title,
                    Modifier.padding(bottom = 3.dp),
                    color = c.text,
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight(650),
                    lineHeight = 20.25.sp,
                )
                Text(tip.body, color = c.muted, fontSize = 12.5.sp, lineHeight = 18.125.sp)
            }
            Spacer(Modifier.size(28.dp))
        }
        Box(
            Modifier.align(Alignment.TopEnd)
                .padding(top = 5.dp, end = 7.dp)
                .clip(CircleShape)
                .tapTarget(Role.Button, onClick = onDismiss)
                .clearAndSetSemantics { contentDescription = tip.dismiss }
                .size(28.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(c.panel2)
                .border(1.dp, c.line, RoundedCornerShape(8.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Text("✕", color = c.muted, fontSize = 14.sp, lineHeight = 21.sp)
        }
    }
}
