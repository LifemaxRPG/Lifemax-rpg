package com.lifemax.rpg.feature.settings.domain

import kotlinx.coroutines.flow.StateFlow

interface SettingsRepository {
    val state: StateFlow<SettingsProjection?>
}

/** How the screen reaches the page while it stays: the plugin sends [command] as [requestId]. */
fun interface SettingsCommands {
    fun send(command: SettingsCommand, requestId: String)
}

/**
 * What the screen asks the page to do while it stays: the page runs its own control's handler, as a
 * tap there would, and answers with a [SettingsReply].
 */
sealed interface SettingsCommand {
    /** The command's name on the page (50-native-settings.js commands). */
    val name: String

    /** Set a switch to [on]. */
    data class Toggle(val id: String, val on: Boolean) : SettingsCommand {
        override val name = "toggle"
    }

    /** Set a select or the haptic strength to [value], one of its options. */
    data class Choose(val id: String, val value: String) : SettingsCommand {
        override val name = "choose"
    }

    /** Press a button: a bar row's arrow, "Reset to default", a haptic try. */
    data class Press(val id: String) : SettingsCommand {
        override val name = "press"
    }

    /** Select a category chip, which the page keeps. */
    data class Category(val id: String) : SettingsCommand {
        override val name = "category"
    }
}

/** What the page does after the screen has closed. */
sealed interface SettingsPageAction {
    /** Show the control (or card) with this id on the page, focused, for the owner to use. */
    data class Reveal(val id: String) : SettingsPageAction

    /** Stay on the classic page until the owner leaves Settings. */
    data object Classic : SettingsPageAction
}

/**
 * The page's answer to a command, delivered with the fresh projection. [message] is the page's own
 * toast or its reason for refusing; [saved] is false when the handler's write did not reach the
 * device; [reveal] names a control the page must show itself (a switch Android asks about first).
 */
data class SettingsReply(
    val command: String,
    val requestId: String,
    val ok: Boolean,
    val changed: Boolean,
    val message: String,
    val saved: Boolean,
    val reveal: String?,
)
