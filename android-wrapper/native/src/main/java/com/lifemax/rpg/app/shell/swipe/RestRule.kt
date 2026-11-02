package com.lifemax.rpg.app.shell.swipe

/**
 * When a screen comes to rest (CONTRACT A35 item 2): it was drawn ([before] Shown) and is no longer
 * ([after] Parked or Gone) while it is not [open]. A screen still sliding out after its close stays
 * Shown until it is off the screen, so it does not rest mid-slide; an open screen never rests. A
 * private tab drops its data here, since a swipe may draw a hidden screen (A34).
 */
fun rests(before: PaneVisibility?, after: PaneVisibility, open: Boolean): Boolean =
    before == PaneVisibility.Shown && after != PaneVisibility.Shown && !open

/** The panes that come to rest going from scene [before] to [after], with [open] the open ones. */
fun StageScene.restedSince(before: StageScene?, open: Collection<String>): Set<String> =
    panes
        .filter { (pane, stage) ->
            rests(before?.panes?.get(pane)?.visibility, stage.visibility, pane in open)
        }
        .keys
