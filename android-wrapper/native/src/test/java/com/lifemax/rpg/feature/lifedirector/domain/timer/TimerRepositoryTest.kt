package com.lifemax.rpg.feature.lifedirector.domain.timer

import com.lifemax.rpg.feature.lifedirector.data.timer.FileTimerJournal
import java.nio.file.Files
import org.junit.Assert.*
import org.junit.Test

class TimerRepositoryTest {
    @Test
    fun journalRoundTripsEveryCommandAndPausedTime() {
        val path = Files.createTempDirectory("timer-controls").resolve("timer.bin")
        val key = TimerEngine.Key("walk", 1000)
        val requests =
            listOf(
                start(),
                TimerRepository.Request("epoch", 2, 21_000, TimerEngine.Command.Pause(key)),
                TimerRepository.Request("epoch", 3, 41_000, TimerEngine.Command.Resume(key)),
                TimerRepository.Request("epoch", 4, 51_000, TimerEngine.Command.Reset(key)),
                TimerRepository.Request(
                    "epoch",
                    5,
                    61_000,
                    TimerEngine.Command.Replace(
                        TimerEngine.Key("walk", 51_000),
                        TimerEngine.Target("run", 90_000),
                    ),
                ),
                TimerRepository.Request(
                    "epoch",
                    6,
                    71_000,
                    TimerEngine.Command.Stop(TimerEngine.Key("run", 61_000)),
                ),
                start(7, 81_000),
                finish(8, 81_000),
            )
        for (request in requests) {
            var expected: TimerRepository.Snapshot? = null
            FileTimerJournal(path).use { disk ->
                val repo = TimerRepository(disk, "epoch")
                assertEquals(TimerRepository.Delivery.COMMITTED, repo.dispatch(request).delivery)
                expected = repo.snapshot()
                if (request.sequence == 2L)
                    assertEquals(20_000L, expected!!.timer!!.elapsedMs(500_000))
                if (request.sequence == 3L)
                    assertEquals(30_000L, expected!!.timer!!.elapsedMs(51_000))
            }
            FileTimerJournal(path).use { disk ->
                val repo = TimerRepository(disk, "epoch")
                assertEquals(expected, repo.snapshot())
                assertEquals(TimerRepository.Delivery.REPLAYED, repo.dispatch(request).delivery)
            }
        }
    }

    private class Memory : TimerRepository.Journal {
        var durable: TimerRepository.Snapshot? = null
        var fault = 0

        override fun read() = durable

        override fun write(snapshot: TimerRepository.Snapshot) {
            if (fault == 1) error("before commit")
            durable = snapshot
            if (fault == 2) error("lost commit acknowledgement")
        }
    }

    private fun start(n: Long = 1, at: Long = 1000) =
        TimerRepository.Request(
            "epoch",
            n,
            at,
            TimerEngine.Command.Start(TimerEngine.Target("walk", 60_000)),
        )

    private fun finish(n: Long = 2, generation: Long = 1000) =
        TimerRepository.Request(
            "epoch",
            n,
            generation + 90_000,
            TimerEngine.Command.Finish(TimerEngine.Key("walk", generation), true),
        )

    @Test
    fun finishCommitsTimerAndOutboxTogetherAndSurvivesRestart() {
        val disk = Memory()
        val repo = TimerRepository(disk, "epoch")
        repo.dispatch(start())
        repo.dispatch(finish())
        val restarted = TimerRepository(disk, "epoch")
        assertNull(restarted.snapshot().timer)
        assertEquals(1, restarted.snapshot().outbox.size)
        assertEquals(1L, restarted.snapshot().outbox.single().completion.minutes)
        assertEquals(TimerRepository.Delivery.REPLAYED, restarted.dispatch(finish()).delivery)
        assertEquals(1, restarted.snapshot().outbox.size)
    }

    @Test
    fun failedWriteBeforeCommitRequiresReconciliationAndKeepsTimer() {
        val disk = Memory()
        val repo = TimerRepository(disk, "epoch")
        repo.dispatch(start())
        disk.fault = 1
        assertEquals(TimerRepository.Delivery.STORAGE_UNKNOWN, repo.dispatch(finish()).delivery)
        assertThrows(IllegalStateException::class.java) { repo.snapshot() }
        disk.fault = 0
        repo.reconcile()
        assertNotNull(repo.snapshot().timer)
        assertTrue(repo.snapshot().outbox.isEmpty())
        assertEquals(TimerRepository.Delivery.COMMITTED, repo.dispatch(finish()).delivery)
    }

    @Test
    fun failureAfterCommitReplaysInsteadOfCompletingTwice() {
        val disk = Memory()
        val repo = TimerRepository(disk, "epoch")
        repo.dispatch(start())
        disk.fault = 2
        assertEquals(TimerRepository.Delivery.STORAGE_UNKNOWN, repo.dispatch(finish()).delivery)
        disk.fault = 0
        repo.reconcile()
        assertEquals(TimerRepository.Delivery.REPLAYED, repo.dispatch(finish()).delivery)
        assertEquals(1, repo.snapshot().outbox.size)
    }

    @Test
    fun lostAcknowledgementNeverDropsPendingDelivery() {
        val disk = Memory()
        val repo = TimerRepository(disk, "epoch")
        repo.dispatch(start())
        repo.dispatch(finish())
        val id = repo.snapshot().outbox.single().id
        assertFalse(repo.acknowledge("old-epoch", id))
        disk.fault = 1
        assertFalse(repo.acknowledge("epoch", id))
        disk.fault = 0
        repo.reconcile()
        assertEquals(id, repo.snapshot().outbox.single().id)
        assertTrue(repo.acknowledge("epoch", id))
        assertTrue(TimerRepository(disk, "epoch").snapshot().outbox.isEmpty())
        assertTrue(repo.acknowledge("epoch", id))
    }

    @Test
    fun staleEpochConflictingPayloadAndOutOfOrderRequestsCannotWrite() {
        val disk = Memory()
        val repo = TimerRepository(disk, "epoch")
        assertEquals(
            TimerRepository.Delivery.WRONG_EPOCH,
            repo.dispatch(start().copy(epoch = "old")).delivery,
        )
        assertEquals(TimerRepository.Delivery.OUT_OF_ORDER, repo.dispatch(start(2)).delivery)
        repo.dispatch(start())
        assertEquals(TimerRepository.Delivery.ID_CONFLICT, repo.dispatch(start(at = 2000)).delivery)
        assertThrows(IllegalArgumentException::class.java) {
            TimerRepository(disk, "restored-save")
        }
    }

    @Test
    fun evictedReceiptsCannotReexecuteAnOldRequest() {
        val repo = TimerRepository(Memory(), "epoch")
        for (n in 1L..70L) repo.dispatch(start(n))
        assertEquals(64, repo.snapshot().receipts.size)
        assertEquals(TimerRepository.Delivery.EXPIRED, repo.dispatch(start()).delivery)
        assertEquals(70L, repo.snapshot().lastSequence)
    }

    @Test
    fun restartedTimerGetsNewGenerationEvenIfClockMovesBackwards() {
        val repo = TimerRepository(Memory(), "epoch")
        repo.dispatch(start())
        repo.dispatch(
            TimerRepository.Request(
                "epoch",
                2,
                1100,
                TimerEngine.Command.Stop(TimerEngine.Key("walk", 1000)),
            )
        )
        repo.dispatch(start(3, 500))
        assertEquals(1001L, repo.snapshot().timer!!.generation)
        assertEquals(TimerEngine.Status.STALE_TIMER, repo.dispatch(finish(4)).status)
    }

    @Test
    fun fullOutboxDoesNotLoseTheActiveTimer() {
        val repo = TimerRepository(Memory(), "epoch")
        for (i in 0L until 256L) {
            val at = 1000 + i * 100_000
            repo.dispatch(start(i * 2 + 1, at))
            repo.dispatch(finish(i * 2 + 2, at))
        }
        repo.dispatch(start(513, 30_000_000))
        assertEquals(
            TimerRepository.Delivery.OUTBOX_FULL,
            repo.dispatch(finish(514, 30_000_000)).delivery,
        )
        assertNotNull(repo.snapshot().timer)
        assertEquals(513L, repo.snapshot().lastSequence)
    }

    @Test
    fun realJournalRestoresTimerReceiptsAndPendingDelivery() {
        val path = Files.createTempDirectory("timer-test").resolve("timer.bin")
        var expected: TimerRepository.Snapshot? = null
        FileTimerJournal(path).use {
            val repo = TimerRepository(it, "epoch")
            repo.dispatch(start())
            repo.dispatch(finish())
            repo.dispatch(start(3, 100_000))
            expected = repo.snapshot()
        }
        FileTimerJournal(path).use {
            val repo = TimerRepository(it, "epoch")
            assertEquals(expected, repo.snapshot())
            assertEquals(
                TimerRepository.Delivery.REPLAYED,
                repo.dispatch(start(3, 100_000)).delivery,
            )
        }
    }

    @Test
    fun realJournalRejectsCorruptionAndSecondOwner() {
        val path = Files.createTempDirectory("timer-test").resolve("timer.bin")
        FileTimerJournal(path).use {
            TimerRepository(it, "epoch")
            assertThrows(Exception::class.java) { FileTimerJournal(path) }
        }
        val bytes = Files.readAllBytes(path)
        bytes[bytes.lastIndex] = (bytes.last().toInt() xor 1).toByte()
        Files.write(path, bytes)
        FileTimerJournal(path).use { disk ->
            assertThrows(IllegalArgumentException::class.java) { TimerRepository(disk, "epoch") }
        }
        assertArrayEquals(bytes, Files.readAllBytes(path))
    }
}
