package com.lifemax.rpg.core.directorshadow

import com.lifemax.rpg.core.save.JsonRead
import com.lifemax.rpg.core.save.JsonReader
import com.lifemax.rpg.core.shadow.ShadowAccept
import com.lifemax.rpg.core.shadow.ShadowAnswer
import com.lifemax.rpg.core.shadow.ShadowDecode
import com.lifemax.rpg.core.shadow.ShadowLedger
import com.lifemax.rpg.core.shadow.ShadowLimits
import com.lifemax.rpg.core.shadow.ShadowRedactor
import com.lifemax.rpg.core.shadow.ShadowRefusal
import com.lifemax.rpg.core.shadow.ShadowState
import com.lifemax.rpg.core.shadow.ShadowSummary
import java.time.ZoneId

/**
 * The Life Director check for one install (LIFE_DIRECTOR_SHADOW_SPEC.md): its own switch, the
 * fingerprint, native's copy of the page's state ([TextMirror]) and the self-disable rules, with
 * the economy check's ledger, ring format and limits (ECONOMY_SHADOW_PLAN 2.3-4, 3). Pure: the
 * clock and Battery Saver are inputs; the ledger is written by the caller whenever [dirty]. One
 * worker thread calls every method but [accept], which reads two volatile fields only.
 *
 * Native never writes the page's state, never answers with a value it computed and never shows one
 * (SH-34): what comes back to the page is a state word and counts.
 */
class DirectorShadowSession(
    val ledger: ShadowLedger,
    private val versionCode: Long,
    private val nativeApi: Int,
    private val fingerprint: String = DirectorShadowLimits.FINGERPRINT,
    checks: ((ShadowRedactor) -> DirectorChecks)? = null,
) {
    private val redactor = ShadowRedactor(ledger.salt)
    private val checks = checks?.invoke(redactor) ?: DirectorChecks(redactor)
    private val mirror = TextMirror()
    private val parts = ArrayList<String>()
    private var partsSeq = 0L
    private var sid = ""
    private var lastSeq = 0L
    private var pageBuild = ""
    private var pageFingerprint = ""
    private var basesThisSession = 0
    private var engineSkips = 0
    private var commandSkips = 0
    /** Take-backs the page sent in full, by digest (SH-41): the latest two. */
    private val takeBacks =
        LinkedHashMap<String, com.lifemax.rpg.core.lifedirector.host.TakeBackState>()
    private val costs = ArrayDeque<Long>()
    private val pageCosts = ArrayDeque<Double>()

    @Volatile private var current: ShadowState = ledger.state

    @Volatile private var needBase = true

    var dirty = false
        private set

    init {
        if (ledger.versionCode != versionCode && ledger.state == ShadowState.DISABLED) {
            ledger.state = ShadowState.OFF // a new app version retries a check that stopped itself
            ledger.reason = null
        }
        ledger.versionCode = versionCode
        ledger.nativeApi = nativeApi
        current = ledger.state
    }

    /** SH-2: the owner's switch and the page's session. */
    fun configure(text: String, batterySaver: Boolean): ShadowAnswer {
        val c =
            when (val d = DirectorShadowJson.configure(text)) {
                is ShadowDecode.Refused -> {
                    ledger.refuse(d.reason)
                    changed()
                    return ShadowAnswer(false, ledger.state, d.reason.word)
                }
                is ShadowDecode.Ok -> d.value
            }
        lose()
        sid = c.sid
        lastSeq = 0
        basesThisSession = 0
        pageBuild = c.build
        pageFingerprint = c.fingerprint
        ledger.pageFaults += c.faults
        when {
            !c.enabled -> set(ShadowState.OFF, null)
            ledger.state == ShadowState.DISABLED -> Unit
            c.fingerprint != fingerprint -> set(ShadowState.STALE, "fingerprint")
            batterySaver -> set(ShadowState.PAUSED, "battery")
            else -> set(ShadowState.ON, null)
        }
        changed()
        return ShadowAnswer(true, ledger.state, ledger.reason)
    }

    /** SH-8: one part of a base; ok on the last part means native holds the page's state. */
    fun base(text: String, now: Long): ShadowAnswer {
        if (ledger.state != ShadowState.ON) return ShadowAnswer(false, ledger.state, ledger.reason)
        val p =
            when (val d = DirectorShadowJson.basePart(text)) {
                is ShadowDecode.Refused -> return refusedBase(d.reason, now)
                is ShadowDecode.Ok -> d.value
            }
        if (p.sid != sid) return refusedBase(ShadowRefusal.OUT_OF_RANGE, now)
        if (p.part == 0) {
            parts.clear()
            partsSeq = p.seq
        }
        if (p.seq != partsSeq || p.part != parts.size)
            return refusedBase(ShadowRefusal.BAD_DELTA, now)
        parts += p.text
        if (parts.size < p.parts) return ShadowAnswer(true, ledger.state, null)
        val whole = parts.joinToString("")
        parts.clear()
        val ops =
            when (val d = DirectorShadowJson.baseOps(whole)) {
                is ShadowDecode.Refused -> return refusedBase(d.reason, now)
                is ShadowDecode.Ok -> d.value
            }
        if (!mirror.apply(ops, p.seq, base = true)) return refusedBase(ShadowRefusal.BAD_DELTA, now)
        if (mirror.stitch() == null) return refusedBase(ShadowRefusal.TOO_LARGE, now)
        lastSeq = maxOf(lastSeq, p.seq)
        needBase = false
        ledger.note("base")
        if (basesThisSession++ > 0 && ledger.resync(now)) disable("resyncLoop", now)
        changed()
        return ShadowAnswer(true, ledger.state, ledger.reason)
    }

    /**
     * SH-20: the quick answer, before any work; may run on any thread. While native has no copy and
     * no base is on its way ([basePending] false), the page is asked for one (`resync`).
     */
    fun accept(chars: Int, batterySaver: Boolean, basePending: Boolean = false): ShadowAccept {
        val state = current
        return when {
            state == ShadowState.ON && batterySaver -> ShadowAccept(false, true, "paused")
            state != ShadowState.ON -> ShadowAccept(false, false, state.word)
            chars > DirectorShadowLimits.MAX_ENVELOPE_CHARS ->
                ShadowAccept(false, true, ShadowRefusal.TOO_LARGE.word)
            needBase && !basePending -> ShadowAccept(false, true, "noBase")
            else -> ShadowAccept.ACCEPTED
        }
    }

    fun pausedForBattery() {
        if (ledger.state == ShadowState.ON) set(ShadowState.PAUSED, "battery")
        lose()
        ledger.skip(ANY, "battery")
    }

    fun queueFull() {
        lose()
        ledger.skip(ANY, "queue")
        changed()
    }

    /**
     * SH-3 to SH-17: one envelope, decoded, applied to the copy where it carries changes, checked.
     */
    fun observe(text: String, now: Long) {
        if (ledger.state != ShadowState.ON) return
        val e =
            when (val d = DirectorShadowJson.envelope(text)) {
                is ShadowDecode.Refused -> {
                    ledger.refuse(d.reason)
                    lose()
                    return
                }
                is ShadowDecode.Ok -> d.value
            }
        val counts = ledger.kind(e.kind)
        counts.seen++
        recent(pageCosts, e.costMs)
        changed()
        if (e.sid != sid || e.seq <= lastSeq) {
            if (e.sid == sid) ledger.note("duplicate") else ledger.skip(e.kind, "resync")
            return
        }
        lastSeq = e.seq
        val engine = e.kind == DirectorShadowJson.PLAN || e.kind == DirectorShadowJson.REPAIR
        var stitched: String? = null
        if (!engine) {
            if (needBase || mirror.seq != e.base) return skipLost(e.kind, "resync")
            if (!mirror.apply(e.ops, e.seq, base = false)) {
                ledger.refuse(ShadowRefusal.BAD_DELTA)
                return skipLost(e.kind, "resync")
            }
            stitched = mirror.stitch() ?: return skipLost(e.kind, "capture")
        }
        var after: String? = null
        if (e.cmd != null) {
            // the copy is at the state before the command; its own changes take it to the state
            // after (SH-40)
            if (!mirror.apply(e.cmd.afterOps, e.seq, base = false)) {
                ledger.refuse(ShadowRefusal.BAD_DELTA)
                return skipLost(e.kind, "resync")
            }
            after = mirror.stitch() ?: return skipLost(e.kind, "capture")
        }
        counts.applied++
        if (e.kind !in DirectorShadowJson.KINDS) return ledger.skip(e.kind, "unknownKind")
        val result =
            try {
                when (e.kind) {
                    DirectorShadowJson.SAVE -> {
                        if (stitched!!.length != e.bytes) return skipLost(e.kind, "capture")
                        checks.save(stitched)
                    }
                    DirectorShadowJson.READ -> {
                        if (SECOND in e.flags) return ledger.skip(e.kind, SECOND)
                        val state =
                            (JsonReader.read(stitched!!) as? JsonRead.Parsed)?.value
                                ?: return skipLost(e.kind, "capture")
                        checks.read(state, e.read!!)
                    }
                    DirectorShadowJson.CMD,
                    DirectorShadowJson.TICK -> {
                        if (SECOND in e.flags) return ledger.skip(e.kind, SECOND)
                        if (commandSkips > 0) {
                            commandSkips--
                            return ledger.skip(e.kind, "sampled")
                        }
                        val before =
                            (JsonReader.read(stitched!!) as? JsonRead.Parsed)?.value
                                ?: return skipLost(e.kind, "capture")
                        val now =
                            (JsonReader.read(after!!) as? JsonRead.Parsed)?.value
                                ?: return skipLost(e.kind, "capture")
                        val start = System.nanoTime()
                        val r = checks.command(before, now, e.cmd!!, e.t1) { takeBacks[it] }
                        if (
                            (System.nanoTime() - start) / NANOS_PER_MS >
                                DirectorShadowLimits.ENGINE_SLOW_MS
                        )
                            commandSkips = DirectorShadowLimits.ENGINE_SAMPLE - 1
                        r.learned?.let { (digest, t) ->
                            takeBacks.remove(digest)
                            takeBacks[digest] = t
                            while (takeBacks.size > 2) takeBacks.remove(takeBacks.keys.first())
                        }
                        CheckResult(r.verdict)
                    }
                    else -> {
                        if (engineSkips > 0) {
                            engineSkips--
                            return ledger.skip(e.kind, "sampled")
                        }
                        val start = System.nanoTime()
                        val r = checks.engine(e.kind, e.engine!!)
                        if (
                            (System.nanoTime() - start) / NANOS_PER_MS >
                                DirectorShadowLimits.ENGINE_SLOW_MS
                        )
                            engineSkips = DirectorShadowLimits.ENGINE_SAMPLE - 1
                        r
                    }
                }
            } catch (x: Exception) {
                return internal(e.kind, x, now)
            } catch (x: StackOverflowError) {
                return internal(e.kind, x, now)
            }
        for (note in result.notes) ledger.codec.merge(note, 1, Int::plus)
        ledger.compared(e.kind, result.verdict, pageBuild, now)
        changed()
    }

    private fun skipLost(kind: String, why: String) {
        ledger.skip(kind, why)
        lose()
    }

    private fun internal(kind: String, error: Throwable, now: Long) {
        ledger.skip(kind, "error")
        if (ledger.error(error.javaClass.simpleName.ifEmpty { "Throwable" }, now))
            disable("errors", now)
        changed()
    }

    /**
     * SH-25: the worker's time for one envelope; a slow 95th percentile of the last 50 stops the
     * check.
     */
    fun timed(millis: Long, now: Long) {
        recent(costs, millis)
        if (
            costs.size == ShadowLimits.SLOW_WINDOW &&
                costs.sorted()[SLOW_INDEX] > ShadowLimits.SLOW_P95_MS
        )
            disable("slow", now)
    }

    fun fault(error: Throwable, now: Long) {
        lose()
        if (ledger.error(error.javaClass.simpleName.ifEmpty { "Throwable" }, now))
            disable("errors", now)
        changed()
    }

    /** Counts only (SH-34). */
    fun summary(): ShadowSummary =
        ShadowSummary(
            state = ledger.state,
            reason = ledger.reason,
            since = ledger.since,
            seen = ledger.seen,
            applied = ledger.applied,
            skipped = ledger.kinds.values.sumOf { it.skipped.values.sum() },
            last = null,
            compared = ledger.compared,
            same = ledger.same,
            allowed = ledger.allowed,
            different = ledger.different,
            lastDifferent = ledger.mismatches().lastOrNull { it.unexplained },
        )

    fun report(now: Long, zone: ZoneId): String =
        DirectorShadowReport.text(
            ledger,
            DirectorReportFacts(
                versionCode,
                nativeApi,
                pageBuild,
                fingerprint,
                pageFingerprint,
                costs.toList(),
                pageCosts.toList(),
                now,
                zone,
            ),
        )

    fun clear() {
        ledger.clear()
        costs.clear()
        pageCosts.clear()
        changed()
    }

    fun ringText(): String = ledger.fit().also { dirty = false }

    private fun refusedBase(reason: ShadowRefusal, now: Long): ShadowAnswer {
        ledger.refuse(reason)
        parts.clear()
        if (reason == ShadowRefusal.TOO_LARGE) disable("tooLarge", now) else lose()
        changed()
        return ShadowAnswer(false, ledger.state, reason.word)
    }

    private fun lose() {
        takeBacks.clear()
        mirror.discard()
        parts.clear()
        needBase = true
        changed()
    }

    private fun disable(reason: String, now: Long) {
        lose()
        if (ledger.state != ShadowState.DISABLED) ledger.disabled(reason, now)
        set(ShadowState.DISABLED, reason)
    }

    private fun set(state: ShadowState, reason: String?) {
        ledger.state = state
        ledger.reason = reason
        current = state
        changed()
    }

    private fun <T> recent(list: ArrayDeque<T>, value: T) {
        list.addLast(value)
        if (list.size > ShadowLimits.SLOW_WINDOW) list.removeFirst()
    }

    private fun changed() {
        dirty = true
    }

    companion object {
        const val ANY = "any"
        private const val SECOND = "second"
        private const val NANOS_PER_MS = 1_000_000L
        private const val SLOW_INDEX =
            ShadowLimits.SLOW_WINDOW * ShadowLimits.SLOW_PERCENTILE / 100 - 1
    }
}
