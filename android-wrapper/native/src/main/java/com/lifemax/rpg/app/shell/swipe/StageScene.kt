package com.lifemax.rpg.app.shell.swipe

import kotlin.math.roundToInt

/** A screen's View visibility: VISIBLE, INVISIBLE (laid out, not drawn) or GONE. */
enum class PaneVisibility {
    Shown,
    Parked,
    Gone,
}

/**
 * Where one screen stands: its `translationX` in whole px, its visibility, whether its
 * `dispatchTouchEvent` swallows every touch ([shielded]), and whether it is hidden from
 * accessibility (`IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS`, for a screen that is not open).
 */
data class PaneStage(
    val tx: Int,
    val visibility: PaneVisibility,
    val shielded: Boolean = false,
    val a11yHidden: Boolean = false,
)

/**
 * The veil, VISIBLE at [tx]: the page colour, and the tab's icon and label unless [tab] is null.
 */
data class VeilStage(val tx: Int, val tab: SwipeTab?)

/**
 * Everything TabSwipe writes to the Views in one frame: a stage for every registered pane, and the
 * veil (null is GONE). Whole pixels: the neighbour stands at the owner's tx + dir·W, so the two
 * screens always meet with no seam.
 */
data class StageScene(val panes: Map<String, PaneStage>, val veil: VeilStage?)

/**
 * The scene at [env]'s time. While dragging it follows the finger (unless the page's motion is off,
 * when nothing moves until the release); while settling it follows the spring. At Idle and Armed it
 * is the rest scene ([restScene]).
 */
fun SwipeModel.scene(env: SwipeEnv): StageScene =
    when (val p = phase) {
        SwipePhase.Idle,
        is SwipePhase.Armed -> StageScene(restScene(env), null)
        is SwipePhase.Dragging ->
            if (!p.gesture.motion) StageScene(restScene(env), null)
            else sliding(env, p.gesture, p.drag.dir, p.drag.offset(env, p.gesture), dragging = true)
        is SwipePhase.Sliding -> {
            val landed = p.slide.landed(env)
            sliding(env, p.gesture, p.slide.dir, p.slide.offset(env), p.commit, landed)
        }
    }

/**
 * Every registered pane at rest: the open screens VISIBLE at 0 (the newest is on top); the composed
 * native neighbours of the owner parked INVISIBLE at ±W while the page allows a swipe with motion,
 * so a drag can show them without a first layout; the rest GONE. This is the Idle invariant
 * TabSwipe checks its Views against.
 */
fun SwipeModel.restScene(env: SwipeEnv): Map<String, PaneStage> {
    val parked = parking(env)
    return panes.keys.associateWith { pane ->
        when (pane) {
            in open -> PaneStage(0, PaneVisibility.Shown)
            in parked -> PaneStage(parked.getValue(pane), PaneVisibility.Parked, a11yHidden = true)
            else -> PaneStage(0, PaneVisibility.Gone, a11yHidden = true)
        }
    }
}

/** The owner's composed native neighbours that are not open, with their parked tx. */
private fun SwipeModel.parking(env: SwipeEnv): Map<String, Int> {
    val owner = owner ?: return emptyMap()
    val s = state?.takeIf { it.on && it.motion } ?: return emptyMap()
    val sides = s.neighbours(owner, panes.keys) ?: return emptyMap()
    return listOf(-1, 1)
        .mapNotNull { dir ->
            (sides[dir] as? Neighbour.Pane)
                ?.tab
                ?.view
                ?.takeIf { it !in open }
                ?.let { it to dir * env.width }
        }
        .toMap()
}

/**
 * The source at −dir·o·W and whatever lies toward [dir] one width further: the neighbour's screen,
 * or the veil. The source stays on screen until it is off it, even after its close ([landed]); a
 * screen opened cold stays INVISIBLE under the veil until it has drawn. Only a dragging source
 * takes touches.
 */
private fun SwipeModel.sliding(
    env: SwipeEnv,
    gesture: Gesture,
    dir: Int,
    offset: Float,
    commit: Commit? = null,
    landed: Boolean = false,
    dragging: Boolean = false,
): StageScene {
    val sourceTx = (-dir * offset * env.width).roundToInt()
    val slotTx = sourceTx + dir * env.width
    val stages = restScene(env).toMutableMap()
    fun place(pane: String, visibility: PaneVisibility, tx: Int, shielded: Boolean) {
        if (pane in panes) stages[pane] = PaneStage(tx, visibility, shielded, pane !in open)
    }
    if (!(landed && commit?.sourceClosed == true)) {
        place(gesture.source, PaneVisibility.Shown, sourceTx, shielded = !dragging)
    }
    val veil =
        when (val side = gesture.neighbours[dir]) {
            is Neighbour.Pane -> {
                place(side.tab.view, PaneVisibility.Shown, slotTx, shielded = true)
                if (side.tab.view in panes) null else VeilStage(slotTx, side.tab)
            }
            is Neighbour.Veil -> {
                val swapped = side.tab.view in panes && commit?.drawn == true
                val visibility = if (swapped) PaneVisibility.Shown else PaneVisibility.Parked
                place(side.tab.view, visibility, slotTx, shielded = true)
                if (swapped) null else VeilStage(slotTx, side.tab)
            }
            is Neighbour.Wall -> VeilStage(slotTx, null)
        }
    return StageScene(stages, veil)
}
