package com.lifemax.rpg.feature.pet.host

import com.lifemax.rpg.feature.pet.world.OutboxEntry
import com.lifemax.rpg.feature.pet.world.SaveWorld
import com.lifemax.rpg.feature.pet.world.WorldBlob
import com.lifemax.rpg.feature.pet.world.WorldCommand
import com.lifemax.rpg.feature.pet.world.WorldContract
import com.lifemax.rpg.feature.pet.world.WorldReply

/**
 * How the world reaches the page's save (docs/specs/CAT_BRAIN_PLAN.md 2.3): the one place that
 * knows the epoch and revision of the stored world, what is waiting to be sent and what was sent
 * without an answer. Pure state and decisions: it is told the time, asks the caller for a snapshot
 * and a way to send, and never reads a clock, starts a thread or touches the page.
 *
 * The rules, each of them tested against FakePage:
 * - **ok only when persisted.** A world write counts when the page answers ok, or when its next
 *   block shows the stored revision reached it; until then it is not trusted, the world stays dirty
 *   and the same operation id is sent again (the page answers "Already recorded." to a repeat of
 *   one that landed).
 * - **Never a flood.** At most one write per [HostTuning.WRITE_EVERY_MS] while the world only
 *   changes; an urgent one (a moment, a pair event) no closer than [HostTuning.URGENT_GAP_MS]; the
 *   flush on screen close and app stop goes at once. One command in flight at a time.
 * - **The outbox.** Commands not yet answered (at most [WorldContract.MAX_OUTBOX]) travel inside
 *   every write, so a restart re-sends them; a command leaves it when the page says ok.
 * - **Stale** replies from another epoch hold writing until the host has adopted the page's world
 *   ([reset]); a stale revision of the same epoch is caught up to and retried; a size refusal is
 *   answered by trimming one level and trying again; `unsaved` is tried again later.
 */
class WorldSync {
    private class Waiting(val command: WorldCommand) {
        var attempts = 0
        var retryAt = 0L
    }

    private class Flight(
        val command: WorldCommand,
        val requestId: String,
        val sentAt: Long,
        val waiting: Waiting?,
    )

    /** The page's world epoch the writes are made under. */
    var epoch = 0L
        private set

    /** The newest revision known to be stored by the page. */
    var rev = 0L
        private set

    /** After a reply from another epoch: no writes until [reset] adopts the page's world. */
    var held = false
        private set

    /** The epoch a stale reply named, while [held]. */
    var staleEpoch: Long? = null
        private set

    /** The trim level of the next write (see [WorldTrim]). */
    var trimLevel = 0
        private set

    /** Writes sent, and answers by reason (`ok`, `stale`, `size`, `unsaved`, ...), for tests. */
    var writes = 0
        private set

    private val answers = LinkedHashMap<String, Int>()

    val answered: Map<String, Int>
        get() = LinkedHashMap(answers)

    private val outbox = ArrayList<Waiting>()
    private var flight: Flight? = null
    private var dirty = false
    private var dirtyAgain = false
    private var urgent = false
    private var lastWriteAt = NEVER
    private var writeRetryAt = 0L
    private var counter = 0L

    /** Whether a write is wanted (the world changed since the last stored one). */
    val isDirty: Boolean
        get() = dirty

    /** Whether something was sent and is waiting for its answer. */
    val inFlight: Boolean
        get() = flight != null

    /** Nothing to write, nothing waiting and nothing in flight: a [pump] would do nothing. */
    val isIdle: Boolean
        get() = !dirty && outbox.isEmpty() && flight == null

    /** The commands not yet answered, in the order they were made, as the blob keeps them. */
    fun pending(): List<OutboxEntry> = outbox.map { OutboxEntry(it.command) }

    /**
     * Starts over on the page's [epoch] with its stored [rev], and the commands that were waiting
     * in the stored world ([pending]): after a start, a reopen, a backup restore.
     */
    fun reset(epoch: Long, rev: Long, pending: List<OutboxEntry> = emptyList()) {
        this.epoch = epoch
        this.rev = rev
        held = false
        staleEpoch = null
        flight = null
        trimLevel = 0
        outbox.clear()
        pending.take(WorldContract.MAX_OUTBOX).forEach { outbox += Waiting(it.command) }
        dirty = false
        dirtyAgain = false
        urgent = false
        writeRetryAt = 0L
    }

    /** The world changed: a write is due (within the usual interval). */
    fun markDirty() {
        dirty = true
        if (flight != null) dirtyAgain = true
    }

    /** Queues [command] for the page and asks for an urgent write that carries it in the outbox. */
    fun enqueue(command: WorldCommand) {
        val op = command.op
        if (op.isNotEmpty() && (outbox.any { it.command.op == op } || flight?.command?.op == op))
            return
        outbox += Waiting(command)
        while (outbox.size > WorldContract.MAX_OUTBOX) outbox.removeAt(0)
        urgent = true
        markDirty()
    }

    /**
     * The page's newest block shows revision [storedRev] stored: a write that reached it landed.
     */
    fun onBlock(storedRev: Long) {
        if (storedRev <= rev) return
        rev = storedRev
        val f = flight ?: return
        val save = f.command as? SaveWorld ?: return
        if (save.rev <= storedRev) {
            flight = null
            landed(dirty = dirtyAgain)
        }
    }

    /**
     * Sends what is due. [now] is the wall time; [visible] whether a screen that can hear the
     * page's answer shows (only then does anything go out unless [flush]); [flush] is the screen
     * closing or the app stopping: everything due goes at once, best effort. [snapshot] builds the
     * world blob to write (at trim level n), or null when there is none; [send] hands a command and
     * a fresh request id to a port and answers whether one took it.
     */
    fun pump(
        now: Long,
        visible: Boolean,
        flush: Boolean,
        snapshot: (Int) -> WorldBlob?,
        send: (WorldCommand, String) -> Boolean,
    ) {
        if (held) return
        var lost: SaveWorld? = null
        flight?.let {
            val waited = now - it.sentAt >= HostTuning.REPLY_TIMEOUT_MS
            // a flush over a write still unanswered is sent as the next revision: it is the newest
            if (!waited && !(flush && it.command is SaveWorld)) return
            flight = null // no answer: the same operation is sent again, which is repeat-safe
            it.waiting?.let { w -> w.retryAt = 0L }
            if (it.command is SaveWorld) {
                dirty = true
                if (waited) lastWriteAt = NEVER // due at once: the debounce is for fresh changes
                else lost = it.command
            }
        }
        if (!visible && !flush) return
        if (dirty && writeDue(now, flush)) {
            val next = (lost?.rev ?: rev) + 1
            val blob = snapshot(trimLevel) ?: return
            val command =
                SaveWorld(
                    op = "world:$epoch:$next",
                    epoch = epoch,
                    rev = next,
                    blob = blob.copy(outbox = pending()),
                )
            val id = requestId()
            if (!send(command, id)) return
            flight = Flight(command, id, now, null)
            writes++
            lastWriteAt = now
            dirty = false
            dirtyAgain = false
            urgent = false
            return
        }
        val due = outbox.firstOrNull { it.retryAt <= now } ?: return
        val id = requestId()
        if (send(due.command, id)) flight = Flight(due.command, id, now, due)
    }

    /** The page's answer to the command sent as [reply].requestId; false when it is not ours. */
    fun onReply(reply: WorldReply, now: Long): Boolean {
        val f = flight ?: return false
        if (reply.requestId != f.requestId) return false
        flight = null
        val save = f.command as? SaveWorld
        if (save != null) saved(save, reply, now) else answered(f.waiting!!, reply, now)
        return true
    }

    private fun saved(save: SaveWorld, reply: WorldReply, now: Long) {
        count(if (reply.ok) "ok" else reply.why.ifEmpty { "refused" })
        if (reply.ok) {
            rev = maxOf(rev, save.rev)
            landed(dirty = dirtyAgain || !reply.changed)
            return
        }
        dirty = true
        lastWriteAt = NEVER // a retry waits for its own timer, not for the debounce
        when (reply.why) {
            "stale" -> {
                val theirs = reply.epoch
                if (theirs != null && theirs != epoch) {
                    held = true
                    staleEpoch = theirs
                } else if (reply.rev != null && reply.rev > rev) {
                    rev = reply.rev // a newer revision is stored: write the next one
                } else writeRetryAt = now + HostTuning.RETRY_MS
            }
            "size" ->
                if (trimLevel < HostTuning.TRIM_LEVEL_LAST) {
                    trimLevel++
                    writeRetryAt = now + HostTuning.TRIM_RETRY_MS
                } else {
                    dirty = false // nothing left to trim: wait for the next change
                }
            "unsaved" -> writeRetryAt = now + HostTuning.RETRY_MS
            else -> writeRetryAt = now + HostTuning.RETRY_MS * 2
        }
    }

    private fun landed(dirty: Boolean) {
        this.dirty = dirty
        writeRetryAt = 0L
    }

    private fun answered(w: Waiting, reply: WorldReply, now: Long) {
        count(if (reply.ok) "ok" else reply.why.ifEmpty { "refused" })
        if (reply.ok) {
            outbox.remove(w)
            markDirty() // the outbox in the stored world is out of date
            return
        }
        w.attempts++
        if (reply.why != "unsaved" && w.attempts >= HostTuning.MAX_ATTEMPTS) outbox.remove(w)
        else w.retryAt = now + HostTuning.RETRY_MS
    }

    private fun writeDue(now: Long, flush: Boolean): Boolean {
        if (flush) return true
        if (now < writeRetryAt) return false
        val since = now - lastWriteAt
        return since >= HostTuning.WRITE_EVERY_MS || (urgent && since >= HostTuning.URGENT_GAP_MS)
    }

    private fun requestId() = "cwh-${++counter}"

    private fun count(key: String) {
        answers.merge(key, 1, Int::plus)
    }

    private companion object {
        const val NEVER = Long.MIN_VALUE / 2
    }
}

/**
 * Makes a world blob smaller, level by level, when the page refuses it for size (plan 2.3): first
 * the places below the top few, then the story of the last catch-up, then the toys nobody is
 * holding, last the places down to two.
 */
object WorldTrim {
    fun apply(blob: WorldBlob, level: Int): WorldBlob {
        if (level <= 0) return blob
        var b = blob
        val keep =
            if (level >= HostTuning.TRIM_LEVEL_LAST) HostTuning.TRIM_SPOTS_LAST
            else HostTuning.TRIM_SPOTS_TO
        b =
            b.copy(
                cats =
                    b.cats.mapValues { (_, c) ->
                        c.copy(
                            spots =
                                c.spots.entries
                                    .sortedByDescending { it.value }
                                    .take(keep)
                                    .associate { it.toPair() }
                        )
                    }
            )
        if (level >= HostTuning.TRIM_LEVEL_AWAY) b = b.copy(away = null)
        if (level >= HostTuning.TRIM_LEVEL_TOYS) b = b.copy(toys = b.toys.filter { it.by != null })
        return b
    }
}
