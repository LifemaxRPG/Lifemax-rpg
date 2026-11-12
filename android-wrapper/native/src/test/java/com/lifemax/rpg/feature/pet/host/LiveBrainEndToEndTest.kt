package com.lifemax.rpg.feature.pet.host

import com.lifemax.rpg.app.CatWorldSetup
import com.lifemax.rpg.feature.pet.body.PlanStep
import com.lifemax.rpg.feature.pet.body.SurfaceId
import com.lifemax.rpg.feature.pet.body.World
import com.lifemax.rpg.feature.pet.brain.BodyExecutor
import com.lifemax.rpg.feature.pet.brain.Brain
import com.lifemax.rpg.feature.pet.brain.BrainFactory
import com.lifemax.rpg.feature.pet.brain.CatWorld
import com.lifemax.rpg.feature.pet.brain.Catalogue
import com.lifemax.rpg.feature.pet.brain.MindState
import com.lifemax.rpg.feature.pet.brain.Spots
import com.lifemax.rpg.feature.pet.world.FakePage
import com.lifemax.rpg.feature.pet.world.FakeTimeSource
import com.lifemax.rpg.feature.pet.world.MomentReport
import com.lifemax.rpg.feature.pet.world.OpIds
import com.lifemax.rpg.feature.pet.world.WorldBlobJson
import com.lifemax.rpg.feature.pet.world.WorldCommand
import com.lifemax.rpg.feature.pet.world.WorldFeed
import com.lifemax.rpg.feature.pet.world.WorldFixtures
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The real brain in the real host against the page (FakePage), end to end on a fake clock: the
 * app's own brain factory ([CatWorldSetup.factory], a live [CatWorld] on the house's bodies), a
 * legacy import, six simulated hours with the owner's acts (feed, vacation, rest day, closing and
 * opening the view), a kill and cold start in the middle, an offline gap caught up, and the house's
 * toys. Every frame no cat moves further than its body can (a declared cut aside).
 */
class LiveBrainEndToEndTest {
    /** The real brain, with a way for the test to post a report as a later phase's would. */
    private class Reporting(val inner: CatWorld) : Brain by inner {
        val queued = ArrayList<WorldCommand>()

        override fun takeReports(): List<WorldCommand> {
            val own = inner.takeReports()
            if (queued.isEmpty()) return own
            return (own + queued).also { queued.clear() }
        }
    }

    /** A process: its own feed, host and port over the page and clocks that outlive it. */
    private class Process(val page: FakePage, val time: FakeTimeSource) {
        val feed = WorldFeed()
        val brains = ArrayList<Reporting>()
        val host =
            CatWorldHost(
                feed,
                time,
                { 0 },
                BrainFactory { cats, clock ->
                    Reporting(CatWorldSetup.factory.create(cats, clock) as CatWorld)
                        .also(brains::add)
                },
            )
        val port = RigPort(page)
        private val lastX = FloatArray(2) { Float.NaN }
        private val lastZ = FloatArray(2)
        var cut = true
        var worstJump = 0f

        init {
            host.registerPort("e2e", port)
        }

        val world: CatWorld
            get() = brains.last().inner

        fun project() {
            page.now = time.epoch
            host.onProjection(true, feed.offer(page.block()))
        }

        /** One frame [ms] later, with the no-teleport watch on the house's bodies. */
        fun frame(ms: Long = FRAME_MS) {
            time.advanceMs(ms)
            host.advance(time.mono)
            while (port.replies.isNotEmpty()) host.onReply(port.replies.removeFirst())
            val house = host.house ?: return
            val steps = ms * STEPS_PER_S / MS_PER_S
            for (i in 0 until house.count) {
                val p = house.bodies[i].pose
                if (!cut && !lastX[i].isNaN()) {
                    val dx = Math.abs(p.x - lastX[i])
                    val dz = Math.abs(p.z - lastZ[i])
                    worstJump = maxOf(worstJump, dx / steps)
                    assertTrue("cat $i jumped ${dx}px in a frame", dx <= steps * MAX_STEP_PX + 1f)
                    assertTrue("cat $i jumped ${dz} deep in a frame", dz <= steps * MAX_STEP_Z + 1f)
                }
                lastX[i] = p.x
                lastZ[i] = p.z
            }
            cut = false
        }

        fun minutes(m: Int) {
            val frames = m * 60_000L / FRAME_MS
            for (i in 0 until frames) {
                if (i % PROJECT_EVERY == 0L) project()
                frame()
            }
        }
    }

    private fun page(): FakePage =
        FakePage(WorldFixtures.NOW).also { p ->
            p.adopt(
                FakePage.Cat(
                    born = WorldFixtures.NOW - 40 * DAY_MS,
                    name = "Ted",
                    lastFed = WorldFixtures.NOW - HOUR_MS,
                    legacy = WorldFixtures.legacy("rich"),
                )
            )
            p.adopt(
                FakePage.Cat(
                    born = WorldFixtures.NOW - 35 * DAY_MS,
                    name = "Lily",
                    lastFed = WorldFixtures.NOW - HOUR_MS,
                )
            )
        }

    @Test
    fun sixHoursOfTheRealBrainWithTheOwnerAKillAndAnOfflineGap() {
        val page = page()
        val time = FakeTimeSource(0L, WorldFixtures.NOW)
        val first = Process(page, time)
        val ted = page.living()[0].id
        val legacy = WorldBlockJsonFor.legacyOf(page, ted)
        first.project()
        val view = first.host.attach()
        first.frame()

        // the real brain drives the house's own bodies: one set for every screen
        val house = first.host.house!!
        assertTrue(first.host.brainDrives)
        for (m in first.world.minds) {
            assertSame(house.bodies[m.slot], (m.exec as BodyExecutor).body)
        }
        // the import: Ted's web brain carried over (places, first moments, quirks, where he was)
        val imported = first.host.snapshotNow()!!.cats[ted]!!
        assertEquals(legacy.fx, imported.fx)
        assertEquals(legacy.innate, imported.innate)
        assertTrue("the web brain's places count: ${imported.spots}", imported.spots.isNotEmpty())
        assertEquals(legacy.pos!!.x.toFloat(), house.bodies[0].pose.x, START_SLACK_PX)

        // an hour, then a meal for Ted: he goes to eat
        first.minutes(60)
        page.cats[0].lastFed = time.epoch
        first.project()
        assertTrue(first.host.ownerAct("feed:0"))
        repeat(SECOND_FRAMES) { first.frame() } // the next think quantum takes the meal
        assertEquals("eat", first.world.now(0).activity)
        assertEquals("fed", first.world.now(0).why)

        // vacation on and a rest day: the brain takes both from the page's block
        page.cats[1].vacation = true
        page.cats[1].vacationSince = time.epoch
        page.restDay = true
        first.minutes(30)
        assertTrue(first.world.minds[1].persona.vacation)
        assertTrue(first.world.restDay)
        page.cats[1].vacation = false
        page.restDay = false
        first.minutes(88)

        // a moment waits in the outbox when the process dies (its answer never came)
        first.port.dropReplies = true
        val moment =
            MomentReport(
                OpIds.firstMoment(ted, "first_glass"),
                ted,
                "first_glass",
                time.epoch,
                null,
            )
        first.brains.last().queued += moment
        first.minutes(1)
        first.host.onStop()
        first.host.detach(view)
        val stored = WorldBlobJson.decode(page.stored!!.blob)
        assertEquals(listOf(moment.op), stored.outbox.map { it.op })
        // the cold start resumes from what the page stored: the answers were dropped, so the page
        // kept the last save it took (up to a minute old), and the cats now roam and move on in
        // that time (they used to rest, so the live bodies matched it)
        val ids = page.living().map { it.id }
        val before =
            ids.map {
                val pos = stored.cats.getValue(it).pos
                Triple(
                    SurfaceId.entries.first { s -> s.key == pos.s },
                    pos.x.toFloat(),
                    pos.z.toFloat(),
                )
            }
        val doing = ids.map { MindState.activityOf(stored.cats.getValue(it).act) }

        // a cold start right away: the cats resume where they were, doing what they did
        val second = Process(page, time)
        second.project()
        val view2 = second.host.attach()
        second.frame()
        val after = second.host.house!!.bodies
        for (i in 0..1) {
            assertEquals(before[i].first, after[i].pose.surface)
            assertEquals(before[i].second, after[i].pose.x, RESUME_SLACK_PX)
            assertEquals(before[i].third, after[i].pose.z, RESUME_SLACK_PX)
        }
        // a long rest is resumed; a short one (a loaf now lasts a few minutes at most) may well be
        // over by the time the cold start draws what is left of it, and the cat moves on
        val registry = Catalogue.core()
        for (i in 0..1) {
            if (registry.find(doing[i])?.asleep == true) {
                assertEquals(doing[i], second.world.now(i).activity)
            }
        }
        second.minutes(60)
        assertEquals("replayed once", 1, second.port.sent.count { it.first == "moment" })
        assertEquals(setOf("first_glass"), page.cats[0].fx.toSet())
        assertTrue(WorldBlobJson.decode(page.stored!!.blob).outbox.isEmpty())

        // the view closes for two hours: frozen, then caught up on the next open
        second.host.detach(view2)
        val atClose = second.host.house!!.bodies.map { it.pose.x }
        time.advanceMs(2 * HOUR_MS)
        second.project()
        second.host.attach()
        second.cut = true
        second.frame()
        val away = second.host.awayStory()
        assertNotNull("a story of the gap", away)
        assertTrue(away!!.lines.isNotEmpty())
        assertTrue(away.lines.all { it.startsWith("Ted") || it.startsWith("Lily") })
        val placed = second.host.house!!.bodies
        for (b in placed) {
            val s = b.pose.surface
            val x = b.pose.x
            val clamped = if (s == SurfaceId.FLOOR) World.clampHouse(x) else World.clampTo(s, x)
            assertEquals("cat on its surface after the gap", clamped, x, 0.5f)
        }
        // each cat that is at its place is on that place's surface (a rest is short now, so a cat
        // whose story rest is over has picked its next place and is still walking there)
        for (slot in 0..1) {
            if (second.world.minds[slot].runner.atPlaceS <= 0.0) continue
            val doing = second.world.now(slot)
            val spot = Spots.all.firstOrNull { it.id == doing.place } ?: continue
            assertEquals(
                "${doing.activity} at ${doing.place}",
                spot.surface,
                placed[slot].pose.surface,
            )
        }
        assertTrue(atClose.size == 2)
        second.minutes(29)
        val story = (0..1).map { second.world.minds[it].runner.explain.entries().takeLast(3) }
        assertTrue("the cats moved after the gap: $story", second.worstJump > 0f)
        assertTrue(page.saves > 0)
    }

    @Test
    fun theHousesToysLieAboutAndACatCanBeToldToBatOne() {
        val page = page()
        val time = FakeTimeSource(0L, WorldFixtures.NOW)
        val p = Process(page, time)
        p.project()
        p.host.attach()
        p.frame()
        val toys = p.host.toys!!
        assertEquals("two of the unlocked toys on the rugs", 2, toys.count)
        val toy = toys[0]!!
        val body = p.host.house!!.bodies[0]
        body.placeAt(toy.x - BAT_REACH_PX, toy.z, SurfaceId.FLOOR, 1)
        val x0 = toy.x
        body.replacePlan(Int.MAX_VALUE / 2, listOf(PlanStep.Bat(toy.id, 0.8f)))
        p.cut = true
        repeat(BAT_FRAMES) {
            p.frame()
            p.cut = true
        }
        assertNotEquals("the toy was batted", x0, toy.x)
        val saved = p.host.snapshotNow()!!.toys
        assertEquals(2, saved.size)
        assertEquals(Math.round(toy.x).toDouble(), saved[0].x, 1.0)
    }

    private companion object {
        const val FRAME_MS = 50L
        const val PROJECT_EVERY = 100L
        const val STEPS_PER_S = 120L
        const val MS_PER_S = 1000L
        const val HOUR_MS = 3_600_000L
        const val DAY_MS = 86_400_000L

        /** The most a body moves in one sub-step (a full-speed leap), as the brain's day test. */
        const val MAX_STEP_PX = 3.5f
        const val MAX_STEP_Z = 1.0f
        const val START_SLACK_PX = 4f
        const val RESUME_SLACK_PX = 0.01f
        const val BAT_REACH_PX = 10f
        const val BAT_FRAMES = 60
        const val SECOND_FRAMES = 20
    }
}

/** The page's legacy block for a cat, decoded as native reads it. */
private object WorldBlockJsonFor {
    fun legacyOf(page: FakePage, id: String) =
        com.lifemax.rpg.feature.pet.world.WorldBlockJson.decode(page.block())
            .cats
            .first { it.id == id }
            .legacy!!
}
