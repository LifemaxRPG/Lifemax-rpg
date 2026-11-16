package com.lifemax.rpg.feature.pet.room

import com.lifemax.rpg.feature.pet.art.PetArtResources
import com.lifemax.rpg.feature.pet.body.SurfaceId
import com.lifemax.rpg.feature.pet.body.World
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The solid furniture: every piece ray cast at rest is its painted sprite pixel for pixel (the
 * fidelity promise of the reference renderer, [VolumeRaster]), a sway shows sides drawn only in the
 * piece's own colours, the yaw does not depend on the frame rate, the faces' bands at rest redraw
 * the painted room, the cats go between the bands, and the cats' surfaces agree with the body's
 * world.
 */
class VolumeTest {
    private val layers = PetArtResources.loadRoom().layers!!
    private val solids = layers.pieces.filter { it.volume != null }

    private fun render(piece: LayerPiece, vx: Float, vy: Float): VolumeFrame {
        val model = piece.volume!!.model
        val frame = VolumeFrame(VolumeRaster.size(model, 1f, 1f))
        VolumeRaster.render(model, vx, vy, piece.volume!!.wall, frame)
        return frame
    }

    // ------------------------------------------------------------------ the fidelity promise

    @Test
    fun atRestEveryPieceIsItsPaintedSpriteExactly() {
        assertEquals(13, solids.size)
        for (piece in solids) {
            val model = piece.volume!!.model
            val frame = render(piece, 0f, 0f)
            assertEquals(piece.name, 0 to 0, frame.x0 to frame.y0)
            assertEquals(piece.name, model.width to model.height, frame.width to frame.height)
            for (i in 0 until model.width * model.height) {
                assertEquals("${piece.name} pixel $i", model.colours[i], frame.colour[i])
                if (model.solid(i)) assertEquals(Face.FRONT, frame.face[i])
            }
        }
    }

    @Test
    fun aSwayShowsSidesButLeavesEveryVisibleFrontAsPainted() {
        for (piece in solids) for (vx in floatArrayOf(-0.36f, 0.36f)) {
            val model = piece.volume!!.model
            val frame = render(piece, vx, -0.22f)
            var revealed = 0
            for (at in 0 until frame.width * frame.height) {
                if (frame.face[at] == Face.FRONT) {
                    assertEquals(piece.name, model.colours[frame.cell[at]], frame.colour[at])
                } else if (frame.revealed(at)) {
                    revealed++
                }
            }
            assertTrue("${piece.name} at $vx shows some side", revealed > 0)
        }
    }

    @Test
    fun theShadesStayInsideEachPiecesOwnPalette() {
        for (piece in solids) {
            val palette = piece.volume!!.model.palette.colours.toSet()
            for (vx in floatArrayOf(-0.36f, -0.12f, 0.2f, 0.36f)) {
                val frame = render(piece, vx, -0.2f)
                for (at in 0 until frame.width * frame.height) {
                    val c = frame.colour[at]
                    if (c != 0) assertTrue("${piece.name}: %08x".format(c), c in palette)
                }
            }
        }
    }

    @Test
    fun theSofasSidesAreItsOwnBluesTheLitOneLighterAndNoBlackSmear() {
        val sofa = solids.first { it.name == "sofa" }
        val left = render(sofa, -0.36f, 0f) // the camera moved left: the left sides show
        val right = render(sofa, 0.36f, 0f)
        fun sideColours(f: VolumeFrame, face: Byte) =
            (0 until f.width * f.height).filter { f.face[it] == face }.map { f.colour[it] }
        val lit = sideColours(left, Face.LEFT)
        val shaded = sideColours(right, Face.RIGHT)
        assertTrue(lit.isNotEmpty() && shaded.isNotEmpty())
        val ink = RampTuning.ART_INK
        // most of a side is the blue it is made of, not the outline
        assertTrue(
            "lit side: ${lit.count { it == ink }} of ${lit.size} ink",
            lit.count { it == ink } * 2 < lit.size,
        )
        assertTrue(shaded.count { it == ink } * 2 < shaded.size)
        val mean = { l: List<Int> -> l.filter { it != ink }.map(Palette::luminanceOf).average() }
        assertTrue("the lit side is not darker", mean(lit) >= mean(shaded))
    }

    @Test
    fun aRampStepsDownAndUpWithinItsOwnHue() {
        val p =
            Palette(
                intArrayOf(
                    0xFF486298.toInt(),
                    0xFF6280BA.toInt(),
                    0xFF324470.toInt(),
                    RampTuning.ART_INK,
                    0xFF50341E.toInt(),
                )
            )
        val mid = p.indexOf(0xFF486298.toInt())
        assertEquals(0xFF324470.toInt(), p.colours[p.step(mid, -1)])
        assertEquals(0xFF324470.toInt(), p.colours[p.step(mid, -2)]) // the darkest blue, not ink
        assertEquals(0xFF6280BA.toInt(), p.colours[p.step(mid, 1)])
        assertEquals(RampTuning.ART_INK, p.colours[p.ink])
        val leg = p.indexOf(0xFF50341E.toInt())
        assertEquals("a brown never turns blue", 0xFF50341E.toInt(), p.colours[p.step(leg, -1)])
    }

    // ------------------------------------------------------------------ the faces in the room

    @Test
    fun theYawDoesNotDependOnTheFrameRate() {
        fun yawsSeen(hz: Int): List<Int> {
            val art = PetArtResources.loadRoom()
            val scene = RoomScene(art, RoomStyle.depth25(DepthLevel.RICH))
            val depth = scene.depth!!
            depth.sway.tilt = 0.7f
            val seen = ArrayList<Int>()
            val canvas = CountingCanvas()
            for (second in 1..6) {
                repeat(hz) { depth.step(1f / hz) }
                depth.paintScene(canvas, NoCats)
                seen.add(Math.round(depth.yawOf("sofa") * 100f))
            }
            return seen
        }
        assertEquals(yawsSeen(30), yawsSeen(60))
        assertEquals(yawsSeen(60), yawsSeen(144))
    }

    @Test
    fun reducedMotionDrawsOnlyTheRest() {
        val scene = RoomScene(PetArtResources.loadRoom(), RoomStyle.DEPTH25_STILL)
        val depth = scene.depth!!
        repeat(100) { depth.step(1f / 60f) }
        depth.paintScene(CountingCanvas(), NoCats)
        for (piece in depth.pieces) assertEquals(piece.name, 0f, depth.yawOf(piece.name), 0f)
    }

    /** A canvas of house pixels that draws only the strips, opaque, where they are asked. */
    private class Paste(val house: IntArray, var x: Int = 0, var y: Int = 0) :
        RoomCanvas by CountingCanvas() {
        override fun stack(
            image: com.lifemax.rpg.feature.pet.art.ArgbImage,
            slot: Int,
            version: Int,
            sx: Int,
            sy: Int,
            sw: Int,
            sh: Int,
            dx: Float,
            dy: Float,
        ) {
            for (j in 0 until sh) for (i in 0 until sw) {
                val c = image[sx + i, sy + j]
                if (c ushr 24 == 0) continue
                val hx = x + Math.round(dx) + i
                val hy = y + Math.round(dy) + j
                house[hy * RoomLayout.HOUSE_WIDTH + hx] = c
            }
        }
    }

    @Test
    fun theBandsAtRestRedrawThePaintedRoom() {
        // the pieces' bands drawn in their order over the bases, at rest, are the backdrops
        val art = PetArtResources.loadRoom()
        val bands = SolidBands(solids, FloatArray(0))
        val house = IntArray(RoomLayout.HOUSE_WIDTH * RoomLayout.HEIGHT)
        for (room in 0..2) for (y in 0 until RoomLayout.HEIGHT) for (x in
            0 until RoomLayout.BACKDROP_WIDTH) {
            house[y * RoomLayout.HOUSE_WIDTH + room * RoomLayout.BACKDROP_WIDTH + x] =
                layers.base(room)[x, y]
        }
        for (p in layers.pieces.filter { it.flat }) paste(house, layers, p)
        val canvas = Paste(house)
        for (o in 0 until bands.orderSize) {
            val code = bands.order[o]
            val piece = solids[bands.piece[code]]
            canvas.x = piece.houseX
            canvas.y = piece.y
            piece.volume!!.faces.paintBand(canvas, 0, bands.band[code], 0f, 0f, 0f, 0f)
        }
        val backdrops =
            listOf(RoomImage.LIVING, RoomImage.BEDROOM, RoomImage.KITCHEN).map { art.image(it) }
        for (room in 0..2) for (y in 0 until RoomLayout.HEIGHT) for (x in
            0 until RoomLayout.BACKDROP_WIDTH) {
            assertEquals(
                "room $room ($x,$y)",
                backdrops[room][x, y],
                house[y * RoomLayout.HOUSE_WIDTH + room * RoomLayout.BACKDROP_WIDTH + x],
            )
        }
    }

    private fun paste(house: IntArray, layers: RoomLayerArt, p: LayerPiece) {
        for (y in 0 until p.height) for (x in 0 until p.width) {
            val c = layers.atlas[p.atlasX + x, p.atlasY + y]
            if (c ushr 24 != 0) house[(p.y + y) * RoomLayout.HOUSE_WIDTH + p.houseX + x] = c
        }
    }

    // ------------------------------------------------------------------ the cats' surfaces

    @Test
    fun theSurfacesTheCatsStandOnLieOnTheirPiecesAsTheBodySeesThem() {
        val expected =
            mapOf(
                "couch" to "sofa",
                "mid" to "cat_tree_slab_mid",
                "top" to "cat_tree_slab_top",
                "counter" to "counter",
            )
        for ((key, name) in expected) {
            val piece = layers.piece(name)
            val volume = piece.volume!!
            val depth = volume.surfaces[key] ?: error("$name has no depth for $key")
            assertTrue("$key lies inside $name", depth in 0f..volume.depth)
            val surface = World.surface(SurfaceId.entries.first { it.key == key })
            // the body's feet row lies on the piece, and its walk lies across it
            assertTrue(
                "$key row ${surface.y} on $name",
                surface.y >= piece.y && surface.y < piece.y + piece.height,
            )
            val x0 = surface.x0 - RoomLayout.HOUSE_X0
            val x1 = surface.x1 - RoomLayout.HOUSE_X0
            assertTrue(
                "$key walk $x0..$x1 on $name",
                x0 >= piece.houseX && x1 < piece.houseX + piece.width,
            )
        }
    }

    @Test
    fun aCatOnTheSofaIsDrawnAfterEveryBandOfTheSofaAndBeforeThePinkCushion() {
        val bands = SolidBands(solids, FloatArray(0))
        val order = DepthOrder(bands.count)
        val sofa = solids.indexOfFirst { it.name == "sofa" }
        val pink = solids.indexOfFirst { it.name == "sofa_cushion_pink" }
        val couch = solids[sofa].volume!!
        order.build(
            bands,
            FloatArray(0),
            1,
            floatArrayOf(couch.sortFront - couch.surfaces["couch"]!!),
            floatArrayOf(109f),
            intArrayOf(sofa),
        )
        val at = (0 until order.size).first { order.order[it] == DepthOrder.CAT }
        for (k in 0 until order.size) {
            val code = order.order[k]
            if (code >= DepthOrder.CAT) continue
            if (bands.piece[code] == sofa) assertTrue("sofa band after the cat", k < at)
            if (bands.piece[code] == pink && bands.band[code] == 0)
                assertTrue("the cushion's front before the cat", k > at)
        }
        assertEquals("every band once", bands.count + 1, order.size)
    }

    @Test
    fun twoCatsInTheTreeHaveTheTopPlatformsFrontBetweenThem() {
        val bands = SolidBands(solids, FloatArray(0))
        val order = DepthOrder(bands.count)
        val mid = solids.indexOfFirst { it.name == "cat_tree_slab_mid" }
        val top = solids.indexOfFirst { it.name == "cat_tree_slab_top" }
        fun keyOn(p: Int, s: String) = solids[p].volume!!.let { it.sortFront - it.surfaces[s]!! }
        // cat 0 on the top platform, cat 1 on the middle one
        // (the manifest says the top platform covers a cat on the middle one)
        assertEquals("cat_tree_slab_top", solids[mid].volume!!.covered["mid"])
        order.build(
            bands,
            FloatArray(0),
            2,
            floatArrayOf(keyOn(top, "top"), keyOn(mid, "mid")),
            floatArrayOf(64f, 100f),
            intArrayOf(top, mid),
            intArrayOf(-1, top),
        )
        val codes = (0 until order.size).map { order.order[it] }
        val upper = codes.indexOf(DepthOrder.CAT)
        val lower = codes.indexOf(DepthOrder.CAT + 1)
        val topBands =
            codes.indices.filter { codes[it] < DepthOrder.CAT && bands.piece[codes[it]] == top }
        assertTrue(
            "the whole top platform between the cats",
            topBands.all { it in (lower + 1) until upper },
        )
        assertEquals("every band once", bands.count + 2, order.size)
    }

    @Test
    fun aCatOnTheFloorIsBetweenTheBandsNearerAndFartherThanIt() {
        val bands = SolidBands(solids, FloatArray(0))
        val order = DepthOrder(bands.count)
        val key = 123.2f
        order.build(bands, FloatArray(0), 1, floatArrayOf(key), floatArrayOf(key), intArrayOf(-1))
        val at = (0 until order.size).first { order.order[it] == DepthOrder.CAT }
        for (k in 0 until order.size) {
            val code = order.order[k]
            if (code >= DepthOrder.CAT) continue
            assertEquals("band key ${bands.key[code]}", bands.key[code] > key, k > at)
        }
    }
}
