package com.lifemax.rpg.feature.pet.body

import com.lifemax.rpg.feature.pet.art.CatPalette
import com.lifemax.rpg.feature.pet.art.Growth
import com.lifemax.rpg.feature.pet.art.PetArtResources
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The preview harness for the lab clips (docs/specs/PET_ART_LAB.md): every new clip is played by a
 * real [CatBody] through a plain `act` step, the frames it shows are recorded, and each shown frame
 * is drawn from the shipped art for a grown cat and a kitten, so a clip that cannot be selected,
 * skips frames, does not hold its last frame or has nothing to draw fails here.
 */
class LabClipsPreviewTest {
    private val dt = 1f / 120f
    private val art = PetArtResources.load()
    private val lab = PetArtResources.loadLab()

    /** The frames [name] shows when acted for [seconds], in order, with repeats collapsed. */
    private fun play(name: String, seconds: Float): List<Int> {
        val body = CatBody(seed = 7)
        body.replacePlan(1, listOf(PlanStep.Act(name, seconds)))
        val shown = ArrayList<Int>()
        repeat((seconds / dt).toInt()) {
            body.step(dt)
            if (body.pose.clipName == name && shown.lastOrNull() != body.pose.frame)
                shown.add(body.pose.frame)
        }
        return shown
    }

    @Test
    fun theClipTableHoldsEveryLabRowAsTheSheetAndLabTableDescribeIt() {
        assertEquals(lab.rows.keys, Clips.lab.map { it.name }.toSet())
        for (clip in Clips.lab) {
            val anim = art.anims[clip.name]
            assertNotNull(clip.name, anim)
            assertEquals(clip.name, anim!!.row, clip.row)
            assertEquals(clip.name, anim.frames, clip.frames)
            assertEquals(clip.name, anim.fps, clip.fps.toDouble(), 0.0)
            assertEquals(clip.name, anim.bottom, clip.bottom)
            assertEquals(clip.name, lab[clip.name]!!.loop, clip.loop)
            assertTrue("${clip.name} comes after the older clips", clip.id >= OLDER_CLIPS)
        }
    }

    @Test
    fun everyLabClipPlaysFromItsFirstFrameThroughEveryFrameInOrder() {
        for (clip in Clips.lab) {
            val shown = play(clip.name, clip.seconds * 2f + 0.5f)
            assertTrue("${clip.name} was never shown", shown.isNotEmpty())
            assertEquals("${clip.name} starts on frame 0", 0, shown.first())
            val firstPass = shown.take(clip.frames)
            assertEquals(
                "${clip.name} plays every frame in order",
                (0 until clip.frames).toList(),
                firstPass,
            )
            if (!clip.loop)
                assertEquals("${clip.name} holds its last frame", clip.frames - 1, shown.last())
            else assertTrue("${clip.name} cycles", shown.size > clip.frames)
        }
    }

    @Test
    fun everyFrameOfEveryLabClipHasArtForEveryCoatAndStage() {
        for (clip in Clips.lab) for (coat in art.legacyCoats) for (stage in
            listOf(Growth.ADULT, 0, 3, 5)) {
            for (f in 0 until clip.frames) {
                val img = art.frame(coat, stage, clip.name, f)
                assertNotNull("${clip.name}#$f $coat $stage", img)
                val drawn = img!!.pixels.count { it != CatPalette.CLEAR }
                assertTrue("${clip.name}#$f $coat $stage has $drawn pixels", drawn > MIN_PIXELS)
            }
        }
    }

    @Test
    fun theCarryWalkKeepsTheWalksStrideAndAMouthAnchorOnEveryFrame() {
        assertEquals(Clips.WALK.stridePx, Clips.CARRY.stridePx, 0f)
        assertTrue(Clips.CARRY.isLocomotion)
        val carry = lab["carry"]!!
        for (stage in listOf(Growth.ADULT, 0, 1, 2, 3, 4, 5)) for (f in
            0 until Clips.CARRY.frames) assertNotNull("carry#$f at $stage", carry.mouth(stage, f))
    }

    private companion object {
        /** the clips before the lab rows: the page's 28 rows and the 4 depth rows */
        const val OLDER_CLIPS = 32
        const val MIN_PIXELS = 150
    }
}
