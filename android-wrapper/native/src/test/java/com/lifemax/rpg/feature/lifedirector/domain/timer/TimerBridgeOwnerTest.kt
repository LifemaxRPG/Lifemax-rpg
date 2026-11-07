package com.lifemax.rpg.feature.lifedirector.domain.timer

import com.lifemax.rpg.feature.lifedirector.data.timer.FileTimerJournal
import com.lifemax.rpg.feature.lifedirector.data.timer.TimerBridgeOwner
import java.nio.file.Files
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class TimerBridgeOwnerTest {
    private fun request(action: String) =
        JSONObject().put("protocol", 1).put("epoch", "e").put("action", action)

    private fun answer(owner: TimerBridgeOwner, action: String): JSONObject {
        val done = CountDownLatch(1)
        var reply: JSONObject? = null
        owner.submit(request(action)) {
            reply = it
            done.countDown()
        }
        assertTrue(done.await(5, TimeUnit.SECONDS))
        return reply!!
    }

    @Test
    fun unpreparedBridgeNeverCreatesAnAuthoritativeTimer() {
        val path = Files.createTempDirectory("timer-owner").resolve("timer.bin")
        TimerBridgeOwner(path).use {
            assertEquals("NOT_PREPARED", answer(it, "snapshot").getString("status"))
        }
        assertFalse(Files.exists(path))
    }

    @Test
    fun preparedJournalCanBeInspectedButCannotRunControlsBeforeActivation() {
        val path = Files.createTempDirectory("timer-owner").resolve("timer.bin")
        FileTimerJournal(path).use {
            TimerMigration.prepare(
                it,
                TimerMigration.snapshot(
                    "e",
                    TimerMigration.LegacyFocus("walk", 1000, plannedMs = 60000),
                    TimerEngine.Target("walk", 60000),
                ),
            )
        }
        val worker = Executors.newSingleThreadExecutor()
        val owner = TimerBridgeOwner(path, worker)
        assertEquals(
            1000L,
            answer(owner, "snapshot")
                .getJSONObject("snapshot")
                .getJSONObject("timer")
                .getLong("generation"),
        )
        assertEquals("NOT_ACTIVE", answer(owner, "start").getString("status"))
        owner.close()
        assertTrue(worker.awaitTermination(5, TimeUnit.SECONDS))
        FileTimerJournal(path).use { assertEquals(1000L, it.read()!!.timer!!.generation) }
    }

    @Test
    fun closingDropsQueuedWorkAndReleasesWorker() {
        val worker = Executors.newSingleThreadExecutor()
        val barrier = CountDownLatch(1)
        worker.execute { barrier.await() }
        val owner =
            TimerBridgeOwner(Files.createTempDirectory("timer-owner").resolve("timer.bin"), worker)
        val calls = java.util.concurrent.atomic.AtomicInteger()
        owner.submit(request("snapshot")) { calls.incrementAndGet() }
        owner.close()
        owner.close()
        barrier.countDown()
        assertTrue(worker.awaitTermination(5, TimeUnit.SECONDS))
        assertEquals(0, calls.get())
        owner.submit(request("snapshot")) { calls.incrementAndGet() }
        assertEquals(0, calls.get())
    }
}
