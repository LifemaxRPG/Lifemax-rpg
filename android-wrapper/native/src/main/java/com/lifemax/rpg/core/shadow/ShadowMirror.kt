package com.lifemax.rpg.core.shadow

import com.lifemax.rpg.core.save.JsonArray
import com.lifemax.rpg.core.save.JsonValue

/** What a base part did to the mirror. */
sealed interface BaseStep {
    /** The part was taken; more are expected. */
    data object Pending : BaseStep

    /** The last part arrived and decoded: the mirror now holds the page's state at [seq]. */
    data class Ready(val seq: Long, val chars: Int) : BaseStep

    /** The same base was already applied (a repeated `(sid, seq)`): nothing changed. */
    data object Duplicate : BaseStep

    /** The part was refused; any base being assembled is dropped. */
    data class Refused(val reason: ShadowRefusal) : BaseStep
}

/** What an envelope did to the mirror. */
sealed interface MirrorStep {
    /**
     * Applied: [between] names the fields that changed since the last action (edits outside any
     * shadow point), [touched] the fields the action itself changed; [before] and [after] are the
     * page's shadow keys just before and just after the action (what the comparison reads).
     */
    data class Applied(
        val between: List<String>,
        val touched: List<String>,
        val before: Map<String, JsonValue>,
        val after: Map<String, JsonValue>,
    ) : MirrorStep

    /** A repeated `(sid, seq)`: answered as accepted, ignored. */
    data object Duplicate : MirrorStep

    /** Out of step with the page ([reason]); the mirror was discarded and a base is needed. */
    data class Resync(val reason: String) : MirrorStep

    /** The delta does not fit the mirror (a record index past the end); discarded as above. */
    data class Refused(val reason: ShadowRefusal) : MirrorStep
}

/**
 * Native's in-memory copy of the page's shadow keys for one page session (ECONOMY_SHADOW_PLAN 2.2,
 * 2.4). Never written to disk; process death loses it and the page's next session sends a new base.
 * Not thread-safe: one worker owns it.
 *
 * Ordering: a base sets the session ([sid], [epoch]) and [seq]; an envelope applies only when its
 * `base` equals [seq], with the same session and epoch, and moves [seq] to its own. A repeated
 * `(sid, seq)` is ignored. Anything else discards the mirror and asks for a base.
 */
class ShadowMirror(redactor: ShadowRedactor) {
    private val paths = TouchPaths(redactor)
    private val keys = HashMap<String, JsonValue>()
    private var assembly: Assembly? = null

    /** The page session the mirror belongs to, or null before the first base. */
    var sid: String? = null
        private set

    /** The page's epoch of the held state. */
    var epoch: Long = 0
        private set

    /** The sequence number of the last message applied. */
    var seq: Long = 0
        private set

    /** The length of the base text the mirror was built from (the memory budget's measure). */
    var heldChars: Int = 0
        private set

    /** Whether the mirror holds a state to apply envelopes to. */
    val ready: Boolean
        get() = sid != null

    /** A copy of the held shadow keys (tests and the comparison stages read it). */
    fun snapshot(): Map<String, JsonValue> = HashMap(keys)

    /** Forgets everything, the base being assembled included. */
    fun discard() {
        keys.clear()
        assembly = null
        sid = null
        epoch = 0
        seq = 0
        heldChars = 0
    }

    /** Takes one base part, in order (part 0 first); the last one replaces the held state. */
    fun base(part: ShadowBasePart): BaseStep {
        if (part.sid == sid && part.seq == seq && part.epoch == epoch) return BaseStep.Duplicate
        if (part.sid == sid && part.seq < seq) return BaseStep.Refused(ShadowRefusal.OUT_OF_RANGE)
        val a = assembly
        if (part.part == 0) {
            assembly = Assembly(part.sid, part.epoch, part.seq, part.parts)
        } else if (
            a == null ||
                a.sid != part.sid ||
                a.epoch != part.epoch ||
                a.seq != part.seq ||
                a.parts != part.parts ||
                a.next != part.part
        ) {
            assembly = null
            return BaseStep.Refused(ShadowRefusal.BAD_DELTA)
        }
        val current = assembly!!
        if (current.text.length + part.text.length > ShadowLimits.MAX_HELD_CHARS) {
            assembly = null
            return BaseStep.Refused(ShadowRefusal.TOO_LARGE)
        }
        current.text.append(part.text)
        current.next++
        if (current.next < current.parts) return BaseStep.Pending
        assembly = null
        return when (val decoded = ShadowEnvelopeJson.baseKeys(current.text.toString())) {
            is ShadowDecode.Refused -> BaseStep.Refused(decoded.reason)
            is ShadowDecode.Ok -> {
                keys.clear()
                keys.putAll(decoded.value)
                sid = current.sid
                epoch = current.epoch
                seq = current.seq
                heldChars = current.text.length
                BaseStep.Ready(current.seq, heldChars)
            }
        }
    }

    /** Applies one envelope's deltas: the before-delta, then the after-delta. */
    fun apply(envelope: ShadowEnvelope): MirrorStep {
        val held = sid
        if (held == null) return resync("noBase")
        if (envelope.sid != held) return resync("session")
        if (envelope.epoch != epoch) return resync("epoch")
        if (envelope.seq <= seq) return MirrorStep.Duplicate
        if (envelope.base != seq) return resync("gap")
        val between = paths.of(envelope.before, keys)
        if (!lay(envelope.before)) return refused()
        val before = HashMap(keys)
        val touched = paths.of(envelope.after, keys)
        if (!lay(envelope.after)) return refused()
        seq = envelope.seq
        return MirrorStep.Applied(between, touched, before, HashMap(keys))
    }

    private fun resync(reason: String): MirrorStep {
        discard()
        return MirrorStep.Resync(reason)
    }

    private fun refused(): MirrorStep {
        discard()
        return MirrorStep.Refused(ShadowRefusal.BAD_DELTA)
    }

    /** Lays [delta] over the held keys; false when a record delta leaves a hole. */
    private fun lay(delta: ShadowDelta): Boolean {
        val records = HashMap<String, JsonArray>()
        for ((key, rec) in delta.rec) records[key] = recordsAfter(keys[key], rec) ?: return false
        keys.putAll(delta.set)
        for (key in delta.del) keys.remove(key)
        keys.putAll(records)
        return true
    }

    private fun recordsAfter(current: JsonValue?, rec: RecordDelta): JsonArray? {
        val old = (current as? JsonArray)?.items ?: emptyList()
        val slots = arrayOfNulls<JsonValue>(rec.length)
        for (i in 0 until minOf(old.size, rec.length)) slots[i] = old[i]
        for (change in rec.changes) slots[change.index] = change.value
        if (slots.any { it == null }) return null
        return JsonArray(slots.map { it!! })
    }

    private class Assembly(val sid: String, val epoch: Long, val seq: Long, val parts: Int) {
        val text = StringBuilder()
        var next = 0
    }
}
