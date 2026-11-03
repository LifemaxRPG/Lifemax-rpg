package com.lifemax.rpg.feature.pet.art

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The animation table against the web's `petAssets.anims` and `petAssets.depth.anims`, recorded
 * from the page.
 */
class AnimTableTest {
    private val table = PetArtResources.load().anims
    private val web = PetArtFixtures.web

    /**
     * The web's rows in the order the page lists them (recorded as `order`, since JSONObject does
     * not keep key order).
     */
    private fun webRows(anims: JSONObject, order: JSONArray): List<Pair<String, JSONObject>> =
        List(order.length()) {
            order.getString(it).let { name -> name to anims.getJSONObject(name) }
        }

    @Test
    fun adultRowsAreTheWebsInOrderWithSameFramesFpsAndFeet() {
        val rows = webRows(web.getJSONObject("anims"), web.getJSONArray("animOrder"))
        assertEquals(28, rows.size)
        // the page's 28 rows first and unchanged; the lab rows (LabArtTest) follow them
        assertEquals(rows.map { it.first }, table.adult.take(rows.size).map { it.name })
        for ((i, pair) in rows.withIndex()) {
            val (name, a) = pair
            val got = table.adult[i]
            assertEquals(name, i, got.row)
            assertEquals(name, a.getInt("row"), got.row)
            assertEquals(name, a.getInt("n"), got.frames)
            assertEquals(name, a.getDouble("fps"), got.fps, 0.0)
            assertEquals(name, a.getInt("bottom"), got.bottom)
            assertFalse(name, got.depth)
        }
    }

    @Test
    fun depthRowsAreTheWebsInOrderWithSameFramesFpsAndFeet() {
        val depth = web.getJSONObject("depth")
        val rows = webRows(depth.getJSONObject("anims"), depth.getJSONArray("animOrder"))
        assertEquals(
            listOf("walk_front", "walk_back", "sniff", "bonk"),
            table.depth.map { it.name },
        )
        assertEquals(rows.map { it.first }, table.depth.map { it.name })
        for ((i, pair) in rows.withIndex()) {
            val got = table.depth[i]
            assertEquals(pair.first, pair.second.getInt("row"), got.row)
            assertEquals(pair.first, pair.second.getInt("n"), got.frames)
            assertEquals(pair.first, pair.second.getDouble("fps"), got.fps, 0.0)
            assertEquals(pair.first, pair.second.getInt("bottom"), got.bottom)
            assertTrue(pair.first, got.depth)
        }
    }

    @Test
    fun frameSizeAndColumnsAreTheSheetsLayout() {
        assertEquals(web.getInt("W"), table.frameWidth)
        assertEquals(web.getInt("H"), table.frameHeight)
        assertEquals(web.getInt("cols"), table.adultColumns)
        assertEquals(web.getJSONObject("depth").getInt("W"), table.frameWidth)
        assertEquals(6, table.depthColumns)
        // no row holds more frames than its sheet has columns
        assertTrue(table.adult.all { it.frames <= table.adultColumns })
        assertTrue(table.depth.all { it.frames <= table.depthColumns })
    }

    @Test
    fun namesAreUniqueAndLookedUpByName() {
        // the page's 28 rows, the 12 lab rows (LabArtTest) and the 4 depth rows
        assertEquals(44, table.names.size)
        assertEquals(table.names.toSet().size, table.names.size)
        assertEquals(7.0, table["idle_sit"]!!.fps, 0.0)
        assertTrue(table.isDepth("sniff"))
        assertFalse(table.isDepth("walk"))
        assertFalse(table.isDepth("nonsense"))
        assertNull(table["nonsense"])
    }

    @Test
    fun frameRectsLieOnTheFrameGridAndWrapLikeTheWebsModulo() {
        val walk = table["walk"]!!
        assertEquals(FrameRect(0, 48, 72, 48), table.frameRect(walk, 0))
        assertEquals(FrameRect(7 * 72, 48, 72, 48), table.frameRect(walk, 7))
        assertEquals(FrameRect(0, 48, 72, 48), table.frameRect(walk, 8))
        assertEquals(FrameRect(7 * 72, 48, 72, 48), table.frameRect(walk, -1))
        val bonk = table["bonk"]!!
        assertEquals(FrameRect(72, 3 * 48, 72, 48), table.frameRect(bonk, 1))
    }

    @Test
    fun frameAtFollowsFloorOfSecondsTimesFpsModuloFrames() {
        val walk = table["walk"]!! // 8 frames at 12 fps
        assertEquals(0, table.frameAt(walk, 0.0))
        assertEquals(0, table.frameAt(walk, 0.08))
        assertEquals(1, table.frameAt(walk, 0.1))
        assertEquals(7, table.frameAt(walk, 0.6))
        assertEquals(0, table.frameAt(walk, 0.7))
        val loafW = table["loaf_w"]!! // 4 frames at 1.5 fps
        assertEquals(1, table.frameAt(loafW, 0.7))
        assertEquals(3, table.frameAt(loafW, 2.1))
    }
}
