package com.lifemax.rpg.feature.overview.presentation.cards

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lifemax.rpg.core.designsystem.boxShadow
import com.lifemax.rpg.core.designsystem.components.PixelIcon
import com.lifemax.rpg.core.designsystem.components.tapTarget
import com.lifemax.rpg.core.designsystem.mix
import com.lifemax.rpg.feature.overview.domain.MoveWarn
import com.lifemax.rpg.feature.overview.domain.NextMove
import com.lifemax.rpg.feature.overview.domain.NextMoves
import com.lifemax.rpg.feature.overview.presentation.LocalOverviewColors
import com.lifemax.rpg.feature.overview.presentation.argb

/** Next moves (.ov-moves): the page's four most useful moves, each a button to where it leads. */
@Composable
internal fun NextMovesCard(next: NextMoves, onMove: (NextMove) -> Unit) {
    OverviewCard {
        CardHead(next.title) { CardNote(next.note) }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            next.moves.forEach { MoveRow(it) { onMove(it) } }
        }
    }
}

private val MoveShape = RoundedCornerShape(14.dp)
private val IconShape = RoundedCornerShape(12.dp)
private val PillShape = RoundedCornerShape(999.dp)

/**
 * One move (.ov-move): the coloured strip with its glow down the left edge, the icon in its tinted
 * square, the title over its two-line note, and the call to act (a pill) or a chevron (✓ when
 * done). A fading move has a red border, a due one a gold border; a done one is dimmed.
 */
@Composable
private fun MoveRow(move: NextMove, onClick: () -> Unit) {
    val c = LocalOverviewColors.current
    val tint = argb(move.color)
    val border =
        when (move.warn) {
            MoveWarn.BAD -> c.bad.copy(alpha = 0.42f)
            MoveWarn.WARN -> c.gold.copy(alpha = 0.40f)
            MoveWarn.NONE -> c.lineSoft
        }
    Box(
        Modifier.fillMaxWidth()
            .height(IntrinsicSize.Min)
            .alpha(if (move.done) 0.72f else 1f)
            .clip(MoveShape)
            .tapTarget(Role.Button, onClick = onClick)
            .background(c.panel2)
            .border(1.dp, border, MoveShape)
    ) {
        Box(
            Modifier.width(3.dp)
                .fillMaxHeight()
                .boxShadow(tint, 10.dp, RectangleShape)
                .background(tint)
        )
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 11.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier.size(40.dp)
                    .clip(IconShape)
                    .background(tint.mix(0.15f, c.panel))
                    .border(1.dp, tint.copy(alpha = 0.40f), IconShape)
                    .clearAndSetSemantics {},
                contentAlignment = Alignment.Center,
            ) {
                PixelIcon(move.icon, tint, Modifier.size(20.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(
                    move.title,
                    color = c.text,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    lineHeight = 17.5.sp,
                )
                Text(
                    move.sub,
                    Modifier.padding(top = 2.dp),
                    color = c.muted,
                    fontSize = 11.5.sp,
                    lineHeight = 14.95.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (move.cta.isNotEmpty()) Cta(move, tint)
            else
                Text(
                    if (move.done) "✓" else "›",
                    // the page's ✓ is read ("check mark") and says the move is done; its › is
                    // only an arrow
                    Modifier.padding(horizontal = 4.dp)
                        .then(if (move.done) Modifier else Modifier.clearAndSetSemantics {}),
                    color = if (move.done) c.good else c.muted,
                    fontSize = 20.sp,
                    fontWeight = if (move.done) FontWeight.Black else FontWeight.Normal,
                    lineHeight = 30.sp,
                )
        }
    }
}

/** The call to act (.ov-cta): the move's colour, or gold for the quests' pill. */
@Composable
private fun Cta(move: NextMove, tint: Color) {
    val c = LocalOverviewColors.current
    Text(
        move.cta,
        Modifier.clip(PillShape)
            .background(tint.copy(alpha = 0.14f))
            .border(1.dp, tint.copy(alpha = 0.38f), PillShape)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        color = (if (move.pill) c.gold else tint).mix(0.62f, c.text),
        fontSize = 12.sp,
        fontWeight = FontWeight.ExtraBold,
        lineHeight = 18.sp,
        maxLines = 1,
    )
}
