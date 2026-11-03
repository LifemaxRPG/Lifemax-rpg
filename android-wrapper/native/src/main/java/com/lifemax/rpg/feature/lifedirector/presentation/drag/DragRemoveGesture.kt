package com.lifemax.rpg.feature.lifedirector.presentation.drag

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.lazy.LazyListItemInfo
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import com.lifemax.rpg.feature.lifedirector.domain.DragModel
import com.lifemax.rpg.feature.lifedirector.domain.DragPoint
import com.lifemax.rpg.feature.lifedirector.presentation.components.DirectorBuzz
import com.lifemax.rpg.feature.lifedirector.presentation.components.LocalDirectorBuzz

/**
 * The running order's rows sit this far in from the screen's sides (the list's content padding).
 */
internal val LIST_SIDE = 12.dp

/**
 * Lifts a session off the running order. It watches the list's touches before the rows and the
 * list's own scrolling see them (the initial pass, taking nothing): a finger that stays put on a
 * row for [DragModel.LIFT_MS] lifts it, and from then on the gesture is this one's alone, so the
 * row under the finger is not tapped on release and the list does not scroll, though it is scrolled
 * for the finger near its edges ([RemoveOverlay]). The gesture belongs to the list, not to a row,
 * so a row scrolling out of view does not end it. A finger that moves or lifts first is a tap or a
 * scroll, and none of this is its business.
 *
 * A session that cannot be removed does not lift: the standard "not now" buzz and the page's
 * reason. A second finger cancels, and so does anything that takes the touch away.
 */
@Composable
internal fun Modifier.dragToRemove(drag: DragRemoveController?, list: LazyListState): Modifier {
    if (drag == null) return this
    val view = LocalView.current
    val buzz = LocalDirectorBuzz.current
    return pointerInput(drag, list) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            val item = list.itemAt(down.position.y) ?: return@awaitEachGesture
            val interrupted =
                withTimeoutOrNull(DragModel.LIFT_MS) {
                    stillHeld(down, viewConfiguration.touchSlop)
                }
            if (interrupted != null) return@awaitEachGesture
            when (val press = drag.press(item.key)) {
                Press.Ignore -> return@awaitEachGesture
                is Press.Refuse -> {
                    buzz?.buzz(*NOT_NOW)
                    drag.refuse(press.reason)
                    // the rest of this touch is not a tap on the row either
                    follow(down) { it.pressed }
                }
                is Press.Lift -> {
                    view.parent?.requestDisallowInterceptTouchEvent(true)
                    buzz?.buzz(LIFT_BUZZ)
                    drag.lift(press.row, down.position.toPoint())
                    carry(drag, down, buzz)
                }
            }
        }
    }
}

/** Waits while the finger stays still on the row; returns when it moved, lifted or was taken. */
private suspend fun AwaitPointerEventScope.stillHeld(
    down: PointerInputChange,
    slop: Float,
): Boolean {
    while (true) {
        val event = awaitPointerEvent(PointerEventPass.Initial)
        val change = event.changes.firstOrNull { it.id == down.id } ?: return false
        if (!change.pressed || change.isConsumed) return false
        if ((change.position - down.position).getDistance() > slop) return false
    }
}

/**
 * The lifted session follows the finger until it lets go or the touch is cancelled. [frame] turns a
 * position of this gesture's own node into the screen's pixels the controller works in (the list's
 * gesture needs no turning; the dial's does). If the gesture itself is cancelled by its node
 * leaving the screen, the session goes back.
 */
internal suspend fun AwaitPointerEventScope.carry(
    drag: DragRemoveController,
    down: PointerInputChange,
    buzz: DirectorBuzz?,
    frame: (Offset) -> Offset = { it },
) {
    var cancelled = true
    try {
        follow(down) { change ->
            if (!drag.isHeld) return@follow false // Back or the screen cancelled it
            if (!change.pressed) {
                cancelled = change.isConsumed // a cancel arrives as a release already consumed
                false
            } else {
                if (drag.move(frame(change.position).toPoint()))
                    buzz?.buzz(if (drag.armed.value) ARM_BUZZ else TICK_BUZZ)
                true
            }
        }
    } finally {
        if (cancelled) drag.cancel() else drag.release()
    }
}

/**
 * Takes this finger's events (and any other's, which are ignored but not left for the rows) until
 * [each] says to stop. A second finger down ends it. Everything is consumed.
 */
internal suspend fun AwaitPointerEventScope.follow(
    down: PointerInputChange,
    each: (PointerInputChange) -> Boolean,
) {
    while (true) {
        val event = awaitPointerEvent(PointerEventPass.Initial)
        val change = event.changes.firstOrNull { it.id == down.id } ?: return
        val second = event.changes.any { it.id != down.id && it.pressed }
        val go = !second && each(change)
        event.changes.forEach { it.consume() }
        if (second) return
        if (!go) return
    }
}

private fun LazyListState.itemAt(y: Float): LazyListItemInfo? =
    layoutInfo.visibleItemsInfo.firstOrNull { y >= it.offset && y < it.offset + it.size }

internal fun Offset.toPoint() = DragPoint(x, y)

/** The vibrator's patterns, as navigator.vibrate takes them. */
internal const val LIFT_BUZZ = 14L
internal const val TICK_BUZZ = 5L
internal const val ARM_BUZZ = 9L
internal val NOT_NOW = longArrayOf(30L, 40L, 30L)
