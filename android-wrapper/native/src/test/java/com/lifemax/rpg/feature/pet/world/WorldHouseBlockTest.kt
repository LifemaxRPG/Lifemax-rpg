package com.lifemax.rpg.feature.pet.world

import com.lifemax.rpg.feature.pet.room.HouseMap
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The `house` key of the page's world block (the rooms the cats' bond has opened, docs/specs/
 * CAT_HOUSE_EXPANSION_PLAN.md): optional, strict, and the only way a room opens.
 */
class WorldHouseBlockTest {
    @After fun back() = HouseMap.reset()

    private fun block(vararg rooms: String?): JSONObject =
        WorldFixtures.block("kitten").also {
            it.remove("house")
            if (rooms.isNotEmpty()) {
                it.put(
                    "house",
                    JSONObject().put("v", 1).put("rooms", JSONArray(rooms.filterNotNull())),
                )
            }
        }

    private fun refused(b: JSONObject, code: String) {
        try {
            WorldBlockJson.decode(b)
            throw AssertionError("a block with $b.house was accepted")
        } catch (e: WorldFormatException) {
            assertEquals(code, e.code)
        }
    }

    @Test
    fun aBlockWithoutHouseOpensNothing() {
        assertEquals(emptySet<String>(), WorldBlockJson.decode(block()).houseRooms)
    }

    @Test
    fun theRoomsThePageNamesAreRead() {
        assertEquals(setOf("sun", "study"), WorldBlockJson.decode(block("sun", "study")).houseRooms)
        assertEquals(
            emptySet<String>(),
            WorldBlockJson.decode(
                    block("none").also { it.getJSONObject("house").put("rooms", JSONArray()) }
                )
                .houseRooms,
        )
    }

    @Test
    fun aMalformedHouseIsRefused() {
        refused(block("sun").also { it.getJSONObject("house").put("v", 2) }, "version")
        refused(block("sun").also { it.getJSONObject("house").put("extra", 1) }, "shape")
        refused(block("sun").also { it.getJSONObject("house").remove("rooms") }, "shape")
        refused(block("Sun"), "range")
        refused(block("sun", "sun"), "shape")
        refused(block("sun").also { it.getJSONObject("house").put("rooms", "sun") }, "type")
        refused(block(*Array(WorldContract.MAX_HOUSE_ROOMS + 1) { "r${('a' + it)}" }), "range")
        refused(block("sun").also { it.put("house", 3) }, "type")
    }

    @Test
    fun theAppsFeedOpensTheRoomsBeforeTheArtIsMade() {
        val told = ArrayList<Set<String>>()
        val feed = WorldFeed { told += it }
        assertTrue(feed.offer(block("sun", "study", "nowhere")))
        assertEquals(listOf(setOf("sun", "study", "nowhere")), told)
        // what the app's feed does with it: rooms it does not know are left out, the rest open
        HouseMap.select(told.single(), force = true)
        assertEquals(
            listOf("study", "living", "bedroom", "kitchen", "sun"),
            HouseMap.current.rooms.map { it.id },
        )
        // a block no newer than the latest tells nobody again
        assertTrue(!feed.offer(block("sun", "study", "nowhere")))
        assertEquals(1, told.size)
    }

    @Test
    fun aFeedOfATestLeavesTheHouseAlone() {
        val feed = WorldFeed()
        assertTrue(feed.offer(block("sun", "study", "bath")))
        assertEquals(3, HouseMap.current.rooms.size)
    }

    @Test
    fun theWidestHouseIsWhatABlobMayName() {
        assertEquals(-576.0, WorldContract.HOUSE_X_MIN, 0.0)
        assertEquals(576.0, WorldContract.HOUSE_X_MAX, 0.0)
        assertFalse(WorldContract.HOUSE_X_MIN > WorldContract.HOUSE_X_MAX)
    }
}
