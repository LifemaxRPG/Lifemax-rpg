package com.lifemax.rpg.feature.pet.brain

import com.lifemax.rpg.feature.pet.body.Occluders
import com.lifemax.rpg.feature.pet.body.SurfaceId
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The owner's "the cat's ears are poking through the cat tree", over whole live days (real bodies
 * at 120 Hz sub-steps): a cat may pass under the tree's middle platform on its way, pausing too
 * (the room draws it in front of the tree), but once at its place it never stays there sitting up,
 * lying or scratching: it scratches the post from in front of the tree, and a cat that stops under
 * the platform steps forward first. The one place the page puts half under the tree, the second
 * bowl, is the exception.
 */
class TreeClearanceDayTest {
    private class Watch(n: Int) {
        val run = IntArray(n)
        val worst = IntArray(n)
        val where = arrayOfNulls<String>(n)
        var scratches = 0
    }

    private fun watch(world: CatWorld, w: Watch, think: Boolean) {
        for ((i, m) in world.minds.withIndex()) {
            val b = m.body!!
            val p = b.pose
            val atBowl = Math.abs(p.x - Spots.BOWL_1.x) < BOWL_REACH
            val under =
                p.surface == SurfaceId.FLOOR &&
                    m.exec.atPlace &&
                    !b.isTravelling &&
                    !atBowl &&
                    Occluders.clipped(p.x, p.z, p.facing)
            w.run[i] = if (under) w.run[i] + 1 else 0
            if (w.run[i] > w.worst[i]) {
                w.worst[i] = w.run[i]
                w.where[i] = "${p.x}/${p.z} ${world.now(i)}"
            }
            if (think && world.now(i).activity == "scratch" && world.now(i).place == "tree post")
                w.scratches++
        }
    }

    private fun day(cats: List<CatProfile>, hours: Double = 24.0): Watch {
        val w = Watch(cats.size)
        CatDays.run(cats, CatDays.owner(cats = cats.size), hours = hours) { world, _, think ->
            watch(world, w, think)
        }
        println("thought of scratching at the tree post: ${w.scratches} sub-steps")
        for (i in cats.indices) {
            assertTrue(
                "cat $i stayed under the platform for ${w.worst[i]} sub-steps: ${w.where[i]}",
                w.worst[i] < MOST_SUB_STEPS,
            )
        }
        return w
    }

    @Test
    fun aHouseOfTwoNeverStaysUnderTheTreesPlatform() {
        day(
            listOf(
                CatDays.cat("Ted", CatDays.PLAYFUL, id = "cmu9rkw00"),
                CatDays.cat("Bean", CatDays.PLAYFUL, id = "cbean0001", stage = 1, slot = 1),
            )
        )
    }

    @Test
    fun lazyShyAndAverageCatsNeverStayUnderTheTreesPlatform() {
        for (persona in listOf(CatDays.LAZY, CatDays.SHY, CatDays.AVERAGE)) {
            day(listOf(CatDays.cat("Ted", persona, id = "cmu9rkw00")))
        }
    }

    private companion object {
        /** A cat passing by stops for a moment (a turn, a look round); 2 s is a stay. */
        const val MOST_SUB_STEPS = 2 * 120

        /** A cat this close to the second bowl's place is at the bowl. */
        const val BOWL_REACH = 2f
    }
}
