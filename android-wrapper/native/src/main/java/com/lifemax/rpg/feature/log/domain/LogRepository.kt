package com.lifemax.rpg.feature.log.domain

import kotlinx.coroutines.flow.StateFlow

interface LogRepository {
    val state: StateFlow<LogProjection?>
}

/** What the screen asks the page to do. The page answers with a new projection. */
interface LogCommands {
    /** Ask for the award a draft would earn. */
    fun preview(draft: LogDraft)

    /** Save the activity. [operationId] makes a repeated submit pay out once. */
    fun submit(draft: LogDraft, operationId: String)
}
