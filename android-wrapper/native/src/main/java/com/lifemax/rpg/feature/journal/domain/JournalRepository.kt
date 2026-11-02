package com.lifemax.rpg.feature.journal.domain

import kotlinx.coroutines.flow.StateFlow

interface JournalRepository {
    /** The frame the screen draws; null before the page's first. */
    val state: StateFlow<JournalFrame?>
}

/**
 * A command the screen sends while it stays (plan §3.4, phase 1). The page answers each with {ok,
 * changed?, message?}; every one but [Unlock] is refused while the lock blocks.
 */
sealed interface JournalCommand {
    /** The wire name (50-native-journal.js commands). */
    val name: String

    /** Select a day or a review; the page decides it and redraws only its own entry. */
    data class Select(val key: String) : JournalCommand {
        override val name = "select"
    }

    /** Write in the page's field for [key]: the page focuses it and the screen steps aside. */
    data class Write(val key: String) : JournalCommand {
        override val name = "write"
    }

    /** Swap day [d]'s question, only if it is still [from] (compare-and-set). */
    data class Swap(val d: String, val from: String) : JournalCommand {
        override val name = "swap"
    }

    /** Ask Android to unlock (the page relocks first if it thought it was open). */
    data object Unlock : JournalCommand {
        override val name = "unlock"
    }

    /** Export a backup (Save as); answered at once, its toast comes later as a notice. */
    data object Backup : JournalCommand {
        override val name = "backup"
    }

    /** Dismiss the Journal's feature tip. */
    data object DismissTip : JournalCommand {
        override val name = "tip"
    }
}

/** How the screen reaches the page while it stays; the reply names [requestId]. */
fun interface JournalCommands {
    fun send(command: JournalCommand, requestId: String)
}

/** The page's answer to a command (NativeTabKit adds the command and the request id). */
data class JournalReply(
    val command: String,
    val requestId: String,
    val ok: Boolean,
    val changed: Boolean,
    val message: String,
)

/**
 * What the screen hands to the page and steps aside for (plan §3.4 actions): the kit closes the
 * screen, the page runs, and the screen comes back when it is done.
 */
sealed interface JournalAction {
    /** A welcome chip that pays: "pet" or "fresh". */
    data class Claim(val id: String) : JournalAction

    /** The one-sentence catch-up (the page's own prompt). */
    data object CatchUp : JournalAction

    /** Another page view: Media through the switch, Life Director through its chip. */
    data class OpenView(val view: String) : JournalAction
}
