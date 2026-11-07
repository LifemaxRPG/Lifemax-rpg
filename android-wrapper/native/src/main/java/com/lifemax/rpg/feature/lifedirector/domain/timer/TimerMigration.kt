package com.lifemax.rpg.feature.lifedirector.domain.timer

/**
 * Import preparation only. The three-store authority handshake must complete before controls run.
 */
object TimerMigration {
    data class LegacyFocus(
        val activityId: String,
        val startMs: Long,
        val pausedAtMs: Long? = null,
        val pausedMs: Long = 0,
        val plannedMs: Long? = null,
    )

    fun snapshot(
        epoch: String,
        focus: LegacyFocus?,
        target: TimerEngine.Target?,
    ): TimerRepository.Snapshot {
        if (focus == null) return TimerRepository.Snapshot(epoch)
        requireNotNull(target) { "Cannot silently discard an orphaned legacy timer" }
        require(target.available && target.activityId == focus.activityId)
        val timer =
            TimerEngine.Timer(
                focus.activityId,
                focus.startMs,
                focus.plannedMs ?: target.plannedMs,
                focus.pausedAtMs,
                focus.pausedMs,
            )
        return TimerRepository.Snapshot(epoch, lastGeneration = timer.generation, timer = timer)
    }

    /** Never replaces a different journal. A lost write ACK can retry the identical import. */
    fun prepare(
        journal: TimerRepository.Journal,
        imported: TimerRepository.Snapshot,
    ): TimerRepository {
        require(
            imported.lastSequence == 0L && imported.outbox.isEmpty() && imported.receipts.isEmpty()
        )
        val existing = journal.read()
        if (existing == null) journal.write(imported)
        else require(existing == imported) { "Existing timer journal needs reconciliation" }
        return TimerRepository(journal, imported.epoch)
    }
}
