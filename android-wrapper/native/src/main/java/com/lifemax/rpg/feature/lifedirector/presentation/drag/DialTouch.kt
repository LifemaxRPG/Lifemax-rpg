package com.lifemax.rpg.feature.lifedirector.presentation.drag

import com.lifemax.rpg.feature.lifedirector.domain.DragModel
import com.lifemax.rpg.feature.lifedirector.domain.DragPoint
import com.lifemax.rpg.feature.lifedirector.domain.OrderRow
import kotlin.math.hypot

/**
 * Who a finger that comes down on the dial belongs to, decided with no view in it so every rule can
 * be tested on its own. The dial has three uses for a touch, told apart in this order:
 * 1. A finger that lands on a drag handle drags the handle, whatever else is under it
 *    ([Start.Handle]).
 * 2. A finger that lands on a session's segment, which the page has an answer about, is a hold in
 *    waiting ([Start.Hold]): it lifts the session if it stays put for [DragModel.LIFT_MS], and is a
 *    tap on the segment if it lets go first ([PendingHold]).
 * 3. Any other finger is the dial's plain touch ([Start.Plain]): a tap on an arc when it stays put,
 *    and otherwise the list's to scroll.
 */
internal object DialTouch {
    /** [press] is what a hold on the segment under the finger would mean (Ignore: no segment). */
    fun start(onHandle: Boolean, press: Press): Start =
        when {
            onHandle -> Start.Handle
            press is Press.Ignore -> Start.Plain
            else -> Start.Hold
        }
}

/** What a finger that has come down on the dial is for. */
internal sealed interface Start {
    data object Handle : Start

    data object Plain : Start

    /** a session's segment to lift, or to refuse, once the hold is long enough */
    data object Hold : Start
}

/** What happens to a finger that is waiting to lift a session. */
internal sealed interface HoldEvent {
    data class Moved(val to: DragPoint) : HoldEvent

    data class Released(val at: DragPoint) : HoldEvent

    /** the touch was cancelled, or another part of the screen took it (a scroll, a swipe) */
    data object Taken : HoldEvent

    data object SecondFinger : HoldEvent

    /** the finger has stayed put for [DragModel.LIFT_MS] */
    data object Elapsed : HoldEvent
}

/** What a waiting hold turns into. */
internal sealed interface HoldVerdict {
    data object Waiting : HoldVerdict

    /** held long enough on a session that can be removed: lift its [row] */
    data class Lifts(val row: OrderRow) : HoldVerdict

    /** held long enough on a session that cannot be removed: the "not now" buzz, and why */
    data class Refuses(val reason: String) : HoldVerdict

    /** let go before the hold was long enough: a tap on the segment, as ever */
    data class Tap(val at: DragPoint) : HoldVerdict

    /**
     * Moved beyond the touch slop, was taken, or joined by a second finger before the hold was long
     * enough: no lift, and the touch goes on as it would have without one.
     */
    data object Yields : HoldVerdict
}

/**
 * A finger waiting on a session's segment since it came down at [down]. It must stay within [slop]
 * (the touch slop, pixels) or the lift is off; a verdict, once given, is final. What the hold means
 * is asked of [press] when it is long enough, not before: the page may have changed its mind.
 */
internal class PendingHold(
    private val down: DragPoint,
    private val slop: Float,
    private val press: () -> Press,
) {
    private var verdict: HoldVerdict = HoldVerdict.Waiting

    fun on(event: HoldEvent): HoldVerdict {
        if (verdict != HoldVerdict.Waiting) return verdict
        verdict =
            when (event) {
                is HoldEvent.Moved ->
                    if (hypot(event.to.x - down.x, event.to.y - down.y) > slop) HoldVerdict.Yields
                    else HoldVerdict.Waiting
                is HoldEvent.Released -> HoldVerdict.Tap(event.at)
                HoldEvent.Taken,
                HoldEvent.SecondFinger -> HoldVerdict.Yields
                HoldEvent.Elapsed ->
                    when (val now = press()) {
                        is Press.Lift -> HoldVerdict.Lifts(now.row)
                        is Press.Refuse -> HoldVerdict.Refuses(now.reason)
                        Press.Ignore -> HoldVerdict.Yields
                    }
            }
        return verdict
    }
}
