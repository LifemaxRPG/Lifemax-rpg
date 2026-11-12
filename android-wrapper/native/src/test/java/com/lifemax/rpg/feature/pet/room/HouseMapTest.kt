package com.lifemax.rpg.feature.pet.room

import com.lifemax.rpg.feature.pet.art.ArgbImage
import com.lifemax.rpg.feature.pet.art.PetArtResources
import com.lifemax.rpg.feature.pet.body.HousePlans
import com.lifemax.rpg.feature.pet.body.World
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The house as data (`house.json`, WP-H1 of the cat house expansion plan): with nothing unlocked it
 * is the three rooms it has always been, and the rooms that open with the cats' bond are drawn,
 * walked in and shown only once they are open.
 */
class HouseMapTest {
    @After fun back() = HouseMap.reset()

    private val all = setOf("sun", "study", "bath")

    /** Opens rooms whatever an earlier test's art load locked. */
    private fun open(rooms: Set<String>) = HouseMap.select(rooms, force = true)

    @Test
    fun withNothingUnlockedTheHouseIsTheThreeRoomsItAlwaysWas() {
        val map = HouseMap.current
        assertEquals(listOf("living", "bedroom", "kitchen"), map.rooms.map { it.id })
        assertEquals(-192, map.x0)
        assertEquals(576, map.width)
        assertEquals(-154f, map.walkMin, 0f)
        assertEquals(346f, map.walkMax, 0f)
        assertEquals(listOf(-152, 0, 280), map.windows.toList())
        assertEquals(listOf(-152, 0), map.floorLights.toList())
        assertEquals(-192, RoomLayout.HOUSE_X0)
        assertEquals(576, RoomLayout.HOUSE_WIDTH)
        assertEquals(-154f, World.HOUSE_MIN_X, 0f)
        assertEquals(346f, World.HOUSE_MAX_X, 0f)
        assertEquals(-154f, World.surface(com.lifemax.rpg.feature.pet.body.SurfaceId.FLOOR).x0, 0f)
    }

    @Test
    fun theWebsRoomSplitIsKept() {
        for (x in listOf(-300f, -154f, -4.1f)) assertEquals("x $x", 0, World.roomOf(x))
        for (x in listOf(-4f, 0f, 100f, 195.9f)) assertEquals("x $x", 1, World.roomOf(x))
        for (x in listOf(196f, 300f, 346f, 900f)) assertEquals("x $x", 2, World.roomOf(x))
    }

    @Test
    fun theRoomsThatCatsGrowIntoOpenOutwardFromTheOldHouse() {
        open(setOf("bath")) // the study is not open: the bathroom is cut off
        assertEquals(3, HouseMap.current.rooms.size)
        open(setOf("sun"))
        assertEquals(
            listOf("living", "bedroom", "kitchen", "sun"),
            HouseMap.current.rooms.map { it.id },
        )
        assertEquals(-192, HouseMap.current.x0)
        assertEquals(768, HouseMap.current.width)
        assertEquals(538f, World.HOUSE_MAX_X, 0f)
        open(setOf("study", "bath"))
        assertEquals(
            listOf("bath", "study", "living", "bedroom", "kitchen"),
            HouseMap.current.rooms.map { it.id },
        )
        assertEquals(-576, RoomLayout.HOUSE_X0)
        assertEquals(-538f, World.HOUSE_MIN_X, 0f)
        open(all)
        assertEquals(1152, RoomLayout.HOUSE_WIDTH)
        assertEquals(-538f, World.HOUSE_MIN_X, 0f)
        assertEquals(538f, World.HOUSE_MAX_X, 0f)
        assertEquals(listOf(-576, -384, -192, 0, 192, 384), HouseMap.current.rooms.map { it.x0 })
        assertEquals(
            "the floor reaches the new walls",
            538f,
            World.surface(com.lifemax.rpg.feature.pet.body.SurfaceId.FLOOR).x1,
            0f,
        )
    }

    @Test
    fun everyPictureTheScreensMakeABitmapOfExistsWhateverIsOpen() {
        // ComposeRoomCanvas asks the art for every RoomImage; a room that is not open is one clear
        // pixel
        for (rooms in listOf(emptySet(), setOf("sun"), all)) {
            open(rooms)
            val art = PetArtResources.loadRoom()
            for (image in RoomImage.entries) {
                val picture = art.image(image)
                assertTrue("$image with $rooms", picture.width >= 1 && picture.height >= 1)
            }
        }
    }

    @Test
    fun theChoiceIsKeptOnceTheArtIsMadeFromIt() {
        HouseMap.reset()
        assertTrue(HouseMap.select(setOf("sun")))
        assertEquals(4, HouseMap.current.rooms.size)
        PetArtResources.loadRoom() // the art now has the sunroom: the map stays as it is
        assertEquals(false, HouseMap.select(setOf("sun", "study", "bath")))
        assertEquals(4, HouseMap.current.rooms.size)
        assertTrue(HouseMap.select(all, force = true))
        assertEquals(6, HouseMap.current.rooms.size)
    }

    @Test
    fun aRoomThisGameDoesNotKnowIsIgnored() {
        open(setOf("garden", "sun"))
        assertEquals(
            listOf("living", "bedroom", "kitchen", "sun"),
            HouseMap.current.rooms.map { it.id },
        )
    }

    @Test
    fun theRoomsKnowWhenTheyOpen() {
        val bonds = HouseMap.everything.associate { it.id to it.unlockBond }
        assertEquals(
            mapOf("living" to null, "bedroom" to null, "kitchen" to null),
            bonds.filterValues { it == null },
        )
        assertEquals(2, bonds["sun"])
        assertEquals(3, bonds["study"])
        assertEquals(4, bonds["bath"])
    }

    @Test
    fun roomsAreFoundByTheirSeamsWhenAllAreOpen() {
        open(all)
        val seams = listOf(-384f, -192f, -4f, 196f, 384f)
        for ((i, cut) in seams.withIndex()) {
            assertEquals("just left of $cut", i, World.roomOf(cut - 0.1f))
            assertEquals("at $cut", i + 1, World.roomOf(cut))
        }
        assertEquals(0, World.roomOf(-576f))
        assertEquals(5, World.roomOf(576f))
    }

    /** The three-way roll the planner used before the house was data. */
    private fun oldTravel(room: Int, roll: Float): Int =
        when (room) {
            0 -> if (roll < 0.7f) 1 else 2
            2 -> if (roll < 0.7f) 1 else 0
            else -> if (roll < 0.5f) 0 else 2
        }

    @Test
    fun aTripAcrossTheOldThreeRoomsIsChosenAsItWas() {
        val plans = HousePlans(com.lifemax.rpg.feature.pet.body.Rng(1), 0, false)
        for (room in 0..2) {
            var roll = 0f
            while (roll < 1f) {
                assertEquals(
                    "room $room roll $roll",
                    oldTravel(room, roll),
                    plans.otherRoom(room, 3, roll),
                )
                roll += 0.001f
            }
        }
    }

    @Test
    fun aTripNeverStaysInTheRoomAndReachesEveryOtherRoom() {
        val plans = HousePlans(com.lifemax.rpg.feature.pet.body.Rng(1), 0, false)
        for (count in 3..6) for (here in 0 until count) {
            val seen = HashSet<Int>()
            var roll = 0f
            while (roll < 1f) {
                val there = plans.otherRoom(here, count, roll)
                assertTrue(
                    "count $count here $here: $there",
                    there in 0 until count && there != here,
                )
                seen.add(there)
                roll += 0.002f
            }
            assertEquals("count $count here $here", count - 1, seen.size)
        }
    }

    @Test
    fun aNeighbourIsTheMostLikelyDestination() {
        val plans = HousePlans(com.lifemax.rpg.feature.pet.body.Rng(1), 0, false)
        var near = 0
        val n = 1000
        for (i in 0 until n) {
            val there = plans.otherRoom(2, 6, (i + 0.5f) / n)
            if (there == 1 || there == 3) near++
        }
        assertEquals(0.7, near / n.toDouble(), 0.01)
    }

    // ---------------------------------------------------------------- the art of the six rooms

    private fun sixRooms(): Pair<RoomArt, RoomLayerArt> {
        open(all)
        val art = PetArtResources.loadRoom()
        return art to art.layers!!
    }

    @Test
    fun everyRoomsPiecesDrawnOverItsBaseGiveItsBackdropExactly() {
        val (art, layers) = sixRooms()
        val w = RoomLayout.BACKDROP_WIDTH
        for ((i, room) in HouseMap.current.rooms.withIndex()) {
            val out = layers.base(i).pixels.copyOf()
            for (piece in layers.pieces.filter { it.room == i }) {
                for (y in 0 until piece.height) for (x in 0 until piece.width) {
                    val p = layers.atlas[piece.atlasX + x, piece.atlasY + y]
                    if (p ushr 24 != 0) out[(piece.y + y) * w + piece.x + x] = p
                }
            }
            val want = art.image(RoomImage.backdrop(room.id)).pixels
            var wrong = 0
            for (k in out.indices) if (out[k] != want[k]) wrong++
            assertEquals("room ${room.id}: $wrong pixels differ", 0, wrong)
        }
    }

    @Test
    fun theNewRoomsAreWholePicturesWithTheirWindowsClear() {
        val (art, layers) = sixRooms()
        for (id in listOf("sun", "study", "bath")) {
            val base = layers.baseOf(id)
            assertEquals(RoomLayout.BACKDROP_WIDTH, base.width)
            assertEquals(RoomLayout.HEIGHT, base.height)
            val def = HouseMap.current.rooms.first { it.id == id }
            var clear = 0
            for (y in 0 until base.height) for (x in 0 until base.width) {
                if (base[x, y] ushr 24 == 0) clear++
            }
            val glass = if (def.window == null) 0 else RoomLayout.GLASS_W * RoomLayout.GLASS_H
            // a mullion or two stay painted in front of the sky: the glass less its bars
            assertTrue("$id: $clear clear pixels, glass $glass", clear in (glass * 9 / 10)..glass)
            if (def.window != null) {
                val gx = def.window - def.x0 + RoomLayout.GLASS_X
                assertEquals(0, base[gx + 5, RoomLayout.GLASS_Y + 5] ushr 24)
            }
            val colours = HashSet<Int>()
            val back = art.image(RoomImage.backdrop(id))
            for (p in back.pixels) colours.add(p)
            assertTrue("$id uses ${colours.size} colours", colours.size in 20..110)
        }
    }

    @Test
    fun theNewPiecesStandInsideTheirRoomsAtTheDepthTheirBaseSays() {
        val (_, layers) = sixRooms()
        val floorRow = Parallax.WALL_ROW
        for (piece in layers.pieces.filter { layers.roomIds[it.room] in all }) {
            assertTrue(
                piece.name,
                piece.x >= 0 && piece.x + piece.width <= RoomLayout.BACKDROP_WIDTH,
            )
            assertTrue(piece.name, piece.y >= 0 && piece.y + piece.height <= RoomLayout.HEIGHT)
            val base = piece.baseY
            if (base != null) {
                assertTrue(
                    "${piece.name} stands on the floor or the skirting",
                    base >= floorRow - 6,
                )
                assertEquals(DepthMath.zFromRow(base.toFloat()), piece.z, 0.01f)
            }
        }
        val solid = layers.pieces.filter { it.volume != null }
        assertTrue("the cat's mask holds ${solid.size} pieces", solid.size <= DepthOrder.MAX_PIECES)
        for (room in HouseMap.current.rooms.indices.filter { layers.roomIds[it] in all }) {
            val own = solid.count { it.room == room }
            assertTrue("room $room has $own solid pieces (the rule is five)", own <= 5)
        }
    }

    private fun compose(art: RoomArt, hour: Double): ArgbImage {
        val scene = RoomScene(art)
        val canvas = SoftCanvas(art)
        scene.paintBackdrop(canvas, hour, 12.0, 0)
        scene.paintScene(canvas, NoCats)
        scene.paintOverlay(canvas, hour, 0)
        return canvas.picture()
    }

    @Test
    fun theOldRoomsLookTheSameWhateverElseIsOpen() {
        val three = PetArtResources.loadRoom()
        val before = listOf(6.0, 12.0, 21.0).map { compose(three, it) }
        val (six, _) = sixRooms()
        for ((k, hour) in listOf(6.0, 12.0, 21.0).withIndex()) {
            val now = compose(six, hour)
            assertEquals(1152, now.width)
            val shift = 384 // the old house now starts three rooms in from the left
            var wrong = 0
            for (y in 0 until before[k].height) for (x in 0 until before[k].width) {
                // the lamp's glow is the only thing the whole-house tint changes at the old walls
                if (before[k][x, y] != now[x + shift, y]) wrong++
            }
            assertEquals("hour $hour: pixels of the old rooms that moved", 0, wrong)
        }
    }

    @Test
    fun theSixRoomHouseDrawsInTheDepthRoomToo() {
        val (art, _) = sixRooms()
        val scene = RoomScene(art, RoomStyle.depth25(DepthLevel.MEDIUM))
        val canvas = SoftCanvas(art, 6)
        scene.depth!!.warm()
        for (hour in listOf(9.0, 14.0, 22.0)) {
            scene.paintBackdrop(canvas, hour, 3.0, 6)
            scene.paintScene(canvas, NoCats)
            scene.paintOverlay(canvas, hour, 6)
        }
        val picture = canvas.picture()
        assertEquals(1152, picture.width)
        // every room has something painted all over it: no blank room
        for (room in 0 until 6) {
            var opaque = 0
            for (y in 20 until RoomLayout.HEIGHT) for (x in 0 until RoomLayout.BACKDROP_WIDTH) {
                if (picture[room * RoomLayout.BACKDROP_WIDTH + x, y] ushr 24 != 0) opaque++
            }
            assertTrue("room $room is painted ($opaque)", opaque > RoomLayout.BACKDROP_WIDTH * 100)
        }
    }
}
