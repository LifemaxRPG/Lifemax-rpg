package com.lifemax.rpg.feature.lifedirector.presentation.drag

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lifemax.rpg.core.designsystem.boxShadow
import com.lifemax.rpg.feature.lifedirector.domain.OrderRow
import com.lifemax.rpg.feature.lifedirector.presentation.components.DirectorPalette as P
import com.lifemax.rpg.feature.lifedirector.presentation.components.DirectorText

/**
 * The lifted session as a small chip: its colour dot, its name on one line (cut with an ellipsis
 * when it is long) and its time, on the panel's colour with an edge and a shadow, nothing to tap.
 * It is as wide as its words need, up to the width the caller allows (see
 * [com.lifemax.rpg.feature.lifedirector.domain.DragChip.maxWidth]), and grows with the text size
 * but stays far smaller than the row it stands for, so it tells what is about to be removed without
 * hiding the list.
 */
@Composable
internal fun LiftedChip(row: OrderRow, modifier: Modifier = Modifier) {
    Row(
        modifier
            .boxShadow(Color.Black.copy(alpha = .6f), 22.dp, CHIP_SHAPE, dy = 8.dp)
            .background(P.bg0, CHIP_SHAPE)
            .border(1.dp, P.line2, CHIP_SHAPE)
            .semantics(mergeDescendants = true) {
                contentDescription =
                    listOf(row.title, row.time, row.length)
                        .filter { it.isNotEmpty() }
                        .joinToString(", ")
            }
            .padding(start = 14.dp, end = 16.dp, top = 9.dp, bottom = 9.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Spacer(Modifier.size(10.dp).background(P.of(row.kind), CircleShape))
        Column(Modifier.weight(1f, fill = false)) {
            Text(
                row.title,
                style = DirectorText,
                color = P.ink,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                chipTime(row),
                style = DirectorText,
                color = P.ink2,
                fontSize = 12.5.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** The chip's second line: when the session starts and how long it runs ("7:15 PM · 1h"). */
internal fun chipTime(row: OrderRow): String =
    listOf(row.time, row.length).filter { it.isNotEmpty() }.joinToString(" · ")

private val CHIP_SHAPE = RoundedCornerShape(18.dp)
