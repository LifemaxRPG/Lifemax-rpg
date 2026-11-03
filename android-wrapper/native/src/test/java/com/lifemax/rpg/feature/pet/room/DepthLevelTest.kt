package com.lifemax.rpg.feature.pet.room

import com.lifemax.rpg.feature.pet.art.PetArtResources
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The depth levels: one camera at three strengths. Every number grows with the level, the camera at
 * rest is the same at every level, and the side the sofa shows and the number of cached camera
 * steps are the ones `PET_ENGINE_PLAN.md` section 5 documents.
 */
class DepthLevelTest {
    private val solids = PetArtResources.loadRoom().layers!!.pieces.filter { it.volume != null }

    @Test
    fun everyNumberGrowsWithTheLevel() {
        val levels = DepthLevel.entries
        for (i in 1 until levels.size) {
            val a = levels[i - 1]
            val b = levels[i]
            assertTrue("$b wall", b.wallGain > a.wallGain)
            assertTrue("$b depth", b.depthGain > a.depthGain)
            assertTrue("$b pitch", b.pitch > a.pitch)
            assertTrue("$b drift", b.idleAmplitude > a.idleAmplitude)
            assertTrue(b.shadowSize > a.shadowSize && b.shadowStrength > a.shadowStrength)
            assertTrue(b.lightStrength > a.lightStrength && b.rimStrength > a.rimStrength)
            assertTrue(b.seamStrength > a.seamStrength)
        }
        assertEquals(1f, DepthLevel.SUBTLE.shadowSize * DepthLevel.SUBTLE.lightStrength, 0f)
    }

    @Test
    fun theCameraAtRestIsTheSameAtEveryLevel() {
        for (level in DepthLevel.entries) {
            assertEquals(0f, level.pitchAt(0f), 0f)
            assertEquals(level.pitchAt(-0.6f), level.pitchAt(0.6f), 0f)
            assertEquals(level.pitch, level.pitchAt(1f), 0f)
            for (row in floatArrayOf(100f, 117f, 128f, 150f)) {
                assertEquals(0, Parallax.shiftAtRow(0f, row, level))
            }
        }
    }

    @Test
    fun theSofaShowsTheDocumentedSideAtFullSway() {
        // the sofa is 11 floor rows deep: its back lags its front by 11 * depthGain at full sway
        val sofa = solids.first { it.name == "sofa" }.volume!!
        val side = DepthLevel.entries.map { Math.round(sofa.depth * it.depthGain) }
        assertEquals(listOf(2, 3, 4), side)
    }

    @Test
    fun eachLevelCachesEnoughStepsForOnePixelAStep() {
        val deepest = solids.maxOf { it.volume!!.model.depth }
        val steps = DepthLevel.entries.map { VolumeSheets.stepsFor(solids, it) }
        assertEquals(listOf(3, 4, 5), steps)
        for ((level, n) in DepthLevel.entries.zip(steps)) {
            assertTrue(
                "$level: a step moves the deepest back at most a pixel",
                deepest * level.depthGain / n <= 1f,
            )
            assertTrue(2 * n + 1 <= RoomCanvas.MAX_SHEETS)
        }
    }
}
