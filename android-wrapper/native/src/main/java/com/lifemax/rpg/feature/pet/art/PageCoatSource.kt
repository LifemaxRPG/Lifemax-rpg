package com.lifemax.rpg.feature.pet.art

import org.json.JSONObject

/**
 * The [CoatSource] that asks the page: the page composes the genetic (`dna_...`) coats and stays
 * the only one that does (`overview.cat.coat.v1`, the `coat {key, kind}` command).
 *
 * Contract, the same as the still portrait's: an answer that is not here yet is null, and asking
 * starts the one request that will fetch it (never a second while the first is out), so callers
 * simply ask again when [onChange] says something arrived. Nothing here waits, and no ask blocks:
 * [Requester.request] only queues the command.
 * - Once per (coat, sheet kind): answers are kept in small least-recently-used caches (sheets
 *   [MAX_SHEETS], palettes [MAX_PALETTES]) and a repeat ask is a lookup.
 * - Refusal is permanent: when the page says no (or its reply is malformed) the pair is never asked
 *   for again, and the caller keeps the still portrait. A request with no answer in [TIMEOUT_MS]
 *   (the screen was hidden, the page reloaded) is asked again, at most [MAX_TRIES] times in all.
 * - Only well-formed replies to a request that is out are taken. A reply for a request nobody is
 *   waiting for (a duplicate, a late one, another screen's) is dropped without effect.
 * - Only coat keys of the form `dna_...` are ever asked for, and at most [MAX_PENDING] requests are
 *   out at once.
 *
 * Thread-safe: asked from a worker thread, answered on the plugin's.
 */
class PageCoatSource(private val clock: () -> Long = { System.nanoTime() / 1_000_000 }) :
    CoatSource {

    /**
     * Sends the command `coat {key: coat, kind: kind.key}`; answers its request id. Must not block.
     */
    fun interface Requester {
        fun request(coat: String, kind: SheetKind): String
    }

    private class Pending(val coat: String, val kind: SheetKind, val at: Long)

    private val lock = Any()
    private val sheets = lru<Pair<String, SheetKind>, ByteArray>(MAX_SHEETS)
    private val palettes = lru<String, CatPalette>(MAX_PALETTES)
    private val refused = lru<Pair<String, SheetKind>, Boolean>(MAX_REFUSED)
    private val tries = lru<Pair<String, SheetKind>, Int>(MAX_REFUSED)
    private val pending = HashMap<String, Pending>()
    private var requester: Requester? = null
    private var listener: (() -> Unit)? = null

    /** Connects the source to the page (or disconnects it with null): requests go through [r]. */
    fun bind(r: Requester?) = synchronized(lock) { requester = r }

    /** Called (on the replying thread) whenever an answer or a refusal arrives. */
    fun onChange(l: (() -> Unit)?) = synchronized(lock) { listener = l }

    override fun sheet(coat: String, kind: SheetKind): ByteArray? =
        synchronized(lock) {
            val key = coat to kind
            sheets[key]?.let {
                return it
            }
            if (key in refused || !isDnaKey(coat)) return null
            val out = pending.values.firstOrNull { it.coat == coat && it.kind == kind }
            if (out != null) {
                if (clock() - out.at < TIMEOUT_MS) return null
                pending.values.remove(out)
            }
            val send = requester ?: return null
            val n = tries[key] ?: 0
            if (n >= MAX_TRIES) {
                refused[key] = true
                return null
            }
            if (pending.size >= MAX_PENDING) return null
            tries[key] = n + 1
            pending[send.request(coat, kind)] = Pending(coat, kind, clock())
            null
        }

    override fun palette(coat: String): CatPalette? = synchronized(lock) { palettes[coat] }

    /** True if the page refused the sheet (for good): the caller should stay on the still. */
    fun isRefused(coat: String, kind: SheetKind): Boolean =
        synchronized(lock) { (coat to kind) in refused }

    /** Treats a sheet that arrived but cannot be used (a decode that failed) as refused. */
    fun reject(coat: String, kind: SheetKind) {
        synchronized(lock) {
            sheets.remove(coat to kind)
            refused[coat to kind] = true
        }
        changed()
    }

    /**
     * Takes the page's reply to a command. Answers true if it was a reply to `coat` (whether or not
     * it was taken), so the caller does not hand it to anyone else.
     */
    fun onReply(reply: JSONObject): Boolean {
        if (!CoatReply.isCoat(reply)) return false
        val id = reply.optString("requestId")
        val taken =
            synchronized(lock) {
                val p = pending.remove(id) ?: return true // nobody is waiting: dropped
                val key = p.coat to p.kind
                if (CoatReply.refused(reply)) {
                    refused[key] = true
                } else {
                    try {
                        val answer = CoatReply.decode(reply, p.coat, p.kind)
                        sheets[key] = answer.png
                        palettes[p.coat] = answer.palette
                    } catch (e: IllegalArgumentException) {
                        refused[key] = true
                    }
                }
                true
            }
        if (taken) changed()
        return true
    }

    private fun changed() {
        val l = synchronized(lock) { listener }
        l?.invoke()
    }

    /** How many requests are out now (for tests). */
    internal val outstanding: Int
        get() = synchronized(lock) { pending.size }

    companion object {
        const val MAX_SHEETS = 12
        const val MAX_PALETTES = 8
        const val MAX_REFUSED = 64
        const val MAX_PENDING = 8
        const val MAX_TRIES = 3
        const val TIMEOUT_MS = 20_000L
        private val DNA_KEY = Regex("dna_[a-z0-9_]{1,59}")

        /** Only genetic coats are asked for; the four legacy coats ship with the app. */
        fun isDnaKey(coat: String): Boolean = DNA_KEY.matches(coat)

        private fun <K, V> lru(max: Int): LinkedHashMap<K, V> =
            object : LinkedHashMap<K, V>(16, 0.75f, true) {
                override fun removeEldestEntry(eldest: MutableMap.MutableEntry<K, V>) = size > max
            }
    }
}
