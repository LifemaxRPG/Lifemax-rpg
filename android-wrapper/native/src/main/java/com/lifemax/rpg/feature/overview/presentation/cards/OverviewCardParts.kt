package com.lifemax.rpg.feature.overview.presentation.cards

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lifemax.rpg.core.designsystem.components.pageButtonShadow
import com.lifemax.rpg.core.designsystem.components.pageShadow
import com.lifemax.rpg.core.designsystem.components.pixelIconPath
import com.lifemax.rpg.core.designsystem.components.tapTarget
import com.lifemax.rpg.feature.overview.domain.CardChip
import com.lifemax.rpg.feature.overview.presentation.LocalOverviewColors

/*
 * The pieces every Overview card shares: the card itself (.card.ov-card), its head (.ov-h) with
 * its link (.ov-link), the label-and-value chips (.ovh-chip) of Rewards and Activity today, the
 * small button (.btn.small) and a pixel icon drawn as one path.
 */

/** The page's .card radius. */
internal val CardShape = RoundedCornerShape(14.dp)

/**
 * A card (.card.ov-card): the panel with its soft border and small shadow, 14 dp inside (15 at the
 * foot), and the 14 dp under it that separates the tiles. [background] lays the card's own fill
 * (the hero's glows) instead of the panel. A card the page makes tappable as a whole takes
 * [onClick], announced as a button.
 */
@Composable
internal fun OverviewCard(
    modifier: Modifier = Modifier,
    padding: PaddingValues = PaddingValues(start = 14.dp, end = 14.dp, top = 14.dp, bottom = 15.dp),
    background: Modifier? = null,
    onClick: (() -> Unit)? = null,
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
            .then(
                if (onClick == null) Modifier
                else Modifier.tapTarget(Role.Button, onClick = onClick)
            )
            .border(1.dp, c.lineSoft, CardShape)
            .padding(padding),
        content = content,
    )
}

/**
 * A card's head (.ov-h): its title (h3, a heading for TalkBack) and, on its right, a note or a
 * link, on a shared baseline, 11 dp above the card's body ([bottom]), [gap] apart (Activity today's
 * head: 6 and 8).
 */
@Composable
internal fun CardHead(
    title: String,
    modifier: Modifier = Modifier,
    bottom: Dp = 11.dp,
    gap: Dp = 10.dp,
    end: (@Composable () -> Unit)? = null,
) {
    val c = LocalOverviewColors.current
    Row(
        modifier.fillMaxWidth().padding(bottom = bottom),
        horizontalArrangement = Arrangement.spacedBy(gap),
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

/**
 * The page's .btn.small (.secondary when [secondary]): 12 px words on the accent, a light top edge,
 * in the light theme a glow; [horizontal] dp either side (18 on the luck card, 10 elsewhere). It
 * keeps the page's size and corners inside a 48 dp touch target.
 */
@Composable
internal fun CardButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    secondary: Boolean = false,
    horizontal: Dp = 10.dp,
) {
    val c = LocalOverviewColors.current
    val shape = RoundedCornerShape(8.dp)
    Box(
        modifier
            .tapTarget(Role.Button, onClick = onClick)
            .pageButtonShadow(shape, c.light, primary = !secondary)
            .clip(shape)
            .background(if (secondary) c.secondaryFill else c.accent)
            .then(if (secondary) Modifier.border(1.dp, c.line, shape) else Modifier)
            .then(
                if (secondary) Modifier
                else
                    Modifier.drawBehind {
                        drawRect(
                            if (c.light) ButtonEdgeLight else ButtonEdgeDark,
                            size = size.copy(height = 1.dp.toPx()),
                        )
                    }
            )
            .padding(horizontal = horizontal, vertical = 7.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            color = if (secondary) c.text else c.onAccent,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            lineHeight = 18.sp,
            maxLines = 1,
        )
    }
}

/** The .btn's inset top edge (01-base.css:29, 00-00-foundation.css:70). */
private val ButtonEdgeDark = Color(0x22FFFFFF)
private val ButtonEdgeLight = Color(0x44FFFFFF)

/**
 * A web pixel icon by name, drawn as one path so its cells meet without the hairline seams that
 * cell-by-cell squares show at fractional sizes.
 */
@Composable
internal fun CardIcon(name: String, color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) { drawPath(pixelIconPath(name, size.minDimension, Offset.Zero), color) }
}
