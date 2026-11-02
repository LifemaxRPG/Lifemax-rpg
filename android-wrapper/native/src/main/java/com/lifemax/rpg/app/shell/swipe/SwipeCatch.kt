package com.lifemax.rpg.app.shell.swipe

import kotlin.math.abs
import kotlin.math.roundToInt

/** The screen a finger may take hold of now, and where it stands ([tx], whole px from rest). */
data class Catching(val pane: String, val tx: Int)

/**
 * Chained swipes (CONTRACT A34 item 8): a finger that lands on a screen still moving with no finger
 * on it takes hold of it where it stands, and the drag goes on from there through the curve's
 * inverse, so nothing jumps. Two moments allow it, and no other:
 * - a cancel settling back to rest: the source is still the open screen, and is caught again;
 * - a commit whose hand-over is done (the destination open and drawn, the source closed) and whose
 *   screen is still sliding in: the destination is now the open screen and the new swipe's source.
 *   A commit still waiting for the page is not caught: the page has not decided yet, and a swipe
 *   moves pixels, never ownership.
 *
 * The caught screen must be the only open one, the page's latest state must allow a swipe from it,
 * and the slide must not have landed.
 */
fun SwipeModel.catching(env: SwipeEnv): Catching? {
    val p = phase as? SwipePhase.Sliding ?: return null
    val s = state?.takeIf { it.on && it.motion } ?: return null
    if (p.slide.landed(env)) return null
    val commit = p.commit
    val offset = p.slide.offset(env)
    val dir = p.slide.dir
    val sourceTx = (-dir * offset * env.width).roundToInt()
    val holder: String
    val tx: Int
    when {
        commit == null -> {
            holder = p.gesture.source
            tx = sourceTx
        }
        commit.complete && !commit.reveal -> {
            holder = commit.dest
            tx = sourceTx + dir * env.width
        }
        else -> return null
    }
    val caught =
        open == listOf(holder) &&
            panes[holder] == true &&
            s.view == holder &&
            s.neighbours(holder, panes.keys) != null &&
            abs(tx) >= 1
    return if (caught) Catching(holder, tx) else null
}

/**
 * The finger takes hold of the screen [catching] names: a new drag from the page's latest state, on
 * the side the screen is displaced toward, holding it at the offset it has.
 */
internal fun SwipeModel.catchGrab(input: SwipeInput.Catch, env: SwipeEnv): SwipeStep {
    val caught = catching(env)
    val s = state
    if (caught == null || s == null || caught.pane != input.pane) return SwipeStep(this)
    val neighbours = s.neighbours(caught.pane, panes.keys) ?: return SwipeStep(this)
    val gesture = Gesture(caught.pane, neighbours, s.sequence, s.motion, s.haptics, s.tune)
    // a screen standing left of rest shows the next tab on its right: the drag is toward +1
    val dir = if (caught.tx < 0) 1 else -1
    val held =
        Tension.holding(
            abs(caught.tx).toFloat() / env.width,
            env.now,
            gesture.scale(env),
            gesture.tune,
            gesture.isWall(dir),
        )
    val anchorX = input.x + dir * held.u * env.width
    val drag = Drag(anchorX, dir, input.x, held.tension)
    return SwipeStep(copy(phase = SwipePhase.Dragging(gesture, drag)))
}
