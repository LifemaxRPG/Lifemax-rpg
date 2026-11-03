package com.lifemax.rpg.feature.pet.room

import com.lifemax.rpg.feature.pet.art.PetArtResources
import com.lifemax.rpg.feature.pet.body.SurfaceId
import com.lifemax.rpg.feature.pet.body.World
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The solid furniture: every piece rendered at rest is its painted sprite pixel for pixel (the
 * fidelity promise), a sway shows sides drawn only in the piece's own colours, the camera steps are
 * quantised and held, the bands of a step redraw the painted room at rest, and the cats' surfaces
 * agree with the body's world.
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

    // ------------------------------------------------------------------ the camera steps

    @Test
    fun theSwayIsQuantisedToStepsAndHeldAtABoundary() {
        val sheets = VolumeSheets(solids, FloatArray(0), DepthLevel.MEDIUM, 4)
        assertEquals(9, sheets.count)
        assertEquals(4, sheets.indexOf(0f, -1))
        assertEquals(8, sheets.indexOf(1f, -1))
        assertEquals(0, sheets.indexOf(-1f, -1))
        assertEquals(-1f, sheets.swayOf(0), 0f)
        assertEquals(0.5f, sheets.swayOf(6), 0f)
        // just past the boundary between steps 4 and 5 (sway 0.125) the step held is kept
        assertEquals(4, sheets.indexOf(0.13f, 4))
        assertEquals(5, sheets.indexOf(0.13f, -1))
        // and taken once the sway is clearly past it
        assertEquals(5, sheets.indexOf(0.17f, 4))
        // back the other way, the same
        assertEquals(5, sheets.indexOf(0.12f, 5))
        assertEquals(4, sheets.indexOf(0.08f, 5))
    }

    @Test
    fun theStepsDoNotDependOnTheFrameRate() {
        fun stepsSeen(hz: Int): List<Int> {
            val art = PetArtResources.loadRoom()
            val scene = RoomScene(art, RoomStyle.depth25(DepthLevel.RICH))
            val depth = scene.depth!!
            depth.sway.tilt = 0.7f
            val seen = ArrayList<Int>()
            val canvas = SoftCanvas(art)
            for (second in 1..6) {
                repeat(hz) { depth.step(1f / hz) }
                depth.paintScene(canvas, NoCats)
                seen.add(depth.stepIndex)
            }
            return seen
        }
        assertEquals(stepsSeen(30), stepsSeen(60))
        assertEquals(stepsSeen(60), stepsSeen(144))
    }

    @Test
    fun reducedMotionRendersOnlyTheRest() {
        val scene = RoomScene(PetArtResources.loadRoom(), RoomStyle.DEPTH25_STILL)
        val depth = scene.depth!!
        assertEquals(1, depth.sheets.count)
        repeat(100) { depth.step(1f / 60f) }
        assertEquals(depth.sheets.rest, depth.stepIndex)
    }

    @Test
    fun theBandsOfTheRestStepRedrawThePaintedRoom() {
        // the pieces' bands drawn in the step's order over the bases are the backdrops
        val art = PetArtResources.loadRoom()
        val sheets = VolumeSheets(solids, FloatArray(0), DepthLevel.RICH, 0)
        val step = sheets.step(0)
        val house = IntArray(RoomLayout.HOUSE_WIDTH * RoomLayout.HEIGHT)
        for (room in 0..2) for (y in 0 until RoomLayout.HEIGHT) for (x in
            0 until RoomLayout.BACKDROP_WIDTH) {
            house[y * RoomLayout.HOUSE_WIDTH + room * RoomLayout.BACKDROP_WIDTH + x] =
                layers.base(room)[x, y]
        }
        for (p in layers.pieces.filter { it.flat }) paste(house, layers, p)
        for (code in step.order) {
            val piece = solids[step.piece[code]]
            for (y in 0 until step.h[code]) for (x in 0 until step.w[code]) {
                val c = step.image[step.sx[code] + x, step.sy[code] + y]
                if (c ushr 24 == 0) continue
                house[
                    (piece.y + step.dy[code] + y) * RoomLayout.HOUSE_WIDTH +
                        piece.houseX +
                        step.dx[code] +
                        x] = c
            }
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
        val sheets = VolumeSheets(solids, FloatArray(0), DepthLevel.RICH, 3)
        val order = DepthOrder(sheets.capacity)
        val sofa = solids.indexOfFirst { it.name == "sofa" }
        val pink = solids.indexOfFirst { it.name == "sofa_cushion_pink" }
        val couch = solids[sofa].volume!!
        for (i in 0 until sheets.count) {
            val step = sheets.step(i)
            order.build(
                step,
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
                if (step.piece[code] == sofa) assertTrue("sofa band after the cat", k < at)
                if (step.piece[code] == pink && step.band[code] == 0)
                    assertTrue("the cushion's front before the cat", k > at)
            }
            assertEquals("every band once", step.count + 1, order.size)
        }
    }

    @Test
    fun twoCatsInTheTreeHaveTheTopPlatformsFrontBetweenThem() {
        val sheets = VolumeSheets(solids, FloatArray(0), DepthLevel.RICH, 3)
        val order = DepthOrder(sheets.capacity)
        val mid = solids.indexOfFirst { it.name == "cat_tree_slab_mid" }
        val top = solids.indexOfFirst { it.name == "cat_tree_slab_top" }
        fun keyOn(p: Int, s: String) = solids[p].volume!!.let { it.sortFront - it.surfaces[s]!! }
        for (i in 0 until sheets.count) {
            val step = sheets.step(i)
            // cat 0 on the top platform, cat 1 on the middle one
            // (the manifest says the top platform covers a cat on the middle one)
            assertEquals("cat_tree_slab_top", solids[mid].volume!!.covered["mid"])
            order.build(
                step,
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
                codes.indices.filter { codes[it] < DepthOrder.CAT && step.piece[codes[it]] == top }
            assertTrue(
                "step $i: the whole top platform between the cats",
                topBands.all { it in (lower + 1) until upper },
            )
            assertEquals("every band once", step.count + 2, order.size)
        }
    }

    @Test
    fun aCatOnTheFloorIsBetweenTheBandsNearerAndFartherThanIt() {
        val sheets = VolumeSheets(solids, FloatArray(0), DepthLevel.RICH, 3)
        val order = DepthOrder(sheets.capacity)
        val step = sheets.step(0) // swayed: sides in many bands
        val key = 123.2f
        order.build(step, FloatArray(0), 1, floatArrayOf(key), floatArrayOf(key), intArrayOf(-1))
        val at = (0 until order.size).first { order.order[it] == DepthOrder.CAT }
        for (k in 0 until order.size) {
            val code = order.order[k]
            if (code >= DepthOrder.CAT) continue
            assertEquals("band key ${step.key[code]}", step.key[code] > key, k > at)
        }
    }
}
