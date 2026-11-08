package com.lifemax.rpg.app.shadow

import com.lifemax.rpg.core.shadow.ShadowAccept
import com.lifemax.rpg.core.shadow.ShadowAnswer
import com.lifemax.rpg.core.shadow.ShadowContent
import com.lifemax.rpg.core.shadow.ShadowHandshake
import com.lifemax.rpg.core.shadow.ShadowLimits
import com.lifemax.rpg.core.shadow.ShadowSession
import com.lifemax.rpg.core.shadow.ShadowSummary
import java.time.ZoneId
import java.util.concurrent.Executor
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

/**
 * Runs the economy shadow off the UI thread (ECONOMY_SHADOW_PLAN 2.6): one background thread, at
 * most [ShadowLimits.QUEUE_CAPACITY] envelopes waiting (past that the newest is dropped, counted
 * `skipped.queue`, and the page is asked for a base). Every job is caught: an exception is counted
 * by class name only and never reaches the caller. The ring is written after a job at most every
 * [ShadowLimits.RING_WRITE_INTERVAL_MS], and at [flush] (the app stopping); the worker has no
 * timer, wake-up or network of its own.
 *
 * Android stays outside: [executor], the clock, Battery Saver and the ring are given, so the JVM
 * tests drive it exactly as the plugin does.
 *
 * @param open builds the session on the worker thread (reading the ring there).
 */
class ShadowWorker(
    private val executor: Executor,
    private val ring: ShadowRing,
    private val now: () -> Long,
    private val batterySaver: () -> Boolean,
    private val open: (ShadowRing, Long) -> ShadowSession,
) {
    @Volatile private var session: ShadowSession? = null
    private val waiting = AtomicInteger()
    private var lastWrite = Long.MIN_VALUE / 2

    /** The handshake, answered on the worker once the ring is read. */
    fun handshake(answer: (ShadowHandshake) -> Unit) = run { answer(it.handshake()) }

    /** The owner's switch; [answer] runs only once the state is stored in the ring. */
    fun configure(text: String, answer: (ShadowAnswer, stored: Boolean) -> Unit) = run {
        val a = it.configure(text, batterySaver())
        answer(a, persist(it, force = true))
    }

    /** One base part. */
    fun base(text: String, answer: (ShadowAnswer) -> Unit) = run { answer(it.base(text, now())) }

    /**
     * One envelope: answered at once on the caller's thread from the session's quick check; an
     * accepted one is queued for the worker. Never blocks and never throws.
     */
    fun observe(text: String): ShadowAccept {
        val s = session ?: return ShadowAccept(false, false, "off")
        val battery = batterySaver()
        val quick = s.accept(text.length, battery)
        if (!quick.accepted) {
            if (quick.reason == PAUSED) run { it.pausedForBattery() }
            return quick
        }
        if (waiting.incrementAndGet() > ShadowLimits.QUEUE_CAPACITY) {
            waiting.decrementAndGet()
            run { it.queueFull() }
            return ShadowAccept(false, true, QUEUE)
        }
        run(after = { waiting.decrementAndGet() }) {
            val start = now()
            it.observe(text, start)
            it.timed(now() - start)
        }
        return quick
    }

    fun summary(answer: (ShadowSummary) -> Unit) = run { answer(it.summary()) }

    /** Clear: [answer] says whether the empty ring reached the disk. */
    fun clear(answer: (stored: Boolean) -> Unit) = run {
        it.clear()
        answer(persist(it, force = true))
    }

    /** Writes the ring now if anything changed (the app stopping). */
    fun flush() = run { persist(it, force = true) }

    private fun run(after: () -> Unit = {}, job: (ShadowSession) -> Unit) {
        try {
            executor.execute {
                val s = session ?: open(ring, now()).also { session = it }
                try {
                    job(s)
                } catch (e: Exception) {
                    s.fault(e, now())
                } finally {
                    after()
                    try {
                        persist(s, force = false)
                    } catch (_: Exception) {
                        // a ring that cannot be written is counted by persist; nothing else to do
                    }
                }
            }
        } catch (_: RuntimeException) {
            after() // the executor refused (shut down): the job is dropped, nothing is thrown
        }
    }

    private fun persist(s: ShadowSession, force: Boolean): Boolean {
        if (!s.dirty) return true
        val t = now()
        if (!force && t - lastWrite < ShadowLimits.RING_WRITE_INTERVAL_MS) return false
        lastWrite = t
        val ok = ring.store(s.ringText())
        if (!ok) s.ledger.note("ringWriteFailed")
        return ok
    }

    companion object {
        private const val PAUSED = "paused"
        private const val QUEUE = "queue"

        /**
         * The production thread: one daemon thread named `economy-shadow`; [setup] runs first on it
         * (the plugin lowers its priority to the background there).
         */
        fun backgroundExecutor(setup: () -> Unit): ExecutorService =
            Executors.newSingleThreadExecutor { r ->
                Thread(
                        {
                            setup()
                            r.run()
                        },
                        "economy-shadow",
                    )
                    .apply { isDaemon = true }
            }

        /**
         * The session for an install: the ring's ledger, or a fresh one. It compares with no page
         * content (the plan and catalog actions are counted `skipped.content`) and reads the
         * device's zone for `info.zoneLag`.
         */
        fun opener(
            fingerprint: () -> String,
            versionCode: Long,
            nativeApi: Int,
            deviceZone: () -> String? = { ZoneId.systemDefault().id },
        ): (ShadowRing, Long) -> ShadowSession = { ring, t ->
            val stored = ring.load()
            val ledger = stored ?: ring.fresh(t)
            ShadowSession(
                    fingerprint(),
                    ledger,
                    versionCode,
                    nativeApi,
                    ShadowContent.NONE,
                    deviceZone,
                )
                .also { if (stored == null && ring.file.exists()) ledger.note("ringReset") }
        }
    }
}
