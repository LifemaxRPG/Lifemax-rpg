package com.lifemax.rpg.feature.journal.presentation.body

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.snapping.SnapPosition
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lifemax.rpg.core.designsystem.cssLinearGradient
import com.lifemax.rpg.core.designsystem.gesture.noTabSwipe
import com.lifemax.rpg.core.designsystem.mix
import com.lifemax.rpg.feature.journal.domain.JournalListItem

/**
 * The tile strip (#view-history .journal-days at phone width): day tiles, quiet pills and review
 * tiles in a row that scrolls sideways and settles on a tile's start. A drag that starts on it is
 * never a tab swipe, as on the page. [selected] is the highlighted key, which the screen moves at
 * once on a tap; [onSelect] sends the tile's key (`select {key}`).
 */
@Composable
internal fun JournalTiles(
    items: List<JournalListItem>,
    selected: String?,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
    state: LazyListState = rememberLazyListState(),
) {
    val tileHeight = tileHeight()
    LazyRow(
        modifier.fillMaxWidth().noTabSwipe(),
        state = state,
        contentPadding = PaddingValues(top = 2.dp, bottom = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        flingBehavior = rememberSnapFlingBehavior(state, SnapPosition.Start),
    ) {
        itemsIndexed(items, key = { i, it -> itemKey(i, it) }) { _, item ->
            when (item) {
                is JournalListItem.Day ->
                    DayTile(item, item.key == selected, tileHeight) { onSelect(item.key) }
                is JournalListItem.Review ->
                    ReviewTile(item, item.key == selected, tileHeight) { onSelect(item.key) }
                is JournalListItem.Quiet -> QuietPill(item.text)
            }
        }
    }
}

private fun itemKey(index: Int, item: JournalListItem): String =
    when (item) {
        is JournalListItem.Day -> item.key
        is JournalListItem.Review -> item.key
        is JournalListItem.Quiet -> "quiet:$index"
    }

/**
 * A day tile's height, which every tile of the strip takes, as the page's row stretches each tile
 * to the tallest: the padding and border, three gaps, the bar and the three lines of text.
 */
@Composable
private fun tileHeight(): Dp =
    with(LocalDensity.current) { (9 + 8 + 2 + 3 * 3 + 4).dp + (15f + 25.3f + 16.5f).sp.toDp() }

/** The press shrinks a tile to .96 (.jx-tile:active), back again on release. */
@Composable
private fun pressScale(source: MutableInteractionSource): Float {
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.96f else 1f, tween(180), label = "press")
    return scale
}

/**
 * A day: the weekday, the date, the XP bar in the archetype's colour, the ✎ for a written day and
 * the XP. Selected, it lifts 2 dp, its border takes the colour and its glow appears (180 ms, as the
 * page's transition). TalkBack reads the page's label ("Today, 177 XP") as a selected tab or not.
 */
@Composable
private fun DayTile(day: JournalListItem.Day, on: Boolean, height: Dp, onClick: () -> Unit) {
    val c = LocalJournalBodyColors.current
    val tint = argb(day.color)
    val shape = RoundedCornerShape(18.dp)
    val source = remember { MutableInteractionSource() }
    val lift by animateDpAsState(if (on) (-2).dp else 0.dp, tween(180), label = "lift")
    val border by animateColorAsState(if (on) tint else c.line, tween(180), label = "border")
    val glowAlpha by animateFloatAsState(if (on) 1f else 0f, tween(180), label = "glow")
    val fill =
        if (on) cssLinearGradient(180f, 0f to tint.mix(0.24f, c.panel2), 1f to c.panel)
        else cssLinearGradient(180f, 0f to c.panel2, 1f to c.panel)
    Column(
        Modifier.offset(y = lift)
            .scale(pressScale(source))
            .widthIn(min = 66.dp)
            .width(IntrinsicSize.Max)
            .heightIn(min = height)
            .glow(tint.copy(alpha = glowAlpha), 22.dp, 18.dp, spread = (-10).dp, dy = 8.dp)
            .clip(shape)
            .background(fill)
            .then(if (on) Modifier.insetTopEdge(JournalInk.white(0x1A), 18.dp) else Modifier)
            .border(1.dp, border, shape)
            .selectable(
                on,
                interactionSource = source,
                indication = null,
                role = Role.Tab,
                onClick = onClick,
            )
            .clearAndSetSemantics {
                contentDescription = day.spoken
                selected = on
                role = Role.Tab
            }
            .padding(start = 7.dp, end = 7.dp, top = 10.dp, bottom = 9.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Text(
            day.weekday.uppercase(),
            color = if (on) c.text else if (day.today) c.accentStrong else c.muted,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.6.sp,
            lineHeight = 15.sp,
            maxLines = 1,
            softWrap = false,
        )
        Text(
            day.date,
            color = c.text,
            fontSize = 23.sp,
            fontWeight = FontWeight(800),
            letterSpacing = (-0.92).sp,
            lineHeight = 25.3.sp,
        )
        Box(
            Modifier.fillMaxWidth()
                .height(4.dp)
                .clip(RoundedCornerShape(9.dp))
                .background(JournalInk.white(0x10))
        ) {
            Box(
                Modifier.fillMaxWidth(day.bar.coerceIn(0, 100) / 100f)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(9.dp))
                    .background(tint)
            )
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            if (day.pen) Text("✎", color = tint, fontSize = 11.sp, lineHeight = 16.5.sp)
            Text(
                day.xp,
                color = c.muted2,
                fontSize = 10.5.sp,
                fontWeight = FontWeight.SemiBold,
                lineHeight = 15.75.sp,
            )
        }
    }
}

/**
 * A weekly or monthly review: its amber badge, title and kind. Selected, its border turns amber and
 * it glows; it does not lift (the page's .jx-rev.on).
 */
@Composable
private fun ReviewTile(rev: JournalListItem.Review, on: Boolean, height: Dp, onClick: () -> Unit) {
    val c = LocalJournalBodyColors.current
    val shape = RoundedCornerShape(18.dp)
    val source = remember { MutableInteractionSource() }
    val border by
        animateColorAsState(
            if (on) JournalInk.Amber else Color(0x66F59E0B),
            tween(180),
            label = "border",
        )
    val glowAlpha by animateFloatAsState(if (on) 1f else 0f, tween(180), label = "glow")
    Column(
        Modifier.scale(pressScale(source))
            .widthIn(min = 104.dp)
            .heightIn(min = height)
            .glow(
                JournalInk.Amber.copy(alpha = glowAlpha),
                22.dp,
                18.dp,
                spread = (-10).dp,
                dy = 8.dp,
            )
            .clip(shape)
            .background(cssLinearGradient(135f, 0f to Color(0x24F59E0B), 1f to Color(0x0AF59E0B)))
            .border(1.dp, border, shape)
            .selectable(
                on,
                interactionSource = source,
                indication = null,
                role = Role.Tab,
                onClick = onClick,
            )
            .clearAndSetSemantics {
                contentDescription = "${rev.title}, ${rev.sub}"
                selected = on
                role = Role.Tab
            }
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterVertically),
    ) {
        Box(
            Modifier.sizeIn(minWidth = 22.dp, minHeight = 22.dp)
                .clip(RoundedCornerShape(7.dp))
                .background(JournalInk.Amber),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                rev.badge,
                color = JournalInk.OnAmber,
                fontSize = 11.sp,
                fontWeight = FontWeight(800),
                lineHeight = 16.5.sp,
            )
        }
        Text(
            rev.title,
            color = c.text,
            fontSize = 13.5.sp,
            fontWeight = FontWeight(750),
            letterSpacing = (-0.27).sp,
            lineHeight = 20.25.sp,
            maxLines = 1,
        )
        Text(
            rev.sub,
            color = JournalInk.AmberText,
            fontSize = 10.5.sp,
            lineHeight = 15.75.sp,
            maxLines = 1,
        )
    }
}

/** The pill for three or more quiet days (.jx-quiet), centred on the row. */
@Composable
private fun QuietPill(text: String) {
    val c = LocalJournalBodyColors.current
    Text(
        text,
        Modifier.dashedBorder(c.line, 99.dp).padding(horizontal = 11.dp, vertical = 6.dp),
        color = c.muted2,
        fontSize = 10.5.sp,
        lineHeight = 15.75.sp,
        maxLines = 1,
        softWrap = false,
    )
}
