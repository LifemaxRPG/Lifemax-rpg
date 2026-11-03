package com.lifemax.rpg.feature.lifedirector.presentation.drag

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.semantics.CustomAccessibilityAction
import com.lifemax.rpg.feature.lifedirector.domain.DragSource
import com.lifemax.rpg.feature.lifedirector.domain.OrderRow
import com.lifemax.rpg.feature.lifedirector.domain.Tap
import com.lifemax.rpg.feature.lifedirector.presentation.components.OrderRowView
import kotlin.math.roundToInt

/**
 * A running-order row that can be lifted. While its session is lifted (until it springs back) the
 * row closes up, so the list shows the day without it, and opens again if the session goes back. A
 * session that can be taken off the day also offers TalkBack's "Remove from today", which is the
 * same removal with no drag. With no [drag] it is just the row.
 */
@Composable
internal fun RemovableRow(
    row: OrderRow,
    onTap: (Tap) -> Unit,
    drag: DragRemoveController?,
    reduced: Boolean,
) {
    if (drag == null) return OrderRowView(row, onTap)
    val held by drag.held.collectAsState()
    val lifted =
        held?.let {
            it.source == DragSource.ROW && it.row.key == row.key && it.phase != DragPhase.RETURNING
        } == true
    val open by
        animateFloatAsState(
            if (lifted) 0f else 1f,
            if (reduced) snap() else tween(SLOT_MS),
            label = "row slot",
        )
    val actions =
        if (row.removal?.ok == true) listOf(removeFromToday { drag.remove(row) }) else emptyList()
    Box(
        Modifier.layout { measurable, constraints ->
                val placeable = measurable.measure(constraints)
                layout(placeable.width, (placeable.height * open).roundToInt()) {
                    placeable.place(0, 0)
                }
            }
            .clipToBounds()
            .graphicsLayer { alpha = open }
    ) {
        OrderRowView(row, onTap, actions = actions)
    }
}

/** TalkBack's name for taking a session off the day. */
internal const val REMOVE_ACTION = "Remove from today"

/** TalkBack's "Remove from today", which does [remove] (a row's, or a dial segment's). */
internal fun removeFromToday(remove: () -> Unit) =
    CustomAccessibilityAction(REMOVE_ACTION) {
        remove()
        true
    }

private const val SLOT_MS = 180
