package com.lifemax.rpg.feature.pet.room

import com.lifemax.rpg.feature.pet.art.PetArtResources
import com.lifemax.rpg.feature.pet.body.World
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The live room with its view angle, painted every frame on a software canvas at 120 Hz: the room
 * is never dead on (the layers are offset at rest, the furniture shows its side), yet nothing moves
 * while the camera stands still, and a cat's shift (so its shadow, drawn with it at the same shift)
 * is the floor's for its depth: the feet stay on their shadow.
 */
class ViewAngleSceneTest {
    private val art = PetArtResources.loadRoom()
    private val live = RoomStyle.live(DepthLevel.RICH, false, LiveDepthTuning.VIEW_ANGLE)

    /** One cat standing on the floor at depth [z]; records the shift it is drawn at. */
    private class OneCat(val z: Float, val x: Float) : CatLayer {
        val shifts = ArrayList<Int>()
        override val count = 1

        override fun depth(i: Int) = z

        override fun feet(i: Int) = World.floorY(z)

        override fun x(i: Int) = x

        override fun surface(i: Int): String? = "floor"

        override fun draw(i: Int) = Unit

        override fun draw(i: Int, shift: Int, style: RoomStyle, canvas: RoomCanvas) {
            shifts.add(shift)
        }
    }

    private fun RoomScene.frame(cats: CatLayer): IntArray {
        val canvas = SoftCanvas(art)
        paintBackdrop(canvas, 12.0, 0.0, 0)
        paintScene(canvas, cats)
        return canvas.pixels
    }

    @Test
    fun theRoomIsOffsetAtRestAndTheFurnitureShowsItsSide() {
        val s = RoomScene(art, live)
        val d = s.depth!!
        repeat(240) {
            d.follow(PanTuning.CENTRE_X)
            d.step(1f / 120f)
        }
        s.frame(NoCats)
        val wall = d.shiftAtZ(0f)
        val near = d.shiftAtZ(95f)
        println("at rest, camera on the middle of the house: wall $wall px, near $near px")
        assertTrue("wall $wall near $near", near - wall >= 4)
        val sofa = d.stepOf("sofa")
        assertTrue("the sofa shows its side at rest ($sofa)", sofa != d.sheets.rest)
    }

    @Test
    fun aStillCameraWithTheViewAngleDrawsBitIdenticalFramesAndAStillCatNeverSlides() {
        val s = RoomScene(art, live)
        val d = s.depth!!
        val cat = OneCat(60f, 150f)
        var last: IntArray? = null
        var changed = 0
        repeat(120 * 8) { i ->
            d.follow(150f)
            d.step(1f / 120f)
            if (i >= 120) {
                val now = s.frame(cat)
                if (last != null && !now.contentEquals(last)) changed++
                last = now
            }
        }
        assertEquals("frames that changed with the camera standing still", 0, changed)
        assertEquals("one shift for the still cat", 1, cat.shifts.toSet().size)
        assertEquals("the floor's shift at its depth", d.shiftAtZ(60f), cat.shifts.last())
    }
}
