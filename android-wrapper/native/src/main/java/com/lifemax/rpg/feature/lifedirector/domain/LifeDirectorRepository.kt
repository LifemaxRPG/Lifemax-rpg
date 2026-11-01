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

    /** Show another section (phase 3A); read-only, the page renders it underneath too. */
    fun showSection(id: String): String

    /** A day from Week: Today on that day, as the page's own row does; read-only. */
    fun gotoDay(index: Int): String

    /**
     * A guide the screen drew was finished, or skipped (Skip, Back): the page records it as seen.
     */
    fun tourSeen(key: String, finished: Boolean): String

    /** What letting the handle [key] go at [place] would change: the page's confirmation. */
    fun dialDrop(key: String, place: DialPlace): String

    /** Change it: the page changes the rule, repairs the plan around it and saves. */
    fun dialApply(key: String, place: DialPlace): String
}
