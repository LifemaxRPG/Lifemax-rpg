package com.lifemax.rpg.feature.library.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lifemax.rpg.core.designsystem.components.SurfaceCard

/** The page's .card at phone width: its soft border, its small shadow, 15 dp inside the border. */
@Composable
internal fun LibraryCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val c = LocalLibraryColors.current
    SurfaceCard(modifier.then(c.smallShadow(CardShape)), PaddingValues(16.dp), c.lineSoft, content)
}

/** The corners of SurfaceCard, which the shadow follows. */
private val CardShape = RoundedCornerShape(14.dp)

/**
 * One piece of a card that runs over several items of a list, as the card holding the filters and
 * the rows does. Each piece draws the card as if it went on past the piece, cut to the piece, so
 * the pieces join into one card with one border and one shadow. The [first] piece has the card's
 * top edge and the [last] its bottom edge; only their shadows reach outside them.
 */
internal fun Modifier.cardPiece(c: LibraryColors, first: Boolean = false, last: Boolean = false) =
    drawWithContent {
            val reach = PieceReach.toPx()
            val top = if (first) -reach else 0f
            val bottom = if (last) size.height + reach else size.height
            clipRect(-reach, top, size.width + reach, bottom) { this@drawWithContent.drawContent() }
        }
        .then(c.smallShadow(CardPieceShape(first, last)))
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

/** The page's .btn sizes: padding inside the border, font size, weight and line height. */
internal enum class ButtonSize(
    val horizontal: Dp,
    val vertical: Dp,
    val fontSize: TextUnit,
    val weight: FontWeight,
    val lineHeight: TextUnit,
) {
    REGULAR(14.dp, 10.dp, 14.sp, FontWeight(650), 21.sp),
    SMALL(10.dp, 7.dp, 12.sp, FontWeight.SemiBold, 18.sp),
    /** .btn.small in a row's Edit and Remove on phones. */
    COMPACT(8.dp, 6.dp, 12.sp, FontWeight.SemiBold, 18.sp),
}

/**
 * The page's .btn: the accent button when [primary], otherwise .secondary, with its border (which
 * the page draws outside the padding) and, in the light theme, its shadow.
 */
@Composable
internal fun PageButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    primary: Boolean = false,
    size: ButtonSize = ButtonSize.REGULAR,
) {
    val c = LocalLibraryColors.current
    val shape = RoundedCornerShape(8.dp)
    val border = if (primary) 0.dp else 1.dp
    Box(
        modifier
            .then(c.buttonShadow(primary, shape))
            .clip(shape)
            .background(if (primary) c.accent else c.secondaryButton)
            .then(if (primary) Modifier else Modifier.border(border, c.line, shape))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = size.horizontal + border, vertical = size.vertical + border),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            color = if (primary) c.onAccent else c.text,
            fontSize = size.fontSize,
            fontWeight = size.weight,
            lineHeight = size.lineHeight,
            textAlign = TextAlign.Center,
        )
    }
}
