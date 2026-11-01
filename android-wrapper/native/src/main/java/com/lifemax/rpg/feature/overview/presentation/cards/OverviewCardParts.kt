package com.lifemax.rpg.feature.overview.presentation.cards

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lifemax.rpg.core.designsystem.components.pageShadow
import com.lifemax.rpg.feature.overview.domain.CardChip
import com.lifemax.rpg.feature.overview.presentation.LocalOverviewColors

/*
 * The pieces every Overview card shares: the card itself (.card.ov-card), its head (.ov-h) with
 * its link (.ov-link), and the label-and-value chips (.ovh-chip) of Rewards and Activity today.
 */

/** The page's .card radius. */
internal val CardShape = RoundedCornerShape(14.dp)

/**
 * A card (.card.ov-card): the panel with its soft border and small shadow, 14 dp inside (15 at the
 * foot), and the 14 dp under it that separates the tiles. [background] lays the card's own fill
 * (the hero's glows) instead of the panel.
 */
@Composable
internal fun OverviewCard(
    modifier: Modifier = Modifier,
    padding: PaddingValues = PaddingValues(start = 14.dp, end = 14.dp, top = 14.dp, bottom = 15.dp),
    background: Modifier? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val c = LocalOverviewColors.current
    Column(
        modifier
            .padding(bottom = 14.dp)
            .fillMaxWidth()
            .pageShadow(CardShape, c.light)
            .clip(CardShape)
            .then(background ?: Modifier.background(c.panel))
            .border(1.dp, c.lineSoft, CardShape)
            .padding(padding),
        content = content,
    )
}

/**
 * A card's head (.ov-h): its title (h3, a heading for TalkBack) and, on its right, a note or a
 * link, on a shared baseline, 11 dp above the card's body.
 */
@Composable
internal fun CardHead(
    title: String,
    modifier: Modifier = Modifier,
    bottom: Dp = 11.dp,
    end: (@Composable () -> Unit)? = null,
) {
    val c = LocalOverviewColors.current
    Row(
        modifier.fillMaxWidth().padding(bottom = bottom),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            title,
            Modifier.weight(1f).semantics { heading() },
            color = c.textDim,
            fontSize = 13.5.sp,
            fontWeight = FontWeight(650),
            letterSpacing = (-0.135).sp,
            lineHeight = 20.25.sp,
        )
        end?.invoke()
    }
}

/** The head's muted note ("most useful first"). */
@Composable
internal fun CardNote(text: String) {
    Text(text, color = LocalOverviewColors.current.muted, fontSize = 11.5.sp, lineHeight = 17.sp)
}

/**
 * The head's link (.ov-link): accent words that go somewhere ("Shop ›"), at the page's size: a 48
 * dp target would make every card's head taller than the page's (a known difference, as on the
 * page, whose link is 23 px tall).
 */
@Composable
internal fun CardLink(text: String, onClick: () -> Unit) {
    Text(
        text,
        Modifier.clip(RoundedCornerShape(6.dp))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(vertical = 2.dp),
        color = LocalOverviewColors.current.accent,
        fontSize = 12.5.sp,
        fontWeight = FontWeight.Bold,
        lineHeight = 18.75.sp,
    )
}

/** The chips (.ovh-chips): each grows from 90 dp and wraps, as the page's flex row does. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun CardChips(chips: List<CardChip>, modifier: Modifier = Modifier) {
    val c = LocalOverviewColors.current
    FlowRow(
        modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        maxItemsInEachRow = 3,
    ) {
        chips.forEach {
            Column(
                Modifier.weight(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(c.panel2)
                    .padding(horizontal = 10.dp, vertical = 8.dp)
            ) {
                Text(it.label, color = c.muted, fontSize = 11.sp, lineHeight = 16.5.sp)
                Text(
                    it.value,
                    color = c.text,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    lineHeight = 21.sp,
                    maxLines = 1,
                )
            }
        }
    }
}
