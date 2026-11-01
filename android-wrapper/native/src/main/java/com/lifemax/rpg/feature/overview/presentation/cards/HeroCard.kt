package com.lifemax.rpg.feature.overview.presentation.cards

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lifemax.rpg.core.designsystem.boxShadow
import com.lifemax.rpg.core.designsystem.components.PixelIcon
import com.lifemax.rpg.core.designsystem.components.tapTarget
import com.lifemax.rpg.core.designsystem.cssLinearGradient
import com.lifemax.rpg.feature.overview.domain.Delta
import com.lifemax.rpg.feature.overview.domain.HeroCard
import com.lifemax.rpg.feature.overview.domain.HeroFoot
import com.lifemax.rpg.feature.overview.domain.HeroStreak
import com.lifemax.rpg.feature.overview.presentation.LocalOverviewArrival
import com.lifemax.rpg.feature.overview.presentation.LocalOverviewColors
import com.lifemax.rpg.feature.overview.presentation.OverviewArrival
import com.lifemax.rpg.feature.overview.presentation.OverviewColors
import com.lifemax.rpg.feature.overview.presentation.cssRadialGlow

/** Tabular figures, as the page's font-variant-numeric: tabular-nums. */
internal val Tabular = TextStyle(fontFeatureSettings = "tnum")

/**
 * Level & today (.ov-hero): today's ring beside the level, the XP bar and the streak, then the
 * week, total and achievements. The whole card is one button to Character; TalkBack reads its words
 * together, as the page gives it no label of its own. [avatar] is the decoded picture.
 */
@Composable
internal fun HeroCardView(
    hero: HeroCard,
    avatar: androidx.compose.ui.graphics.ImageBitmap?,
    onOpen: () -> Unit,
) {
    val c = LocalOverviewColors.current
    OverviewCard(
        padding = PaddingValues(start = 16.dp, end = 16.dp, top = 18.dp, bottom = 14.dp),
        background =
            Modifier.background(c.panel)
                .cssRadialGlow(0.9f, 0.7f, -0.08f, 1.08f, c.good.copy(alpha = 0.10f), 0.60f)
                .cssRadialGlow(1.2f, 0.9f, 0.88f, -0.12f, c.accent.copy(alpha = 0.24f), 0.62f)
                .tapTarget(Role.Button, onClick = onOpen)
                .semantics(mergeDescendants = true) {},
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TodayRing(hero.ring)
            HeroMeta(hero, avatar, Modifier.weight(1f).padding(top = 6.dp))
        }
        HeroFootRow(hero.foot)
    }
}

/** The level row, the XP line and bar, and the streak (.ov-meta). */
@Composable
private fun HeroMeta(
    hero: HeroCard,
    avatar: androidx.compose.ui.graphics.ImageBitmap?,
    modifier: Modifier,
) {
    val c = LocalOverviewColors.current
    Column(modifier) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Avatar(hero, avatar)
            LevelBadge(hero.level)
            Column(Modifier.weight(1f)) {
                Text(
                    hero.title,
                    color = c.accentStrong,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 1.54.sp,
                    lineHeight = 14.85.sp,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Clip,
                )
                if (hero.className.isNotEmpty())
                    Text(
                        hero.className,
                        color = c.muted,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 1.6.sp,
                        lineHeight = 14.85.sp,
                        maxLines = 1,
                    )
            }
            Text("›", color = c.muted, fontSize = 26.sp, lineHeight = 26.sp)
        }
        Row(Modifier.fillMaxWidth().padding(top = 11.dp)) {
            Text(
                hero.xp.text,
                Modifier.weight(1f),
                color = c.textDim,
                fontSize = 11.5.sp,
                lineHeight = 17.25.sp,
                style = Tabular,
            )
            Text(
                hero.xp.next,
                color = c.textDim,
                fontSize = 11.5.sp,
                lineHeight = 17.25.sp,
                style = Tabular,
            )
        }
        XpBar(hero.xp.pct, Modifier.padding(top = 6.dp, bottom = 12.dp))
        Streak(hero.streak)
    }
}

/** .ov-ava: the picture, or the initial, in a circle ringed with the accent's tint. */
@Composable
private fun Avatar(hero: HeroCard, avatar: androidx.compose.ui.graphics.ImageBitmap?) {
    val c = LocalOverviewColors.current
    Box(
        Modifier.padding(end = 2.dp)
            .size(44.dp)
            .border(2.dp, c.accentDim, CircleShape)
            .padding(2.dp)
            .clip(CircleShape)
            .background(c.panel3),
        contentAlignment = Alignment.Center,
    ) {
        if (avatar != null)
            Image(avatar, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        else
            Text(
                hero.initial,
                color = c.text,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                lineHeight = 27.sp,
            )
    }
}

/** .ov-lvbadge: the level on gold, with its glow. */
@Composable
private fun LevelBadge(level: Int) {
    val shape = RoundedCornerShape(12.dp)
    Box(
        Modifier.boxShadow(OverviewColors.BadgeGlow, 14.dp, shape, 5.dp)
            .sizeIn(minWidth = 40.dp, minHeight = 40.dp)
            .clip(shape)
            .background(
                cssLinearGradient(
                    135f,
                    0f to OverviewColors.BadgeTop,
                    1f to OverviewColors.BadgeBottom,
                )
            )
            .padding(horizontal = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            level.toString(),
            color = OverviewColors.BadgeInk,
            fontSize = 19.sp,
            fontWeight = FontWeight.Black,
            lineHeight = 28.5.sp,
        )
    }
}

/** .xpbar.ov-xpbar: 7 dp, the accent into green, with its soft glow. */
@Composable
private fun XpBar(pct: Float, modifier: Modifier) {
    val c = LocalOverviewColors.current
    // the bar fills from empty on an arrival, as the page's ovReplay does
    val fill = LocalOverviewArrival.current?.let { OverviewArrival.fill(it.ms) } ?: 1f
    val frac = (pct / 100f).coerceIn(0f, 1f) * fill
    Box(
        modifier
            .fillMaxWidth()
            .height(7.dp)
            .clip(CircleShape)
            .background(c.xpTrack)
            .border(1.dp, c.lineSoft, CircleShape)
    ) {
        if (frac > 0f)
            Box(
                Modifier.fillMaxWidth(frac)
                    .fillMaxSize()
                    .boxShadow(OverviewColors.XpGlow, 12.dp, CircleShape)
                    .clip(CircleShape)
                    .background(c.xpFill)
            )
    }
}

/** .ov-streak: the flame, the days, "day streak", and the last seven days (today ringed). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Streak(streak: HeroStreak) {
    val c = LocalOverviewColors.current
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(7.dp),
        verticalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        val mid = Modifier.align(Alignment.CenterVertically)
        PixelIcon(
            "flame",
            OverviewColors.Flame,
            mid.size(16.dp).boxShadow(OverviewColors.FlameGlow, 6.dp, CircleShape),
        )
        Text(
            streak.days.toString(),
            mid,
            color = c.text,
            fontSize = 17.sp,
            fontWeight = FontWeight.Bold,
            lineHeight = 21.sp,
            style = Tabular,
        )
        Text(streak.label, mid, color = c.textDim, fontSize = 12.5.sp, lineHeight = 18.75.sp)
        Row(
            mid.padding(start = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            streak.dots.forEach { dot ->
                val ring = c.text.copy(alpha = 0.55f)
                Box(
                    Modifier.size(7.dp)
                        .drawBehind {
                            if (dot.now) {
                                // outline 1.5 px, 1.5 px away from the dot
                                val r = size.minDimension / 2 + 1.5.dp.toPx() + 0.75.dp.toPx()
                                drawCircle(
                                    ring,
                                    r,
                                    style =
                                        androidx.compose.ui.graphics.drawscope.Stroke(1.5.dp.toPx()),
                                )
                            }
                        }
                        .then(
                            if (dot.on)
                                Modifier.boxShadow(OverviewColors.DotGlow, 6.dp, CircleShape)
                            else Modifier
                        )
                        .clip(CircleShape)
                        .background(if (dot.on) OverviewColors.Flame else c.line)
                )
            }
        }
    }
}

/** .ov-hero-foot: three columns under a hairline: label, value, and the week's change. */
@Composable
private fun HeroFootRow(foot: List<HeroFoot>) {
    val c = LocalOverviewColors.current
    Row(
        Modifier.fillMaxWidth()
            .padding(top = 16.dp)
            .drawBehind { drawRect(c.lineSoft, size = size.copy(height = 1.dp.toPx())) }
            .padding(top = 13.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        foot.forEach { f ->
            Column(Modifier.weight(1f)) {
                Text(
                    f.label,
                    color = c.muted,
                    fontSize = 8.5.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 1.275.sp,
                    lineHeight = 12.75.sp,
                )
                Text(
                    f.value,
                    Modifier.padding(top = 4.dp),
                    color = c.text,
                    fontSize = 15.5.sp,
                    fontWeight = FontWeight.Bold,
                    lineHeight = 23.25.sp,
                    maxLines = 1,
                    softWrap = false,
                    style = Tabular,
                )
                f.delta?.let { DeltaText(it) }
            }
        }
    }
}

/** A ▲ or ▼ change, green or red (em.up / em.dn). */
@Composable
internal fun DeltaText(d: Delta, modifier: Modifier = Modifier) {
    val c = LocalOverviewColors.current
    Text(
        d.text,
        modifier,
        color = if (d.up) c.good else c.bad,
        fontSize = 11.sp,
        fontWeight = FontWeight.ExtraBold,
        lineHeight = 16.5.sp,
    )
}
