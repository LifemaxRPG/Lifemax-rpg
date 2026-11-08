package com.lifemax.rpg.feature.pet.brain

import com.lifemax.rpg.feature.pet.body.SurfaceId
import com.lifemax.rpg.feature.pet.body.World
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Whole live days (real bodies at 120 Hz sub-steps, plan 4.1 "properties"), checked at every
 * sub-step: no cat teleports, none is ever stuck, two cats never hold one single-occupancy perch, a
 * kitten never gets onto the fridge, a weak cat keeps to the low places by its bowl, and every
 * activity ends on its own (the watchdog never fires).
 */
class BrainDayTest {
    /** The most a body moves in one sub-step (a full-speed leap is the fastest thing it does). */
    private val maxStepPx = 3.5f
    private val maxStepZ = 1.0f
    private val stuckSteps = 20 * 120

    private class Watch(n: Int) {
        val x = FloatArray(n) { Float.NaN }
        val z = FloatArray(n)
        val idle = IntArray(n)
        val worstIdle = IntArray(n)
        var worstMove = 0f
        var clashes = 0
        val clashSeen = ArrayList<String>()
        var changes = IntArray(n)
        val last = arrayOfNulls<Doing>(n)
    }

    private fun watch(world: CatWorld, w: Watch, think: Boolean) {
        val bodies = world.minds.map { it.body!! }
        for ((i, b) in bodies.withIndex()) {
            val p = b.pose
            if (!w.x[i].isNaN()) {
                w.worstMove = maxOf(w.worstMove, Math.abs(p.x - w.x[i]))
                assertTrue(
                    "cat $i jumped in depth ${w.z[i]} -> ${p.z}",
                    Math.abs(p.z - w.z[i]) <= maxStepZ,
                )
            }
            w.x[i] = p.x
            w.z[i] = p.z
            w.idle[i] = if (b.isIdle) w.idle[i] + 1 else 0
            w.worstIdle[i] = maxOf(w.worstIdle[i], w.idle[i])
            if (think && world.now(i) !== w.last[i]) {
                w.last[i] = world.now(i)
                w.changes[i]++
            }
        }
        if (bodies.size == 2) {
            val a = bodies[0].pose
            val b = bodies[1].pose
            val shared = a.surface == b.surface && a.surface != SurfaceId.FLOOR
            val apart = Math.abs(a.x - b.x) >= HALF_GAP
            // two cats curled up together on the sofa are the one legal sharing (SocialDirector)
            val curled = a.surface == SurfaceId.COUCH && world.sharing()
            if (shared && !(a.surface == SurfaceId.COUCH && apart) && !curled) {
                if (w.clashSeen.size < 6)
                    w.clashSeen +=
                        "${a.surface.key} ${a.x}/${b.x} ${world.now(0)} | ${world.now(1)}"
                w.clashes++
            }
        }
    }

    @Test
    fun aDayOfTwoCatsKeepsEveryPropertyAtEverySubStep() {
        val cats =
            listOf(
                CatDays.cat("Ted", CatDays.PLAYFUL, id = "cmu9rkw00"),
                CatDays.cat("Bean", CatDays.PLAYFUL, id = "cbean0001", stage = 1, slot = 1),
            )
        val w = Watch(2)
        var kittenHigh = 0
        val world =
            CatDays.run(cats, CatDays.owner(cats = 2), hours = 24.0) { world, _, think ->
                watch(world, w, think)
                if (world.minds[1].body!!.pose.surface == SurfaceId.FRIDGE) kittenHigh++
            }
        assertTrue("moved ${w.worstMove} px in a sub-step", w.worstMove <= maxStepPx)
        for (i in 0..1) {
            assertTrue("cat $i idle for ${w.worstIdle[i]} sub-steps", w.worstIdle[i] < stuckSteps)
            assertTrue("cat $i changed only ${w.changes[i]} times", w.changes[i] > 30)
            assertEquals("watchdog", 0, world.minds[i].runner.watchdogs)
        }
        assertEquals("two cats on one perch: ${w.clashSeen}", 0, w.clashes)
        assertEquals("the kitten reached the fridge", 0, kittenHigh)
    }

    @Test
    fun aWeakCatKeepsLowAndNearItsBowlUntilFed() {
        val case = CatDays.case("weak")
        val w = Watch(1)
        var weakSteps = 0
        var nearBowl = 0
        var high = 0
        var begs = 0
        val bowl = Spots.bowl(0).x
        CatDays.run(case.cats, case.acts, hours = 24.0) { world, _, think ->
            watch(world, w, think)
            val m = world.minds[0]
            if (!m.persona.weak) return@run
            weakSteps++
            val p = m.body!!.pose
            if (Math.abs(p.x - bowl) <= BrainTuning.Persona.WEAK_BOWL_REACH) nearBowl++
            val low =
                p.surface == SurfaceId.FLOOR ||
                    p.surface == SurfaceId.BED ||
                    World.surface(p.surface).prop
            if (!low) high++
            if (
                think &&
                    world.now(0).activity == "beg" &&
                    w.last[0] === world.now(0) &&
                    w.changes[0] > 0
            )
                begs++
        }
        assertTrue(weakSteps > 0)
        assertEquals("a weak cat went up high", 0, high)
        assertTrue("near the bowl ${nearBowl * 100 / weakSteps} %", nearBowl >= weakSteps * 8 / 10)
        assertTrue("it begged while the owner was there", begs > 0)
        assertTrue(w.worstIdle[0] < stuckSteps)
    }

    private companion object {
        const val HALF_GAP = 20f
    }
}
