package com.lifemax.rpg.feature.habits

import com.lifemax.rpg.feature.habits.domain.Activity
import com.lifemax.rpg.feature.habits.domain.Ambient
import com.lifemax.rpg.feature.habits.domain.Silhouettes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The people in the windows: what they do, by the habit's name, and their poses over time. */
class SilhouettesTest {
    @Test
    fun peopleDoWhatTheHabitIsCalled() {
        assertEquals(Activity.LIFTING, Silhouettes.activityOf("Ten push-ups"))
        assertEquals(Activity.LIFTING, Silhouettes.activityOf("Plank 1 min"))
        assertEquals(Activity.READING, Silhouettes.activityOf("Read 10 pages"))
        assertEquals(Activity.READING, Silhouettes.activityOf("Language app"))
        assertEquals(Activity.TYPING, Silhouettes.activityOf("Journal 5 min"))
        assertEquals(Activity.TYPING, Silhouettes.activityOf("Plan the day"))
        assertEquals(Activity.STRETCHING, Silhouettes.activityOf("Stretch 10 min"))
        assertEquals(Activity.STRETCHING, Silhouettes.activityOf("Meditate"))
        assertEquals(Activity.STRETCHING, Silhouettes.activityOf("Cold shower"))
        assertEquals(Activity.WALKING, Silhouettes.activityOf("Walk 20 min"))
        assertEquals(Activity.WALKING, Silhouettes.activityOf("Make the bed"))
        // nothing fits: generic office life
        assertNull(Silhouettes.activityOf("Vitamins"))
        assertNull(Silhouettes.activityOf(""))
    }

    @Test
    fun aFloorsPeopleMostlyDoItsHabitAndSomeoneWalksPast() {
        val lifting = (0 until 6).map { Silhouettes.activityFor("Ten push-ups", it) }
        assertEquals(Activity.LIFTING, lifting[0])
        assertEquals(Activity.LIFTING, lifting[1])
        assertEquals(Activity.WALKING, lifting[2])
        assertTrue(lifting.count { it == Activity.LIFTING } >= 4)
        // a name nothing fits fills its windows with ordinary office life, by turns
        val generic = (0 until 3).map { Silhouettes.activityFor("Vitamins", it) }.toSet()
        assertEquals(setOf(Activity.TYPING, Activity.WALKING, Activity.READING), generic)
    }

    @Test
    fun posesAreSmallAndFitAWindow() {
        for (a in Activity.entries) {
            val poses = Silhouettes.poses(a)
            assertTrue(poses.isNotEmpty())
            for (p in poses) {
                assertTrue(p.limbs.size % 4 == 0)
                // standing on the sill, no wider than a window, no taller than the smallest one
                val xs =
                    p.shapes.flatMap { s -> s.filterIndexed { i, _ -> i % 2 == 0 } } +
                        p.limbs.filterIndexed { i, _ -> i % 2 == 0 }
                val ys =
                    p.shapes.flatMap { s -> s.filterIndexed { i, _ -> i % 2 == 1 } } +
                        p.limbs.filterIndexed { i, _ -> i % 2 == 1 }
                assertTrue(p.shapes.all { it.size % 2 == 0 && it.size >= 6 })
                assertTrue(xs.all { it in -11f..11f })
                assertTrue(ys.all { it in -25f..0f })
                assertTrue(p.headY - Silhouettes.HEAD_R >= -25f)
            }
        }
    }

    @Test
    fun theyMoveInStepsAndEachPersonIsOutOfStepWithTheOthers() {
        for (a in Activity.entries) {
            val n = Silhouettes.poses(a).size
            val frames = (0 until Silhouettes.cycle(a)).map { Silhouettes.frame(a, it, 0) }
            assertTrue(frames.all { it in 0 until n })
            // every pose shows at some point, and the cycle comes round
            assertEquals((0 until n).toSet(), frames.toSet())
            assertEquals(
                Silhouettes.frame(a, 3, 7),
                Silhouettes.frame(a, 3 + Silhouettes.cycle(a), 7),
            )
        }
        // two typists are at different points of their work
        val one = (0 until 24).map { Silhouettes.frame(Activity.TYPING, it, 1) }
        val two = (0 until 24).map { Silhouettes.frame(Activity.TYPING, it, 5) }
        assertTrue(one != two)
        // a pose holds for a few steps: about twelve a second, never a flicker
        val step = Ambient.step(2.5f)
        assertEquals(30, step)
    }

    @Test
    fun someoneWalkingPastCrossesTheWindowAndLeavesIt() {
        val w = 17f
        assertEquals(w / 2f, Silhouettes.walkX(0f, 4, w), 0f)
        val xs = (1..130).map { Silhouettes.walkX(it / 20f, 4, w) }
        assertTrue(xs.any { it.isNaN() })
        val inside = xs.filter { !it.isNaN() }
        assertTrue(inside.isNotEmpty())
        assertTrue(inside.all { it in -6.01f..(w + 6.01f) })
        // they go one way only, the way they face
        val dir = Silhouettes.walkDir(4)
        inside.zipWithNext().forEach { (a, b) ->
            if (kotlin.math.abs(b - a) < 5f) assertTrue((b - a) * dir >= 0f)
        }
    }
}
