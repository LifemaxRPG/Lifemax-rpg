package com.lifemax.rpg.feature.pet.brain

import com.lifemax.rpg.feature.pet.body.CatBody
import com.lifemax.rpg.feature.pet.body.Rng
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * Long runs (plan 4.1 "soak" and "allocation"): a month of two cats in fast mode under a random
 * owner (visits, meals, hunger stages, a vacation) never throws, never drifts a drive or the mood
 * out of range, never lets both cats claim one perch, keeps choosing (no deadlock), and never needs
 * the watchdog; and a live cat in steady state allocates nothing per sub-step, as the body does.
 */
class BrainSoakTest {
    private fun randomOwner(days: Int, seed: Int): List<OwnerAct> {
        val rng = Rng(seed)
        val acts = ArrayList<OwnerAct>()
        for (d in 0 until days) {
            val day = d * 24 * CatDays.HOUR
            repeat(1 + (rng.nextFloat() * 4).toInt()) {
                val at = day + (rng.nextFloat() * 23 * CatDays.HOUR).toLong()
                acts += OwnerAct(at, "open")
                acts += OwnerAct(at + (rng.nextFloat() * 40 * CatDays.MINUTE).toLong(), "close")
            }
            for (slot in 0..1) {
                acts +=
                    OwnerAct(
                        day + (rng.nextFloat() * 12 * CatDays.HOUR).toLong(),
                        "hunger:$slot:${(rng.nextFloat() * 5).toInt()}",
                    )
                if (rng.chance(0.8f))
                    acts +=
                        OwnerAct(day + (rng.nextFloat() * 24 * CatDays.HOUR).toLong(), "feed:$slot")
            }
            if (d == days / 2) acts += OwnerAct(day, "vacation:on")
            if (d == days / 2 + 2) acts += OwnerAct(day, "vacation:off")
            if (rng.chance(0.15f)) acts += OwnerAct(day, "restday:on")
            else acts += OwnerAct(day, "restday:off")
        }
        return acts
    }

    @Test
    fun aMonthOfTwoCatsNeverStallsOrDrifts() {
        val days = 30
        val cats =
            listOf(
                CatDays.cat("Ted", CatDays.PLAYFUL, id = "cmu9rkw00"),
                CatDays.cat("Lily", CatDays.SHY, id = "cmv2cdxc0", slot = 1, stage = 3),
            )
        val changes = IntArray(2)
        val last = arrayOfNulls<Doing>(2)
        var clashes = 0
        val t0 = System.nanoTime()
        val world =
            CatDays.run(
                cats,
                randomOwner(days, 11),
                hours = 24.0 * days,
                fidelity = CatWorld.Fidelity.FAST,
            ) { world, _, think ->
                if (!think) return@run
                for (i in 0..1) {
                    val m = world.minds[i]
                    for (d in Drives.ALL) {
                        val v = m.drives[d]
                        if (v.isNaN() || v < 0.0 || v > 1.0)
                            throw AssertionError("cat $i ${d.key} = $v")
                    }
                    if (Math.abs(m.mood.valence) > 1.0 || Math.abs(m.mood.arousal) > 1.0)
                        throw AssertionError("mood")
                    if (world.now(i) !== last[i]) {
                        last[i] = world.now(i)
                        changes[i]++
                    }
                }
                val a = world.book.claimOf(0)
                if (a != null && a.single && a === world.book.claimOf(1)) clashes++
            }
        val seconds = (System.nanoTime() - t0) / 1e9
        println("soak: $days days x 2 cats in %.1f s, picks %s".format(seconds, changes.toList()))
        assertEquals(0, clashes)
        for (i in 0..1) {
            assertTrue("cat $i chose only ${changes[i]} times", changes[i] > days * 30)
            assertEquals(0, world.minds[i].runner.watchdogs)
            assertTrue(world.minds[i].runner.explain.entries().size == ExplainRing.CAPACITY)
        }
    }

    // ---------------------------------------------------------------- allocation

    private val bean: Any? =
        runCatching {
                Class.forName("java.lang.management.ManagementFactory")
                    .getMethod("getThreadMXBean")
                    .invoke(null)
            }
            .getOrNull()

    private fun supported(): Boolean =
        runCatching {
                val type = Class.forName("com.sun.management.ThreadMXBean")
                type.isInstance(bean) &&
                    type.getMethod("isThreadAllocatedMemorySupported").invoke(bean) == true
            }
            .getOrDefault(false)

    private fun allocatedBytes(): Long {
        val type = Class.forName("com.sun.management.ThreadMXBean")
        val method = type.getMethod("getThreadAllocatedBytes", Long::class.javaPrimitiveType)
        return method.invoke(bean, Thread.currentThread().id) as Long
    }

    @Test
    fun aLiveCatInSteadyStateAllocatesNothingPerSubStep() {
        assumeTrue("thread allocation counting needs a HotSpot JVM", supported())
        val clock =
            com.lifemax.rpg.feature.pet.world.WorldClock(
                com.lifemax.rpg.feature.pet.world.FakeTimeSource(
                    0,
                    CatDays.MIDNIGHT + 13 * CatDays.HOUR,
                )
            )
        val world = CatWorld(clock, listOf(CatDays.cat("Ted", CatDays.LAZY)))
        world.minds[0].force("sleep", null, 3600.0)
        var step = 0L
        fun run(n: Int) =
            repeat(n) {
                step++
                world.step(step, step % 30L == 0L)
            }
        run(20_000) // warm up: the first quanta, class loading, JIT
        val body: CatBody = world.minds[0].body!!
        val before = allocatedBytes()
        run(40_000)
        val used = allocatedBytes() - before
        assertEquals("sleep", world.now(0).activity)
        assertTrue(body.pose.clipName.startsWith("sleep"))
        assertTrue("allocated $used bytes over 40000 sub-steps", used <= TOLERANCE_BYTES)
    }

    private companion object {
        const val TOLERANCE_BYTES = 4_096L
    }
}
