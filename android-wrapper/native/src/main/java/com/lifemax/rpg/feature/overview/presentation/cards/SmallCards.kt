package com.lifemax.rpg.feature.overview.presentation.cards

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lifemax.rpg.core.designsystem.components.PageEyebrow
import com.lifemax.rpg.core.designsystem.components.PixelIcon
import com.lifemax.rpg.core.designsystem.components.pageButtonShadow
import com.lifemax.rpg.core.designsystem.components.tapTarget
import com.lifemax.rpg.core.designsystem.mix
import com.lifemax.rpg.feature.overview.domain.LuckCard
import com.lifemax.rpg.feature.overview.domain.RecentCard
import com.lifemax.rpg.feature.overview.domain.RecentItem
import com.lifemax.rpg.feature.overview.domain.RewardsCard
import com.lifemax.rpg.feature.overview.presentation.LocalOverviewColors
import com.lifemax.rpg.feature.overview.presentation.argb
import com.lifemax.rpg.feature.overview.presentation.cssRadialGlow

/** Rewards: sparks, the streak with its shields, rested XP; the card and "Shop ›" open the shop. */
@Composable
internal fun RewardsCardView(rewards: RewardsCard, onShop: () -> Unit) {
    val c = LocalOverviewColors.current
    OverviewCard(
        background = Modifier.background(c.panel).tapTarget(Role.Button, onClick = onShop)
    ) {
        CardHead(rewards.title) { CardLink(rewards.link, onShop) }
        CardChips(rewards.chips)
    }
}

/**
 * Daily luck (.ov-duo.solo .ov-luck): the eyebrow, then the score in monospace (its tier's colour
 * once rolled, a dim dash row before) over the tier's words, and the button on the right.
 */
@Composable
internal fun LuckCardView(luck: LuckCard, onLuck: () -> Unit) {
    val c = LocalOverviewColors.current
    OverviewCard(
        padding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
        background =
            Modifier.background(c.panel)
                .cssRadialGlow(1.1f, 0.9f, 1f, 0f, c.accent.copy(alpha = 0.16f), 0.60f),
    ) {
        PageEyebrow(luck.eyebrow)
        Row(
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    luck.score,
                    Modifier.padding(top = 4.dp),
                    color = luck.color?.let(::argb) ?: c.muted,
                    fontSize = if (luck.rolled) 34.sp else 26.sp,
                    fontWeight = FontWeight.Black,
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = if (luck.rolled) (-1.02).sp else 1.04.sp,
                    lineHeight = if (luck.rolled) 35.7.sp else 27.3.sp,
                    style = Tabular,
                    maxLines = 1,
                )
                Text(
                    luck.text,
                    Modifier.padding(top = 2.dp),
                    color = c.textDim,
                    fontSize = 12.sp,
                    fontWeight = FontWeight(650),
                    lineHeight = 18.sp,
                )
            }
            CardButton(luck.button, onLuck, secondary = luck.rolled, horizontal = 18.dp)
        }
    }
}

/** Recent activity (.ov-feed): the last four logs, or the page's empty line; "History ›". */
@Composable
internal fun RecentCardView(recent: RecentCard, onHistory: () -> Unit) {
    val c = LocalOverviewColors.current
    OverviewCard {
        CardHead(recent.title) { CardLink(recent.link, onHistory) }
        if (recent.items.isEmpty())
            Column(
                Modifier.fillMaxWidth().padding(vertical = 14.dp, horizontal = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    recent.emptyIcon,
                    Modifier.padding(bottom = 8.dp).alpha(0.7f).clearAndSetSemantics {},
                    color = c.muted,
                    fontSize = 28.sp,
                    lineHeight = 42.sp,
                )
                Text(
                    recent.emptyText,
                    color = c.muted,
                    fontSize = 14.sp,
                    lineHeight = 21.sp,
                    textAlign = TextAlign.Center,
                )
            }
        else
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                recent.items.forEachIndexed { i, item ->
                    RecentRow(item, last = i == recent.items.lastIndex)
                }
            }
    }
}

/** One log (.ov-fi): its attribute's icon, the name over its line, and the XP in green. */
@Composable
private fun RecentRow(item: RecentItem, last: Boolean) {
    val c = LocalOverviewColors.current
    val tint = argb(item.color)
    val shape = RoundedCornerShape(11.dp)
    Row(
        Modifier.fillMaxWidth()
            .drawBehind {
                if (!last)
                    drawRect(
                        c.lineSoft,
                        Offset(0f, size.height - 1.dp.toPx()),
                        size.copy(height = 1.dp.toPx()),
                    )
            }
            .padding(horizontal = 2.dp, vertical = 9.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(36.dp)
                .clip(shape)
                .background(tint.mix(0.14f, c.panel2))
                .border(1.dp, tint.copy(alpha = 0.36f), shape)
                .clearAndSetSemantics {},
            contentAlignment = Alignment.Center,
        ) {
            PixelIcon(item.icon, tint, Modifier.size(18.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(
                item.name,
                color = c.text,
                fontSize = 13.5.sp,
                fontWeight = FontWeight.Bold,
                lineHeight = 20.25.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                item.meta,
                Modifier.padding(top = 2.dp),
                color = c.muted,
                fontSize = 11.5.sp,
                lineHeight = 17.25.sp,
            )
        }
        Text(
            item.xp,
            color = c.good,
            fontSize = 13.5.sp,
            fontWeight = FontWeight.Black,
            lineHeight = 20.25.sp,
        )
    }
}
