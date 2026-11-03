package com.lifemax.rpg.feature.pet.host

import com.lifemax.rpg.feature.pet.body.CatBody
import com.lifemax.rpg.feature.pet.brain.BrainFactory
import com.lifemax.rpg.feature.pet.brain.Doing
import com.lifemax.rpg.feature.pet.brain.StandInBrain
import com.lifemax.rpg.feature.pet.world.FakePage
import com.lifemax.rpg.feature.pet.world.FakeTimeSource
import com.lifemax.rpg.feature.pet.world.WorldClock
import com.lifemax.rpg.feature.pet.world.WorldContract
import com.lifemax.rpg.feature.pet.world.WorldFeed
import com.lifemax.rpg.feature.pet.world.WorldFixtures
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * The catch-up over a gap is deterministic, bounded and cheap, and the host's steady frame path
 * allocates nothing (the bodies and the clock are refilled in place; only the periodic change check
 * and a write build objects, and the test keeps the wall clock still so neither runs).
 */
class CatchUpAndFrameTest {
    private fun brain(startEpoch: Long = WorldFixtures.NOW): StandInBrain {
        val clock = WorldClock(FakeTimeSource(0, startEpoch), 0)
        return StandInBrain(clock, listOf("Ted", "Lily"))
    }

    @Test
    fun theSameGapFromTheSameClockTellsTheSameStory() {
        val a = brain().catchUp(3 * 3_600_000L)
        val b = brain().catchUp(3 * 3_600_000L)
        assertEquals(a, b)
        assertTrue(a.lines.size in 1..WorldContract.MAX_AWAY_LINES)
        assertEquals(2, a.places.size)
        assertTrue(a.lines.all { it.length <= WorldContract.MAX_AWAY_LINE })
    }

    @Test
    fun aGapOverADayIsADay() {
        val day = 24 * 3_600_000L
        assertEquals(brain().catchUp(day), brain().catchUp(30 * day))
    }

    @Test
    fun theStoryEndsWhereTheTimetablePutsTheCatsWhenTheGapEnds() {
        // noon plus five hours is five in the afternoon: Ted is on the fridge (parkour visit)
        val noon = WorldFixtures.NOW - WorldFixtures.NOW % 86_400_000L + 12 * 3_600_000L
        val story = brain(noon).catchUp(5 * 3_600_000L)
        assertEquals("fridge", story.places[0].surface)
    }

    @Test
    fun aCatchUpCostsFarLessThanTheFiveMillisecondBudget() {
        val b = brain()
        repeat(200) { b.catchUp(24 * 3_600_000L) } // warm up
        val started = System.nanoTime()
        repeat(1000) { b.catchUp(24 * 3_600_000L) }
        val each = (System.nanoTime() - started) / 1000 / 1e6
        assertTrue("catch-up took $each ms", each < 5.0)
    }

    @Test
    fun theHostCapsTheGapItHandsABrainAtADay() {
        val rig = WorldRig(cats = 1)
        rig.project()
        val view = rig.host.attach()
        rig.minutes(1)
        rig.host.detach(view)
        rig.time.advanceMs(72 * 3_600_000L)
        rig.host.attach()
        rig.frame()
        assertEquals(listOf(HostTuning.CATCH_UP_MAX_MS), rig.brain.gaps)
    }

    /** A brain that moves the bodies itself and does nothing per step: the host's own cost only. */
    private class Quiet(clock: WorldClock, names: List<String>) : StandInBrain(clock, names) {
        override val drivesBodies = true

        override fun bind(bodies: List<CatBody>) {}

        override fun step(step: Long, think: Boolean) {}

        override fun now(slot: Int) = Doing("sleep", "bed", 0.0, "")
    }

    @Test
    fun theHostsFramePathAllocatesNothingInSteadyState() {
        val bean = runCatching { threadBean() }.getOrNull()
        assumeTrue("thread allocation counting needs a HotSpot JVM", bean != null)
        val page = FakePage(WorldFixtures.NOW)
        page.adopt(
            FakePage.Cat(WorldFixtures.NOW - 9 * 86_400_000L, "Ted", lastFed = WorldFixtures.NOW)
        )
        page.adopt(
            FakePage.Cat(WorldFixtures.NOW - 5 * 86_400_000L, "Lily", lastFed = WorldFixtures.NOW)
        )
        val time = FakeTimeSource(0, WorldFixtures.NOW)
        val feed = WorldFeed().also { it.offer(page.block()) }
        val host =
            CatWorldHost(
                feed,
                time,
                { 0 },
                BrainFactory { c, clock -> Quiet(clock, c.map { it.name }) },
            )
        host.attach()
        assertTrue("the world started", host.active)
        val frame = 16_000_000L
        repeat(3000) {
            time.mono += frame
            host.advance(time.mono)
        } // warm up; the wall clock stands still
        val before = allocated(bean!!)
        repeat(20_000) {
            time.mono += frame
            host.advance(time.mono)
        }
        val used = allocated(bean) - before
        assertTrue("allocated $used bytes over 20000 frames", used <= TOLERANCE_BYTES)
    }

    private fun threadBean(): Any {
        val bean =
            Class.forName("java.lang.management.ManagementFactory")
                .getMethod("getThreadMXBean")
                .invoke(null)
        val type = Class.forName("com.sun.management.ThreadMXBean")
        check(
            type.isInstance(bean) &&
                type.getMethod("isThreadAllocatedMemorySupported").invoke(bean) == true
        )
        return bean
    }

    private fun allocated(bean: Any): Long =
        Class.forName("com.sun.management.ThreadMXBean")
            .getMethod("getThreadAllocatedBytes", Long::class.javaPrimitiveType)
            .invoke(bean, Thread.currentThread().id) as Long

    private companion object {
        /** Reflection and boxing of the measuring calls themselves, not the frames. */
        const val TOLERANCE_BYTES = 4096L
    }
}
