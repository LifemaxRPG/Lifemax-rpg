package com.lifemax.rpg.feature.lifedirector.domain.timer

import org.junit.Assert.*
import org.junit.Test

class TimerMigrationTest {
    private class Disk : TimerRepository.Journal {
        var state: TimerRepository.Snapshot? = null
        var fault = 0

        override fun read() = state

        override fun write(snapshot: TimerRepository.Snapshot) {
            if (fault == 1) error("before write")
            state = snapshot
            if (fault == 2) error("after write")
        }
    }

    private val target = TimerEngine.Target("walk", 900_000)
    private val focus = TimerMigration.LegacyFocus("walk", 1000, plannedMs = 600_000)

    @Test
    fun runningImportKeepsElapsedAndOriginalPlannedDuration() {
        val imported = TimerMigration.snapshot("e", focus, target)
        val timer = TimerMigration.prepare(Disk(), imported).snapshot().timer!!
        assertEquals(120_000L, timer.elapsedMs(121_000))
        assertEquals(600_000L, timer.plannedMs)
        assertTrue(timer.due(900_000))
    }

    @Test
    fun pausedImportNeverAccumulatesTimeAcrossRestart() {
        val disk = Disk()
        val imported =
            TimerMigration.snapshot(
                "e",
                focus.copy(pausedAtMs = 121_000, pausedMs = 20_000),
                target,
            )
        TimerMigration.prepare(disk, imported)
        assertEquals(100_000L, TimerRepository(disk, "e").snapshot().timer!!.elapsedMs(10_000_000))
    }

    @Test
    fun missingLegacyDurationFallsBackToVerifiedSchedule() {
        assertEquals(
            900_000L,
            TimerMigration.snapshot("e", focus.copy(plannedMs = null), target).timer!!.plannedMs,
        )
        assertNull(TimerMigration.snapshot("e", null, null).timer)
    }

    @Test
    fun orphanInvalidAndConflictingImportsNeverReplaceData() {
        assertThrows(IllegalArgumentException::class.java) {
            TimerMigration.snapshot("e", focus, null)
        }
        assertThrows(IllegalArgumentException::class.java) {
            TimerMigration.snapshot("e", focus.copy(startMs = 0), target)
        }
        val disk = Disk()
        val imported = TimerMigration.snapshot("e", focus, target)
        TimerMigration.prepare(disk, imported)
        assertThrows(IllegalArgumentException::class.java) {
            TimerMigration.prepare(disk, TimerMigration.snapshot("new", focus, target))
        }
        assertEquals(imported, disk.state)
    }

    @Test
    fun interruptedImportCanRetryWithoutRestartingTheTimer() {
        for (fault in listOf(1, 2)) {
            val disk = Disk()
            val imported = TimerMigration.snapshot("e", focus, target)
            disk.fault = fault
            assertThrows(IllegalStateException::class.java) {
                TimerMigration.prepare(disk, imported)
            }
            disk.fault = 0
            assertEquals(imported, TimerMigration.prepare(disk, imported).snapshot())
        }
    }

    @Test
    fun pendingRewardsPreventEpochRotation() {
        val disk = Disk()
        val repo = TimerMigration.prepare(disk, TimerMigration.snapshot("e", focus, target))
        repo.dispatch(
            TimerRepository.Request(
                "e",
                1,
                601_000,
                TimerEngine.Command.Finish(TimerEngine.Key("walk", 1000), true),
            )
        )
        assertFalse(repo.rotateEpoch("e", TimerRepository.Snapshot("new")))
        assertEquals(1, repo.snapshot().outbox.size)
        repo.acknowledge("e", repo.snapshot().outbox.single().id)
        assertTrue(repo.rotateEpoch("e", TimerRepository.Snapshot("new")))
        assertEquals(
            TimerRepository.Delivery.WRONG_EPOCH,
            repo
                .dispatch(
                    TimerRepository.Request("e", 2, 700_000, TimerEngine.Command.Start(target))
                )
                .delivery,
        )
        assertFalse(repo.acknowledge("e", "e/1"))
    }

    @Test
    fun rotationLostAcknowledgementReconcilesEitherDurableEpoch() {
        for (fault in listOf(1, 2)) {
            val disk = Disk()
            val repo = TimerRepository(disk, "e")
            disk.fault = fault
            assertFalse(repo.rotateEpoch("e", TimerRepository.Snapshot("new")))
            assertThrows(IllegalStateException::class.java) { repo.snapshot() }
            disk.fault = 0
            repo.reconcile()
            assertEquals(if (fault == 1) "e" else "new", repo.snapshot().epoch)
        }
    }
}
