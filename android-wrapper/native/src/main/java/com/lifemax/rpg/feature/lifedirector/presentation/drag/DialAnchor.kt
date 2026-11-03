package com.lifemax.rpg.feature.lifedirector.presentation.drag

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.LayoutCoordinates

/**
 * Where the dial is on the screen, so a finger on it can carry a card over the whole screen. The
 * overlay that draws the card and the dial each report their place; [frame] turns a point on the
 * dial into a point on the overlay (the pixels the controller works in), wherever the list has been
 * scrolled to. [centres] says where the dial draws each session's segment, for the card to settle
 * back into.
 */
internal class DialAnchor {
    private var overlay: LayoutCoordinates? = null
    private var dial: LayoutCoordinates? = null
    private var centres: (String) -> Offset? = { null }

    fun overlayAt(coordinates: LayoutCoordinates) {
        overlay = coordinates
    }

    /** [centres] gives a session's segment centre in the dial's own pixels. */
    fun dialAt(coordinates: LayoutCoordinates, centres: (String) -> Offset?) {
        dial = coordinates
        this.centres = centres
    }

    /** The overlay's pixel for a point of the dial, or the point itself while either is unknown. */
    fun frame(local: Offset): Offset {
        val over = overlay?.takeIf { it.isAttached } ?: return local
        val on = dial?.takeIf { it.isAttached } ?: return local
        return over.localPositionOf(on, local)
    }

    /** Where the segment of the session with this [id] is on the overlay, if the dial is on it. */
    fun home(id: String): Offset? =
        if (dial?.isAttached == true) centres(id)?.let(::frame) else null
}
