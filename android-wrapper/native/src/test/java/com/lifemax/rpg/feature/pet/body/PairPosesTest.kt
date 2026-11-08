package com.lifemax.rpg.feature.pet.body

import com.lifemax.rpg.feature.pet.art.Growth
import com.lifemax.rpg.feature.pet.art.PetArtResources
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The brain's copy of the art lab's pair poses ([PairPose]) is the shipped `lab.json`: the same
 * three poses, rows, mirroring, contact frames and every stage's offset, so the cats stand where
 * the art was fitted (and never on one point: every offset is a real distance).
 */
class PairPosesTest {
    private val lab = PetArtResources.loadLab()

    @Test
    fun theTableIsTheLabsPairs() {
        assertEquals(lab.pairs.keys, PairPose.entries.map { it.key }.toSet())
        for (pose in PairPose.entries) {
            val p = lab.pairs.getValue(pose.key)
            assertEquals(pose.key, p.a, pose.a)
            assertEquals(pose.key, p.b, pose.b)
            assertEquals(pose.key, p.mirrorB, pose.mirrorB)
            assertEquals(pose.key, p.contactFrame, pose.contactFrame)
            for (stage in listOf(Growth.ADULT) + (0..5)) {
                val o = p.offset(stage)
                assertEquals("${pose.key} dx at $stage", o.x.toInt(), pose.dx(stage))
                assertEquals("${pose.key} dy at $stage", o.y.toInt(), pose.dy(stage))
                assertTrue(
                    "${pose.key} at $stage is a real distance",
                    Math.abs(pose.dx(stage)) > 10,
                )
            }
        }
    }

    @Test
    fun theGapScalesWithDepthAndMeetsHalfwayBetweenSizes() {
        val adult = PairPose.GREET.gap(-1, -1, World.ART_Z)
        assertEquals(59f, adult, 0.01f)
        assertTrue(PairPose.GREET.gap(-1, -1, 80f) > adult)
        val mixed = PairPose.GREET.gap(-1, 0, World.ART_Z)
        assertEquals((59f + 37f) / 2f, mixed, 0.01f)
        assertTrue("the spooned cat lies further back", PairPose.SNUGGLE.backZ(-1, 50f) > 0f)
    }
}
