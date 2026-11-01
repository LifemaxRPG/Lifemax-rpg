package com.lifemax.rpg.feature.lifedirector.presentation

import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.changedToDownIgnoreConsumed
import androidx.compose.ui.input.pointer.positionChanged
import com.lifemax.rpg.feature.lifedirector.domain.SectionFlick

/**
 * A sideways flick over the screen, between sections ([SectionFlick]). It reads the drag after the
 * screen's own parts (main pass, parent last): a drag one of them took (a dial handle, the strip,
 * the tabs, the list scrolling) is theirs. Once the finger has clearly gone sideways the flick
 * takes the drag, so a row under the finger does not also take it as a tap.
 */
internal suspend fun PointerInputScope.detectSectionFlick(flick: (Int) -> Unit) =
    awaitPointerEventScope {
        while (true) {
            val down =
                awaitPointerEvent().changes.firstOrNull { it.changedToDownIgnoreConsumed() }
                    ?: continue
            val start = down.position
            val startMs = down.uptimeMillis
            var mine = false
            var theirs = false
            var end = start
            var endMs = startMs
            while (true) {
                val c = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
                if (!mine && c.isConsumed && c.positionChanged()) theirs = true
                end = c.position
                endMs = c.uptimeMillis
                val d = end - start
                if (
                    !theirs &&
                        !mine &&
                        kotlin.math.abs(d.x) > viewConfiguration.touchSlop &&
                        kotlin.math.abs(d.x) > kotlin.math.abs(d.y) * 1.6f
                )
                    mine = true
                if (mine) c.consume()
                if (!c.pressed) break
            }
            if (theirs || !mine) continue
            val dx = (end.x - start.x).toDp().value
            val dy = (end.y - start.y).toDp().value
            val v = dx / ((endMs - startMs).coerceAtLeast(1) / 1000f)
            val dir = SectionFlick.decide(dx, dy, v)
            if (dir != 0) flick(dir)
        }
    }

/** Tells [touched] of every finger that comes down on the screen, taking nothing from it. */
internal suspend fun PointerInputScope.observeTouches(touched: () -> Unit) =
    awaitPointerEventScope {
        while (true) {
            val event = awaitPointerEvent(PointerEventPass.Initial)
            if (event.changes.any { it.changedToDownIgnoreConsumed() }) touched()
        }
    }
