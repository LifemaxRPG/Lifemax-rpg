package com.lifemax.rpg.feature.lifedirector.domain.timer

import com.lifemax.rpg.feature.lifedirector.data.timer.TimerEndpoint
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class TimerEndpointTest {
    private class Disk : TimerRepository.Journal {
        var saved: TimerRepository.Snapshot? = null

        override fun read() = saved

        override fun write(snapshot: TimerRepository.Snapshot) {
            saved = snapshot
        }
    }

    private fun request(action: String, seq: Long = 1, at: Long = 1000) =
        JSONObject()
            .put("protocol", 1)
            .put("epoch", "e")
            .put("action", action)
            .put("sequence", seq)
            .put("atMs", at)
            .put("activityId", "walk")
            .put("generation", 1000)

    @Test
    fun controlsFollowVerifiedDurationAndCatchUpOnlyOnce() {
        val repo = TimerRepository(Disk(), "e")
        val endpoint = TimerEndpoint(repo, { true }) { TimerEngine.Target(it, 60_000) }
        assertEquals(
            "COMMITTED",
            endpoint.handle(request("start").put("plannedMs", 1)).getString("status"),
        )
        assertEquals(60_000L, repo.snapshot().timer!!.plannedMs)
        endpoint.handle(request("pause", 2, 21_000))
        assertEquals(20_000L, repo.snapshot().timer!!.elapsedMs(500_000))
        endpoint.handle(request("resume", 3, 41_000))
        val finish = request("catchUp", 4, 121_000)
        assertEquals("COMMITTED", endpoint.handle(finish).getString("status"))
        assertEquals("REPLAYED", endpoint.handle(finish).getString("status"))
        assertEquals(1, repo.snapshot().outbox.size)
        assertEquals(1L, repo.snapshot().outbox.single().completion.minutes)
    }

    @Test
    fun inactiveAuthorityCannotBeEnabledByCaller() {
        val repo = TimerRepository(Disk(), "e")
        val endpoint = TimerEndpoint(repo, { false }) { TimerEngine.Target(it, 60_000) }
        assertEquals(
            "NOT_ACTIVE",
            endpoint.handle(request("start").put("authorityReady", true)).getString("status"),
        )
        assertNull(repo.snapshot().timer)
        assertEquals("OK", endpoint.handle(request("snapshot")).getString("status"))
    }

    @Test
    fun invalidTypesAndStaleEpochNeverMutate() {
        val repo = TimerRepository(Disk(), "e")
        val endpoint = TimerEndpoint(repo, { true }) { TimerEngine.Target(it, 60_000) }
        for ((key, value) in
            listOf("protocol" to "1", "sequence" to 1.5, "atMs" to -1, "activityId" to 42)) {
            assertEquals(
                "INVALID",
                endpoint.handle(request("start").put(key, value)).getString("status"),
            )
        }
        assertEquals(
            "WRONG_EPOCH",
            endpoint.handle(request("start").put("epoch", "old")).getString("status"),
        )
        assertEquals(0L, repo.snapshot().lastSequence)
    }

    @Test
    fun changedPayloadCannotReuseCommittedSequence() {
        val repo = TimerRepository(Disk(), "e")
        val endpoint = TimerEndpoint(repo, { true }) { TimerEngine.Target(it, 60_000) }
        endpoint.handle(request("start"))
        assertEquals(
            "ID_CONFLICT",
            endpoint.handle(request("start", at = 2000)).getString("status"),
        )
        assertEquals(1000L, repo.snapshot().timer!!.generation)
    }

    @Test
    fun retryAfterActivityLeavesScheduleStillRecognizesReceipt() {
        val repo = TimerRepository(Disk(), "e")
        var available = true
        val endpoint =
            TimerEndpoint(repo, { true }) {
                if (available) TimerEngine.Target(it, 60_000) else null
            }
        endpoint.handle(request("start"))
        available = false
        assertEquals("REPLAYED", endpoint.handle(request("start")).getString("status"))
        assertEquals(
            "INVALID",
            endpoint.handle(request("start", 2).put("activityId", "gone")).getString("status"),
        )
    }
}
