package com.lifemax.rpg.feature.lifedirector.presentation.glance

import com.lifemax.rpg.feature.lifedirector.domain.DialArc
import com.lifemax.rpg.feature.lifedirector.domain.Tap

/*
 * What a tap on the glance sends. Every tap goes to the page as the
 * Overview's `plan {act, id}` command (LifeDirector.native.fromGlance), which opens Life Director's
 * Today and repeats the tap there, so Life Director opens on the right sheet.
 */
internal object GlanceTaps {
    /** The head's link, the set-up button, and anywhere else on the card: only opens. */
    val open = Tap("glanceOpen")

    /**
     * An arc: its own tap (a session, a meal), or, for fixed time (whose tip the page shows only on
     * Life Director itself), just opening.
     */
    fun arc(arc: DialArc): Tap = arc.tap ?: open

    /**
     * A tap in the middle: the page's glance lets only the running timer's clock take one
     * (.ld-glance .ld-core{pointer-events:none}, the clock set back to auto); anything else lands
     * on the dial behind it, which only opens.
     */
    fun centre(tap: Tap): Tap = if (tap.act == TIMER) tap else open

    const val TIMER = "focusOpen"
}
