package com.lifemax.rpg.feature.lifedirector.presentation.dial

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.changedToDown
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import com.lifemax.rpg.feature.lifedirector.domain.DialArc
import com.lifemax.rpg.feature.lifedirector.domain.DialHandle
import com.lifemax.rpg.feature.lifedirector.domain.DirectorDial
import com.lifemax.rpg.feature.lifedirector.presentation.DialHold
import com.lifemax.rpg.feature.lifedirector.presentation.components.DirectorBuzz

/**
 * What the dial's gestures read when a touch comes: the latest [dial], the handle held, whether the
 * handles wait ([inert]: the page is asked about one), the build playing and where to report.
 */
internal class DialInput(
    val dial: DirectorDial,
    val hold: DialHold?,
    val inert: Boolean,
    val run: BuildRun?,
    val actions: HandleActions,
    val onArc: (DialArc) -> Unit,
)

/**
 * The dial's touches (attachDial and dragOn in logic.js). A touch on a handle is the dial's from
 * the start, so neither the list's scroll nor a swipe takes it; it holds the handle, which stays on
 * its own stop until the finger has moved past the touch slop and then follows the finger from stop
 * to stop with a 6 ms buzz each. Letting go reports where; a touch the system takes away, or one
 * that never passed the slop, reports a cancel: a tap on a handle on a minute between the stops
 * (rules may sit on any minute) would otherwise ask to move it to the nearest. Any other touch is a
 * tap only when it stays put, so the list scrolls from anywhere else on the dial; a tap within 400
 * ms of a drag ending is ignored, as on the page. While a build plays, a touch skips it and is
 * swallowed, so the tap never opens a session.
 */
internal class DialGestures(
    private val scope: PointerInputScope,
    private val input: () -> DialInput,
    private val buzz: DirectorBuzz?,
) {
    private var dragEnd = Long.MIN_VALUE / 2

    suspend fun run() = scope.awaitEachGesture { gesture() }

    private suspend fun AwaitPointerEventScope.gesture() {
        val down =
            awaitPointerEvent(PointerEventPass.Initial).changes.firstOrNull { it.changedToDown() }
                ?: return
        val now = input()
        val unit = unitPx(size.width.toFloat())
        now.run?.let { run ->
            down.consume()
            run.skip()
            return
        }
        val radius = maxOf(DragGeometry.HIT_RADIUS, HIT_DP.toPx() / unit)
        val handle =
            if (now.inert || now.dial.stops.isEmpty()) null
            else
                DragGeometry.handleAt(
                    now.dial.handles,
                    now.dial.radii.outer,
                    down.position / unit,
                    radius,
                )
        if (handle != null) {
            down.consume()
            drag(now, handle, down, unit)
            return
        }
        if (down.uptimeMillis - dragEnd < TAP_PAUSE_MS) {
            down.consume()
            return
        }
        val up = waitForUpOrCancellation() ?: return
        val dial = input().dial
        DialGeometry.arcAt(dial.arcs, dial.radii, up.position / unit)?.let {
            up.consume()
            input().onArc(it)
        }
    }

    private suspend fun AwaitPointerEventScope.drag(
        now: DialInput,
        handle: DialHandle,
        down: PointerInputChange,
        unit: Float,
    ) {
        val stops = now.dial.stops
        var index = DragGeometry.stopOf(handle, stops)
        now.actions.hold(DialHold(handle, stops, now.dial.dragWords, index, held = true))
        var moved = false
        var cancelled = true
        var last = down.uptimeMillis
        try {
            while (true) {
                val event = awaitPointerEvent()
                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                last = change.uptimeMillis
                if (!change.pressed) {
                    // a cancel arrives as a release already consumed
                    cancelled = change.isConsumed
                    change.consume()
                    break
                }
                if (
                    !moved &&
                        (change.position - down.position).getDistance() >
                            viewConfiguration.touchSlop
                )
                    moved = true
                if (moved) {
                    val i = DragGeometry.stopAt(stops, DialGeometry.angleAt(change.position / unit))
                    if (i != index) {
                        index = i
                        buzz?.buzz(BUZZ_MS)
                        now.actions.move(i)
                    }
                }
                event.changes.forEach { it.consume() }
            }
        } finally {
            dragEnd = last
            now.actions.release(cancelled || !moved)
        }
    }

    private companion object {
        /** A handle's touch target, at least 48 dp across however small the dial is drawn. */
        val HIT_DP = 24.dp

        /** How long after a drag a tap is ignored (ui.dragEnd on the page). */
        const val TAP_PAUSE_MS = 400L

        /** The page's buzz as a held handle passes a stop. */
        const val BUZZ_MS = 6L
    }
}

/**
 * One 48 dp node per handle for TalkBack (the web handle's role="slider"): its label and time, a
 * slider over the day's stops whose increment and decrement move it a quarter hour later or
 * earlier, as the web handle's arrow keys do, and the same two moves as custom actions. Each asks
 * the page first ([HandleActions.step]); nothing moves while the handles wait ([inert]).
 */
@Composable
internal fun HandleTargets(
    dial: DirectorDial,
    hold: DialHold?,
    inert: Boolean,
    actions: HandleActions,
) {
    val stops = dial.stops
    if (dial.handles.isEmpty() || stops.isEmpty()) return
    Layout(
        content = {
            dial.handles.forEach { h ->
                val own = DragGeometry.stopOf(h, stops)
                val named = h.aria.startsWith(h.label) && h.aria.length > h.label.length
                val step = { by: Int ->
                    if (!inert && hold == null) actions.step(h, by)
                    !inert && hold == null
                }
                Box(
                    Modifier.semantics {
                        contentDescription = if (named) h.label else h.aria
                        stateDescription =
                            if (named) h.aria.substring(h.label.length).trim() else stops[own].time
                        progressBarRangeInfo =
                            ProgressBarRangeInfo(
                                own.toFloat(),
                                0f..stops.lastIndex.toFloat(),
                                (stops.size - 2).coerceAtLeast(0),
                            )
                        setProgress { target ->
                            when {
                                target > own -> step(1)
                                target < own -> step(-1)
                                else -> false
                            }
                        }
                        customActions =
                            listOf(
                                CustomAccessibilityAction(LATER) { step(1) },
                                CustomAccessibilityAction(EARLIER) { step(-1) },
                            )
                    }
                )
            }
        },
        modifier = Modifier.fillMaxSize(),
    ) { measurables, constraints ->
        val target = 48.dp.roundToPx()
        val scale = unitPx(constraints.maxWidth.toFloat())
        val placeables = measurables.map { it.measure(Constraints.fixed(target, target)) }
        layout(constraints.maxWidth, constraints.maxHeight) {
            placeables.forEachIndexed { i, placeable ->
                val at = DialGeometry.point(dial.radii.outer, dial.handles[i].a) * scale
                placeable.place(at.x.toInt() - target / 2, at.y.toInt() - target / 2)
            }
        }
    }
}

private const val LATER = "Move 15 minutes later"
private const val EARLIER = "Move 15 minutes earlier"
