package com.lifemax.rpg.feature.pet.room

import com.lifemax.rpg.feature.pet.art.ArgbImage
import com.lifemax.rpg.feature.pet.art.PetArtResources
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The furniture layers cut out of the three backdrops (`tools/pet_room_cut.js`): drawn over their
 * base in order they give the original backdrop back pixel for pixel, the manifest says what the
 * page's `PET_Z_BLOCK` says, and the atlas holds the pieces' own pixels.
 */
class RoomLayersTest {
    private val art = PetArtResources.loadRoom()
    private val layers = art.layers!!
    private val originals =
        listOf(RoomImage.LIVING, RoomImage.BEDROOM, RoomImage.KITCHEN).map { art.image(it) }

    /** The pixels of [piece] out of the atlas. */
    private fun sprite(piece: LayerPiece): ArgbImage {
        val out = IntArray(piece.width * piece.height)
        for (y in 0 until piece.height) for (x in 0 until piece.width) {
            out[y * piece.width + x] = layers.atlas[piece.atlasX + x, piece.atlasY + y]
        }
        return ArgbImage(piece.width, piece.height, out)
    }

    /** The base of [room] with its pieces drawn over it in order. */
    private fun reassemble(room: Int): IntArray {
        val out = layers.base(room).pixels.copyOf()
        val w = RoomLayout.BACKDROP_WIDTH
        for (piece in layers.pieces.filter { it.room == room }) {
            val s = sprite(piece)
            for (y in 0 until s.height) for (x in 0 until s.width) {
                val p = s[x, y]
                if (p ushr 24 != 0) out[(piece.y + y) * w + piece.x + x] = p
            }
        }
        return out
    }

    @Test
    fun theBaseWithItsPiecesIsTheBackdropExactly() {
        for (room in 0..2) {
            val got = reassemble(room)
            val want = originals[room].pixels
            var first = ""
            var wrong = 0
            for (i in want.indices) {
                if (got[i] != want[i]) {
                    wrong++
                    if (first.isEmpty()) first = "(${i % 192},${i / 192})"
                }
            }
            assertEquals("room $room: $wrong pixels differ, first at $first", 0, wrong)
        }
    }

    @Test
    fun theFurnitureIsReallyGoneFromTheBase() {
        // under a piece the base holds something else: the wall or the floor, never the piece
        for (piece in layers.pieces) {
            val s = sprite(piece)
            var opaque = 0
            var same = 0
            for (y in 0 until s.height) for (x in 0 until s.width) {
                if (s[x, y] ushr 24 == 0) continue
                opaque++
                if (layers.base(piece.room)[piece.x + x, piece.y + y] == s[x, y]) same++
            }
            assertTrue("${piece.name} has pixels", opaque > 0)
            // a few pixels of a piece happen to be the colour of the wall behind; most are not
            assertTrue(
                "${piece.name}: $same of $opaque pixels still in the base",
                same * 4 < opaque,
            )
        }
    }

    @Test
    fun theWindowsStayClearInTheBases() {
        // the glass of the living room's window is house x 62..125, the bedroom's 22..85
        for ((room, x) in listOf(0 to 80, 1 to 40)) {
            val glass = layers.base(room)[x, RoomLayout.GLASS_Y + 20]
            assertEquals("the glass of room $room is see-through", 0, glass ushr 24)
        }
    }

    @Test
    fun theManifestHasTheWebsBasesAndTheExpectedPieces() {
        val base = layers.pieces.associate { it.name to it.baseY }
        // PET_Z_BLOCK, 24-pet-depth.js: the row of the lowest pixel of each painted piece
        assertEquals(128, base["sofa"])
        assertEquals(129, base["floor_lamp"])
        assertEquals(126, base["side_table_plant"])
        assertEquals(137, base["cat_tree_slab_base"])
        assertEquals(128, base["fridge"])
        assertEquals(128, base["counter"])
        assertEquals(null, base["rug_living"])
        assertEquals(null, base["rug_bedroom"])
        assertEquals(
            listOf(
                "rug_living",
                "sofa",
                "sofa_cushion_yellow",
                "sofa_cushion_pink",
                "floor_lamp",
                "side_table_plant",
                "rug_bedroom",
                "bookcase_plant",
                "cat_tree_post",
                "cat_tree_cubby",
                "cat_tree_slab_base",
                "cat_tree_slab_mid",
                "cat_tree_slab_top",
                "counter",
                "fridge",
            ),
            layers.pieces.map { it.name },
        )
    }

    @Test
    fun theSurfacesAreOnThePiecesThatProvideThem() {
        assertEquals(listOf("sofa"), layers.providing("couch").map { it.name })
        assertEquals(listOf("cat_tree_slab_mid"), layers.providing("mid").map { it.name })
        assertEquals(listOf("cat_tree_slab_top"), layers.providing("top").map { it.name })
        assertEquals(listOf("counter"), layers.providing("counter").map { it.name })
    }

    @Test
    fun aPiecesDepthIsTheDepthOfItsBaseRow() {
        val sofa = layers.piece("sofa")
        // petZFromY(128) = ((128 - 98) / 34 * 100 - 55) * 100 / 130
        assertEquals(25.57f, sofa.z, 0.01f)
        assertEquals(DepthMath.zFromRow(137f), layers.piece("cat_tree_post").z, 1e-4f)
        assertEquals(LayerPieceFlat, layers.piece("rug_living").z, 0f)
        // a cat at the art depth (35) is in front of every standing piece but the tree
        for (p in layers.pieces) if (!p.flat && !p.name.startsWith("cat_tree"))
            assertTrue(p.name, p.z < 35f)
    }

    @Test
    fun everyPieceLiesInsideItsRoomAndInItsPlane() {
        for (p in layers.pieces) {
            assertTrue(p.name, p.x >= 0 && p.y >= 0)
            assertTrue(p.name, p.x + p.width <= RoomLayout.BACKDROP_WIDTH)
            assertTrue(p.name, p.y + p.height <= RoomLayout.HEIGHT)
            assertNotNull(p.plane)
        }
    }

    @Test
    fun onlyThePiecesThatStandOnTheFloorCastAShadow() {
        val onFloor = layers.pieces.filter { it.onFloor }.map { it.name }.toSet()
        assertEquals(
            setOf(
                "sofa",
                "floor_lamp",
                "side_table_plant",
                "cat_tree_slab_base",
                "counter",
                "fridge",
            ),
            onFloor,
        )
    }

    private companion object {
        const val LayerPieceFlat = RoomLayerArt.FLAT_Z
    }
}
