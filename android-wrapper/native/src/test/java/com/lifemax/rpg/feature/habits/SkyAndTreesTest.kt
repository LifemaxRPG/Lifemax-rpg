package com.lifemax.rpg.feature.habits

import com.lifemax.rpg.feature.habits.domain.Ambient
import com.lifemax.rpg.feature.habits.domain.BuildingSpec as B
import com.lifemax.rpg.feature.habits.domain.CloudKind
import com.lifemax.rpg.feature.habits.domain.TreeKind
import com.lifemax.rpg.feature.habits.domain.Trees
import com.lifemax.rpg.feature.habits.presentation.headerBand
import kotlin.math.abs
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The sky's clouds and the street's trees are pure layouts: the same for the same seed, each cloud
 * in a lane of its own, every tree inside the room it is given, and the page header's band is left
 * to the app's surface.
 */
class SkyAndTreesTest {
    private val seed = HabitsScenarios.TODAY.hashCode()

    @Test
    fun cloudLayoutsAreDeterministic() {
        val a = Ambient.clouds(seed)
        val b = Ambient.clouds(seed)
        assertEquals(Ambient.CLOUDS, a.size)
        for (i in a.indices) {
            assertEquals(a[i].kind, b[i].kind)
            assertEquals(a[i].x0, b[i].x0, 0f)
            assertEquals(a[i].scale, b[i].scale, 0f)
            assertArrayEquals(a[i].lobes, b[i].lobes, 0f)
            assertEquals(a[i].x0 - .25f, Ambient.cloudAt(a[i], 0f), 1.5f)
        }
        // another day, another sky
        val other = Ambient.clouds(seed + 1)
        assertNotEquals(a.map { it.lobes.toList() }, other.map { it.lobes.toList() })
    }

    @Test
    fun everyCloudHasALaneOfItsOwnClearOfTheTowers() {
        for (s in listOf(seed, 1, 99, -7)) {
            val clouds = Ambient.clouds(s)
            assertEquals(clouds.size, clouds.map { it.lane }.toSet().size)
            // the lowest lane is well above the far city (the horizon is at 1.0 of the band)
            assertTrue(clouds.all { it.y <= .6f })
            for (i in clouds.indices) for (j in i + 1 until clouds.size) assertTrue(
                abs(clouds[i].y - clouds[j].y) >= .12f
            )
            // the shapes vary: a heap, a streak, small puffs
            assertTrue(clouds.map { it.kind }.toSet().size == CloudKind.entries.size)
            // a streak is flat, a heap is tall
            for (c in clouds) {
                assertTrue(c.lobes.size % 4 == 0 && c.lobes.size >= 12)
                var up = 0f
                var wide = 0f
                var k = 0
                while (k < c.lobes.size) {
                    up = maxOf(up, -c.lobes[k + 1] + c.lobes[k + 3])
                    wide = maxOf(wide, abs(c.lobes[k]) + c.lobes[k + 2])
                    k += 4
                }
                assertTrue(wide <= c.reach + 1f)
                assertTrue(up <= c.rise + 1f)
            }
        }
    }

    @Test
    fun theNearAndBigDriftFasterAndAllComeRound() {
        val clouds = Ambient.clouds(seed)
        val sorted = clouds.sortedBy { it.depth }
        assertTrue(sorted.zipWithNext().all { (p, q) -> p.speed <= q.speed })
        assertTrue(clouds.all { it.speed > 0f })
        for (c in clouds) for (t in 0 until 4000 step 13) assertTrue(
            Ambient.cloudAt(c, t.toFloat()) in -.26f..1.26f
        )
    }

    @Test
    fun treeVariantsComeFromTheSeed() {
        for (s in -50..200) {
            assertEquals(Trees.variant(s), Trees.variant(s))
            assertTrue(Trees.variant(s) in 0 until Trees.VARIANTS)
        }
        val kinds = (0 until 300).map { Trees.shape(it).kind }
        assertTrue(kinds.toSet().size == TreeKind.entries.size)
        // round trees are the common ones
        assertTrue(kinds.count { it == TreeKind.ROUND } > kinds.count { it == TreeKind.PINE })
        // the same seed is the same tree, another seed (often) another
        assertTrue((0 until 40).map { Trees.variant(it) }.toSet().size > 3)
    }

    @Test
    fun everyTreeStaysInTheRoomItIsGiven() {
        for (shape in Trees.shapes) {
            assertTrue(shape.halfWidth <= 22f)
            assertTrue(shape.height <= 68f && shape.height >= 55f)
            assertTrue(shape.crown < -15f)
            when (shape.kind) {
                TreeKind.PINE -> assertTrue(shape.outline.size >= 40 && shape.lobes.isEmpty())
                else ->
                    assertTrue(shape.lobes.size >= 15 && shape.bumps.size == shape.lobes.size * 3)
            }
            // a park tree (drawn at .8) clears the building beside it, a street tree the lots
            assertTrue(.8f * shape.halfWidth <= 18f)
            assertTrue(shape.halfWidth <= B.GAP / 2f)
        }
    }

    @Test
    fun theHeaderBandIsThePagesTopInsetInPixels() {
        // the scene starts under it, whatever the density; never more than the screen
        assertEquals(64 * 3, headerBand(64f, 3f, 5000))
        assertEquals(176, headerBand(110f, 1.6f, 5000))
        assertEquals(0, headerBand(0f, 2.6f, 5000))
        assertEquals(300, headerBand(500f, 2.6f, 300))
    }
}
