package com.lifemax.rpg.feature.library.domain

import kotlinx.coroutines.flow.StateFlow

interface MediaRepository {
    val state: StateFlow<MediaProjection?>
}

/**
 * What the screen hands to the page. The screen closes, the page runs its own handler (the form, a
 * confirm, the player, a file button of its own) and brings the screen back when it is done.
 */
sealed interface MediaAction {
    data object Add : MediaAction

    data object DismissTip : MediaAction

    /** Another pane of the Library, by its page view. */
    data class OpenPane(val id: String) : MediaAction

    data class Edit(val id: String) : MediaAction

    data class SetFinished(val id: String, val finished: Boolean) : MediaAction

    data class Remove(val id: String) : MediaAction

    data class Play(val id: String) : MediaAction

    data class Attach(val id: String) : MediaAction

    data class Replace(val id: String) : MediaAction

    data class RemoveAudio(val id: String) : MediaAction

    data class OpenLink(val id: String) : MediaAction
}
