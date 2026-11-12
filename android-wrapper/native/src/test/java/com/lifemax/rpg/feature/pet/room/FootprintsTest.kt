package com.lifemax.rpg.feature.pet.room

import com.lifemax.rpg.feature.pet.art.PetArtResources
import com.lifemax.rpg.feature.pet.body.CatBody
import com.lifemax.rpg.feature.pet.body.CatEventType
import com.lifemax.rpg.feature.pet.body.Footprints
import com.lifemax.rpg.feature.pet.body.PlanStep
import com.lifemax.rpg.feature.pet.body.World
import com.lifemax.rpg.feature.pet.brain.Spots
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The owner's "still clipped into sofa": a cat walking from the corner at the sofa's far end to
 * behind the bed went straight through the sofa, and by its right end only its ears showed above
 * the arm. Every floor walk now goes round the furniture that stands on the floor ([Footprints]):
 * from anywhere in the house to every place the brain rests a cat on the floor (and back), the
 * cat's feet are never inside a piece, and it still gets where it was sent. The footprints are the
 * shipped layers'.
 */
class FootprintsTest {
    private val layers = PetArtResources.loadRoom().layers!!

    @Test
    fun theFootprintsAreTheShippedLayers() {
        for (p in layers.pieces) {
            val v = p.volume ?: continue
            if (!p.onFloor || p.name.startsWith("cat_tree")) continue
            val f = Footprints.pieces.firstOrNull { it.name == p.name }
            if (f == null) { // only a piece no cat can walk behind may be left out
                assertTrue(p.name, v.sortFront < World.floorY(0f))
                continue
            }
            assertEquals(p.name, (p.houseX + RoomLayout.HOUSE_X0).toFloat(), f.x0, 0f)
            assertEquals(p.name, (p.houseX + p.width + RoomLayout.HOUSE_X0).toFloat(), f.x1, 0f)
            assertEquals(p.name, v.sortFront, f.front, 0f)
            assertEquals(p.name, v.sortFront - v.depth, f.back, 0f)
        }
    }

    @Test
    fun theStraightWayFromTheSofasFarCornerToBehindTheBedWentThroughTheSofa() {
        val a = Spots.BEHIND_CUSHION
        val b = Spots.BEHIND_BED
        var through = 0
        for (s in 0..STEPS) {
            val t = s.toFloat() / STEPS
            val x = a.x + (b.x - a.x) * t
            val z = a.z + (b.z - a.z) * t
            if (Footprints.insideAny(x, z)?.name == "sofa") through++
        }
        assertTrue("the straight line is inside the sofa at $through of $STEPS points", through > 0)
        val walk = walk(a.x, a.z, b.x, b.z)
        assertEquals("the walk goes round it", "", walk.inside)
        assertTrue("and arrives", walk.arrived)
    }

    @Test
    fun noFloorWalkGoesThroughFurniture() {
        val ends = Spots.all.filter { it.open && it.onFloor }.map { it.x to it.z }
        val starts = ArrayList<Pair<Float, Float>>()
        var x = World.HOUSE_MIN_X
        while (x <= World.HOUSE_MAX_X) {
            for (z in START_Z) if (Footprints.insideAny(x, z) == null) starts += x to z
            x += GRID_PX
        }
        val bad = ArrayList<String>()
        for ((sx, sz) in starts) {
            for ((ex, ez) in ends) {
                for ((ax, az, bx, bz) in listOf(listOf(sx, sz, ex, ez), listOf(ex, ez, sx, sz))) {
                    val w = walk(ax, az, bx, bz)
                    if (w.inside.isNotEmpty() || !w.arrived) {
                        bad += "($ax, $az) to ($bx, $bz): ${w.inside} arrived ${w.arrived}"
                    }
                }
            }
        }
        assertTrue("${bad.size} walks: ${bad.take(MAX_SHOWN)}", bad.isEmpty())
    }

    @Test
    fun aCatPlacedInsideTheSofaStepsOutToItsFront() {
        // where the owner's kitten was, inside the sofa's right arm
        assertNotNull(Footprints.insideAny(OWNER_X, OWNER_Z))
        val out = FloatArray(2)
        assertTrue(Footprints.next(OWNER_X, OWNER_Z, BED_X, BED_Z, out))
        assertEquals("straight out to the front first", OWNER_X, out[0], 0f)
        assertTrue(Footprints.insideAny(out[0], out[1]) == null)
        val w = walk(OWNER_X, OWNER_Z, BED_X, BED_Z)
        assertTrue(w.arrived)
        assertEquals("once out it never goes back in", "", w.inside)
    }

    private class Walk(val inside: String, val arrived: Boolean)

    /** A cat walking on the floor from ([x0], [z0]) to ([x1], [z1]): where its feet went. */
    private fun walk(x0: Float, z0: Float, x1: Float, z1: Float): Walk {
        val body = CatBody(seed = SEED, startX = x0, startZ = z0)
        body.replacePlan(1, listOf(PlanStep.Walk(x1, z1)))
        var inside = ""
        var arrived = false
        var left = Footprints.insideAny(x0, z0) == null
        var t = 0
        while (!arrived && t < MAX_TICKS) {
            body.step(DT)
            t++
            val p = Footprints.insideAny(body.pose.x, body.pose.z)
            if (p == null) left = true
            else if (left && inside.isEmpty() && Footprints.insideAny(x1, z1) == null) {
                inside = "${p.name} at (${body.pose.x}, ${body.pose.z})"
            }
            for (i in 0 until body.pose.events.count) {
                if (body.pose.events.type(i) == CatEventType.Arrived) arrived = true
            }
        }
        arrived = arrived && Math.abs(body.pose.x - x1) < ARRIVE_PX
        return Walk(inside, arrived)
    }

    private companion object {
        const val SEED = 7
        const val OWNER_X = -38f
        const val OWNER_Z = 16f
        const val BED_X = 40f
        const val BED_Z = 10f
        const val DT = 1f / 30f
        const val MAX_TICKS = 30 * 120
        const val STEPS = 100
        const val GRID_PX = 40f
        const val ARRIVE_PX = 1f
        const val MAX_SHOWN = 8
        val START_Z = floatArrayOf(8f, 20f, 30f, 35f, 50f, 62f)
    }
}
