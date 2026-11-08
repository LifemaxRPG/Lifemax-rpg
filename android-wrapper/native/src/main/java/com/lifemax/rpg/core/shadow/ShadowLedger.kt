package com.lifemax.rpg.core.shadow

import java.util.TreeMap

/** Where the check stands (ECONOMY_SHADOW_PLAN 2.3, 3.3). */
enum class ShadowState(val word: String) {
    /** The owner's switch is off: nothing is taken. */
    OFF("off"),

    /** On: envelopes are taken and recorded. */
    ON("on"),

    /**
     * Stopped after errors, a resync loop, slowness or a save too large ([ShadowLedger.reason]).
     */
    DISABLED("disabled"),

    /** The page's rules are not the ones native was built from (an OTA page): compares nothing. */
    STALE("stale"),

    /** Paused while Battery Saver is on (decision O-5): envelopes are dropped and counted. */
    PAUSED("paused");

    companion object {
        fun of(word: String): ShadowState? = entries.firstOrNull { it.word == word }
    }
}

/** The counts kept for one action kind. */
class KindCounts {
    /** Envelopes of this kind received. */
    var seen = 0

    /** Envelopes applied to the mirror (6a records their touch map; 6b+ compares them). */
    var applied = 0

    /** Envelopes not applied, by reason word (`queue`, `battery`, `resync`, `unknownKind`, ...). */
    val skipped = TreeMap<String, Int>()
}

/**
 * One touch-map entry: an action kind and the fields it changed (WP6a's no-op comparison). Entries
 * with the same kind and paths are one entry whose [count] and [last] grow.
 */
data class TouchEntry(
    val id: String,
    val first: Long,
    val last: Long,
    val count: Int,
    val kind: String,
    val pageBuild: String,
    val paths: List<String>,
) {
    /** What makes two recordings the same entry. */
    val signature: String
        get() = kind + "\u0000" + paths.joinToString("\u0000")
}

/**
 * Everything the shadow keeps between page sessions (the ring's content, 3.1), as a pure model: the
 * header (since, salt, state, reason, app, faults), counters per kind, decoder refusals, internal
 * errors by class name, and at most [ShadowLimits.RING_MAX_ENTRIES] touch entries, coalesced by
 * signature, oldest dropped first. Holds no value from the save and no owner text: kinds are
 * validated words, paths come from [TouchPaths], the page build is the page's own label. Not
 * thread-safe: one worker owns it.
 */
class ShadowLedger(val since: Long, val salt: ByteArray) {
    var state: ShadowState = ShadowState.OFF
    var reason: String? = null
    var versionCode: Long = 0
    var nativeApi: Int = 0
    var nativeFaults: Int = 0
    var pageFaults: Int = 0
    val kinds = TreeMap<String, KindCounts>()
    val refused = TreeMap<String, Int>()
    val info = TreeMap<String, Int>()
    val errors = TreeMap<String, Int>()
    internal val errorTimes = ArrayList<Long>()
    internal val resyncTimes = ArrayList<Long>()
    internal val entries = ArrayList<TouchEntry>()
    internal var nextId = 1

    /** The touch entries, oldest first. */
    fun entries(): List<TouchEntry> = entries.toList()

    /** The counts of [kind]; past [ShadowLimits.MAX_KINDS] kinds, new ones share `other`. */
    fun kind(kind: String): KindCounts =
        kinds[kind]
            ?: (if (kinds.size >= ShadowLimits.MAX_KINDS) kinds.getOrPut(OTHER) { KindCounts() }
            else KindCounts().also { kinds[kind] = it })

    /** Counts one skipped envelope of [kind] for [why]. */
    fun skip(kind: String, why: String) {
        kind(kind).skipped.merge(why, 1, Int::plus)
    }

    /** Counts one refused message. */
    fun refuse(reason: ShadowRefusal) {
        refused.merge(reason.word, 1, Int::plus)
    }

    /** Counts one informational event (`info.*`). */
    fun note(word: String) {
        info.merge(word, 1, Int::plus)
    }

    /**
     * Counts an internal error by its class name only and answers whether the check must now
     * disable itself ([ShadowLimits.ERRORS_BEFORE_DISABLE] within [ShadowLimits.ERROR_WINDOW_MS]).
     */
    fun error(className: String, now: Long): Boolean {
        nativeFaults++
        errors.merge(className.take(MAX_CLASS_NAME), 1, Int::plus)
        if (errors.size > MAX_ERROR_CLASSES) errors.pollLastEntry()
        errorTimes += now
        errorTimes.removeAll { now - it >= ShadowLimits.ERROR_WINDOW_MS }
        return errorTimes.size >= ShadowLimits.ERRORS_BEFORE_DISABLE
    }

    /** Notes one resynchronisation and answers whether they now come too often (`resyncLoop`). */
    fun resync(now: Long): Boolean {
        resyncTimes += now
        resyncTimes.removeAll { now - it >= ShadowLimits.HOUR_MS }
        return resyncTimes.size > ShadowLimits.RESYNCS_PER_HOUR
    }

    /** Records that one action of [kind] changed [paths]; nothing for an empty list. */
    fun touched(kind: String, paths: List<String>, pageBuild: String, now: Long) {
        if (paths.isEmpty()) return
        val at = minute(now)
        val entry = TouchEntry("t-$nextId", at, at, 1, kind, pageBuild, TouchPaths.capped(paths))
        val i = entries.indexOfFirst { it.signature == entry.signature }
        if (i >= 0) {
            val old = entries.removeAt(i)
            entries += old.copy(last = at, count = old.count + 1, pageBuild = pageBuild)
            return
        }
        nextId++
        entries += entry
        while (entries.size > ShadowLimits.RING_MAX_ENTRIES) entries.removeAt(0)
    }

    /** Drops the oldest entries until the ring's text fits [ShadowLimits.RING_MAX_BYTES]. */
    fun fit(): String {
        var text = ShadowLedgerJson.write(this)
        while (text.toByteArray(Charsets.UTF_8).size > ShadowLimits.RING_MAX_BYTES) {
            if (entries.isEmpty()) {
                kinds.clear()
                text = ShadowLedgerJson.write(this)
                break
            }
            entries.subList(0, maxOf(1, entries.size / FIT_STEP)).clear()
            text = ShadowLedgerJson.write(this)
        }
        return text
    }

    /** Empties the counts and entries (Clear); the salt, state and app stay. */
    fun clear() {
        kinds.clear()
        refused.clear()
        info.clear()
        errors.clear()
        errorTimes.clear()
        resyncTimes.clear()
        entries.clear()
        nativeFaults = 0
        pageFaults = 0
        nextId = 1
    }

    /** All envelopes received, over every kind. */
    val seen: Int
        get() = kinds.values.sumOf { it.seen }

    /** All envelopes applied, over every kind. */
    val applied: Int
        get() = kinds.values.sumOf { it.applied }

    companion object {
        /** The kind under which kinds past [ShadowLimits.MAX_KINDS] are counted. */
        const val OTHER = "other"
        private const val MAX_CLASS_NAME = 60
        private const val MAX_ERROR_CLASSES = 16
        private const val FIT_STEP = 10

        /** [millis] floored to the minute (3.1: times are kept to the minute). */
        fun minute(millis: Long): Long = millis - Math.floorMod(millis, ShadowLimits.MINUTE_MS)
    }
}
