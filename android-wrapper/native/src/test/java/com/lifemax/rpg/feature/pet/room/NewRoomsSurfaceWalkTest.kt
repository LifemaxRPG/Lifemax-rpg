package com.lifemax.rpg.feature.pet.room

import com.lifemax.rpg.feature.pet.art.PetArtResources
import com.lifemax.rpg.feature.pet.body.JumpCapability
import com.lifemax.rpg.feature.pet.body.Occluders
import com.lifemax.rpg.feature.pet.body.ParkourPlanner
import com.lifemax.rpg.feature.pet.body.Solids
import com.lifemax.rpg.feature.pet.body.SurfaceId
import com.lifemax.rpg.feature.pet.body.World
import com.lifemax.rpg.feature.pet.brain.Spots
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The raised places of the rooms that open with the cats' bond (WP-H2: the Sunroom's bench, the
 * Study's desk and shelf, the Bathroom's tub rim and sink) agree with the art the way the three
 * first rooms' do: every [SurfaceWalkTest] check, run with the whole house open and the new
 * surfaces added, and the body's tables (surfaces, solids, occluders, places) against what
 * `tools/gen_pet_room_new.js` drew into `layers_ext.json`.
 */
class NewRoomsSurfaceWalkTest : SurfaceWalkTest() {
    override val opened = setOf("sun", "study", "bath")

    override val surfaces =
        super.surfaces +
            listOf(SurfaceId.BENCH, SurfaceId.DESK, SurfaceId.SHELF, SurfaceId.TUB, SurfaceId.SINK)

    override val solidPieces =
        super.solidPieces +
            mapOf(
                "sun_bench" to "sun_bench",
                "wicker_chair" to "wicker_chair",
                "study_shelf" to "study_shelf",
                "desk" to "desk",
                "desk_chair" to "desk_chair",
                "bathtub" to "bathtub",
                "sink_cabinet" to "sink_cabinet",
            )

    private val houseX0 = mapOf("sun" to 384, "study" to -384, "bath" to -576)

    private val rows: List<JSONObject> by lazy {
        val text =
            NewRoomsSurfaceWalkTest::class
                .java
                .getResourceAsStream("/pet/room/layers_ext.json")!!
                .readBytes()
                .toString(Charsets.UTF_8)
        val all = JSONObject(text).getJSONArray("pieces")
        List(all.length()) { all.getJSONObject(it) }
    }

    @After fun closeThemAgain() = HouseMap.reset()

    @Test
    fun theBodysSurfacesAreTheOnesTheArtWasDrawnWith() {
        var stands = 0
        for (row in rows) {
            val stand = row.optJSONObject("stand") ?: continue
            val x0 = houseX0.getValue(row.getString("room"))
            for (key in stand.keys()) {
                val id = SurfaceId.entries.first { it.key == key }
                val at = stand.getJSONArray(key)
                val s = World.surface(id)
                assertEquals("$key row", at.getInt(0).toFloat(), s.y, 0f)
                assertEquals("$key left", (x0 + at.getInt(1)).toFloat(), s.x0, 0f)
                assertEquals("$key right", (x0 + at.getInt(2)).toFloat(), s.x1, 0f)
                assertEquals(
                    "$key is carried by ${row.getString("name")}",
                    key,
                    row.getJSONArray("surfaces").getString(0),
                )
                stands++
            }
        }
        assertEquals("five raised places", 5, stands)
    }

    @Test
    fun everyStandingPieceOfTheNewRoomsIsInTheOccludersTable() {
        HouseMap.select(opened, force = true)
        val layers = PetArtResources.loadRoom().layers!!
        var checked = 0
        for (p in layers.pieces) {
            val v = p.volume ?: continue
            if (p.name in CORE_PIECES) continue
            val o = Occluders.pieces.firstOrNull { it.name == p.name }
            requireNotNull(o) { "the table has no ${p.name}" }
            assertEquals(p.name, (p.houseX + RoomLayout.HOUSE_X0).toFloat(), o.x0, 0f)
            assertEquals(p.name, (p.houseX + p.width + RoomLayout.HOUSE_X0).toFloat(), o.x1, 0f)
            assertEquals(p.name, p.y.toFloat(), o.top, 0f)
            assertEquals(p.name, (p.y + p.height).toFloat(), o.bottom, 0f)
            assertEquals(p.name, v.sortFront, o.front, 0f)
            checked++
        }
        assertEquals("the ten standing pieces of the new rooms", 10, checked)
    }

    @Test
    fun theNewPlacesAreOnTheirSurfacesAndOpenWithTheirRoom() {
        val raised =
            listOf(
                Spots.SUN_BENCH to SurfaceId.BENCH,
                Spots.DESK to SurfaceId.DESK,
                Spots.SHELF to SurfaceId.SHELF,
                Spots.TUB_RIM to SurfaceId.TUB,
                Spots.SINK to SurfaceId.SINK,
            )
        HouseMap.select(opened, force = true)
        for ((spot, id) in raised) {
            val s = World.surface(id)
            assertEquals(spot.id, id, spot.surface)
            assertTrue("${spot.id} x ${spot.x} on $s", spot.x >= s.x0 && spot.x <= s.x1)
            assertTrue("${spot.id} is open", spot.open)
            assertTrue("${spot.id} holds one cat", spot.single)
        }
        HouseMap.reset()
        for ((spot, _) in raised) assertTrue("${spot.id} is closed", !spot.open)
    }

    @Test
    fun aGrownCatJumpsToEveryNewPlaceAndTheShelfIsReachedOverTheDesk() {
        HouseMap.select(opened, force = true)
        val ways =
            listOf(
                Spots.SUN_BENCH to listOf(SurfaceId.BENCH),
                Spots.DESK to listOf(SurfaceId.DESK),
                Spots.SHELF to listOf(SurfaceId.DESK, SurfaceId.SHELF),
                Spots.TUB_RIM to listOf(SurfaceId.TUB),
                Spots.SINK to listOf(SurfaceId.SINK),
            )
        for ((spot, via) in ways) {
            val route =
                ParkourPlanner.plan(
                    JumpCapability.ADULT,
                    SurfaceId.FLOOR,
                    spot.x + FLOOR_START,
                    spot.surface,
                    spot.x,
                )
            assertNotNull("a way to the ${spot.id}", route)
            assertEquals(spot.id, via, route!!.leaps.map { it.to })
        }
        // a small kitten reaches the bench but not the high shelf
        val kitten = JumpCapability.forStage(0)
        assertNull(
            ParkourPlanner.plan(kitten, SurfaceId.FLOOR, -330f, SurfaceId.SHELF, Spots.SHELF.x)
        )
    }

    @Test
    fun theSolidsOfTheNewRoomsAreCarriedByTheirSurfaces() {
        val owned =
            mapOf(
                "sun_bench" to SurfaceId.BENCH,
                "study_shelf" to SurfaceId.SHELF,
                "desk" to SurfaceId.DESK,
                "bathtub" to SurfaceId.TUB,
                "sink_cabinet" to SurfaceId.SINK,
            )
        for ((name, id) in owned) {
            val solid = Solids.all.first { it.name == name }
            assertTrue("$name is no obstacle to $id", !solid.blocks(id, SurfaceId.FLOOR))
            assertTrue("$name stops any other leap", solid.blocks(SurfaceId.COUCH, SurfaceId.FLOOR))
        }
    }

    private companion object {
        const val FLOOR_START = -20f

        val CORE_PIECES =
            setOf(
                "sofa",
                "sofa_cushion_yellow",
                "sofa_cushion_pink",
                "floor_lamp",
                "side_table_plant",
                "bookcase_plant",
                "cat_tree_post",
                "cat_tree_cubby",
                "cat_tree_slab_base",
                "cat_tree_slab_mid",
                "cat_tree_slab_top",
                "counter",
                "fridge",
            )
    }
}
