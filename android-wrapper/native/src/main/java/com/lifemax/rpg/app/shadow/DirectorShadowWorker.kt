package com.lifemax.rpg.app.shadow

import com.lifemax.rpg.core.directorshadow.DirectorShadowLimits
import com.lifemax.rpg.core.directorshadow.DirectorShadowSession
import com.lifemax.rpg.core.shadow.ShadowAccept
import com.lifemax.rpg.core.shadow.ShadowAnswer
import com.lifemax.rpg.core.shadow.ShadowLimits
import com.lifemax.rpg.core.shadow.ShadowSummary
import java.time.ZoneId
import java.util.concurrent.Executor
import java.util.concurrent.atomic.AtomicInteger

/**
 * Runs the Life Director check off the UI thread (LIFE_DIRECTOR_SHADOW_SPEC SH-25 to SH-30), on the
 * economy check's background thread ([executor], shared, so the two never run at once): at most
 * [ShadowLimits.QUEUE_CAPACITY] envelopes waiting, every job caught and counted by class name, the
 * ring ([ShadowRing] in `director-shadow`) written at most every
 * [ShadowLimits.RING_WRITE_INTERVAL_MS] and when the app stops. No timer, wake-up or network.
 */
class DirectorShadowWorker(
    private val executor: Executor,
    private val ring: ShadowRing,
    private val now: () -> Long,
    private val batterySaver: () -> Boolean,
    private val open: (ShadowRing, Long) -> DirectorShadowSession,
) {
    @Volatile private var session: DirectorShadowSession? = null
    private val waiting = AtomicInteger()
    private val bases = AtomicInteger()
    private var lastWrite = Long.MIN_VALUE / 2

    fun configure(text: String, answer: (ShadowAnswer, stored: Boolean) -> Unit) = run {
        val a = it.configure(text, batterySaver())
        answer(a, persist(it, force = true))
    }

    fun base(text: String, answer: (ShadowAnswer) -> Unit) {
        bases.incrementAndGet()
        run(after = { bases.decrementAndGet() }) { answer(it.base(text, now())) }
    }

    /** Answered at once from the session's quick check; an accepted envelope is queued. */
    fun observe(text: String): ShadowAccept {
        val s = session ?: return ShadowAccept(false, false, "off")
        val quick = s.accept(text.length, batterySaver(), bases.get() > 0)
        if (!quick.accepted) {
            if (quick.reason == "paused") run { it.pausedForBattery() }
            return quick
        }
        if (waiting.incrementAndGet() > ShadowLimits.QUEUE_CAPACITY) {
            waiting.decrementAndGet()
            run { it.queueFull() }
            return ShadowAccept(false, true, "queue")
        }
        run(after = { waiting.decrementAndGet() }) {
            val start = now()
            it.observe(text, start)
            val end = now()
            it.timed(end - start, end)
        }
        return quick
    }

    fun summary(answer: (ShadowSummary) -> Unit) = run { answer(it.summary()) }

    fun report(zone: () -> ZoneId = { ZoneId.systemDefault() }, answer: (String) -> Unit) = run {
        answer(it.report(now(), zone()))
    }

    fun clear(answer: (stored: Boolean) -> Unit) = run {
        it.clear()
        answer(persist(it, force = true))
    }

    fun flush() = run { persist(it, force = true) }

    private fun run(after: () -> Unit = {}, job: (DirectorShadowSession) -> Unit) {
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
                        // counted by persist; nothing else to do
                    }
                }
            }
        } catch (_: RuntimeException) {
            after()
        }
    }

    private fun persist(s: DirectorShadowSession, force: Boolean): Boolean {
        if (!s.dirty) return true
        val t = now()
        if (!force && t - lastWrite < ShadowLimits.RING_WRITE_INTERVAL_MS) return false
        lastWrite = t
        val ok = ring.store(s.ringText())
        if (!ok) s.ledger.note("ringWriteFailed")
        return ok
    }

    companion object {
        /** The session for an install: the ring's ledger, or a fresh one. */
        fun opener(versionCode: Long, nativeApi: Int): (ShadowRing, Long) -> DirectorShadowSession =
            { ring, t ->
                val stored = ring.load()
                val ledger = stored ?: ring.fresh(t)
                DirectorShadowSession(ledger, versionCode, nativeApi).also {
                    if (stored == null && ring.file.exists()) ledger.note("ringReset")
                }
            }

        /** The ring's folder (SH-30). */
        const val FOLDER = DirectorShadowLimits.RING_FOLDER
    }
}
