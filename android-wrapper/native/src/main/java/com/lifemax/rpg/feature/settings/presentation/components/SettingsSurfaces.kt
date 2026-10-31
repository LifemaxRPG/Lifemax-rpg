package com.lifemax.rpg.feature.settings.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lifemax.rpg.core.designsystem.LocalPageColors
import com.lifemax.rpg.core.designsystem.PageColors
import com.lifemax.rpg.core.designsystem.boxShadow
import com.lifemax.rpg.core.designsystem.components.tapTarget
import com.lifemax.rpg.feature.settings.domain.ButtonStyle

/*
 * The page's .card cut into the list items it spans, and the page's .btn in every form Settings
 * uses. Core's PageCard draws a whole card and core's PageButton only the regular accent button;
 * Settings needs a card per row (the rows are the list's items) and the secondary, small, danger
 * and disabled buttons too.
 */

/**
 * One piece of a card that runs over several items of a list. Each piece draws the card as if it
 * went on past the piece, cut to the piece, so the pieces join into one card with one border and
 * one shadow (the page's .card: panel, a line-soft edge, 14 dp corners, its small shadow). The
 * [first] piece has the card's top edge and the [last] its bottom edge; only their shadows reach
 * outside them.
 */
internal fun Modifier.cardPiece(c: PageColors, first: Boolean, last: Boolean): Modifier {
    val shape = CardPieceShape(first, last)
    val shadow =
        if (c.light)
            Modifier.boxShadow(CardShadowLightFar, 10.dp, shape, 2.dp)
                .boxShadow(CardShadowLightNear, 2.dp, shape, 1.dp)
        else Modifier.boxShadow(CardShadowDark, 8.dp, shape, 2.dp)
    return drawWithContent {
            val reach = PieceReach.toPx()
            val top = if (first) -reach else 0f
            val bottom = if (last) size.height + reach else size.height
            clipRect(-reach, top, size.width + reach, bottom) { this@drawWithContent.drawContent() }
        }
        .then(shadow)
        .drawWithCache {
            val border = 1.dp.toPx()
            val fill = pieceOutline(size, first, last, inset = 0f)
            val edge = pieceOutline(size, first, last, inset = border / 2)
            val stroke = Stroke(border)
            onDrawBehind {
                drawOutline(fill, c.panel)
                drawOutline(edge, c.lineSoft, style = stroke)
            }
        }
}

/** The card's outline as one of its pieces sees it, for the shadow. */
private data class CardPieceShape(val first: Boolean, val last: Boolean) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density) =
        density.pieceOutline(size, first, last, inset = 0f)
}

/** The card's outline [inset] inside its edge: it runs on past the piece's open ends. */
private fun Density.pieceOutline(size: Size, first: Boolean, last: Boolean, inset: Float) =
    Outline.Rounded(
        RoundRect(
            left = inset,
            top = if (first) inset else -PieceReach.toPx(),
            right = size.width - inset,
            bottom = if (last) size.height - inset else size.height + PieceReach.toPx(),
            cornerRadius = CornerRadius(14.dp.toPx() - inset),
        )
    )

/** Past a piece's corners and its shadow. */
private val PieceReach = 32.dp

/** The line under a row (.settingsline's border-bottom), along the row's bottom edge. */
internal fun Modifier.rowLine(c: PageColors, show: Boolean): Modifier =
    if (!show) this
    else
        drawBehind {
            val w = 1.dp.toPx()
            drawRect(c.lineSoft, topLeft = Offset(0f, size.height - w), size = Size(size.width, w))
        }

/**
 * The page's .btn as Settings uses it: the accent button (with its light top edge, and its glow in
 * the light theme), .secondary (panel2 with a line border; the panel and the small shadow when
 * light), the danger button (drawn in --bad, which is what the page means by --red, a colour it
 * never defines), .small, and :disabled at .45 opacity. A regular button is at least 48 dp tall
 * (the page's is 41); a small one keeps its 32 dp, and Compose widens its touch area. TalkBack
 * reads [description] when the page gives one, otherwise the label. A [pending] button (the page
 * has not answered its press yet) is faded and takes no tap. A button that is one of a group's
 * choices says whether it is the [selected] one.
 */
@Composable
internal fun SettingsButton(
    label: String,
    description: String,
    style: ButtonStyle,
    small: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    pending: Boolean = false,
    selected: Boolean? = null,
) {
    val c = LocalPageColors.current
    val ink = SettingsInk(c)
    val shape = RoundedCornerShape(8.dp)
    val secondary = style == ButtonStyle.Secondary
    val border = if (secondary) 1.dp else 0.dp
    val tappable = enabled && !pending
    Box(
        modifier
            .alpha(
                when {
                    !enabled -> .45f
                    pending -> .6f
                    else -> 1f
                }
            )
            .then(
                when {
                    !c.light -> Modifier
                    secondary ->
                        Modifier.boxShadow(CardShadowLightFar, 10.dp, shape, 2.dp)
                            .boxShadow(CardShadowLightNear, 2.dp, shape, 1.dp)
                    else -> Modifier.boxShadow(ButtonGlowLight, 8.dp, shape, 2.dp)
                }
            )
            .clip(shape)
            .background(
                when (style) {
                    ButtonStyle.Primary -> c.accent
                    ButtonStyle.Secondary -> ink.secondary
                    ButtonStyle.Danger -> c.bad
                }
            )
            .then(
                if (secondary) Modifier.border(border, c.line, shape)
                else
                    Modifier.drawBehind {
                        drawRect(
                            if (c.light) ButtonEdgeLight else ButtonEdgeDark,
                            size = Size(size.width, 1.dp.toPx()),
                        )
                    }
            )
            .then(
                if (description.isNotEmpty())
                    Modifier.semantics { contentDescription = description }
                else Modifier
            )
            .then(
                when {
                    selected != null ->
                        Modifier.selectable(
                            selected,
                            enabled = tappable,
                            role = Role.RadioButton,
                            onClick = onClick,
                        )
                    small || !tappable ->
                        Modifier.clickable(tappable, role = Role.Button, onClick = onClick)
                    else -> Modifier.tapTarget(Role.Button, onClick = onClick)
                }
            )
            .padding(
                horizontal = (if (small) 10.dp else 14.dp) + border,
                vertical = (if (small) 7.dp else 10.dp) + border,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            color = if (secondary) c.text else ink.onAccent,
            fontSize = if (small) 12.sp else 14.sp,
            fontWeight = if (small) FontWeight.SemiBold else FontWeight(650),
            lineHeight = if (small) 18.sp else 21.sp,
            textAlign = TextAlign.Center,
        )
    }
}

/**
 * A box drawn like one of the page's inputs: [fill], a 1 dp line edge and [radius] corners. The
 * caller adds the tap, then the page's padding plus the 1 dp edge, which CSS draws outside it.
 */
internal fun Modifier.inputSurface(c: PageColors, fill: Color, radius: Dp): Modifier {
    val shape = RoundedCornerShape(radius)
    return clip(shape).background(fill).border(1.dp, c.line, shape)
}
