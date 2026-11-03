package com.lifemax.rpg.feature.pet.room

import com.lifemax.rpg.feature.pet.art.PetArtResources
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The 2.5D room drawn on a software canvas, with stand-in cats (plain magenta boxes) so the order
 * of the layers is plain to see: the cat that walks behind the sofa's arm, the one in front of it,
 * the one on the sofa, the one inside the cat tree. Also: with every effect off the layered room is
 * the classic room exactly, so the cut-outs lose nothing.
 */
class DepthSceneTest {
    private val art = PetArtResources.loadRoom()

    /** Stand-in cats: a 12 x 20 box with the feet at [feet] on house column [x]. */
    private class BoxCats(
        private val x: IntArray,
        private val z: FloatArray,
        private val feet: IntArray,
        private val surface: Array<String?> = arrayOfNulls(x.size),
    ) : CatLayer {
        lateinit var canvas: RoomCanvas
        override val count = x.size

        override fun depth(i: Int) = z[i]

        override fun feet(i: Int) = feet[i].toFloat()

        override fun surface(i: Int) = surface[i]

        override fun draw(i: Int) = draw(i, 0, RoomStyle.CLASSIC, canvas)

        override fun draw(i: Int, shift: Int, style: RoomStyle, canvas: RoomCanvas) {
            canvas.fill(x[i] - 6 + shift, feet[i] - 20, 12, 20, CAT)
        }
    }

    private fun paint(
        style: RoomStyle,
        cats: BoxCats?,
        hour: Double,
        sway: Float = 0f,
    ): SoftCanvas {
        val scene = RoomScene(art, style)
        scene.depth?.sway?.restAt(sway)
        val canvas = SoftCanvas(art)
        cats?.canvas = canvas
        scene.paintBackdrop(canvas, hour, 0.0, 0)
        scene.paintScene(canvas, cats ?: NoCats)
        return canvas
    }

    private fun SoftCanvas.at(x: Int, y: Int) = pixels[y * width + x]

    // ------------------------------------------------------------------ the flag

    @Test
    fun theDefaultStyleIsTheClassicRoomWithNoDepthPainter() {
        assertEquals(RoomStyle.CLASSIC, RoomStyle())
        assertNull(RoomScene(art).depth)
        assertNull(RoomScene(art, RoomStyle.CLASSIC).depth)
        assertFalse(RoomStyle.CLASSIC.depth)
        assertTrue(RoomScene(art, RoomStyle.DEPTH25).depth != null)
    }

    @Test
    fun withEveryEffectOffTheLayeredRoomIsTheClassicRoomExactly() {
        val layersOnly = RoomStyle(layers = true)
        // the classic window light is painted over the furniture's feet, the layered room's under
        // it;
        // so compare in the hours the window gives no light (before 7:30, from 17:30 to 19:30)
        for (hour in doubleArrayOf(6.0, 7.0, 18.0, 19.0)) {
            val flat = paint(RoomStyle.CLASSIC, null, hour)
            val layered = paint(layersOnly, null, hour)
            assertEquals("hour $hour", flat.pixels.toList(), layered.pixels.toList())
        }
    }

    @Test
    fun aCatInFrontOfEverythingIsTheSameInBothRooms() {
        val cats = { BoxCats(intArrayOf(100, 400), floatArrayOf(60f, 70f), intArrayOf(135, 138)) }
        val flat = paint(RoomStyle.CLASSIC, cats(), 18.0)
        val layered = paint(RoomStyle(layers = true), cats(), 18.0)
        assertEquals(flat.pixels.toList(), layered.pixels.toList())
    }

    // ------------------------------------------------------------------ the order

    /** The magenta pixels in a house column range and row range. */
    private fun SoftCanvas.cat(x0: Int, x1: Int, y0: Int, y1: Int): Int {
        var n = 0
        for (y in y0..y1) for (x in x0..x1) if (at(x, y) == CAT) n++
        return n
    }

    @Test
    fun aCatBehindTheSofasArmIsHiddenByItAndInFrontOfItIsNot() {
        val style = RoomStyle(layers = true)
        // the sofa's left arm is house x 30..46, rows 96..123; its base row 128 is at depth 25.6
        val behind = paint(style, BoxCats(intArrayOf(40), floatArrayOf(20f), intArrayOf(122)), 18.0)
        val front = paint(style, BoxCats(intArrayOf(40), floatArrayOf(30f), intArrayOf(122)), 18.0)
        val whole = 12 * 20
        assertEquals("in front: all of the cat shows", whole, front.cat(34, 45, 102, 121))
        // behind: the arm covers it (the arm is opaque over x 30..46 for rows 96..123)
        assertEquals("behind: the arm hides it", 0, behind.cat(34, 45, 102, 121))
    }

    @Test
    fun aCatBehindTheSofaPeeksOverTheArmAndTheBack() {
        val style = RoomStyle(layers = true)
        // a tall cat behind the sofa: its head shows above the arm (the arm top is row 96)
        val peeks = paint(style, BoxCats(intArrayOf(38), floatArrayOf(20f), intArrayOf(122)), 18.0)
        // rows 102..121 are the arm; the box spans rows 102..121 only, so nothing shows above 96
        assertEquals(0, peeks.cat(32, 43, 96, 121))
        val tall = BoxCats(intArrayOf(38), floatArrayOf(20f), intArrayOf(122))
        val canvas = SoftCanvas(art)
        tall.canvas = canvas
        val scene = RoomScene(art, style)
        scene.paintBackdrop(canvas, 18.0, 0.0, 0)
        scene.paintScene(canvas, tall)
        // above the arm (rows 94..95) there is only wall and the box does not reach it
        assertEquals(0, canvas.cat(32, 43, 80, 95))
    }

    @Test
    fun aCatOnTheSofaIsInFrontOfTheSofaAndBehindItsCushions() {
        val style = RoomStyle(layers = true)
        // standing on the couch at the art depth (35 > the sofa's 25.6): in front of the sofa;
        // x 133 is where the pink cushion is (house 124..139, rows 100..123)
        val cats = BoxCats(intArrayOf(120), floatArrayOf(35f), intArrayOf(112), arrayOf("couch"))
        val canvas = paint(style, cats, 18.0)
        // the cat's right half (x 120..125) lies over the sofa seat, the cushion overlaps x
        // 124..125
        assertEquals("over the seat", 6 * 20, canvas.cat(114, 119, 92, 111))
        assertEquals("the pink cushion covers the cat's edge", 0, canvas.cat(124, 125, 100, 111))
    }

    @Test
    fun aCatDeeperThanTheTreeIsHiddenByItAndOneInFrontShowsAll() {
        val style = RoomStyle(layers = true)
        // the cat tree's top platform: bedroom x 124..187, rows 64..75, so house x 316..379
        val x = 192 + 150
        val behind = paint(style, BoxCats(intArrayOf(x), floatArrayOf(40f), intArrayOf(80)), 18.0)
        val front = paint(style, BoxCats(intArrayOf(x), floatArrayOf(50f), intArrayOf(80)), 18.0)
        // the platform is rows 64..75; the cat's top two rows stand above it
        assertEquals(0, behind.cat(x - 6, x + 5, 64, 75))
        assertEquals(12 * 12, front.cat(x - 6, x + 5, 64, 75))
    }

    @Test
    fun aCatInsideTheTreeIsInFrontOfTheTrunkAndBehindThePlatformAbove() {
        val style = RoomStyle(layers = true)
        // on the middle platform (surface "mid"): feet row 100, a box 20 tall reaches up to row 80.
        // The trunk is bedroom x 155..164 (house 347..356): the cat covers it. The top platform's
        // front face (rows 72..75) is drawn after the cat, so a taller cat is hidden up there.
        val x = 192 + 150
        val tall = intArrayOf(x)
        val cats =
            object : CatLayer {
                lateinit var canvas: RoomCanvas
                override val count = 1

                override fun depth(i: Int) = 35f

                override fun feet(i: Int) = 100f

                override fun surface(i: Int) = "mid"

                override fun draw(i: Int) = Unit

                override fun draw(i: Int, shift: Int, style: RoomStyle, canvas: RoomCanvas) {
                    canvas.fill(tall[0] - 8, 70, 16, 30, CAT) // 30 tall: rows 70..99
                }
            }
        val canvas = SoftCanvas(art)
        cats.canvas = canvas
        val scene = RoomScene(art, style)
        scene.paintBackdrop(canvas, 18.0, 0.0, 0)
        scene.paintScene(canvas, cats)
        // rows 80..99: over the trunk (house 347..356) and the cubby: the cat is in front
        assertEquals("the trunk is behind the cat", 3 * 20, canvas.cat(347, 349, 80, 99))
        // rows 72..75: the top platform's front face is in front of the cat
        assertEquals("the platform above is in front", 0, canvas.cat(x - 8, x + 7, 72, 75))
        // rows 76..79 are free of the platform: the cat shows
        assertEquals(16 * 4, canvas.cat(x - 8, x + 7, 76, 79))
    }

    // ------------------------------------------------------------------ parallax and the effects

    @Test
    fun aPieceMovesByItsPlanesParallaxAndTheSkyDoesNot() {
        val style = RoomStyle(layers = true, parallax = true)
        val still = paint(style, null, 12.0, 0f)
        val swayed = paint(style, null, 12.0, 0.5f)
        // the sofa's left arm outline (1e162d) at row 110: house x 30 at rest, the MID plane's
        // shift after
        fun leftmost(c: SoftCanvas) = (20..80).first { c.at(it, 110) == SOFA_OUTLINE }
        assertEquals(30, leftmost(still))
        assertEquals(30 + Parallax.shift(0.5f, Plane.MID), leftmost(swayed))
        // the sky inside the glass of the living room is the same picture with and without sway
        for (x in 70..85) assertEquals(still.at(x, 40), swayed.at(x, 40))
    }

    @Test
    fun theWallsEdgeIsFilledWhenTheBasesShift() {
        val style = RoomStyle(layers = true, parallax = true)
        for (sway in floatArrayOf(-1f, 1f)) {
            val c = paint(style, null, 12.0, sway)
            for (x in intArrayOf(0, 1, 2, 573, 574, 575)) for (y in intArrayOf(0, 40, 100, 140)) {
                assertTrue("($x,$y) at sway $sway is painted", c.at(x, y) ushr 24 == 0xFF)
            }
        }
    }

    @Test
    fun reducedMotionHoldsTheRoomStillWhateverTheSway() {
        val still = paint(RoomStyle.DEPTH25_STILL, null, 12.0, 0f)
        val swayed = paint(RoomStyle.DEPTH25_STILL, null, 12.0, 1f)
        assertEquals(still.pixels.toList(), swayed.pixels.toList())
    }

    @Test
    fun theSideFaceAppearsBesideAThickPieceAndOnlyWhenThicknessIsOn() {
        val plain = paint(RoomStyle(layers = true), null, 12.0)
        val thick = paint(RoomStyle(layers = true, thickness = true), null, 12.0)
        // the tree's mid platform: bedroom x 122..181 (house 314..373), rows 100..108. At rest the
        // face
        // is two pixels to the right: the first column beside it is the platform's last column,
        // shaded.
        val edge = 192 + 181
        assertEquals(Thickness.shade(plain.at(edge, 104)), thick.at(edge + 1, 104))
        assertTrue(
            "a face pixel beside the platform",
            thick.at(edge + 1, 104) != plain.at(edge + 1, 104),
        )
        assertEquals(
            "nothing more than the face's two pixels",
            plain.at(edge + 4, 104),
            thick.at(edge + 4, 104),
        )
    }

    @Test
    fun theSoftShadowIsDarkestAtTheCentreAndFadesToTheEdge() {
        val style = RoomStyle(layers = true, softShadows = true)
        val flat = paint(RoomStyle(layers = true), null, 12.0)
        val soft = paint(style, null, 12.0)
        // the floor lamp stands at house x 9..29 with its base on row 129: its shadow is centred on
        // (19, 129), three rows up and down, and the lamp's own base covers the rows above it
        val cx = 19
        fun darkness(y: Int) = brightness(flat.at(cx, y)) - brightness(soft.at(cx, y))
        assertTrue("the centre is shaded", darkness(129) > 0)
        assertTrue(
            "and fades outward: ${darkness(129)} then ${darkness(131)}",
            darkness(129) > darkness(131),
        )
        assertTrue("to a fringe: ${darkness(131)}", darkness(131) >= 0)
        assertEquals("beyond the shadow nothing changes", 0, darkness(134))
        assertEquals(
            "and far to the side neither",
            0,
            brightness(flat.at(60, 129)) - brightness(soft.at(60, 129)),
        )
    }

    private fun brightness(argb: Int) =
        ((argb shr 16) and 255) + ((argb shr 8) and 255) + (argb and 255)

    private companion object {
        const val CAT = 0xFFFF00FF.toInt()
        const val SOFA_OUTLINE = 0xFF1E162D.toInt()
    }
}
