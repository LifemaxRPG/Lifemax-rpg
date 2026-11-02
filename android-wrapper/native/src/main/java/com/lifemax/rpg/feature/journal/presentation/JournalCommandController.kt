package com.lifemax.rpg.feature.journal.presentation

import com.lifemax.rpg.feature.journal.domain.JournalCommand
import com.lifemax.rpg.feature.journal.domain.JournalCommands
import com.lifemax.rpg.feature.journal.domain.JournalReply

/**
 * The Journal's commands in flight, by request id. Phase 1's commands are safe to repeat on the
 * page (a select of the selected day writes nothing, a swap is compare-and-set, the tip's dismissal
 * and an unlock are idempotent), so a lost reply needs no operation id: the next projection says
 * what the page holds, and a retry is a new request. The page answers only while the screen is
 * open, so an opening forgets what was in flight ([reset]).
 */
internal class JournalCommandController(
    private val commands: JournalCommands,
    private val newId: () -> String,
) {
    private val pending = LinkedHashMap<String, JournalCommand>()

    /** Sends [command]; answers its request id. */
    fun send(command: JournalCommand): String {
        val id = newId()
        pending[id] = command
        commands.send(command, id)
        return id
    }

    /** The command [reply] answers, no longer in flight; null when it is not one of ours. */
    fun answered(reply: JournalReply): JournalCommand? = pending.remove(reply.requestId)

    /** Whether a command like [kind] is still in flight. */
    fun waiting(kind: (JournalCommand) -> Boolean): Boolean = pending.values.any(kind)

    fun reset() = pending.clear()
}
