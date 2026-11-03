package com.lifemax.rpg.feature.lifedirector.presentation.dial

import android.view.View
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerEvent
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.changedToDownIgnoreConsumed
import com.lifemax.rpg.feature.lifedirector.domain.DialArc
import com.lifemax.rpg.feature.lifedirector.domain.DirectorDial
import com.lifemax.rpg.feature.lifedirector.domain.DragModel
import com.lifemax.rpg.feature.lifedirector.domain.DragSource
import com.lifemax.rpg.feature.lifedirector.presentation.components.DirectorBuzz
import com.lifemax.rpg.feature.lifedirector.presentation.drag.DragRemoveController
import com.lifemax.rpg.feature.lifedirector.presentation.drag.HoldEvent
import com.lifemax.rpg.feature.lifedirector.presentation.drag.HoldVerdict
import com.lifemax.rpg.feature.lifedirector.presentation.drag.LIFT_BUZZ
import com.lifemax.rpg.feature.lifedirector.presentation.drag.NOT_NOW
import com.lifemax.rpg.feature.lifedirector.presentation.drag.PendingHold
import com.lifemax.rpg.feature.lifedirector.presentation.drag.carry
import com.lifemax.rpg.feature.lifedirector.presentation.drag.follow
import com.lifemax.rpg.feature.lifedirector.presentation.drag.toPoint
import kotlinx.coroutines.withTimeoutOrNull

/**
 * What the dial needs to lift a session off the day: the controller it shares with the running
 * order's rows, and the view whose parents must not take the touch once a session is lifted.
 */
internal class DialLift(val drag: DragRemoveController, val view: View?)

/** How a finger that waited on a session's segment is finished with. */
internal sealed interface HoldEnd {
    /** the hold was long enough: the session was lifted (or refused) and the touch is spent */
    data object Spent : HoldEnd

    /** the finger let go first: a tap, whose release is [up] */
    data class Tapped(val up: PointerInputChange) : HoldEnd

    /** the finger moved, was taken or joined by another: the dial's plain touch carries on */
    data object Yielded : HoldEnd
}

/**
 * A finger waits on [arc], a session's segment ([DialTouch] has decided it is one). It watches the
 * touches after the dial's own parts, as a tap does (main pass), so a scroll that takes the touch
 * ends the wait. Held still for [DragModel.LIFT_MS] it lifts the session like a held row: the
 * "lift" haptic, the card under the finger, the swipes and the list's scrolling kept off it. A
 * session that cannot be removed gets the "not now" buzz and the page's reason, and the rest of the
 * touch is not a tap. The lifted session is carried in the screen's pixels, whatever the list is
 * scrolled to ([DialAnchor]).
 */
internal suspend fun AwaitPointerEventScope.holdOnSegment(
    lift: DialLift,
    arc: DialArc,
    down: PointerInputChange,
    buzz: DirectorBuzz?,
): HoldEnd {
    val drag = lift.drag
    val hold = PendingHold(down.position.toPoint(), viewConfiguration.touchSlop) { drag.press(arc) }
    var verdict: HoldVerdict = HoldVerdict.Waiting
    var last = down
    withTimeoutOrNull(DragModel.LIFT_MS) {
        while (verdict == HoldVerdict.Waiting) {
            val event = awaitPointerEvent()
            event.changes.firstOrNull { it.id == down.id }?.let { last = it }
            verdict = hold.on(event.holdEvent(down))
        }
    }
    if (verdict == HoldVerdict.Waiting) verdict = hold.on(HoldEvent.Elapsed)
    return when (val end = verdict) {
        is HoldVerdict.Lifts -> {
            lift.view?.parent?.requestDisallowInterceptTouchEvent(true)
            buzz?.buzz(LIFT_BUZZ)
            val anchor = drag.anchor
            drag.lift(
                end.row,
                anchor.frame(down.position).toPoint(),
                drag.dialGrab(),
                DragSource.DIAL,
            )
            carry(drag, down, buzz, anchor::frame)
            HoldEnd.Spent
        }
        is HoldVerdict.Refuses -> {
            buzz?.buzz(*NOT_NOW)
            drag.refuse(end.reason)
            follow(down) { it.pressed } // the rest of this touch is not a tap on the segment
            HoldEnd.Spent
        }
        is HoldVerdict.Tap -> HoldEnd.Tapped(last)
        else -> HoldEnd.Yielded
    }
}

/** What this pointer event means to a finger waiting at [down]. */
private fun PointerEvent.holdEvent(down: PointerInputChange): HoldEvent {
    if (changes.any { it.id != down.id && it.changedToDownIgnoreConsumed() })
        return HoldEvent.SecondFinger
    val change = changes.firstOrNull { it.id == down.id } ?: return HoldEvent.Taken
    return when {
        // a cancel arrives as a release already consumed
        !change.pressed ->
            if (change.isConsumed) HoldEvent.Taken
            else HoldEvent.Released(change.position.toPoint())
        change.isConsumed -> HoldEvent.Taken
        else -> HoldEvent.Moved(change.position.toPoint())
    }
}

/** The centre of each session's segment in the dial's pixels, for a lifted card to settle into. */
internal fun segmentCentres(dial: () -> DirectorDial, side: Float): (String) -> Offset? = { id ->
    dial()
        .arcs
        .firstOrNull { it.tap?.id == id }
        ?.let { arc ->
            val radii = dial().radii
            val ring = if (arc.outer) radii.outer else radii.inner
            DialGeometry.point(ring, (arc.a0 + arc.a1) / 2) * unitPx(side)
        }
}
