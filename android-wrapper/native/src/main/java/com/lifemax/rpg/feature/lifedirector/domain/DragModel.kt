package com.lifemax.rpg.feature.lifedirector.domain

import kotlin.math.abs
import kotlin.math.hypot

/** A point on the screen, in pixels from the top left of the list that fills it. */
data class DragPoint(val x: Float, val y: Float)

/** The trash target's circle: where it is and how big, in the same pixels. */
data class DragTarget(val centre: DragPoint, val radius: Float)

/** Where a session was lifted from: a row of the running order, or its segment on the dial. */
enum class DragSource {
    ROW,
    DIAL,
}

/**
 * A session lifted off the day: the row it is (a segment on the dial lifts the row of the same
 * session), where the finger is, whether the finger is over the trash ([armed]), and where it was
 * lifted from ([source]).
 */
data class Lifted(
    val row: OrderRow,
    val finger: DragPoint,
    val armed: Boolean = false,
    val source: DragSource = DragSource.ROW,
)

/** What letting go means. */
sealed interface DragEnd {
    /** over the trash: take the session off the day */
    data class Remove(val id: String) : DragEnd

    /** anywhere else, or cancelled: the session goes back where it was and nothing changes */
    data object PutBack : DragEnd
}

/**
 * The arithmetic of lifting a session off the running order and dropping it on the trash, with no
 * view in it so every rule can be tested on its own: when a touch becomes a lift, where the trash
 * is, when the finger is over it (with a margin to leave it by, so it does not flicker on its
 * edge), how fast the list scrolls near its edges, and what letting go or cancelling does.
 */
object DragModel {
    /** How long a finger must stay put on a row to lift it. */
    const val LIFT_MS = 350L

    /** The trash circle's diameter, and how far above the bottom bars its foot sits, in dp. */
    const val TARGET_DP = 76f
    const val TARGET_LIFT_DP = 28f

    /** How far beyond the circle the finger counts as over it, and how far it must go to leave. */
    const val ENTER_DP = 14f
    const val EXIT_DP = 40f

    /**
     * The band at each edge of the list in which it scrolls, and its fastest speed (dp per second).
     */
    const val EDGE_DP = 80f
    const val SCROLL_DP_S = 900f

    /** Whether a finger down for [heldMs] that has moved [movedPx] has lifted its row. */
    fun lifts(heldMs: Long, movedPx: Float, slopPx: Float): Boolean =
        heldMs >= LIFT_MS && movedPx <= slopPx

    /**
     * Where the trash sits on a list [width] by [height] pixels with [bottomInset] of bars under
     * it: centred, its foot [TARGET_LIFT_DP] above the bars. [density] is pixels per dp.
     */
    fun target(width: Float, height: Float, bottomInset: Float, density: Float): DragTarget {
        val radius = TARGET_DP * density / 2
        val foot = height - bottomInset - TARGET_LIFT_DP * density
        return DragTarget(DragPoint(width / 2, foot - radius), radius)
    }

    /**
     * Whether the finger is over the trash. Not over it yet ([was] false) it must come within
     * [ENTER_DP] of the circle; over it, it stays over until it is [EXIT_DP] away.
     */
    fun hovers(was: Boolean, finger: DragPoint, target: DragTarget, density: Float): Boolean {
        val away = hypot(finger.x - target.centre.x, finger.y - target.centre.y)
        val reach = (if (was) EXIT_DP else ENTER_DP) * density
        return away <= target.radius + reach
    }

    /**
     * How far to scroll per second, in pixels, for a finger at [y] on a list whose scrolling part
     * runs from [top] to [bottom]: nothing in the middle, up to [SCROLL_DP_S] at the very edge,
     * negative (towards the top) at the top. The speed grows with the square of how deep the finger
     * is in the band, so it eases in. Over the trash ([armed]) the list holds still.
     */
    fun scroll(y: Float, top: Float, bottom: Float, armed: Boolean, density: Float): Float {
        if (armed) return 0f
        val edge = EDGE_DP * density
        val depth =
            when {
                y < top + edge -> -((top + edge - y) / edge)
                y > bottom - edge -> (y - (bottom - edge)) / edge
                else -> 0f
            }.coerceIn(-1f, 1f)
        val eased = depth * abs(depth)
        return eased * SCROLL_DP_S * density
    }

    /** Lifts [row] with the finger at [finger]. */
    fun lift(row: OrderRow, finger: DragPoint, source: DragSource = DragSource.ROW) =
        Lifted(row, finger, source = source)

    /**
     * The finger moved: the chip follows it (see [DragChip]), and the trash is armed by [hovers].
     */
    fun move(lifted: Lifted, finger: DragPoint, target: DragTarget, density: Float) =
        lifted.copy(finger = finger, armed = hovers(lifted.armed, finger, target, density))

    /** The finger let go: over the trash it removes the session, anywhere else it goes back. */
    fun release(lifted: Lifted): DragEnd =
        if (lifted.armed) DragEnd.Remove(lifted.row.tap?.id.orEmpty()) else DragEnd.PutBack

    /** Cancelled (Back, a second finger, the screen leaving): always back, never a removal. */
    fun cancel(): DragEnd = DragEnd.PutBack
}
