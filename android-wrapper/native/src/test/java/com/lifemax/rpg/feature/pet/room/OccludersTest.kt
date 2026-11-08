package com.lifemax.rpg.feature.pet.room

import com.lifemax.rpg.feature.pet.art.PetArtResources
import com.lifemax.rpg.feature.pet.body.Occluders
import com.lifemax.rpg.feature.pet.body.SurfaceId
import com.lifemax.rpg.feature.pet.body.World
import com.lifemax.rpg.feature.pet.brain.Spots
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The owner's "the cats clip behind the sofa and are barely visible": how much of a resting cat
 * each floor place of the brain leaves hidden behind nearer furniture, in the classic room and in
 * the 2.5D room, measured on the shipped pixels; the brain's own table ([Occluders]) agrees with
 * the shipped layers and never says a cat is more visible than the pixels do; no resting place
 * hides more than [Occluders.MAX_HIDDEN] of a cat (the hidden corner behind the sofa hid all of
 * one); the floor right behind the sofa is hidden, its front is not. And the owner's "the cat's
 * ears are poking through the cat tree": the pieces a cat could stand under are the shipped ones, a
 * cat sitting up under the tree's middle platform is [Occluders.clipped], and no place the brain
 * rests a cat at is (but the second bowl, which the page puts half under the tree).
 */
class OccludersTest {
    private val layers = PetArtResources.loadRoom().layers!!

    @Test
    fun theTableIsTheShippedLayers() {
        for (p in layers.pieces) {
            val v = p.volume ?: continue
            val o = Occluders.pieces.firstOrNull { it.name == p.name }
            requireNotNull(o) { "the table has no ${p.name}" }
            assertEquals(p.name, (p.houseX + RoomLayout.HOUSE_X0).toFloat(), o.x0, 0f)
            assertEquals(p.name, (p.houseX + p.width + RoomLayout.HOUSE_X0).toFloat(), o.x1, 0f)
            assertEquals(p.name, p.y.toFloat(), o.top, 0f)
            assertEquals(p.name, (p.y + p.height).toFloat(), o.bottom, 0f)
            assertEquals(p.name, v.sortFront, o.front, 0f)
        }
    }

    /** The share of a resting cat at [x], [z] the shipped pixels hide (props as their boxes). */
    private fun pixelShare(x: Float, z: Float, layered: Boolean): Float {
        val k = World.depthScale(z)
        val feet = World.floorY(z)
        val w = Occluders.CAT_W * k
        val h = Occluders.CAT_H * k
        var hidden = 0
        val n = SAMPLES
        for (i in 0 until n) for (j in 0 until n) {
            val px = x - w / 2 + w * (i + 0.5f) / n
            val py = feet - h + h * (j + 0.5f) / n
            if (coveredByPixels(px, py, feet, layered)) hidden++
        }
        return hidden.toFloat() / (n * n)
    }

    private fun coveredByPixels(px: Float, py: Float, feet: Float, layered: Boolean): Boolean {
        for (o in Occluders.pieces) {
            if (o.front <= feet + CAT_AFTER) continue
            if (px < o.x0 || px >= o.x1 || py < o.top || py >= o.bottom) continue
            if (!o.layered) return true
            if (!layered) continue
            val p = layers.piece(o.name)
            val ix = (px - o.x0).toInt()
            val iy = (py - o.top).toInt()
            val a = layers.atlas.pixels[(p.atlasY + iy) * layers.atlas.width + p.atlasX + ix]
            if ((a ushr ALPHA_SHIFT) >= OPAQUE) return true
        }
        return false
    }

    @Test
    fun noRestingPlaceHidesMostOfACatInEitherRoom() {
        val report = StringBuilder()
        val wrong = ArrayList<String>()
        for (s in Spots.all.filter { it.onFloor }) {
            for (layered in listOf(false, true)) {
                val pixels = pixelShare(s.x, s.z, layered)
                val table = Occluders.hiddenShare(s.x, s.z, layered)
                report.append(
                    String.format(
                        "%-20s %s pixels %.2f table %.2f%n",
                        s.id,
                        if (layered) "2.5D   " else "classic",
                        pixels,
                        table,
                    )
                )
                if (table < pixels - SLACK) wrong += "${s.id}: the table says less than the pixels"
                if (pixels > Occluders.MAX_HIDDEN)
                    wrong += "${s.id} hides ${pixels * 100} % of a cat"
                if (s.hidden) wrong += "${s.id} is marked hidden"
            }
        }
        println(report)
        assertTrue(wrong.joinToString("; "), wrong.isEmpty())
    }

    @Test
    fun rightBehindTheSofaACatIsHiddenAndInFrontItIsNot() {
        val table = Occluders.hiddenShare(-100f, 20f)
        val pixels = pixelShare(-100f, 20f, true)
        println("right behind the sofa: table $table pixels $pixels")
        assertTrue("table $table", table > 0.9f)
        assertTrue("pixels $pixels", pixels > 0.6f)
        assertTrue(Occluders.hidden(-100f, 20f))
        val z = Occluders.visibleZ(-100f, 20f)
        assertTrue("steps out in front of the sofa: $z", z > 25f && !Occluders.hidden(-100f, z))
        assertTrue(!Occluders.hidden(-60f, World.ART_Z))
        // the classic room draws the sofa as part of its backdrop: only the floor cushion is there
        assertTrue(Occluders.hiddenShare(-100f, 20f, false) <= Occluders.MAX_HIDDEN)
        assertEquals(SurfaceId.FLOOR, Spots.BEHIND_CUSHION.surface)
    }

    @Test
    fun theOverhangsAreTheShippedPiecesThatStandClearOfTheFloor() {
        val shipped =
            layers.pieces
                .filter { it.volume?.wall == false && it.occludes && !it.onFloor }
                .map { it.name }
        assertEquals(shipped.sorted(), Occluders.overhangs.map { it.name }.sorted())
        for (o in Occluders.overhangs) {
            val p = layers.piece(o.name)
            val v = p.volume!!
            assertEquals(o.name, (p.houseX + RoomLayout.HOUSE_X0).toFloat(), o.x0, 0f)
            assertEquals(o.name, (p.houseX + p.width + RoomLayout.HOUSE_X0).toFloat(), o.x1, 0f)
            assertEquals(o.name, (p.y + p.height).toFloat(), o.underside, 0f)
            assertEquals(o.name, v.sortFront - v.depth, o.back, 0f)
            assertEquals(o.name, v.sortFront, o.front, 0f)
        }
    }

    @Test
    fun aCatSittingUpUnderTheMiddlePlatformIsClippedAndRestsInFrontOfTheTree() {
        // where the brain scratched the post before: the cats' floor depth, under the platform
        assertTrue(Occluders.clipped(OLD_SCRATCH_X, World.ART_Z, 1))
        assertTrue(Occluders.clipped(OLD_SCRATCH_X, World.ART_Z))
        // the room under the platform (its underside, row 109, down to the floor, row 132) is
        // less than a kitten sitting up needs, so no cat fits
        assertTrue(World.floorY(World.ART_Z) - MID_UNDERSIDE < KITTEN_SITTING_H)
        val z = Occluders.visibleZ(OLD_SCRATCH_X, World.ART_Z, 1)
        assertTrue("in front of the tree: z $z", World.floorY(z) >= TREE_FRONT)
        assertEquals(z, Spots.SCRATCH_TREE.z, 0f)
        assertTrue(!Occluders.clipped(Spots.SCRATCH_TREE.x, Spots.SCRATCH_TREE.z, 1))
        // behind the tree (feet behind row 125) and beside it, nothing is in the way of its head
        assertTrue(!Occluders.clipped(OLD_SCRATCH_X, BEHIND_Z))
        assertTrue(!Occluders.clipped(LEFT_X, World.ART_Z))
        assertTrue(!Occluders.clipped(RIGHT_X, World.ART_Z))
        // left of the tree, facing away from it, a cat's head is clear; facing it, it is not
        assertTrue(!Occluders.clipped(NEAR_X, World.ART_Z, -1))
        assertTrue(Occluders.clipped(NEAR_X, World.ART_Z, 1))
    }

    @Test
    fun noPlaceTheBrainRestsACatAtIsUnderThePlatformButTheSecondBowl() {
        val clipped = Spots.all.filter { it.onFloor && Occluders.clipped(it.x, it.z, it.face) }
        assertEquals(listOf(Spots.BOWL_1), clipped)
        assertTrue("the bowl stays a place", !Spots.BOWL_1.hidden)
        assertTrue(Spots.all.none { it.hidden })
    }

    private companion object {
        const val OLD_SCRATCH_X = 134f
        const val NEAR_X = 100f
        const val LEFT_X = 60f
        const val RIGHT_X = 230f
        const val BEHIND_Z = 10f
        const val MID_UNDERSIDE = 109f
        const val TREE_FRONT = 137f

        /** A kitten sitting up, ears included, measured on the shipped sprites. */
        const val KITTEN_SITTING_H = 34f

        const val SAMPLES = 16
        const val ALPHA_SHIFT = 24
        const val OPAQUE = 0x80
        const val CAT_AFTER = 0.001f
        const val SLACK = 0.08f
    }
}
