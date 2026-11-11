package com.lifemax.rpg.app.shadow

import com.lifemax.rpg.core.directorshadow.DirectorShadowStream
import com.lifemax.rpg.core.shadow.ShadowState
import java.io.File
import java.nio.file.Files
import java.util.concurrent.Executor
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The Life Director check's worker (SH-25 to SH-30): the real day's messages handed over as the
 * plugin does, work only on the worker, a base on its way lets an envelope in, the ring written in
 * its own folder beside the economy's, and the state kept across a new session.
 */
class DirectorShadowWorkerTest {
    private val dir: File = Files.createTempDirectory("director-worker").toFile()
    private var clock = DirectorShadowStream.T

    @After
    fun removeTheFolder() {
        dir.deleteRecursively()
    }

    private class Held : Executor {
        val jobs = ArrayDeque<Runnable>()

        override fun execute(command: Runnable) {
            jobs.addLast(command)
        }

        fun drain() {
            while (jobs.isNotEmpty()) jobs.removeFirst().run()
        }
    }

    private fun worker(executor: Executor) =
        DirectorShadowWorker(
            executor,
            ShadowRing(dir, DirectorShadowWorker.FOLDER),
            { clock },
            { false },
            DirectorShadowWorker.opener(1, 135),
        )

    @Test
    fun theDayThroughTheWorkerIsCheckedAndStoredInItsOwnFolder() {
        val held = Held()
        val w = worker(held)
        var accepted = 0
        for ((method, text) in DirectorShadowStream.messages) {
            clock += 1000
            when (method) {
                "configure" -> w.configure(text) { _, _ -> }
                "base" -> w.base(text) {}
                "observe" -> if (w.observe(text).accepted) accepted++
            }
            assertTrue(
                "nothing runs on the caller's thread",
                held.jobs.isNotEmpty() || method == "observe",
            )
            held.drain()
        }
        var compared = 0
        var different = -1
        w.summary {
            compared = it.compared
            different = it.different
        }
        w.flush()
        held.drain()
        assertTrue(accepted > 0 && compared > 0)
        assertEquals(0, different)
        assertTrue(File(dir, "director-shadow/ring.json").isFile)
        assertFalse(File(dir, "economy-shadow/ring.json").exists())
        // a new app session reads the same counts back
        val again = worker(held)
        var state: ShadowState? = null
        var seen = 0
        again.summary {
            state = it.state
            seen = it.compared
        }
        held.drain()
        assertEquals(ShadowState.ON, state)
        assertEquals(compared, seen)
    }

    @Test
    fun anEnvelopeRightBehindItsBaseIsLetIn() {
        val held = Held()
        val w = worker(held)
        val messages = DirectorShadowStream.messages
        w.configure(messages.first { it.first == "configure" }.second) { _, _ -> }
        held.drain()
        for ((m, t) in messages) if (m == "base") w.base(t) {} // queued, not yet run
        val first = messages.first { it.first == "observe" }.second
        assertTrue(w.observe(first).accepted)
        held.drain()
        var compared = 0
        w.summary { compared = it.compared }
        held.drain()
        assertEquals(1, compared)
    }
}
