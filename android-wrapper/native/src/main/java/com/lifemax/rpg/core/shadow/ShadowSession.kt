package com.lifemax.rpg.core.shadow

/** The answer to `handshake()` (2.3). */
data class ShadowHandshake(
    val protocol: Int,
    val capabilities: List<String>,
    val fingerprint: String,
    val state: ShadowState,
    val reason: String?,
)

/** The answer to `configure()` and `base()`: [ok] with the state the check is now in. */
data class ShadowAnswer(val ok: Boolean, val state: ShadowState, val reason: String?)

/** The answer to `observe()`, given before any work (2.3). */
data class ShadowAccept(val accepted: Boolean, val resync: Boolean, val reason: String?) {
    companion object {
        val ACCEPTED = ShadowAccept(true, false, null)
    }
}

/** The answer to `summary()` (2.3): counts only, no value from the save. */
data class ShadowSummary(
    val state: ShadowState,
    val reason: String?,
    val since: Long,
    val seen: Int,
    val applied: Int,
    val skipped: Int,
    val last: TouchEntry?,
)

/**
 * The economy shadow's logic for one install (ECONOMY_SHADOW_PLAN 2.3-2.6, 4; WP6a): the switch,
 * the content fingerprint, the mirror, the touch map and the self-disable rules. Pure: the clock,
 * Battery Saver and the native fingerprint are inputs; the ledger it keeps is written by the caller
 * (the ring) whenever [dirty]. One worker thread calls every method except [accept], which reads
 * only the two volatile fields and may run on the bridge's thread.
 *
 * In WP6a nothing is compared: an applied envelope records which fields the action changed.
 */
class ShadowSession(
    private val nativeFingerprint: String,
    val ledger: ShadowLedger,
    private val versionCode: Long,
    private val nativeApi: Int,
) {
    private val redactor = ShadowRedactor(ledger.salt)
    private val mirror = ShadowMirror(redactor)
    private val costs = ArrayDeque<Long>()
    private var pageBuild = ""
    private var basesThisSession = 0

    /** The state, for [accept] on another thread. */
    @Volatile private var current: ShadowState = ledger.state

    /** Whether the next envelope will be refused until a base arrives. */
    @Volatile private var needBase = true

    /** Whether the ledger changed since the ring last stored it. */
    var dirty = false
        private set

    init {
        if (ledger.versionCode != versionCode && ledger.state == ShadowState.DISABLED) {
            // a new app version retries a check that stopped itself (section 4, self-disable)
            ledger.state = ShadowState.OFF
            ledger.reason = null
        }
        ledger.versionCode = versionCode
        ledger.nativeApi = nativeApi
        current = ledger.state
    }

    /** The held copy, for tests and the later comparison stages. */
    fun mirrorSnapshot() = mirror.snapshot()

    fun handshake(): ShadowHandshake =
        ShadowHandshake(
            ShadowLimits.PROTOCOL,
            listOf(ShadowLimits.CAPABILITY),
            nativeFingerprint,
            ledger.state,
            ledger.reason,
        )

    /** The owner's switch and the page's session (2.3). */
    fun configure(text: String, batterySaver: Boolean): ShadowAnswer {
        val config =
            when (val d = ShadowEnvelopeJson.configure(text)) {
                is ShadowDecode.Refused -> {
                    ledger.refuse(d.reason)
                    changed()
                    return ShadowAnswer(false, ledger.state, d.reason.word)
                }
                is ShadowDecode.Ok -> d.value
            }
        mirror.discard()
        needBase = true
        basesThisSession = 0
        pageBuild = config.page.build
        ledger.pageFaults += config.page.faults
        when {
            !config.enabled -> set(ShadowState.OFF, null)
            ledger.state == ShadowState.DISABLED -> Unit
            config.page.fingerprint != nativeFingerprint -> set(ShadowState.STALE, "fingerprint")
            batterySaver -> set(ShadowState.PAUSED, "battery")
            else -> set(ShadowState.ON, null)
        }
        changed()
        return ShadowAnswer(true, ledger.state, ledger.reason)
    }

    /** One base part (2.3); ok on the last part means the mirror holds the page's state. */
    fun base(text: String, now: Long): ShadowAnswer {
        if (ledger.state != ShadowState.ON) return ShadowAnswer(false, ledger.state, ledger.reason)
        val part =
            when (val d = ShadowEnvelopeJson.basePart(text)) {
                is ShadowDecode.Refused -> return refusedBase(d.reason)
                is ShadowDecode.Ok -> d.value
            }
        return when (val step = mirror.base(part)) {
            is BaseStep.Pending -> ShadowAnswer(true, ledger.state, null)
            is BaseStep.Duplicate -> ShadowAnswer(true, ledger.state, null)
            is BaseStep.Refused -> refusedBase(step.reason)
            is BaseStep.Ready -> {
                needBase = false
                ledger.note("base")
                if (basesThisSession++ > 0 && ledger.resync(now)) disable("resyncLoop")
                changed()
                ShadowAnswer(true, ledger.state, ledger.reason)
            }
        }
    }

    /**
     * The quick answer to `observe()`, before any work (2.3): may run on any thread. Accepted
     * envelopes are then handed to [observe] by the worker.
     */
    fun accept(chars: Int, batterySaver: Boolean): ShadowAccept {
        val state = current
        return when {
            state == ShadowState.ON && batterySaver -> ShadowAccept(false, true, "paused")
            state != ShadowState.ON -> ShadowAccept(false, false, state.word)
            chars > ShadowLimits.MAX_ENVELOPE_CHARS ->
                ShadowAccept(false, true, ShadowRefusal.TOO_LARGE.word)
            needBase -> ShadowAccept(false, true, "noBase")
            else -> ShadowAccept.ACCEPTED
        }
    }

    /** Battery Saver came on while the check ran: pause, drop the mirror, count the drop. */
    fun pausedForBattery() {
        if (ledger.state == ShadowState.ON) set(ShadowState.PAUSED, "battery")
        mirror.discard()
        needBase = true
        ledger.skip(ANY, "battery")
        changed()
    }

    /** Counts an envelope the worker's queue had no room for (`skipped.queue`); a base follows. */
    fun queueFull() {
        mirror.discard()
        needBase = true
        ledger.skip(ANY, "queue")
        changed()
    }

    /** Takes one accepted envelope: decode, apply to the mirror, record its touch map. */
    fun observe(text: String, now: Long) {
        if (ledger.state != ShadowState.ON) return
        val envelope =
            when (val d = ShadowEnvelopeJson.envelope(text)) {
                is ShadowDecode.Refused -> {
                    ledger.refuse(d.reason)
                    lose()
                    return
                }
                is ShadowDecode.Ok -> d.value
            }
        val counts = ledger.kind(envelope.kind)
        counts.seen++
        changed()
        when (val step = mirror.apply(envelope)) {
            is MirrorStep.Duplicate -> ledger.note("duplicate")
            is MirrorStep.Resync -> {
                ledger.skip(envelope.kind, "resync")
                lose()
            }
            is MirrorStep.Refused -> {
                ledger.refuse(step.reason)
                lose()
            }
            is MirrorStep.Applied -> {
                counts.applied++
                ledger.touched(ShadowKeys.BETWEEN, step.between, pageBuild, now)
                when {
                    envelope.kind !in ShadowKeys.KINDS -> ledger.skip(envelope.kind, "unknownKind")
                    ShadowFlags.INTERLEAVED in envelope.flags ->
                        ledger.skip(envelope.kind, "interleaved")
                    else -> ledger.touched(envelope.kind, step.touched, pageBuild, now)
                }
            }
        }
    }

    /**
     * Notes how long the worker took for one envelope; disables the check when the 95th percentile
     * of the last [SLOW_WINDOW] is over [SLOW_P95_MS] (section 4 budgets).
     */
    fun timed(millis: Long) {
        costs.addLast(millis)
        if (costs.size > SLOW_WINDOW) costs.removeFirst()
        if (costs.size == SLOW_WINDOW && costs.sorted()[SLOW_INDEX] > SLOW_P95_MS) disable("slow")
    }

    /** An internal error, counted by class name only; five in a day disable the check. */
    fun fault(error: Throwable, now: Long) {
        mirror.discard()
        needBase = true
        if (ledger.error(error.javaClass.simpleName.ifEmpty { "Throwable" }, now)) disable("errors")
        changed()
    }

    fun summary(): ShadowSummary =
        ShadowSummary(
            state = ledger.state,
            reason = ledger.reason,
            since = ledger.since,
            seen = ledger.seen,
            applied = ledger.applied,
            skipped = ledger.kinds.values.sumOf { it.skipped.values.sum() },
            last = ledger.entries().lastOrNull { it.kind != ShadowKeys.BETWEEN },
        )

    /** Clear (6e's button): the counts and entries go; ok once the ring is stored empty. */
    fun clear() {
        ledger.clear()
        changed()
    }

    /** The ring's text, fitted to its bounds; marks the ledger stored. */
    fun ringText(): String = ledger.fit().also { dirty = false }

    private fun refusedBase(reason: ShadowRefusal): ShadowAnswer {
        ledger.refuse(reason)
        if (reason == ShadowRefusal.TOO_LARGE) disable("tooLarge") else lose()
        changed()
        return ShadowAnswer(false, ledger.state, reason.word)
    }

    private fun lose() {
        mirror.discard()
        needBase = true
        changed()
    }

    private fun disable(reason: String) {
        mirror.discard()
        needBase = true
        set(ShadowState.DISABLED, reason)
    }

    private fun set(state: ShadowState, reason: String?) {
        ledger.state = state
        ledger.reason = reason
        current = state
        changed()
    }

    private fun changed() {
        dirty = true
    }

    companion object {
        /** The pseudo-kind for drops counted before an envelope is read. */
        const val ANY = "any"
        private const val SLOW_WINDOW = ShadowLimits.SLOW_WINDOW
        private const val SLOW_P95_MS = ShadowLimits.SLOW_P95_MS
        private const val SLOW_INDEX = SLOW_WINDOW * ShadowLimits.SLOW_PERCENTILE / 100 - 1
    }
}
