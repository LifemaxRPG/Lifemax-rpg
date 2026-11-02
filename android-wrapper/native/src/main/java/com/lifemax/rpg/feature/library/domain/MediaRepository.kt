package com.lifemax.rpg.feature.library.domain

import kotlinx.coroutines.flow.StateFlow

interface MediaRepository {
    val state: StateFlow<MediaProjection?>
}

/**
 * What the screen asks of the audio dock and the native file picker, where the owner has them on:
 * Play then starts the book in the dock without the screen closing, and Attach or Replace opens
 * Android's picker. Without it (null, or [dock] false) every tap is handed to the page as before.
 */
interface MediaAudioPort {
    /** The dock is on: Play is a command. */
    val dock: Boolean

    /** The native picker may open for Attach and Replace. */
    val canPick: Boolean

    fun play(id: String)

    fun pick(id: String)
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
