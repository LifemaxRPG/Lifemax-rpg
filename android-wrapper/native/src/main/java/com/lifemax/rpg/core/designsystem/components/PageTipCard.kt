package com.lifemax.rpg.core.designsystem.components

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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lifemax.rpg.core.designsystem.LocalPageColors
import com.lifemax.rpg.core.designsystem.PageColors
import com.lifemax.rpg.core.designsystem.cssLinearGradient
import com.lifemax.rpg.core.designsystem.mix

/**
 * The page's feature tip (.feature-tip): its [icon], [title] and [body], and a ✕ that dismisses it,
 * which TalkBack names [dismiss]. The ✕ is 28 dp as on the page; its 48 dp target fits inside the
 * tip's padding, so it lies over the tip's corner without moving anything.
 */
@Composable
fun PageTipCard(icon: String, title: String, body: String, dismiss: String, onDismiss: () -> Unit) {
    val c = LocalPageColors.current
    val tip = remember(c) { TipColors(c) }
    val shape = RoundedCornerShape(10.dp)
    Box(
        Modifier.padding(bottom = 14.dp)
            .fillMaxWidth()
            .pageShadow(shape, c.light)
            .clip(shape)
            .background(tip.fill)
            .border(1.dp, tip.border, shape)
    ) {
        Row(
            Modifier.padding(horizontal = 17.dp, vertical = 15.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // the page hides its icon from screen readers (aria-hidden)
            Box(
                Modifier.size(36.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(c.accentDim)
                    .clearAndSetSemantics {},
                contentAlignment = Alignment.Center,
            ) {
                Text(icon, color = c.accentStrong, fontSize = 18.sp, lineHeight = 27.sp)
            }
            Column(Modifier.weight(1f)) {
                Text(
                    title,
                    Modifier.padding(bottom = 3.dp),
                    color = c.text,
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight(650),
                    lineHeight = 20.25.sp,
                )
                Text(body, color = c.muted, fontSize = 12.5.sp, lineHeight = 18.125.sp)
            }
            Spacer(Modifier.size(28.dp))
        }
        Box(
            Modifier.align(Alignment.TopEnd)
                .padding(top = 5.dp, end = 7.dp)
                .clip(CircleShape)
                .tapTarget(Role.Button, onClick = onDismiss)
                .clearAndSetSemantics { contentDescription = dismiss }
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

/** The tip's fill and border (02-layout.css .feature-tip), with the light theme's own. */
private class TipColors(c: PageColors) {
    val fill =
        if (c.light) cssLinearGradient(135f, 0f to TipTopLight, 0.7f to Color.White)
        else cssLinearGradient(135f, 0f to c.accent.mix(0.14f, c.panel), 0.7f to c.panel)
    val border = if (c.light) TipBorderLight else c.accent.mix(0.35f, c.line)
}

private val TipTopLight = Color(0xFFE8EEFF)
private val TipBorderLight = Color(0xFFC5D0EA)
