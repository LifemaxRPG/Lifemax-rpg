package com.lifemax.rpg.feature.lifedirector.domain

import kotlinx.coroutines.flow.StateFlow

interface LifeDirectorRepository {
    val state: StateFlow<LifeDirectorProjection?>
}

/** What the screen asks of the page without closing. The page answers with a new projection. */
interface LifeDirectorCommands {
    /** Show another day of the strip; read-only. */
    fun showDay(index: Int)
}
