package com.lifemax.rpg.app.shadow

import com.lifemax.rpg.core.save.JsonNumber
import com.lifemax.rpg.core.shadow.FakeShadowPage
import com.lifemax.rpg.core.shadow.ShadowLimits
import com.lifemax.rpg.core.shadow.ShadowSession
import com.lifemax.rpg.core.shadow.ShadowSessionTest
import com.lifemax.rpg.core.shadow.ShadowState
import java.io.File
import java.nio.file.Files
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executor
import java.util.concurrent.TimeUnit
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The worker (ECONOMY_SHADOW_PLAN 2.6, 4): work happens on its own thread and never on the
 * caller's, the queue holds sixteen envelopes and drops the newest past that, exceptions are
 * counted and never reach the caller, the ring is written at most every thirty seconds, and a
 * refused executor drops the job quietly.
 */
class ShadowWorkerTest {
    private val dir: File = Files.createTempDirectory("worker").toFile()

    @After
    fun removeTheFolder() {
        dir.deleteRecursively()
    }

    private var clock = ShadowSessionTest.T
    private val start = mapOf("sparks" to JsonNumber(1.0))

    /** Runs jobs only when [drain] is called, so the queue can be filled. */
    private class Held : Executor {
        val jobs = ArrayDeque<Runnable>()

        override fun execute(command: Runnable) {
            jobs.addLast(command)
        }

        fun drain() {
            while (jobs.isNotEmpty()) jobs.removeFirst().run()
        }
    }

    private fun worker(executor: Executor, battery: () -> Boolean = { false }) =
        ShadowWorker(executor, ShadowRing(dir), { clock }, battery) { ring, t ->
            ShadowSession(ShadowSessionTest.FP, ring.load() ?: ring.fresh(t), 1, 106)
        }

    private fun configured(w: ShadowWorker, held: Held, page: FakeShadowPage) {
        var stored = false
        w.configure(ShadowSessionTest.configure(ShadowSessionTest.FP)) { a, ok ->
            stored = ok && a.state == ShadowState.ON
        }
        page.base(start).forEach { w.base(it) {} }
        held.drain()
        assertTrue(stored)
    }

    @Test
    fun theWorkRunsOnItsOwnBackgroundThread() {
        val pool = ShadowWorker.backgroundExecutor {}
        try {
            val w = worker(pool)
            val seen = arrayOfNulls<Thread>(1)
            val done = CountDownLatch(1)
            w.handshake {
                seen[0] = Thread.currentThread()
                done.countDown()
            }
            assertTrue(done.await(5, TimeUnit.SECONDS))
            assertNotEquals(Thread.currentThread(), seen[0])
            assertEquals("economy-shadow", seen[0]!!.name)
            assertTrue(seen[0]!!.isDaemon)
        } finally {
            pool.shutdownNow()
        }
    }

    @Test
    fun theSeventeenthWaitingEnvelopeIsDroppedAndABaseIsAskedFor() {
        val held = Held()
        val w = worker(held)
        val page = FakeShadowPage()
        configured(w, held, page)
        var state = start
        val answers =
            (1..ShadowLimits.QUEUE_CAPACITY + 1).map {
                val next = state + ("sparks" to JsonNumber(it + 1.0))
                w.observe(page.envelope("save.other", state, next)).also { state = next }
            }
        assertTrue(answers.take(ShadowLimits.QUEUE_CAPACITY).all { it.accepted })
        assertFalse(answers.last().accepted)
        assertTrue(answers.last().resync)
        assertEquals("queue", answers.last().reason)
        held.drain()
        var summary = 0
        w.summary { summary = it.applied }
        held.drain()
        assertEquals(
            ShadowLimits.QUEUE_CAPACITY,
            summary,
        ) // the waiting ones ran; the drop then discarded the copy
        assertEquals("noBase", w.observe(page.envelope("save.other", state, state)).reason)
    }

    @Test
    fun anExceptionInsideIsCountedNeverThrownAndFiveDisable() {
        val held = Held()
        val boom = object : RuntimeException("boom") {}
        val w = worker(held)
        val page = FakeShadowPage()
        configured(w, held, page)
        // a job that throws: the worker catches it and counts it by class name only
        repeat(5) { w.summary { throw boom } }
        held.drain()
        var state: ShadowState? = null
        w.summary { state = it.state }
        held.drain()
        assertEquals(ShadowState.DISABLED, state)
        assertEquals("disabled", w.observe("{}").reason)
    }

    @Test
    fun theRingIsWrittenAtMostEveryThirtySecondsAndOnFlush() {
        val held = Held()
        val w = worker(held)
        val page = FakeShadowPage()
        configured(w, held, page) // configure writes at once
        val ring = ShadowRing(dir).file
        val first = ring.readText()
        w.observe(page.envelope("save.other", start, start + ("sparks" to JsonNumber(5.0))))
        held.drain()
        assertEquals(first, ring.readText()) // within thirty seconds: not yet
        clock += ShadowLimits.RING_WRITE_INTERVAL_MS
        w.flush()
        held.drain()
        assertNotEquals(first, ring.readText())
        var cleared = false
        w.clear { cleared = it }
        held.drain()
        assertTrue(cleared)
        assertFalse(ring.readText().contains("save.other"))
    }

    @Test
    fun batterySaverPausesWithoutReadingTheEnvelope() {
        val held = Held()
        var battery = false
        val w = worker(held, battery = { battery })
        val page = FakeShadowPage()
        configured(w, held, page)
        battery = true
        val a = w.observe("not even json")
        assertEquals("paused", a.reason)
        held.drain()
        var state: ShadowState? = null
        w.summary { state = it.state }
        held.drain()
        assertEquals(ShadowState.PAUSED, state)
    }

    @Test
    fun aRefusedExecutorDropsTheJobQuietly() {
        val w = worker(Executor { throw java.util.concurrent.RejectedExecutionException() })
        w.handshake { error("must not run") }
        assertEquals("off", w.observe("{}").reason)
    }
}
