package com.lifemax.rpg.feature.pet.host

import com.lifemax.rpg.feature.pet.body.HouseCat
import com.lifemax.rpg.feature.pet.body.HouseLife
import com.lifemax.rpg.feature.pet.body.Rng
import com.lifemax.rpg.feature.pet.body.ToyField
import com.lifemax.rpg.feature.pet.brain.Brain
import com.lifemax.rpg.feature.pet.brain.BrainFactory
import com.lifemax.rpg.feature.pet.brain.BrainState
import com.lifemax.rpg.feature.pet.brain.OwnerAct
import com.lifemax.rpg.feature.pet.brain.PlayAnswer
import com.lifemax.rpg.feature.pet.world.Away
import com.lifemax.rpg.feature.pet.world.BlobCat
import com.lifemax.rpg.feature.pet.world.SystemTimeSource
import com.lifemax.rpg.feature.pet.world.TimeSource
import com.lifemax.rpg.feature.pet.world.WorldBlob
import com.lifemax.rpg.feature.pet.world.WorldBlock
import com.lifemax.rpg.feature.pet.world.WorldCat
import com.lifemax.rpg.feature.pet.world.WorldClock
import com.lifemax.rpg.feature.pet.world.WorldCommand
import com.lifemax.rpg.feature.pet.world.WorldCommandJson
import com.lifemax.rpg.feature.pet.world.WorldFeed
import java.util.TimeZone
import org.json.JSONObject

/**
 * How a screen's plugin lets the host talk to the page: the screen shows ([visible]: only then can
 * the page's answer reach it) and [send] hands a command to the page, answering whether it went.
 */
interface WorldPort {
    val visible: Boolean

    fun send(command: String, payload: JSONObject, requestId: String): Boolean
}

/** A view that draws the world (a screen's room): hold it while shown, hand it back when gone. */
class WorldView internal constructor()

/**
 * The one cat world of the process (docs/specs/CAT_BRAIN_PLAN.md 2.7), so the Overview card and the
 * Cat tab show the same two cats in the same places. Headless: frames, the page's blocks and
 * answers are pushed in by the screens and plugins; it never starts a thread or reads a view.
 *
 * Lifecycle. It is **inactive** until a brain exists ([brainFactory], set by the app), the page has
 * sent a good `world` block, and it still sends them: then the screens keep their shipped autopilot
 * rooms (an older page, a page that sends no block, a switch off). It starts on the first good
 * block: the page's stored world is restored (or each cat imported from its web brain once), the
 * gap since the last write is caught up, and [house] is the one set of bodies every screen draws.
 * It runs only while a view is attached and frames arrive ([advance], idempotent per frame time);
 * with no view it is frozen, and the next view re-anchors the clock and catches up the gap. It is
 * saved through [WorldSync] (debounced, change-detected, outbox, epoch and revision), flushed when
 * the last view leaves and when the app stops ([onStop]). A new epoch (a backup restore), a
 * different set of cats or a lost block rebuilds it ([generation] changes, so screens rebuild their
 * stage). Calls come from any thread; they are cheap and serialised on one lock.
 */
class CatWorldHost(
    private val feed: WorldFeed = WorldFeed.shared,
    private val time: TimeSource = SystemTimeSource,
    private val zoneOffsetMin: () -> Int = ::deviceZoneMinutes,
    /** The brain to build for the living cats; null keeps the host inactive. */
    @Volatile var brainFactory: BrainFactory? = AutopilotBrain.factory,
) {
    private val lock = Any()
    private val sync = WorldSync()
    private val ports = LinkedHashMap<String, WorldPort>()
    private val views = ArrayList<WorldView>()
    private val listeners = java.util.concurrent.CopyOnWriteArrayList<Runnable>()

    private var seen: WorldBlock? = null
    private var seenAt = 0L
    private val cues = WorldCues()
    private var misses = 0
    private var errorsSeen = 0
    private var lastPumpAt = 0L
    private var clock: WorldClock? = null
    private var brain: Brain? = null
    private var current: WorldBlob? = null
    private var block: WorldBlock? = null

    private var needAnchor = false
    private var lastBodyNanos = 0L
    private var lastCheckAt = 0L
    private var lastWriteableHash = 0
    private var lastPlaces = 0
    private var lastHeartbeatAt = 0L
    private var away: Away? = null

    private val subStep =
        WorldClock.SubStepListener { step, think ->
            val b = brain ?: return@SubStepListener
            b.step(step, think)
            if (think) {
                val reports = b.takeReports()
                if (reports.isNotEmpty()) reports.forEach(sync::enqueue)
            }
        }

    /** Whether the world exists: the screens draw [house] instead of their own autopilot rooms. */
    @Volatile
    var active = false
        private set

    /** Changes whenever [house] is rebuilt or gone, so a screen knows to make its stage anew. */
    @Volatile
    var generation = 0
        private set

    /** The one set of bodies every screen draws; null while [active] is false. */
    @Volatile
    var house: HouseLife? = null
        private set

    /** The house's toys, shared by its cats and drawn with them; null while [active] is false. */
    @Volatile
    var toys: ToyField? = null
        private set

    /**
     * Whether the brain moves the bodies of [house] itself (the native brain, not the autopilot).
     */
    val brainDrives: Boolean
        get() = synchronized(lock) { active && brain?.drivesBodies == true }

    /**
     * The owner did [kind] to the cats (`feed:<slot>`, `blink:<slot>`, `bowl:<slot>`; see the
     * brain's owner acts): handed to the brain. True when the brain moves the bodies and so answers
     * it itself; false leaves it to the caller's own reflex (the shipped autopilot's).
     */
    fun ownerAct(kind: String): Boolean {
        synchronized(lock) {
            val b = brain ?: return false
            if (!active) return false
            b.owner(OwnerAct(clock?.simMs ?: 0L, kind))
            return b.drivesBodies
        }
    }

    /**
     * The owner tossed the page's toy [key] into the room (the Cat tab's toy tray): the brain drops
     * it near the cats and sends one to play, or says why none will. Null when no world is running
     * that the brain moves (the caller then has nothing to offer the toy to).
     */
    fun offerToy(key: String): PlayAnswer? {
        synchronized(lock) {
            val b = brain?.takeIf { active && it.drivesBodies } ?: return null
            val c = clock ?: return null
            return b.offerToy(key, c.epochMs()).also { sync.markDirty() }
        }
    }

    /**
     * DEVELOPER DEMO: one cat runs a parkour tour now (the Cat tab's "Demo parkour" button). Null
     * when no world is running that the brain moves. No data effects (nothing goes to the page).
     */
    fun demoParkour(): PlayAnswer? {
        synchronized(lock) {
            val b = brain?.takeIf { active && it.drivesBodies } ?: return null
            return b.demoParkour()
        }
    }

    /** The living cats of the world as the page last described them. */
    @Volatile
    var cats: List<WorldCat> = emptyList()
        private set

    /** Views attached now. */
    val viewCount: Int
        get() = synchronized(lock) { views.size }

    /** Writes sent to the page so far, and the answers by reason (tests and diagnostics). */
    val writes: Int
        get() = synchronized(lock) { sync.writes }

    val answers: Map<String, Int>
        get() = synchronized(lock) { sync.answered }

    /**
     * [listener] runs whenever the world is built, rebuilt or retired ([generation] changed), on
     * whichever thread caused it: a screen sets a state and recomposes. Remove it with
     * [removeListener].
     */
    fun addListener(listener: Runnable) {
        listeners += listener
    }

    fun removeListener(listener: Runnable) {
        listeners -= listener
    }

    // ------------------------------------------------------------------ ports and the page

    /** A screen's plugin offers its way to the page under [id] (once per plugin). */
    fun registerPort(id: String, port: WorldPort) {
        synchronized(lock) { ports[id] = port }
    }

    fun unregisterPort(id: String) {
        synchronized(lock) { ports.remove(id) }
    }

    /**
     * A projection of a screen reached the plugin: [hasBlock] whether it carried a `world` block
     * and [accepted] whether the feed took it as the newest. Three projections in a row without a
     * good block mean the page stopped sending it: the world retires and the screens fall back.
     */
    fun onProjection(hasBlock: Boolean, accepted: Boolean) {
        synchronized(lock) {
            val errors = feed.errors.values.sum()
            val bad = !hasBlock || errors > errorsSeen
            errorsSeen = errors
            if (accepted || (hasBlock && !bad)) misses = 0 else if (bad) misses++
            if (misses >= LOST_AFTER && active) retire()
            if (accepted) take()
        }
    }

    /** The page's answer reached a plugin; true when it was the answer to one of the host's. */
    fun onReply(reply: JSONObject): Boolean {
        if (!reply.optString("requestId").startsWith(REQUEST_PREFIX)) return false
        synchronized(lock) {
            val r = WorldCommandJson.reply(reply) ?: return true
            sync.onReply(r, time.epochMs())
            if (sync.held) take()
            pump(false)
        }
        return true
    }

    // ------------------------------------------------------------------ views and lifecycle

    /** A screen's room appeared: the world runs while any view is held. */
    fun attach(): WorldView {
        val view = WorldView()
        synchronized(lock) {
            if (views.isEmpty()) needAnchor = true
            views += view
            take()
            if (views.size == 1) ownerAct(OWNER_OPEN)
            playCues() // a meal served just before the room appeared (the Cat tab opened for it)
        }
        return view
    }

    /** The room left the screen (idempotent). The last one to leave saves what is waiting. */
    fun detach(view: WorldView) {
        synchronized(lock) {
            if (views.remove(view) && views.isEmpty()) {
                ownerAct(OWNER_CLOSE)
                cues.hidden()
                markIfChanged(true)
                pump(true)
            }
        }
    }

    /**
     * The app stopped: save at once and re-anchor the clock on the next frame (the gap is caught
     * up).
     */
    fun onStop() {
        synchronized(lock) {
            if (active) {
                needAnchor = true
                markIfChanged(true)
                pump(true)
            }
        }
    }

    /** Sends what is due now, best effort (a screen closing). */
    fun flush() {
        synchronized(lock) {
            if (active) {
                markIfChanged(true)
                pump(true)
            }
        }
    }

    /**
     * Brings the world up to the frame at [frameNanos] (the frame clock the views are drawn on):
     * runs the brain's sub-steps due, steps the bodies and, now and then, saves. A second call with
     * the same frame time (a second view) does nothing.
     */
    fun advance(frameNanos: Long) {
        synchronized(lock) {
            take()
            val c = clock ?: return
            val h = house ?: return
            if (needAnchor) anchor(c, frameNanos)
            if (frameNanos <= lastBodyNanos) return
            c.advanceTo(frameNanos, WorldClock.MAX_LIVE_STEPS, subStep)
            val dt = ((frameNanos - lastBodyNanos) / NANOS_PER_SECOND).toFloat()
            lastBodyNanos = frameNanos
            // a brain that moves the bodies steps them on its own sub-steps (above)
            if (brain?.drivesBodies != true) h.step(dt.coerceAtMost(HostTuning.MAX_STEP_S))
            val wall = time.epochMs()
            if (wall - lastCheckAt >= HostTuning.CHANGE_CHECK_MS) {
                lastCheckAt = wall
                markIfChanged(false)
            }
            if (wall - lastPumpAt >= PUMP_EVERY_MS) {
                lastPumpAt = wall
                pump(false)
            }
        }
    }

    // ------------------------------------------------------------------ reading the world

    /** Cat [id]'s behaviour state as the brain keeps it now, for the panels. */
    fun stateOf(id: String): BlobCat? {
        synchronized(lock) {
            return if (active) snapshot(0)?.cats?.get(id) else null
        }
    }

    /** The "while you were away" story of the last catch-up, or null. */
    fun awayStory(): Away? {
        synchronized(lock) {
            return if (active) away else null
        }
    }

    /** The world as it would be written now (tests and the day viewer). */
    fun snapshotNow(): WorldBlob? {
        synchronized(lock) {
            return snapshot(0)
        }
    }

    // ------------------------------------------------------------------ the page's blocks

    /** Takes the feed's newest block when it is new: starts, updates or rebuilds the world. */
    private fun take() {
        val b = feed.latest ?: return
        if (b === seen) return
        seen = b
        seenAt = time.epochMs()
        misses = 0
        if (brainFactory == null || b.cats.isEmpty()) {
            if (active) retire()
            return
        }
        val same = active && b.epoch == sync.epoch && b.cats.map { it.id } == cats.map { it.id }
        if (!same) start(b)
        else {
            block = b
            cats = b.cats
            brain?.update(b)
            clock?.clockOffsetMs = b.clockOffsetMs
            applyHunger(b)
            sync.onBlock(b.stored?.rev ?: 0L)
        }
        playCues()
        pump(false)
    }

    /** The meals the page served (its block's `fed` cues), each played once while a view shows. */
    private fun playCues() {
        val b = block ?: return
        val br = brain ?: return
        if (!active || !br.drivesBodies || views.isEmpty()) return
        cues.take(b.fedCues, seenAt, time.epochMs()) { slot ->
            br.owner(OwnerAct(clock?.simMs ?: 0L, "feed:$slot"))
        }
    }

    private fun applyHunger(b: WorldBlock) {
        val h = house ?: return
        b.cats.forEachIndexed { i, c ->
            if (i < h.count) h.bodies[i].weak = c.hunger.stage >= WEAK_FROM
        }
    }

    /** Builds the world for [b]: restore or import, place the cats, catch up the gap. */
    private fun start(b: WorldBlock) {
        val factory = brainFactory ?: return
        if (active && b.epoch == sync.epoch)
            pump(true) // the old world is saved before it is rebuilt
        val c = WorldClock(time, zoneOffsetMin(), b.clockOffsetMs)
        val stored = b.stored
        val blob =
            WorldImport.merge(
                b,
                stored?.blob,
                stored?.rejectedCats.orEmpty(),
                c.dayKey(),
                c.hourOfDay(),
                c.epochMs(),
            )
        val made = factory.create(b.cats, c)
        made.update(b)
        made.restore(BrainState(blob.cats, blob.pair, blob.toys, blob.gift))
        val life =
            HouseLife(
                b.cats.map { HouseCat(Rng.hash(it.id), it.hunger.stage >= WEAK_FROM) },
                autopilot = !made.drivesBodies,
            )
        val field = WorldToys.field(b, blob)
        WorldToys.attach(life, field, b.cats)
        if (made.drivesBodies) {
            made.bind(life.bodies.toList())
            made.bindToys(field)
        } else
            b.cats.forEachIndexed { i, cat ->
                blob.cats[cat.id]?.pos?.let { HousePlaces.place(life, i, it) }
            }
        clock = c
        brain = made
        current = blob
        block = b
        cats = b.cats
        house = life
        toys = field
        away = blob.away
        sync.reset(b.epoch, stored?.rev ?: 0L, blob.outbox)
        needAnchor = true
        lastCheckAt = time.epochMs()
        lastHeartbeatAt = lastCheckAt
        lastWriteableHash = 0
        active = true
        generation++
        listeners.forEach { it.run() }
        if (views.isNotEmpty()) made.owner(OwnerAct(c.simMs, OWNER_OPEN))
        val gap = if (blob.at > 0L) c.epochMs() - blob.at else 0L
        if (gap >= HostTuning.CATCH_UP_MIN_MS) catchUp(gap)
        lastWriteableHash = fingerprint(snapshot(0))
        lastPlaces = placesPrint(snapshot(0))
        // a world new to the page (an import) is written soon; a restored one only when it changes
        if (stored?.blob == null) sync.markDirty()
    }

    private fun retire() {
        pump(true)
        active = false
        house = null
        toys = null
        clock = null
        brain = null
        current = null
        generation++
        listeners.forEach { it.run() }
    }

    // ------------------------------------------------------------------ time

    private fun anchor(c: WorldClock, frameNanos: Long) {
        val gap = c.anchorTo(frameNanos, time.epochMs())
        lastBodyNanos = frameNanos
        needAnchor = false
        if (gap >= HostTuning.CATCH_UP_MIN_MS) catchUp(gap)
    }

    /** Fast-forwards [gapMs] (at most a day): the brain's story places the cats and tells it. */
    private fun catchUp(gapMs: Long) {
        val b = brain ?: return
        val h = house ?: return
        val c = clock ?: return
        val covered = gapMs.coerceAtMost(HostTuning.CATCH_UP_MAX_MS)
        val result = b.catchUp(covered)
        // a brain that moves the bodies has put them where its story ends; else the host does
        if (!b.drivesBodies)
            result.places.forEach { p -> if (p.slot in 0 until h.count) HousePlaces.placeAt(h, p) }
        val lines =
            result.lines
                .map { it.trim().take(HostTuning.AWAY_LINE_MAX) }
                .filter { it.isNotEmpty() }
                .take(HostTuning.AWAY_LINES)
        if (lines.isNotEmpty()) {
            away = Away(c.epochMs() - gapMs, lines)
            sync.markDirty()
        }
    }

    // ------------------------------------------------------------------ persistence

    /** The world as it would be written (the outbox is added by the sync). */
    private fun snapshot(trim: Int): WorldBlob? {
        val base = current ?: return null
        val h = house ?: return null
        val c = clock ?: return null
        val state = brain?.export() ?: BrainState(base.cats, base.pair, base.toys, base.gift)
        val placed = LinkedHashMap<String, BlobCat>()
        cats.forEachIndexed { i, cat ->
            val saved = state.cats[cat.id] ?: base.cats[cat.id] ?: return@forEachIndexed
            placed[cat.id] =
                if (i < h.count) saved.copy(pos = HousePlaces.positionOf(h, i)) else saved
        }
        val blob =
            base.copy(
                cats = placed,
                pair = state.pair,
                toys = toys?.let { WorldToys.export(it, h, cats) } ?: state.toys,
                gift = state.gift,
                away = away,
                at = c.epochMs(),
            )
        return WorldTrim.apply(blob, trim)
    }

    /** What makes a write worth sending: behaviour that changed, not where a cat happens to be. */
    private fun fingerprint(w: WorldBlob?): Int =
        w?.let {
            listOf(
                    it.cats.map { (id, c) ->
                        listOf(id, c.act, c.fx, c.innate, c.counters, c.spots.keys, c.day)
                    },
                    it.pair,
                    it.toys,
                    it.gift,
                    it.away,
                )
                .hashCode()
        } ?: 0

    private fun markIfChanged(force: Boolean) {
        val now = time.epochMs()
        val snap = snapshot(0)
        val print = fingerprint(snap)
        val beat = now - lastHeartbeatAt >= HEARTBEAT_MS
        // closing or stopping also saves where the cats are, which no other write is made for
        val moved = force && placesPrint(snap) != lastPlaces
        if (print != lastWriteableHash || beat || moved) {
            lastWriteableHash = print
            lastPlaces = placesPrint(snap)
            if (beat) lastHeartbeatAt = now
            sync.markDirty()
        }
    }

    /** Where the cats stand, to the pixel and the surface. */
    private fun placesPrint(w: WorldBlob?): Int =
        w?.cats?.values?.map { listOf(it.pos.s, it.pos.x.toInt(), it.pos.z.toInt()) }.hashCode()

    private fun pump(flush: Boolean) {
        if (!active || (sync.isIdle && !flush)) return
        val visible = ports.values.any { it.visible }
        sync.pump(time.epochMs(), visible, flush, { snapshot(it) }) { command, id ->
            send(command, id, flush)
        }
    }

    private fun send(command: WorldCommand, id: String, flush: Boolean): Boolean {
        val port =
            ports.values.firstOrNull { it.visible }
                ?: if (flush) ports.values.firstOrNull() else null
        port ?: return false
        return port.send(command.name, WorldCommandJson.encode(command), id)
    }

    companion object {
        /** The owner's acts the views make: one is on screen, none is. */
        private const val OWNER_OPEN = "open"
        private const val OWNER_CLOSE = "close"

        /** The request ids the host uses start with this, so a plugin can tell its answers. */
        const val REQUEST_PREFIX = "cwh-"

        /** The hunger stage from which a cat is weak (plan 1.4). */
        const val WEAK_FROM = 3

        /** Projections without a good block after which the world retires. */
        private const val LOST_AFTER = 3

        /** A write even when nothing changed this often, so a restart's gap stays short. */
        private const val HEARTBEAT_MS = 10 * 60_000L

        private const val NANOS_PER_SECOND = 1_000_000_000.0

        /** How often due sends and retries are looked at while frames run. */
        private const val PUMP_EVERY_MS = 1000L

        /** The one host of the process, on the page's feed and the real clocks. */
        val shared: CatWorldHost by lazy { CatWorldHost() }

        private fun deviceZoneMinutes(): Int =
            TimeZone.getDefault().getOffset(System.currentTimeMillis()) / MS_PER_MINUTE

        private const val MS_PER_MINUTE = 60_000
    }
}
