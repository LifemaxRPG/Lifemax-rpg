package com.lifemax.rpg.feature.pet.room

import com.lifemax.rpg.feature.pet.art.PetArtResources
import com.lifemax.rpg.feature.pet.body.HouseCat
import com.lifemax.rpg.feature.pet.body.HouseLife
import com.lifemax.rpg.feature.pet.body.PlanStep
import com.lifemax.rpg.feature.pet.body.SurfaceId
import com.lifemax.rpg.feature.pet.body.World
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What a frame of the live room costs on the JVM test harness, for the performance guard's
 * thresholds. The painter's own work is measured (stepping the cats, the camera and the sway,
 * choosing each piece's step, ordering about two hundred strips with the cats between them and
 * asking a canvas to draw each) against a canvas that only counts: the phone's cost is that plus
 * what the canvas does with each strip, which only the phone can say. Printed (one line per level)
 * so a run's numbers can be read from the test report; the asserts are generous bounds that catch
 * an accident, not a regression of a tenth of a millisecond.
 */
class DepthFrameCostTest {
    private val art = PetArtResources.loadRoom()

    private fun frameMs(style: RoomStyle, soft: Boolean = false): Double {
        val scene = RoomScene(art, style)
        val depth = scene.depth
        depth?.asyncWarm = true
        depth?.warm()
        val canvas: RoomCanvas = if (soft) SoftCanvas(art, 6) else CountingCanvas()
        val house = HouseLife(listOf(HouseCat(3, false), HouseCat(8, false)), autopilot = false)
        val cats = Cats(house)
        val director = CameraDirector()
        val steps = ArrayList<PlanStep>()
        repeat(80) {
            steps.add(PlanStep.Walk(-140f))
            steps.add(PlanStep.Walk(330f))
        }
        house.bodies[0].placeAt(-140f, World.ART_Z, SurfaceId.FLOOR, 1)
        house.bodies[0].replacePlan(1, steps)
        house.bodies[1].placeAt(90f, World.ART_Z, SurfaceId.FLOOR, 1)
        val dt = 1f / 60f
        fun frame() {
            house.step(dt)
            director.update(house.bodies[0], house.bodies[1], dt)
            depth?.follow(director.camera.cx)
            depth?.step(dt)
            scene.paintBackdrop(canvas, 20.0, 12.0, 6)
            scene.paintScene(canvas, cats)
            scene.paintOverlay(canvas, 20.0, 6)
        }
        repeat(WARM) { frame() }
        val started = System.nanoTime()
        repeat(FRAMES) { frame() }
        return (System.nanoTime() - started) / 1e6 / FRAMES
    }

    private class Cats(val house: HouseLife) : CatLayer {
        override val count = 2

        override fun depth(i: Int) = house.bodies[i].pose.z

        override fun feet(i: Int) = house.bodies[i].pose.y

        override fun draw(i: Int) = Unit
    }

    @Test
    fun aFrameOfEveryLevelCostsAFractionOfTheBudgetOnTheJvm() {
        val classic = frameMs(RoomStyle.CLASSIC)
        val costs = DepthLevel.entries.associateWith { frameMs(RoomStyle.live(it)) }
        println(
            "FRAMECOST (ms per frame, painter only) classic=%.4f subtle=%.4f medium=%.4f rich=%.4f"
                .format(
                    classic,
                    costs[DepthLevel.SUBTLE],
                    costs[DepthLevel.MEDIUM],
                    costs[DepthLevel.RICH],
                )
        )
        val softClassic = frameMs(RoomStyle.CLASSIC, soft = true)
        val soft = DepthLevel.entries.associateWith { frameMs(RoomStyle.live(it), soft = true) }
        println(
            "FRAMECOST (ms per frame, with a software canvas that blits every strip) " +
                "classic=%.3f subtle=%.3f medium=%.3f rich=%.3f"
                    .format(
                        softClassic,
                        soft[DepthLevel.SUBTLE],
                        soft[DepthLevel.MEDIUM],
                        soft[DepthLevel.RICH],
                    )
        )
        assertTrue("classic $classic", classic < BOUND_MS)
        for ((level, ms) in costs) assertTrue("$level $ms ms", ms < BOUND_MS)
    }

    @Test
    fun makingACameraStepCostsAFewMillisecondsOnce() {
        val sheets = scene(DepthLevel.MEDIUM).sheets
        val started = System.nanoTime()
        sheets.warmAll()
        val ms = (System.nanoTime() - started) / 1e6
        println(
            "STEPCOST (ms, all %d camera steps of MEDIUM) total=%.1f each=%.1f"
                .format(sheets.count, ms, ms / sheets.count)
        )
        val bytes = (0 until sheets.count).sumOf { sheets.step(it).image.pixels.size * 4L }
        println(
            "STEPMEM (all %d camera steps of MEDIUM, as bitmaps) %.2f MB, %d strips in the rest step"
                .format(sheets.count, bytes / 1e6, sheets.step(sheets.rest).count)
        )
        assertTrue("all steps in $ms ms", ms < STEPS_BOUND_MS)
        assertTrue("the steps' bitmaps stay under 8 MB: $bytes", bytes < STEPS_BYTES_BOUND)
    }

    private fun scene(level: DepthLevel) = RoomScene(art, RoomStyle.live(level)).depth!!

    private companion object {
        const val WARM = 600
        const val FRAMES = 1_500

        /** A frame of the painter alone must be far below the 16.7 ms of a 60 Hz budget. */
        const val BOUND_MS = 8.0
        const val STEPS_BOUND_MS = 20_000.0

        /** The cap on the bitmaps of the camera steps of one room (the level's steps, all made). */
        const val STEPS_BYTES_BOUND = 8_000_000L
    }
}
