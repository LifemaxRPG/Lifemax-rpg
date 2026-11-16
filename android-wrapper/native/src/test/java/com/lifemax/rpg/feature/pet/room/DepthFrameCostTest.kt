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
 * aiming each piece, ordering its bands with the cats between them and asking a canvas to draw
 * each) against a canvas that only counts: the phone's cost is that plus what the canvas does with
 * each strip, which only the phone can say. Printed (one line per level) so a run's numbers can be
 * read from the test report; the asserts are generous bounds that catch an accident, not a
 * regression of a tenth of a millisecond.
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
    fun bakingTheFacesCostsAFewMillisecondsOnce() {
        val fresh = PetArtResources.loadRoom() // nothing baked yet
        val depth = RoomScene(fresh, RoomStyle.live(DepthLevel.RICH)).depth!!
        val started = System.nanoTime()
        depth.warm()
        val ms = (System.nanoTime() - started) / 1e6
        val bytes = depth.pieces.sumOf { it.volume!!.faces.bytes }
        val runs = depth.pieces.sumOf { it.volume!!.faces.runs }
        println(
            "FACEBAKE (all %d pieces, once for every level) %.1f ms, %.2f MB of bitmaps, %d runs"
                .format(depth.pieces.size, ms, bytes / 1e6, runs)
        )
        // what a frame of the live room at RICH asks the canvas for
        val canvas = CountingCanvas()
        val scene = RoomScene(fresh, RoomStyle.live(DepthLevel.RICH))
        scene.depth!!.sway.restAt(0.6f)
        scene.paintBackdrop(canvas, 12.0, 0.0, 6)
        scene.paintScene(canvas, NoCats)
        println(
            "FACEFRAME (RICH, the whole house) %d draw calls, %d of them strips blitting %d art px"
                .format(canvas.calls, canvas.strips, canvas.stripPixels)
        )
        assertTrue("baked in $ms ms", ms < BAKE_BOUND_MS)
        assertTrue("the faces' bitmaps stay under 4 MB: $bytes", bytes < FACE_BYTES_BOUND)
    }

    private companion object {
        const val WARM = 600
        const val FRAMES = 1_500

        /** A frame of the painter alone must be far below the 16.7 ms of a 60 Hz budget. */
        const val BOUND_MS = 8.0
        const val BAKE_BOUND_MS = 20_000.0

        /** The cap on the bitmaps of the faces of the whole house. */
        const val FACE_BYTES_BOUND = 4_000_000L
    }
}
