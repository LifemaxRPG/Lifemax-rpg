package com.lifemax.rpg.feature.lifedirector.domain

import kotlinx.coroutines.flow.StateFlow

interface LifeDirectorRepository {
    val state: StateFlow<LifeDirectorProjection?>
}

/**
 * What the screen asks of the page without closing. The page answers each with a [DirectorReply]
 * carrying the returned id, together with the projection it produced.
 */
interface LifeDirectorCommands {
    /** Show another day of the strip; read-only. */
    fun showDay(index: Int): String

    /** What letting the handle [key] go at [place] would change: the page's confirmation. */
    fun dialDrop(key: String, place: DialPlace): String

    /** Change it: the page changes the rule, repairs the plan around it and saves. */
    fun dialApply(key: String, place: DialPlace): String
}
