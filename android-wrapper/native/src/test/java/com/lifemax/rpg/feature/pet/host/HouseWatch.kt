package com.lifemax.rpg.feature.pet.host

import com.lifemax.rpg.app.CatWorldSetup
import com.lifemax.rpg.feature.pet.brain.BrainFactory
import com.lifemax.rpg.feature.pet.brain.CatWorld
import com.lifemax.rpg.feature.pet.world.FakePage
import com.lifemax.rpg.feature.pet.world.FakeTimeSource
import com.lifemax.rpg.feature.pet.world.WorldFeed
import java.io.File

/**
 * The real brain in the real host against the page, watched minute by minute: one process (its own
 * feed, host and port over a page and clocks that outlive it, so a [restart] is a cold start on the
 * same save). Every minute [minutes] writes one [Row] per cat: where it stands, on which surface,
 * what it does and where; [timeline] prints them, as the overlap regression tests read them.
 */
internal class HouseWatch(val page: FakePage, val time: FakeTimeSource) {
    /** One cat at one minute. */
    data class Row(
        val minute: Int,
        val slot: Int,
        val surface: String,
        val x: Float,
        val z: Float,
        val activity: String,
        val place: String,
        val pose: String,
        val why: String,
    )

    val feed = WorldFeed()
    val brains = ArrayList<CatWorld>()
    val host =
        CatWorldHost(
            feed,
            time,
            { 0 },
            BrainFactory { cats, clock ->
                (CatWorldSetup.factory.create(cats, clock) as CatWorld).also(brains::add)
            },
        )
    val port = RigPort(page)
    val rows = ArrayList<Row>()
    private var minute = 0
    private var view: WorldView? = null

    init {
        host.registerPort("watch", port)
    }

    val world: CatWorld
        get() = brains.last()

    /** The page projects now; its block reaches the host. */
    fun project() {
        page.now = time.epoch
        host.onProjection(true, feed.offer(page.block()))
    }

    /** A view opens (the world starts on the first one) and one frame runs. */
    fun open() {
        project()
        view = host.attach()
        frame()
    }

    /** The view closes: the world freezes and is saved. */
    fun close() {
        view?.let(host::detach)
        view = null
    }

    fun frame(ms: Long = FRAME_MS) {
        time.advanceMs(ms)
        host.advance(time.mono)
        while (port.replies.isNotEmpty()) host.onReply(port.replies.removeFirst())
    }

    /** [m] minutes with the view open, a row per cat at each minute's end. */
    fun minutes(m: Int) {
        val frames = MS_PER_MINUTE / FRAME_MS
        repeat(m) {
            for (i in 0 until frames) {
                if (i % PROJECT_EVERY == 0L) project()
                frame()
            }
            minute++
            log()
        }
    }

    /** Skips [m] minutes with no view and no frames (the app closed, the phone in a pocket). */
    fun away(m: Int) {
        time.advanceMs(m * MS_PER_MINUTE)
        minute += m
    }

    /** A row per cat now. */
    fun log() {
        val house = host.house ?: return
        for (slot in 0 until house.count) {
            val p = house.bodies[slot].pose
            val d = world.now(slot)
            rows +=
                Row(
                    minute,
                    slot,
                    p.surface.key,
                    p.x,
                    p.z,
                    d.activity,
                    d.place,
                    com.lifemax.rpg.feature.pet.body.Clips.all[p.clip].name,
                    d.why,
                )
        }
    }

    /** A cold start on the same page and clocks: the rows go on in the new process. */
    fun restart(): HouseWatch {
        close()
        host.onStop()
        return HouseWatch(page, time).also {
            it.minute = minute
            it.rows += rows
        }
    }

    /** The rows as a table, one line a minute, both cats side by side. */
    fun timeline(title: String): String {
        val sb = StringBuilder("== $title\n")
        rows
            .groupBy { it.minute }
            .forEach { (m, r) ->
                sb.append(String.format("%4d", m))
                for (row in r) {
                    sb.append(
                        String.format(
                            " | %-8s %7.1f %5.1f %-15s %-18s %-10s %-22s",
                            row.surface,
                            row.x,
                            row.z,
                            row.activity,
                            row.place,
                            row.pose,
                            row.why.take(22),
                        )
                    )
                }
                sb.append('\n')
            }
        return sb.toString()
    }

    /** Writes [timeline] under `CAT_TIMELINE` (a folder) when that is set. */
    fun dump(name: String, title: String) {
        val dir = System.getenv("CAT_TIMELINE") ?: return
        File(dir).mkdirs()
        File(dir, "$name.txt").writeText(timeline(title))
    }

    companion object {
        const val FRAME_MS = 50L
        const val PROJECT_EVERY = 100L
        const val MS_PER_MINUTE = 60_000L
    }
}
