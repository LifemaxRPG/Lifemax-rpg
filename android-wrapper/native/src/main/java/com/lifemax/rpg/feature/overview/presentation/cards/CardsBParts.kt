package com.lifemax.rpg.feature.overview.presentation.cards

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lifemax.rpg.core.designsystem.LocalPageColors
import com.lifemax.rpg.core.designsystem.components.SurfaceCard
import com.lifemax.rpg.core.designsystem.components.pageButtonShadow
import com.lifemax.rpg.core.designsystem.components.pageShadow
import com.lifemax.rpg.core.designsystem.components.pixelIconPath
import com.lifemax.rpg.core.designsystem.components.tapTarget
import com.lifemax.rpg.feature.overview.domain.CardChip

// align at merge: the pieces Cards B share with Cards A (.card.ov-card, .ov-h, .ov-link,
// .ovh-chips, .btn.small). ov-native-a draws the same classes (OverviewCardHead, OverviewChips);
// keep one of each and point these cards at it.

/**
 * The page's `.card.ov-card`: the card's border, corners and shadow with the Overview's padding (14
 * 14 15). A card the page makes tappable as a whole takes [onClick], announced as a button.
 */
@Composable
internal fun CardsBFrame(
    modifier: Modifier = Modifier,
    padding: PaddingValues = OvCardPadding,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val c = LocalPageColors.current
    val tap =
        if (onClick == null) Modifier
        else Modifier.clip(CardShape).tapTarget(Role.Button, onClick = onClick)
    SurfaceCard(modifier.pageShadow(CardShape, c.light).then(tap), padding, c.lineSoft, content)
}

/**
 * `.ov-h`: the card's title (`.card h3`, a heading for TalkBack) and, on the baseline at the other
 * end, its link or note. Health's head sits closer to its body ([below] 6) and its pair closer
 * together ([gap] 8).
 */
@Composable
internal fun CardsBHead(
    title: String,
    modifier: Modifier = Modifier,
    below: Dp = 11.dp,
    gap: Dp = 10.dp,
    end: (@Composable () -> Unit)? = null,
) {
    val c = LocalPageColors.current
    Row(
        modifier.fillMaxWidth().padding(bottom = below),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            title,
            Modifier.alignByBaseline().semantics { heading() },
            color = c.textDim,
            fontSize = 13.5.sp,
            fontWeight = FontWeight(650),
            letterSpacing = (-0.135).sp,
            lineHeight = 20.25.sp,
        )
        if (end != null) Box(Modifier.alignByBaseline().padding(start = gap)) { end() }
    }
}

/** A head's muted note (`.ov-h .muted`, 11.5 px). */
@Composable
internal fun CardsBNote(text: String) {
    Text(text, color = LocalPageColors.current.muted, fontSize = 11.5.sp, lineHeight = 17.25.sp)
}

/**
 * `.ov-link` ("Details ›"): accent words that go somewhere. They keep the page's size; Compose
 * widens the touch area of anything smaller than 48 dp to 48 dp around it.
 */
@Composable
internal fun CardsBLink(text: String, onClick: () -> Unit) {
    Text(
        text,
        Modifier.clip(LinkShape).clickable(role = Role.Button, onClick = onClick),
        color = LocalPageColors.current.accent,
        fontSize = 12.5.sp,
        fontWeight = FontWeight.Bold,
        lineHeight = 18.75.sp,
    )
}

/**
 * `.ovh-chips`: small captioned values that share a row (`flex: 1 1 90px`, 8 px apart), as many to
 * a row as fit at 90 dp each, each row's chips growing to fill it.
 */
@Composable
internal fun CardsBChips(chips: List<CardChip>, modifier: Modifier = Modifier) {
    if (chips.isEmpty()) return
    val c = LocalPageColors.current
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val perRow = chipsPerRow(maxWidth.value)
        Column(verticalArrangement = Arrangement.spacedBy(ChipGap)) {
            chips.chunked(perRow).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(ChipGap)) {
                    row.forEach { chip ->
                        Column(
                            Modifier.weight(1f)
                                .clip(ChipShape)
                                .background(c.panel2)
                                .padding(horizontal = 10.dp, vertical = 8.dp)
                        ) {
                            Text(
                                chip.label,
                                color = c.muted,
                                fontSize = 11.sp,
                                lineHeight = 16.5.sp,
                            )
                            Text(
                                chip.value,
                                color = c.text,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                lineHeight = 21.sp,
                            )
                        }
                    }
                }
            }
        }
    }
}

/** How many 90 dp chips 8 dp apart fit in [width] dp: one at least. */
internal fun chipsPerRow(width: Float): Int =
    ((width + ChipGap.value) / (ChipBasis + ChipGap.value)).toInt().coerceAtLeast(1)

/**
 * The page's small accent button (`.btn.small`): 12 px words on the accent, a light top edge, and
 * in the light theme a glow. It keeps the page's 31 px height; Compose widens its touch area to 48
 * dp around it.
 */
@Composable
internal fun CardsBSmallButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val c = LocalPageColors.current
    val edge = if (c.light) ButtonEdgeLight else ButtonEdgeDark
    Box(modifier) {
        Text(
            text,
            Modifier.pageButtonShadow(ButtonShape, c.light)
                .clip(ButtonShape)
                .background(c.accent)
                .clickable(role = Role.Button, onClick = onClick)
                .drawBehind { drawRect(edge, size = Size(size.width, 1.dp.toPx())) }
                .padding(horizontal = 10.dp, vertical = 7.dp),
            color = c.onAccent,
            fontSize = 12.sp,
            fontWeight = FontWeight(600),
            lineHeight = 18.sp,
            softWrap = false,
        )
    }
}

/**
 * A web pixel icon by name, drawn as one path so its cells meet without the hairline seams that
 * cell-by-cell squares show at fractional sizes. // align at merge: core PixelIcon could draw so.
 */
@Composable
internal fun CardIcon(name: String, color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) { drawPath(pixelIconPath(name, size.minDimension, Offset.Zero), color) }
}

/** `.card.ov-card`'s padding. */
internal val OvCardPadding = PaddingValues(start = 14.dp, top = 14.dp, end = 14.dp, bottom = 15.dp)

/** The page's card corners (--radius). */
internal val CardShape = RoundedCornerShape(14.dp)

private val LinkShape = RoundedCornerShape(6.dp)
private val ChipShape = RoundedCornerShape(12.dp)
private val ChipGap = 8.dp
private const val ChipBasis = 90f
private val ButtonShape = RoundedCornerShape(8.dp)

/** The .btn's inset top edge (01-base.css:29, 00-00-foundation.css:70). */
private val ButtonEdgeDark = Color(0x22FFFFFF)
private val ButtonEdgeLight = Color(0x44FFFFFF)
