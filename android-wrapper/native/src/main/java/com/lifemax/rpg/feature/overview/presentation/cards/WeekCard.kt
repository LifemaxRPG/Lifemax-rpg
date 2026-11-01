package com.lifemax.rpg.feature.overview.presentation.cards

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lifemax.rpg.core.designsystem.LocalPageColors
import com.lifemax.rpg.core.designsystem.feedback.HapticScale
import com.lifemax.rpg.core.designsystem.feedback.LocalPageBuzz
import com.lifemax.rpg.core.designsystem.feedback.play
import com.lifemax.rpg.core.designsystem.mix
import com.lifemax.rpg.feature.overview.domain.WeekCard

/**
 * The This week card (ovWeekHtml): the week's total against last week's, the chart, the picked
 * day's line and the week's top three attributes. [selected] is the day the screen has picked (the
 * view model's, which starts at the page's); a day's tap buzzes 6 ms at the owner's strength
 * ([haptics], none when haptics are off) through [LocalPageBuzz], as the page's ovPick does, and
 * asks [onSelect] to pick it.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun WeekCardView(
    tile: WeekCard,
    selected: Int,
    haptics: HapticScale?,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = LocalPageColors.current
    val buzz = LocalPageBuzz.current
    OverviewCard(modifier) {
        CardHead(tile.title) { Total(tile) }
        WeekChart(
            tile.bars,
            tile.pace,
            selected,
            onPick = { i ->
                buzz?.play(haptics, WEEK_PICK_MS)
                onSelect(i)
            },
        )
        val tip = tile.bars.getOrNull(selected)?.tip
        Text(
            if (tip == null) buildAnnotatedString {}
            else
                buildAnnotatedString {
                    withStyle(SpanStyle(color = c.text, fontWeight = FontWeight.Bold)) {
                        append(tip.label)
                    }
                    append(" · ")
                    append(tip.rest)
                },
            Modifier.fillMaxWidth()
                .padding(start = 2.dp, top = 12.dp, end = 2.dp)
                .heightIn(min = 18.dp)
                .semantics { liveRegion = LiveRegionMode.Polite },
            color = c.textDim,
            fontSize = 12.5.sp,
            lineHeight = 18.75.sp,
        )
        if (tile.top.isEmpty()) {
            if (tile.empty.isNotEmpty())
                Text(
                    tile.empty,
                    Modifier.padding(top = 10.dp),
                    color = c.muted,
                    fontSize = 12.sp,
                    lineHeight = 18.sp,
                )
        } else
            FlowRow(
                Modifier.padding(top = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                tile.top.forEach { t -> TopPill(Color(t.color.toInt()), t.text) }
            }
    }
}

/** `.ov-sum`: the week's XP, then the change against last week in green or red. */
@Composable
private fun Total(tile: WeekCard) {
    val c = LocalPageColors.current
    Row {
        Text(
            tile.total,
            Modifier.alignByBaseline(),
            color = c.text,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            lineHeight = 21.sp,
        )
        tile.delta?.let { d ->
            Text(
                d.text,
                Modifier.alignByBaseline().padding(start = 7.dp),
                color = if (d.up) c.good else c.bad,
                fontSize = 11.sp,
                fontWeight = FontWeight(800),
                lineHeight = 16.5.sp,
            )
        }
    }
}

/** `.ov-top span`: a pill tinted with the attribute's colour, its dot, and its words. */
@Composable
private fun TopPill(color: Color, text: String) {
    val c = LocalPageColors.current
    Row(
        Modifier.background(color.mix(.13f, c.panel2), CircleShape)
            .border(1.dp, color.copy(alpha = .34f), CircleShape)
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(7.dp).background(color, CircleShape))
        Text(
            text,
            Modifier.padding(start = 6.dp),
            color = c.textDim,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            lineHeight = 16.5.sp,
        )
    }
}
