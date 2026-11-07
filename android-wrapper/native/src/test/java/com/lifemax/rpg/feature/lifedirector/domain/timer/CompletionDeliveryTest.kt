package com.lifemax.rpg.feature.lifedirector.domain.timer

import org.junit.Assert.*
import org.junit.Test

class CompletionDeliveryTest {
    private class Disk : TimerRepository.Journal {
        var state: TimerRepository.Snapshot? = null
        var fail = false

        override fun read() = state

        override fun write(snapshot: TimerRepository.Snapshot) {
            if (fail) error("disk full")
            state = snapshot
        }
    }

    private fun ready(disk: Disk): TimerRepository {
        val repo = TimerRepository(disk, "epoch")
        repo.dispatch(
            TimerRepository.Request(
                "epoch",
                1,
                1000,
                TimerEngine.Command.Start(TimerEngine.Target("walk", 60000)),
            )
        )
        repo.dispatch(
            TimerRepository.Request(
                "epoch",
                2,
                61000,
                TimerEngine.Command.Finish(TimerEngine.Key("walk", 1000), true),
            )
        )
        return repo
    }

    @Test
    fun timeoutThenRestartReusesExactCompletionIdentity() {
        val disk = Disk()
        val first = ready(disk)
        var sent: TimerRepository.Pending? = null
        val failed =
            CompletionDelivery(first) {
                sent = it
                error("lost ACK")
            }
        assertEquals(CompletionDelivery.Outcome.RETRY, failed.attempt())
        val restarted = TimerRepository(disk, "epoch")
        val delivered =
            CompletionDelivery(restarted) {
                assertEquals(sent, it)
                CompletionDelivery.Reply(CompletionDelivery.RemoteStatus.COMMITTED, it)
            }
        assertEquals(CompletionDelivery.Outcome.ACKNOWLEDGED, delivered.attempt())
        assertEquals(CompletionDelivery.Outcome.EMPTY, delivered.attempt())
    }

    @Test
    fun wrongEpochOrPayloadCannotAcknowledge() {
        val repo = ready(Disk())
        val wrongEpoch =
            CompletionDelivery(repo) {
                CompletionDelivery.Reply(
                    CompletionDelivery.RemoteStatus.COMMITTED,
                    it.copy(epoch = "old"),
                )
            }
        assertEquals(CompletionDelivery.Outcome.NEEDS_REVIEW, wrongEpoch.attempt())
        val wrongMinutes =
            CompletionDelivery(repo) {
                CompletionDelivery.Reply(
                    CompletionDelivery.RemoteStatus.COMMITTED,
                    it.copy(completion = it.completion.copy(minutes = 2)),
                )
            }
        assertEquals(CompletionDelivery.Outcome.NEEDS_REVIEW, wrongMinutes.attempt())
        assertEquals(1, repo.snapshot().outbox.size)
    }

    @Test
    fun refusedOrUnknownResultsRetainTheCompletion() {
        val repo = ready(Disk())
        for (status in
            CompletionDelivery.RemoteStatus.values().filter {
                it != CompletionDelivery.RemoteStatus.COMMITTED
            }) {
            val delivery = CompletionDelivery(repo) { CompletionDelivery.Reply(status, it) }
            assertNotEquals(CompletionDelivery.Outcome.ACKNOWLEDGED, delivery.attempt())
            assertEquals(1, repo.snapshot().outbox.size)
        }
    }

    @Test
    fun acknowledgementStorageFailureRetainsCompletionForReconciliation() {
        val disk = Disk()
        val repo = ready(disk)
        val delivery =
            CompletionDelivery(repo) {
                CompletionDelivery.Reply(CompletionDelivery.RemoteStatus.COMMITTED, it)
            }
        disk.fail = true
        assertEquals(CompletionDelivery.Outcome.STORAGE_UNKNOWN, delivery.attempt())
        assertEquals(CompletionDelivery.Outcome.STORAGE_UNKNOWN, delivery.attempt())
        disk.fail = false
        repo.reconcile()
        assertEquals(1, repo.snapshot().outbox.size)
        assertEquals(CompletionDelivery.Outcome.ACKNOWLEDGED, delivery.attempt())
    }
}
