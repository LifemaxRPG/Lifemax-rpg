package com.lifemax.rpg.feature.pet.host

import com.lifemax.rpg.feature.pet.brain.BrainFactory
import com.lifemax.rpg.feature.pet.brain.BrainState
import com.lifemax.rpg.feature.pet.world.BlobCat
import com.lifemax.rpg.feature.pet.world.FakePage
import com.lifemax.rpg.feature.pet.world.FakeTimeSource
import com.lifemax.rpg.feature.pet.world.WorldBlock
import com.lifemax.rpg.feature.pet.world.WorldCat
import com.lifemax.rpg.feature.pet.world.WorldClock
import com.lifemax.rpg.feature.pet.world.WorldCommand
import com.lifemax.rpg.feature.pet.world.WorldFeed
import com.lifemax.rpg.feature.pet.world.WorldFixtures
import org.json.JSONObject

/**
 * A stand-in brain that keeps what the host hands it (the restored state, the newest block) and
 * writes its current activity into the saved state, so the host's change detection, persistence and
 * restore can be followed. Reports are pushed by the test ([report]).
 */
class RigBrain(clock: WorldClock, val cats: List<WorldCat>) :
    TimetableBrain(clock, cats.map { it.name }) {
    var restored: BrainState? = null
    var lastBlock: WorldBlock? = null
    var steps = 0L
    private var state = emptyMap<String, BlobCat>()
    private val reports = ArrayList<WorldCommand>()

    override fun step(step: Long, think: Boolean) {
        steps++
        super.step(step, think)
    }

    /** The gaps the host asked this brain to catch up, in order. */
    val gaps = ArrayList<Long>()

    override fun catchUp(gapMs: Long): com.lifemax.rpg.feature.pet.brain.CatchUp {
        gaps += gapMs
        return super.catchUp(gapMs)
    }

    override fun export(): BrainState =
        BrainState(
            state.mapValues { (id, c) ->
                val slot = cats.indexOfFirst { it.id == id }
                c.copy(act = now(slot).activity.replace(' ', '_'))
            }
        )

    override fun restore(state: BrainState) {
        restored = state
        this.state = state.cats
    }

    override fun update(block: WorldBlock) {
        lastBlock = block
    }

    override fun takeReports(): List<WorldCommand> =
        if (reports.isEmpty()) emptyList() else ArrayList(reports).also { reports.clear() }

    fun report(c: WorldCommand) {
        reports += c
    }
}

/** A screen's way to the page for tests: visible or not, delivering to FakePage, answers queued. */
class RigPort(private val page: FakePage) : WorldPort {
    override var visible = true
    var open = true
    val sent = ArrayList<Pair<String, String>>() // command, request id
    val replies = ArrayDeque<JSONObject>()
    var dropReplies = false

    override fun send(command: String, payload: JSONObject, requestId: String): Boolean {
        if (!open) return false
        sent += command to requestId
        val reply = page.command(command, payload, requestId)
        if (!dropReplies) replies += reply
        return true
    }
}

/**
 * The whole loop on the JVM: a FakePage with one or two cats, a feed, a world host on a fake clock
 * with a [RigBrain], and one port. [frame] is one display frame; [minutes] runs simulated time at
 * one-second frames, projecting the page every [projectEvery] frames and delivering answers.
 */
class WorldRig(
    cats: Int = 1,
    val page: FakePage = FakePage(WorldFixtures.NOW),
    val time: FakeTimeSource = FakeTimeSource(0L, WorldFixtures.NOW),
    val feed: WorldFeed = WorldFeed(),
    withFactory: Boolean = true,
) {
    val brains = ArrayList<RigBrain>()
    val host = CatWorldHost(feed, time, { 0 }, null)
    val port = RigPort(page)
    var projectEvery = 5

    init {
        repeat(cats) { i ->
            page.adopt(
                FakePage.Cat(
                    born = WorldFixtures.NOW - (40 - i * 5) * 86_400_000L,
                    name = if (i == 0) "Ted" else "Lily",
                    bondPts = FakePage.BOND_AT[3],
                    lastFed = WorldFixtures.NOW - FakePage.HOUR,
                )
            )
        }
        if (withFactory)
            host.brainFactory = BrainFactory { c, clock -> RigBrain(clock, c).also(brains::add) }
        host.registerPort("rig", port)
    }

    val brain: RigBrain
        get() = brains.last()

    /** The page projects now: the block reaches the feed and the host. */
    fun project() {
        page.now = time.epoch
        val accepted = feed.offer(page.block())
        host.onProjection(hasBlock = true, accepted = accepted)
    }

    /** Hands the queued answers to the host (a visible screen hears them). */
    fun deliver() {
        if (!port.visible) return
        while (port.replies.isNotEmpty()) host.onReply(port.replies.removeFirst())
    }

    /** One display frame [ms] later. */
    fun frame(ms: Long = 1000) {
        time.advanceMs(ms)
        host.advance(time.mono)
        deliver()
    }

    /** Runs [minutes] of simulated time with a view attached, projecting now and then. */
    fun minutes(minutes: Int, frameMs: Long = 1000) {
        val frames = minutes * 60_000L / frameMs
        for (i in 0 until frames) {
            if (i % projectEvery == 0L) project()
            frame(frameMs)
        }
    }

    /** A cold start: a new host and brains on the same page and clocks (the process was killed). */
    fun restart(): WorldRig =
        WorldRig(0, page, time, WorldFeed(), withFactory = false).also { next ->
            next.page.cats.size
            next.host.brainFactory = BrainFactory { c, clock ->
                RigBrain(clock, c).also(next.brains::add)
            }
        }
}
