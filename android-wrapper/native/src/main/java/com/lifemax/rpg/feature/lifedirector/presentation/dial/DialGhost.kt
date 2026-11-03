package com.lifemax.rpg.feature.lifedirector.presentation.dial

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import com.lifemax.rpg.feature.lifedirector.domain.ArcStyle
import com.lifemax.rpg.feature.lifedirector.domain.DialArc
import com.lifemax.rpg.feature.lifedirector.domain.DragSource
import com.lifemax.rpg.feature.lifedirector.presentation.drag.DragRemoveController
import com.lifemax.rpg.feature.lifedirector.presentation.drag.Held
import kotlinx.coroutines.flow.MutableStateFlow

/** How much of a lifted session's segment is left on the dial: a ghost of it, where it was. */
internal const val GHOST_ALPHA = .28f

/**
 * The id of the session whose segment is lifted off the dial now, from the lift until its card has
 * settled back into the segment (or the page has taken the session off the day). A session lifted
 * from its row leaves the dial as it is. Null with no [removal] (the glance's dial) or nothing
 * lifted from the dial.
 */
@Composable
internal fun rememberGhost(removal: DragRemoveController?): String? {
    val held by
        remember(removal) { removal?.held ?: MutableStateFlow<Held?>(null) }.collectAsState()
    return held?.takeIf { it.source == DragSource.DIAL }?.row?.tap?.id
}

/** Whether [arc] is a session's segment that the page lets be taken off the day. */
internal fun DialArc.removable(removal: DragRemoveController): Boolean =
    style == ArcStyle.SESSION && removal.session(tap?.id.orEmpty())?.removal?.ok == true
