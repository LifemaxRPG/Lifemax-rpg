package com.lifemax.rpg.feature.pet.room

import com.lifemax.rpg.feature.pet.art.PetArtResources
import com.lifemax.rpg.feature.pet.body.CatBody
import com.lifemax.rpg.feature.pet.body.HouseCat
import com.lifemax.rpg.feature.pet.body.HouseLife
import com.lifemax.rpg.feature.pet.body.PlanStep
import com.lifemax.rpg.feature.pet.body.Solids
import com.lifemax.rpg.feature.pet.body.SurfaceId
import com.lifemax.rpg.feature.pet.body.World
import kotlin.math.abs
import kotlin.math.sin
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The body's surfaces and the room's draw order agree. A real cat is walked across every surface
 * the manifest gives a depth (the sofa, the tree's two platforms, the counter, and the fridge, the
 * bookcase and the side table the parkour reaches) with the camera panning across the house, in the
 * live room (2.5D at MEDIUM with the camera-follow parallax), and in every frame:
 * - the cat is drawn after every band of the piece it stands on and in the right order against
 *   every other piece (nearer after it, farther before it), the piece that covers it last;
 * - its feet are on the piece: inside its sprite's columns, on its rows, over painted pixels, and
 *   its drawn position moves with the piece, so it does not slide on the seat. A cat on the floor,
 *   at any depth, anywhere in the house, sorts by its floor row among the pieces, so the room needs
 *   no minimum depth: cats may pass behind the sofa's arm and inside the tree.
 */
open class SurfaceWalkTest {
    /**
     * The rooms open for the run: none but the core three here, every other room in the subclass.
     */
    protected open val opened: Set<String> = emptySet()

    private val art by lazy {
        HouseMap.select(opened, force = true)
        PetArtResources.loadRoom()
    }
    private val level = DepthLevel.MEDIUM

    @After fun backToTheCoreRooms() = HouseMap.reset()

    /** A body as the room's cat layer; records the shift the painter drew it with. */
    private class BodyCats(val bodies: Array<CatBody>) : CatLayer {
        val shifts = IntArray(bodies.size)

        override val count = bodies.size

        override fun depth(i: Int) = bodies[i].pose.z

        override fun feet(i: Int) = bodies[i].pose.y

        override fun surface(i: Int): String = bodies[i].pose.surface.key

        override fun draw(i: Int) = Unit

        override fun draw(i: Int, shift: Int, style: RoomStyle, canvas: RoomCanvas) {
            shifts[i] = shift
        }
    }

    private class Walker(art: RoomArt, level: DepthLevel) {
        val scene = RoomScene(art, RoomStyle.live(level))
        val depth = scene.depth!!
        val house = HouseLife(listOf(HouseCat(3, false)), autopilot = false)
        val cats = BodyCats(house.bodies)
        val canvas = SoftCanvas(art)
        val body: CatBody
            get() = house.bodies[0]

        /** One frame at 60 Hz with the screen's middle at [screenX]. */
        fun frame(screenX: Float) {
            house.step(DT)
            depth.follow(screenX)
            depth.step(DT)
            scene.paintBackdrop(canvas, 12.0, 0.0, 0)
            scene.paintScene(canvas, cats)
        }
    }

    private fun surfaceOf(id: SurfaceId) = World.surface(id)

    private fun pieceOf(w: Walker, key: String): LayerPiece =
        w.depth.pieces.last { it.volume!!.surfaces.containsKey(key) }

    // ------------------------------------------------------------------ the agreement, by surface

    protected open val surfaces =
        listOf(
            SurfaceId.COUCH,
            SurfaceId.MID,
            SurfaceId.TOP,
            SurfaceId.COUNTER,
            SurfaceId.FRIDGE,
            SurfaceId.BOOKCASE,
            SurfaceId.TABLE,
        )

    @Test
    fun everySurfaceThatHasADepthLiesOnItsPieceAsTheBodySeesIt() {
        val w = Walker(art, level)
        for (id in surfaces) {
            val s = surfaceOf(id)
            val piece = pieceOf(w, id.key)
            val volume = piece.volume!!
            assertTrue("${id.key} depth", volume.surfaces[id.key]!! in 0f..volume.depth)
            val left = piece.houseX + RoomLayout.HOUSE_X0
            assertTrue("${id.key} walk ${s.x0}..${s.x1} on ${piece.name}", s.x0 >= left)
            assertTrue("${id.key} walk on ${piece.name}", s.x1 <= left + piece.width)
            assertTrue(
                "${id.key} row ${s.y} on ${piece.name}",
                s.y >= piece.y && s.y <= piece.y + piece.height,
            )
        }
    }

    /** The body's solid by name and the layer piece it stands where. */
    protected open val solidPieces =
        mapOf(
            "sofa" to "sofa",
            "side_table" to "side_table_plant",
            "bookcase" to "bookcase_plant",
            "fridge" to "fridge",
            "counter" to "counter",
            "cat_tree_top" to "cat_tree_slab_top",
            "cat_tree_mid" to "cat_tree_slab_mid",
        )

    @Test
    fun theBodysSolidsStandWhereTheLayersDo() {
        // what a flying cat must clear is the piece the room draws
        val w = Walker(art, level)
        val byName = solidPieces
        for (solid in Solids.all) {
            val name = byName[solid.name] ?: continue
            val piece = w.depth.solid(name)
            val left = (piece.houseX + RoomLayout.HOUSE_X0).toFloat()
            assertEquals("${solid.name} left", left, solid.x0, 6f)
            assertEquals("${solid.name} right", left + piece.width, solid.x1, 6f)
            assertTrue("${solid.name} bottom", solid.bottom <= piece.y + piece.height + 2)
        }
    }

    @Test
    fun aCatWalkedAcrossEverySurfaceKeepsItsPlaceInTheDrawOrderAndItsFeetOnThePiece() {
        for (id in surfaces) {
            val w = Walker(art, level)
            val s = surfaceOf(id)
            w.body.placeAt(s.x0, World.ART_Z, id, 1)
            w.body.replacePlan(
                1,
                listOf(PlanStep.Walk(s.x1), PlanStep.Act("loaf", 0.5f), PlanStep.Walk(s.x0)),
            )
            var frame = 0
            var checked = 0
            // the camera pans across the house while the cat walks to and fro
            while (frame < FRAMES) {
                val screenX = PanTuning.CENTRE_X + 190f * sin(frame * 0.015f)
                w.frame(screenX)
                if (frame % 3 == 0) {
                    checkOnPiece(w, id)
                    checkOrder(w, id.key)
                    checked++
                }
                frame++
            }
            assertTrue("${id.key}: the cat stayed on its surface", w.body.pose.surface == id)
            assertTrue("${id.key}: frames checked", checked > 100)
        }
    }

    /** The cat's feet are on the piece: its columns, its rows, over paint, and moving with it. */
    private fun checkOnPiece(w: Walker, id: SurfaceId) {
        val piece = pieceOf(w, id.key)
        val volume = piece.volume!!
        val pose = w.body.pose
        val where = "${id.key} x=${pose.x}"
        val column = pose.x.toInt() - RoomLayout.HOUSE_X0 - piece.houseX
        assertTrue("$where: column $column inside ${piece.name}", column in 0 until piece.width)
        assertTrue("$where: row ${pose.y}", pose.y >= piece.y && pose.y <= piece.y + piece.height)
        assertTrue(
            "$where: over painted pixels of ${piece.name}",
            paintedBelow(piece, column, pose.y),
        )
        // drawn where the piece is: the front moves with the floor, the surface lags by the
        // piece's own yaw; a cat never drifts from the seat by more than that
        val swing = w.depth.sway.shown
        val front = Parallax.shiftAtRow(swing, volume.parallaxFront, level)
        val lag = abs(w.cats.shifts[0] - front)
        val limit = Math.ceil(volume.surfaces[id.key]!! * level.depthGain * 2.0).toInt() + 1
        assertTrue("$where: shift ${w.cats.shifts[0]} against the front $front", lag <= limit)
    }

    /** Whether the piece has a painted pixel at [column] within a few rows of [row]. */
    private fun paintedBelow(piece: LayerPiece, column: Int, row: Float): Boolean {
        val layers = art.layers!!
        val rows = (row.toInt() - piece.y - 1)..(row.toInt() - piece.y + 3)
        return rows.any { y ->
            y in 0 until piece.height &&
                layers.atlas[piece.atlasX + column, piece.atlasY + y] ushr 24 != 0
        }
    }

    /**
     * The order of the last frame: bands of the cat's own piece before it, every other piece's
     * bands nearer than the cat after it and farther before it, the covering piece last.
     */
    private fun checkOrder(w: Walker, surface: String) {
        val depth = w.depth
        val order = depth.order
        val codes = IntArray(order.size) { order.order[it] }
        val at = codes.indexOfFirst { it >= DepthOrder.CAT }
        assertTrue("the cat is drawn", at >= 0)
        val own = depth.pieces.indexOfLast { it.volume!!.surfaces.containsKey(surface) }
        val volume = depth.pieces[own].volume!!
        val cover =
            volume.covered[surface]?.let { name -> depth.pieces.indexOfFirst { it.name == name } }
        val catKey = volume.sortFront - volume.surfaces[surface]!!
        for (k in codes.indices) {
            val code = codes[k]
            if (code >= VolumeStep.FURNITURE) continue
            val piece = depth.bandPiece(code)
            val key = depth.bandKey(code)
            val where = "$surface: ${depth.pieces[piece].name} band at row $key, slot $k, cat $at"
            when {
                piece == cover -> assertTrue("$where: the cover is over the cat", k > at)
                piece == own -> assertTrue("$where: the piece's own bands first", k < at)
                key > catKey + DepthOrder.CAT_AFTER -> assertTrue("$where: nearer, after", k > at)
                else -> assertTrue("$where: farther, before", k < at)
            }
        }
    }

    // ------------------------------------------------------------------ the floor, at any depth

    @Test
    fun aCatOnTheFloorAtAnyDepthAnywhereIsSortedByItsFloorRowAmongThePieces() {
        val w = Walker(art, level)
        for (z in intArrayOf(0, 8, 15, 25, 35, 45, 60, 80, 100)) {
            for (x in -150..340 step 30) {
                w.body.placeAt(x.toFloat(), z.toFloat(), SurfaceId.FLOOR, 1)
                w.frame(PanTuning.CENTRE_X + x * 0.4f)
                val key = World.floorY(z.toFloat())
                val depth = w.depth
                val codes = IntArray(depth.order.size) { depth.order.order[it] }
                val at = codes.indexOfFirst { it >= DepthOrder.CAT }
                for (k in codes.indices) {
                    val code = codes[k]
                    if (code >= VolumeStep.FURNITURE) continue
                    val band = depth.bandKey(code)
                    val farther = band <= key + DepthOrder.CAT_AFTER
                    assertEquals("z=$z x=$x band row $band, cat row $key", farther, k < at)
                }
            }
        }
    }

    @Test
    fun aCatWalkedAlongTheFloorStaysInFrontOfTheSofaAndTheCounterAtTheArtDepth() {
        // the autopilot's depth: the floor row 132 is in front of the sofa, counter and fridge
        // (base 128) and behind the cat tree's base (137), whose near rows are drawn over its feet
        val w = Walker(art, level)
        val key = World.floorY(World.ART_Z)
        assertEquals(132f, key, 0.01f)
        w.body.placeAt(-154f, World.ART_Z, SurfaceId.FLOOR, 1)
        w.body.replacePlan(1, listOf(PlanStep.Walk(340f), PlanStep.Walk(-154f)))
        var frame = 0
        var inFrontOfSofa = 0
        while (frame < FRAMES) {
            w.frame(PanTuning.CENTRE_X + 190f * sin(frame * 0.015f))
            val depth = w.depth
            val codes = IntArray(depth.order.size) { depth.order.order[it] }
            val at = codes.indexOfFirst { it >= DepthOrder.CAT }
            val sofa = depth.pieces.indexOfFirst { it.name == "sofa" }
            val bands = codes.filter { it < VolumeStep.FURNITURE && depth.bandPiece(it) == sofa }
            if (bands.isNotEmpty()) {
                assertTrue(
                    "every band of the sofa is behind the cat",
                    bands.all { codes.indexOf(it) < at },
                )
                inFrontOfSofa++
            }
            frame++
        }
        assertTrue(inFrontOfSofa == frame)
        assertFalse(w.depth.pieces.isEmpty())
    }

    private companion object {
        const val DT = 1f / 60f
        const val FRAMES = 900
    }
}
