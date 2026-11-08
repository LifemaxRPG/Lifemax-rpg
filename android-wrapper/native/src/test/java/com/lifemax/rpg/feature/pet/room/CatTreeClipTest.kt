package com.lifemax.rpg.feature.pet.room

import com.lifemax.rpg.feature.pet.art.PetArtResources
import com.lifemax.rpg.feature.pet.body.Occluders
import com.lifemax.rpg.feature.pet.body.World
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The owner's "the cat's ears are poking through the cat tree", on a software canvas with stand-in
 * cats (magenta boxes the size of a cat sitting up): at the cats' own floor depth the foot of the
 * tree is under its middle platform, and the platform's front was drawn across a cat sitting there
 * with its ears above it. Now a cat that does not fit under a piece it stands inside is drawn in
 * front of every piece it stands inside, at every camera step; a cat behind the tree, beside it or
 * up on its platform is drawn as before.
 */
class CatTreeClipTest {
    private val art = PetArtResources.loadRoom()
    private val style = RoomStyle.live(DepthLevel.RICH)

    /** Boxes [w] x [h] (at full scale) with the feet at world [x], depth [z]. */
    private class Cats(
        private val x: FloatArray,
        private val z: FloatArray,
        private val w: Int,
        private val h: Int,
        private val surface: Array<String?> = arrayOfNulls(x.size),
        private val feetRow: FloatArray = FloatArray(x.size) { World.floorY(z[it]) },
        private val knowsX: Boolean = true,
    ) : CatLayer {
        lateinit var canvas: RoomCanvas
        override val count = x.size

        override fun depth(i: Int) = z[i]

        override fun feet(i: Int) = feetRow[i]

        override fun x(i: Int) = if (knowsX) x[i] else Float.NaN

        override fun surface(i: Int) = surface[i]

        override fun draw(i: Int) = draw(i, 0, RoomStyle.CLASSIC, canvas)

        override fun draw(i: Int, shift: Int, style: RoomStyle, canvas: RoomCanvas) {
            val k = World.depthScale(z[i])
            val bw = Math.round(w * k)
            val bh = Math.round(h * k)
            val house = Math.round(x[i] - RoomLayout.HOUSE_X0)
            val feet = Math.round(feetRow[i])
            canvas.fill(house - bw / 2 + shift, feet - bh, bw, bh, colour(i))
        }

        fun colour(i: Int) = if (i == 0) CAT else OTHER
    }

    private fun paint(cats: Cats, sway: Float): Pair<SoftCanvas, DepthScene> {
        val scene = RoomScene(art, style)
        val depth = scene.depth!!
        depth.sway.restAt(sway)
        val canvas = SoftCanvas(art)
        cats.canvas = canvas
        scene.paintBackdrop(canvas, HOUR, 0.0, 0)
        scene.paintScene(canvas, cats)
        return canvas to depth
    }

    /** The cat's pixels above [HEAD_ROW] (its head and back: the bowls stand lower). */
    private fun SoftCanvas.head(colour: Int): Int {
        var n = 0
        for (y in 0 until HEAD_ROW) for (x in 0 until width) if (pixels[y * width + x] == colour)
            n++
        return n
    }

    /** What [head] counts of a box [w] x [h] standing at depth [z] with nothing over it. */
    private fun whole(z: Float, w: Int, h: Int): Int {
        val k = World.depthScale(z)
        val top = Math.round(World.floorY(z)) - Math.round(h * k)
        return Math.round(w * k) * (HEAD_ROW - top)
    }

    /** Where in the drawing order cat [cat] is, and the last band of the piece [name]. */
    private fun DepthScene.catAt(cat: Int): Int =
        (0 until order.size).first { order.order[it] == DepthOrder.CAT + cat }

    private fun DepthScene.lastBandOf(name: String): Int {
        val p = pieces.indexOf(solid(name))
        return (0 until order.size).last {
            val c = order.order[it]
            c < VolumeStep.FURNITURE && bandPiece(c) == p
        }
    }

    @Test
    fun aKittenAndAGrownCatSittingUpAtTheTreeFootShowWholeAtEveryCameraStep() {
        for (h in intArrayOf(KITTEN_H, Occluders.CAT_UPRIGHT_H.toInt())) {
            for (sway in floatArrayOf(-1f, -0.5f, 0f, 0.5f, 1f)) {
                val cats = Cats(floatArrayOf(OLD_SCRATCH_X), floatArrayOf(World.ART_Z), W, h)
                val (canvas, depth) = paint(cats, sway)
                assertEquals(
                    "height $h sway $sway: nothing of the tree over the cat",
                    whole(World.ART_Z, W, h),
                    canvas.head(CAT),
                )
                for (name in TREE) {
                    assertTrue(
                        "height $h sway $sway: $name is drawn before the cat",
                        depth.lastBandOf(name) < depth.catAt(0),
                    )
                }
            }
        }
    }

    @Test
    fun withoutTheFixTheMiddlePlatformCutAcrossTheCat() {
        // a layer that does not say where its cats are gets the order of before: the platform's
        // front is drawn over the cat and only its ears show above it
        val cats =
            Cats(
                floatArrayOf(OLD_SCRATCH_X),
                floatArrayOf(World.ART_Z),
                W,
                KITTEN_H,
                knowsX = false,
            )
        val (canvas, _) = paint(cats, 0f)
        val shown = canvas.head(CAT)
        assertTrue(
            "the platform hid part of the cat: $shown",
            shown < whole(World.ART_Z, W, KITTEN_H),
        )
        assertTrue("and its ears still show above it", shown > 0)
    }

    @Test
    fun aCatBehindTheTreeIsStillHiddenByThePlatformsFront() {
        // feet on row 121, behind the tree's footprint (rows 125..137): a cat behind a table
        val z = 10f
        val behind = Cats(floatArrayOf(OLD_SCRATCH_X + 20f), floatArrayOf(z), W, KITTEN_H)
        val (canvas, depth) = paint(behind, 0f)
        assertTrue(depth.lastBandOf("cat_tree_slab_mid") > depth.catAt(0))
        assertTrue(canvas.head(CAT) < whole(z, W, KITTEN_H))
    }

    @Test
    fun aCatBesideTheTreeIsDrawnAsBefore() {
        // clear of the tree's pieces either side: the order is the order of a layer without x
        for (x in floatArrayOf(60f, 230f)) {
            val with = paint(Cats(floatArrayOf(x), floatArrayOf(World.ART_Z), W, KITTEN_H), 1f)
            val without =
                paint(
                    Cats(floatArrayOf(x), floatArrayOf(World.ART_Z), W, KITTEN_H, knowsX = false),
                    1f,
                )
            assertEquals("x $x", without.first.pixels.toList(), with.first.pixels.toList())
        }
    }

    @Test
    fun aCatOnTheMiddlePlatformStaysUnderTheTopOneWhileTheOtherSitsInFrontOfTheTree() {
        // cat 1 up on the middle platform (feet row 100), cat 0 on the floor under it
        val cats =
            Cats(
                floatArrayOf(OLD_SCRATCH_X, MID_X),
                floatArrayOf(World.ART_Z, World.ART_Z),
                W,
                KITTEN_H,
                arrayOf(null, "mid"),
                floatArrayOf(World.floorY(World.ART_Z), MID_FEET),
            )
        for (sway in floatArrayOf(-1f, 0f, 1f)) {
            val (_, depth) = paint(cats, sway)
            val onMid = depth.catAt(1)
            val onFloor = depth.catAt(0)
            assertTrue(
                "sway $sway: the top platform covers the cat on the middle one",
                depth.lastBandOf("cat_tree_slab_top") > onMid,
            )
            assertTrue(
                "sway $sway: the cat on the floor is in front of it all",
                onFloor > depth.lastBandOf("cat_tree_slab_top") && onFloor > onMid,
            )
        }
    }

    private companion object {
        const val CAT = 0xFFFF00FF.toInt()
        const val OTHER = 0xFF00FFFF.toInt()
        const val HOUR = 18.0

        /** Where the brain scratched the post before: under the middle platform. */
        const val OLD_SCRATCH_X = 134f
        const val MID_X = 152f
        const val MID_FEET = 100f

        /** A cat sitting up is about this wide; a kitten sitting up this tall (full scale). */
        const val W = 24
        const val KITTEN_H = 34

        /** Rows above this are the cat's head and back, clear of the bowls. */
        const val HEAD_ROW = 118

        val TREE =
            listOf( // the pieces over the cat's x (the cubby stands right of it)
                "cat_tree_post",
                "cat_tree_slab_base",
                "cat_tree_slab_mid",
                "cat_tree_slab_top",
            )
    }
}
