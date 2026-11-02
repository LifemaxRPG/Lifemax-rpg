package com.lifemax.rpg.core.designsystem.components

import androidx.compose.ui.Modifier
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
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.lifemax.rpg.core.designsystem.PageColors

/**
 * One piece of a card that runs over several items of a list, as the card holding the filters and
 * the rows does. Each piece draws the card as if it went on past the piece, cut to the piece, so
 * the pieces join into one card with one border and one shadow. The [first] piece has the card's
 * top edge and the [last] its bottom edge; only their shadows reach outside them.
 */
fun Modifier.cardPiece(c: PageColors, first: Boolean = false, last: Boolean = false) =
    drawWithContent {
            val reach = PieceReach.toPx()
            val top = if (first) -reach else 0f
            val bottom = if (last) size.height + reach else size.height
            clipRect(-reach, top, size.width + reach, bottom) { this@drawWithContent.drawContent() }
        }
        .pageShadow(CardPieceShape(first, last), c.light)
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
