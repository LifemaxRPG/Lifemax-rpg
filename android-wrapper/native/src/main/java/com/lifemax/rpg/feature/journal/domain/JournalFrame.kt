package com.lifemax.rpg.feature.journal.domain

/**
 * What the Journal's screen holds (CONTRACT A35, plan §3.2.4). Only [Open] has a field able to hold
 * the owner's journal: [Locked] and [Resting] keep the chrome alone (the switch, the lock's words,
 * the palette), so a locked or resting screen cannot draw private words, whatever reached it.
 */
sealed interface JournalFrame {
    val chrome: JournalChrome

    /** The page's open frame: the Journal, unlocked or with no lock. */
    data class Open(
        override val chrome: JournalChrome,
        val journal: OpenJournal,
        val notice: JournalNotice?,
    ) : JournalFrame

    /**
     * The lock card: from the page's withheld frame, with the prompt's [status], or made on the
     * screen when the app stopped with the lock on (idle, no notice).
     */
    data class Locked(
        override val chrome: JournalChrome,
        val status: LockStatus,
        val notice: JournalNotice?,
    ) : JournalFrame

    /** A screen not on show: the switch on the page background, and nothing else. */
    data class Resting(override val chrome: JournalChrome) : JournalFrame
}
